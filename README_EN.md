# Java-Based Network Device Auto Backup Tool

I needed an automated network backup tool for my business, but since I hadn't studied Python much, I decided to write it in Java.

## Dependencies

```xml
<!-- jsch for SSH -->
<dependency>
  <groupId>com.jcraft</groupId>
  <artifactId>jsch</artifactId>
  <version>0.1.55</version>
</dependency>

<!-- commons-net for Telnet -->
<dependency>
  <groupId>commons-net</groupId>
  <artifactId>commons-net</artifactId>
  <version>3.9.0</version>
</dependency>

<!-- Lombok -->
<dependency>
  <groupId>org.projectlombok</groupId>
  <artifactId>lombok</artifactId>
  <version>1.18.22</version>
</dependency>

<!-- Logging -->
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

<!-- POI Dependency for Excel -->
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

## Core Code

### Entity Class

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionInfo {
    // Device name
    private String sysName;
    // Address
    private String host;
    // Username
    private String username;
    // Password
    private String password;
    // Port
    private Integer port;
    // Connection type (1: SSH, 2: Telnet)
    private Integer connectType;
    // Privilege password
    private String enablePassword;
    // Brand
    private String brand;
}
```

### Establishing Connection

Mainly using `jsch` to connect via SSH and `commons-net` for Telnet. Here is the core code snippet. You can visit the GitHub repository for the complete code.

For SSH connections, the basic steps are: `new Jsch()` -> `getSession()` -> `session.openChannel()` -> `channel.getOutputStream()/getInputStream` -> `channel.connect()` -> `Execute methods for retrieving streams, sending commands, etc.` -> `Close the streams (failure to close streams can cause thread hang-ups)`

For Telnet connections, the process is somewhat similar:

```java
// SSH connection method
private void connectSSH() throws JSchException, IOException, InterruptedException {
    // 3. Create objects, open session and channel
    // Create a Jsch object
    JSch jsch = new JSch();
    sshSession = jsch.getSession(connectionInfo.getUsername(),
            connectionInfo.getHost(),
            connectionInfo.getPort());// Configure connection information
    sshSession.setPassword(connectionInfo.getPassword());
    Properties config = new Properties();
    config.put("StrictHostKeyChecking", "no");
    sshSession.setConfig(config);
    sshSession.connect(Global.CONNECT_TIMEOUT);// Connect the session
    sshChannel = (ChannelShell) sshSession.openChannel("shell");
    // Get input and output streams
    bufferedWriter = (new BufferedWriter(new OutputStreamWriter(sshChannel.getOutputStream(), StandardCharsets.UTF_8)));
    bufferedReader = (new BufferedReader(new InputStreamReader(sshChannel.getInputStream(), StandardCharsets.UTF_8)));
    sshChannel.connect();// Connect the channel
    // 4. Send commands
    execute();// Send commands
}

// Telnet connection method
private void connectTelnet() throws IOException, InterruptedException {
    // 3. Create a Telnet client
    telnetClient = new TelnetClient("VT220");
    telnetClient.setConnectTimeout(Global.CONNECT_TIMEOUT);// Set timeout
    telnetClient.connect(this.connectionInfo.getHost());// Connect
    this.bufferedWriter = (new BufferedWriter(new OutputStreamWriter(
            telnetClient.getOutputStream()));
    this.bufferedReader = (new BufferedReader(new InputStreamReader(
            telnetClient.getInputStream()));
    // 4. Enter username and password
    sendShell(connectionInfo.getUsername());
    sendShell(connectionInfo.getPassword());
    // 5. Send commands
    execute();
}

/**
 * Execute
 */
private void execute() throws IOException, InterruptedException {
    // 1. Get login information and process some non-command information
    String firstText = getResultString(bufferedReader);
    //[Adapted for Huawei] Error: Please choose 'YES' or 'NO' first before pressing 'Enter'. [Y/N]:
    if (firstText.contains("The password needs to be changed. Change now? [Y/N]")) {
        // Send 'n'
        sendShell("n");
    }
    // 2. Execute the command set
    shellList.forEach(this::sendShell);
    Thread.sleep(Global.SHELL_WAIT_TIME);
    bufferedWriter.write("saveSuccess");/*Save success delimiter*/
    bufferedWriter.flush();
    // 3. Get the return information
    String result = getResultString(bufferedReader);
    // 4. Check if the save was successful
    if (result.contains("saveSuccess")) {
        // 5. Handle More page breaks, as some devices do not have a command to disable paging
        String s = decodeResultText(result);
        // 6. Save the file
        Path backupDir = FilesUtils.createBackupDir();// Create a daily storage directory
        FilesUtils.saveFile(backupDir, s, connectionInfo);
        log.info(String.format("[%s-%s] Backup successful", this.connectionInfo.getSysName(), connectionInfo.getHost()));
    } else {
        // Save failed
        throw new RuntimeException("The 'saveSuccess' flag was not detected, so the save failed");
    }
}
```

### Reading Streams, Sending Commands, and Handling Delimiters

Please note that you need to introduce a `Thread.sleep(500)` delay to block the thread when retrieving data from streams or sending data to streams. Attempting to read a stream before data arrives or sending data before it is complete can result in incomplete data. The code examples below illustrate these principles.

**Reading Data from Streams**: In the code examples, we read the stream immediately after logging in, but this requires some efficiency sacrifice to block the thread. Otherwise, if you read the stream before data arrives, you may receive no data. Also, after reading each chunk of data from the stream, you need to wait because some devices do not have a command to disable paging, so you need to send a space, which also requires waiting for the next data to be returned. The explanation may not be perfect, but you can understand it based on the code.

**Sending Data to Streams**: Sending data to the stream also requires using `Thread.sleep` to block the thread. Additionally, you need to use `flush()` to refresh the stream data, as data will wait in the buffer until it is sent when the buffer is full. Therefore, you need to use `flush()` to send the data in real time.

**Handling More, Delimiters, etc.**: In some devices from manufacturers that have not disabled paging, you may encounter ANSI escape sequences such as `\u001B` and `\b` in the returned data. My approach is to use regular expressions for matching and replacing these sequences. If you have other solutions, they can also be explored.

Here is the code for handling ANSI escape sequences and delimiters:

```java
/**
 * Get the data from the stream
 */
private String getResultString(BufferedReader bufferedReader) throws IOException, InterruptedException {
    Thread.sleep(Global.SHELL_WAIT_TIME); /* Wait for data to be returned */
    /* Read the data */
    StringBuilder stringBuilder = new StringBuilder();
    char[] buffer = new char[4096];
    while (bufferedReader.ready()) {
        int i = bufferedReader.read(buffer, 0, buffer.length);
        if (i < 0) {
            break;
        }
        String s = new String(buffer, 0, i);
        stringBuilder.append(s);
        Thread.sleep(Global.SHELL_WAIT_TIME); /* Delay is needed to wait for data to be returned; otherwise, data may not be read */
    }
    return stringBuilder.toString();
}

/**
 * Send a command to the stream
 *
 * @param shell Command
 */
private void sendShell(String shell) {
    try {
        Thread.sleep(Global.SHELL_WAIT_TIME); /* Wait, or the command won't execute properly */
        bufferedWriter.write(shell); /* Send the command */
        bufferedWriter.newLine(); /* Send a carriage return for the next line */
        bufferedWriter.flush(); /* Refresh, or the command won't arrive in real time */
    } catch (IOException | InterruptedException e) {
        log.error(String.format("[%s-%s] Exception while executing the command!", connectionInfo.getSysName(), connectionInfo.getHost()));
    }
}

/**
 * Handling More, delimiters, etc.
 */
public String decodeResultText(String resultConfig) {
    String temp = resultConfig;
    /* Remove ESC */
    String regexESC = "\\s*\u001B\\[\\d*D";
    Pattern patternESC = Pattern.compile(regexESC);
    Matcher matcherESC = patternESC.matcher(temp);
    temp = matcherESC.replaceAll("");
    /* Remove all MORE */
    String regexMORE = "-*\\s*more*\\s*-*";
    Pattern patternMORE = Pattern.compile(regexMORE, Pattern.CASE_INSENSITIVE); /* Ignore case */
    Matcher matcherMORE = patternMORE.matcher(temp);
    temp = matcherMORE.replaceAll("");
    /* Remove all BS */
    String regexBS = "\\s*\b";
    Pattern patternBS = Pattern.compile(regexBS);
    Matcher matcherBS = patternBS.matcher(temp);
    temp = matcherBS.replaceAll("");
    return temp;
}
```
