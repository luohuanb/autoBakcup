package cn.orangenode.net.connect;

import cn.orangenode.net.Global;
import cn.orangenode.net.entity.ConnectionInfo;
import cn.orangenode.net.utils.FilesUtils;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.telnet.TelnetClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Connection implements Runnable {

    // 设备信息
    private final ConnectionInfo connectionInfo;
    //ssh客户端
    private Session sshSession;
    private ChannelShell sshChannel;
    //telnet客户端
    TelnetClient telnetClient;
    //设备命令集
    public ArrayList<String> shellList;
    /*输入输出流*/
    private BufferedWriter bufferedWriter;
    private BufferedReader bufferedReader;


    /**
     * 初始化连接
     *
     * @param connectionInfo 连接信息
     * @param shellList      命令集
     */
    public Connection(ConnectionInfo connectionInfo, ArrayList<String> shellList) {
        this.connectionInfo = connectionInfo;
        this.shellList = shellList;
    }

    //执行备份
    @Override
    public void run() {
        try {
            // 1. 判断连接类型ssh/telnet
            if (connectionInfo.getConnectType() == 1) {
                // 2.连接ssh
                connectSSH();
            } else {
                // 2.连接telnet
                connectTelnet();
            }
        } catch (Exception e) {
            //保存失败
            log.error(String.format("[%s-%s]备份失败,%s", connectionInfo.getSysName(), connectionInfo.getHost(), e.getMessage()));
            //保存失败加入保存队列
            Global.BACKUP_FAIL_ARRAY_LIST.add(connectionInfo);
        } finally {
            //关闭所有连接
            try {
                if (bufferedWriter != null) {
                    bufferedWriter.close();
                }
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (sshChannel != null && sshChannel.isConnected()) {
                    sshChannel.disconnect();
                }
                if (sshSession != null && sshSession.isConnected()) {
                    sshSession.disconnect();
                }
                if (telnetClient != null && telnetClient.isConnected()) {
                    telnetClient.disconnect();
                }
            } catch (IOException e) {
                log.error(String.format("关闭连接时出错:%s", e.getMessage()));
            }
            Global.COUNT_DOWN_LATCH.countDown();
        }

    }

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
}
