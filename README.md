# 基于Java的网络设备自动备份工具

因为业务上有需要一个网络自动备份工具，但是我呢没怎么学习python，所以使用java进行编写。

## 依赖

```xml
<!--jsch  用于ssh -->
<dependency>
  <groupId>com.jcraft</groupId>
  <artifactId>jsch</artifactId>
  <version>0.1.55</version>
</dependency>

<!-- commons-net 用于telnet -->
<dependency>
  <groupId>commons-net</groupId>
  <artifactId>commons-net</artifactId>
  <version>3.9.0</version>
</dependency>

<!--lombok-->
<dependency>
  <groupId>org.projectlombok</groupId>
  <artifactId>lombok</artifactId>
  <version>1.18.22</version>
</dependency>

<!--日志-->
<dependency>
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
  <version>1.2.10</version>
</dependency>
<dependency>
  <groupId>org.apache.logging.log4j</groupId>
  <artifactId>log4j-core</artifactId>
  <version>2.20.0</version>
</dependency>

<!--  POI依赖 excel  -->
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi</artifactId>
  <version>5.2.3</version>
</dependency>
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi-ooxml</artifactId>
  <version>5.2.3</version>
</dependency>
```



## 核心代码

### 实体类

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionInfo {
    //设备名
    private String sysName;
    // 地址
    private String host;
    // 用户名
    private String username;
    // 密码
    private String password;
    // 端口
    private Integer port;
    // 连接类型（1:ssh,2:telnet）
    private Integer connectType;
    // 特权密码
    private String enablePassword;
    // 品牌
    private String brand;
}
```

### 创建连接

主要使用`jsch`连接`ssh`，`commons-net`连接`telnet`。这里只展示核心代码，需要完整代码的可以前往`github`仓库获取。

因为telnet和ssh大致一样，这里整理一下`ssh大概流程`：`new Jsch`() -> `getSession()` -> `session.openChannel()` -> `channel.getOutputStream()/getInputStream` -> `channel.connenct()` -> `执行获取流，发送命令等方法` -> `关闭流(如果不关闭流可能导致多线程任务挂起)`

```java
//ssh连接方式
private void connectSSH() throws JSchException, IOException, InterruptedException {
    // 3.创建对象，打开session和channel
    //创建Jsch对象
    JSch jsch = new JSch();
    sshSession = jsch.getSession(connectionInfo.getUsername(),
            connectionInfo.getHost(),
            connectionInfo.getPort());//配置连接信息
    sshSession.setPassword(connectionInfo.getPassword());
    Properties config = new Properties();
    config.put("StrictHostKeyChecking", "no");
    sshSession.setConfig(config);
    sshSession.connect(Global.CONNECT_TIMEOUT);//连接Session
    sshChannel = (ChannelShell) sshSession.openChannel("shell");
    //获取输入输出流
    bufferedWriter = (new BufferedWriter(new OutputStreamWriter(sshChannel.getOutputStream(), StandardCharsets.UTF_8)));
    bufferedReader = (new BufferedReader(new InputStreamReader(sshChannel.getInputStream(), StandardCharsets.UTF_8)));
    sshChannel.connect();//连接channel
    // 4.发送指令
    execute();//发送命令
}
//telnet连接方式
private void connectTelnet() throws IOException, InterruptedException {
    // 3.创建telnet客户端
    telnetClient = new TelnetClient("VT220");
    telnetClient.setConnectTimeout(Global.CONNECT_TIMEOUT);//设置超时时间
    telnetClient.connect(this.connectionInfo.getHost());//连接
    this.bufferedWriter = (new BufferedWriter(new OutputStreamWriter(
            telnetClient.getOutputStream())));
    this.bufferedReader = (new BufferedReader(new InputStreamReader(
            telnetClient.getInputStream())));
    // 4.输入用户名密码
    sendShell(connectionInfo.getUsername());
    sendShell(connectionInfo.getPassword());
    // 5.发送指令
    execute();
}
/**
 * 执行
 */
private void execute() throws IOException, InterruptedException {
    // 1. 获取登陆信息，处理一些非命令集的信息
    String firstText = getResultString(bufferedReader);
    //[适配Huawei] Error: Please choose 'YES' or 'NO' first before pressing 'Enter'. [Y/N]:
    if (firstText.contains("The password needs to be changed. Change now? [Y/N]")) {
        //发送 n
        sendShell("n");
    }
    // 2. 执行命令集
    shellList.forEach(this::sendShell);
    Thread.sleep(Global.SHELL_WAIT_TIME);
    bufferedWriter.write("saveSuccess");/*保存成功休止符*/
    bufferedWriter.flush();
    // 3. 获取返回信息
    String result = getResultString(bufferedReader);
    // 4. 判断是否保存成功
    if (result.contains("saveSuccess")) {
        // 5. 处理More分页符,有部分设备没有取消分页功能，所以需要手动的
        String s = decodeResultText(result);
        // 6. 保存文件
        Path backupDir = FilesUtils.createBackupDir();//创建每日存储目录
        FilesUtils.saveFile(backupDir, s, connectionInfo);
        log.info(String.format("[%s-%s]备份成功", this.connectionInfo.getSysName(), connectionInfo.getHost()));
    } else {
        //保存失败
        throw new RuntimeException("未检测到\"saveSuccess\"标识,保存失败");
    }
}
```

### 读取流、发送命令、处理返回的分隔符

这里需要注意流获取和发送过程中，我们需要使用`Thread.sleep(500)`阻塞一下线程。因为流还未到达，就获取流，或者流还未发送完成就获取流，会导致流读取失败读取的数据不完成情况。在下列代码中有具体实例。

`获取流内数据`：因为代码实例中，登陆后直接对流进行获取，只能牺牲部分效率对线程进行阻塞。否则登陆流信息还未返回就直接读取流会没数据返回，并且在每次将流内的数据读取完成后还需要等待，因为有时部分设备会`没有取消分页的指令`，我们只能`发送空格`这时也需要等待下一次的流返回`数据`。可能表达的不是那么好大家根据代码进行理解。

`向流内发送数据`：向流内发送数据也是如此都是需要`Thread.sleep`进行阻塞。并且还需要使用`flush()`对流内数据进行刷新，否则流数据会在`缓冲区`内等待缓存区满后再进行发送。所以需要使用`flush()`刷新缓冲区。

`处理More、分隔符等`：在一些没有`取消分页`厂商的设备中，我们获取到的返回值数据，会出现`\u001B`和`\b`之类的`ANSI转义序列`，我的方案是使用正则表达式进行匹配替换，大家有其他方案也可以探讨一下。

```java
/**
 * 获取流内返回数据
 */
private String getResultString(BufferedReader bufferedReader) throws IOException, InterruptedException {
    Thread.sleep(Global.SHELL_WAIT_TIME);/*等待数据返回时间*/
    /*读取数据*/
    StringBuilder stringBuilder = new StringBuilder();
    char[] buffer = new char[4096];
    while (bufferedReader.ready()) {
        int i = bufferedReader.read(buffer, 0, buffer.length);
        if (i < 0) {
            break;
        }
        String s = new String(buffer, 0, i);
        stringBuilder.append(s);
        Thread.sleep(Global.SHELL_WAIT_TIME);/*需要延迟等待数据返回否则读不到数据*/
    }
    return stringBuilder.toString();
}
/**
 * 向流发送命令
 *
 * @param shell 命令
 */
private void sendShell(String shell) {
    try {
        Thread.sleep(Global.SHELL_WAIT_TIME);/*等待，否则无法执行完毕*/
        bufferedWriter.write(shell);/*发送命令*/
        bufferedWriter.newLine();/*发送回车,下一行*/
        bufferedWriter.flush();/*刷新，否则命令不会实时到达*/
    } catch (IOException | InterruptedException e) {
        log.error(String.format("[%s-%s]执行命令时异常!", connectionInfo.getSysName(), connectionInfo.getHost()));
    }
}
/**
 * 处理More、分隔符等
 */
public String decodeResultText(String resultConfig) {
    String temp = resultConfig;
    /*删除ESC*/
    String regexESC = "\\s*\u001B\\[\\d*D";
    Pattern patternESC = Pattern.compile(regexESC);
    Matcher matcherESC = patternESC.matcher(temp);
    temp = matcherESC.replaceAll("");
    /*删除所有MORE*/
    String regexMORE = "-*\\s*more*\\s*-*";
    Pattern patternMORE = Pattern.compile(regexMORE, Pattern.CASE_INSENSITIVE);/*忽略大小写*/
    Matcher matcherMORE = patternMORE.matcher(temp);
    temp = matcherMORE.replaceAll("");
    /*删除所有BS*/
    String regexBS = "\\s*\b";
    Pattern patternBS = Pattern.compile(regexBS);
    Matcher matcherBS = patternBS.matcher(temp);
    temp = matcherBS.replaceAll("");
    return temp;
}
```



大致的代码就是这样，仓库地址：`https://gitee.com/lh789/network-system_auto-backup`
