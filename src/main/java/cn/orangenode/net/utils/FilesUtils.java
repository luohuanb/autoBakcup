package cn.orangenode.net.utils;

import cn.orangenode.net.entity.ConnectionInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
public class FilesUtils {
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public static String getYMD(){
        return SIMPLE_DATE_FORMAT.format(new Date());
    }

    /**
     * 创建备份文件存放目录
     *
     * @return 路径
     */
    public static Path createBackupDir() {
        //初始化配置文件保存路径”backupConfig/yyyy-MM-dd/yyyy-MM-dd_ip.txt“
        File file = new File(System.getProperty("user.dir") + "\\backupConfig");
        String timeYMD = getYMD();
        Path targetDir = Paths.get(file.getPath() + "\\" + timeYMD);
        // 判断文件夹是否存在
        // 不存在或存在但不是文件夹则创建一个文件夹
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            try {
                //createDirectories可创建多层目录，createDirectory只能创建单层目录
                Files.createDirectories(targetDir);
            } catch (IOException e) {
                log.error("创建文件夹时发成错误:" + e.getMessage());
            }
        }
        return targetDir;
    }

    /**
     * 写出设备配置文件
     *
     * @param backupDir 配置文件存放目录
     * @param text      内容
     * @param connectionInfo   连接信息
     */
    public static synchronized void saveFile(Path backupDir, String text, ConnectionInfo connectionInfo) {
        //生成File对象
        String backupFilePath = backupDir + "\\" + String.format("[%s-%s].txt", connectionInfo.getSysName(), connectionInfo.getHost());
        //向File写入配置
        FilesUtils.writeFile(new File(backupFilePath), text);
    }

    /**
     * 向File写入文本
     *
     * @param file 文件
     * @param text 文本
     */
    private static synchronized void writeFile(File file, String text) {
        // 写入数据
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(file);
            fileWriter.write(text);
            fileWriter.flush();
        } catch (IOException e) {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException ex) {
                    log.error("写出配置文件时发生异常:" + ex.getMessage());
                }
            }
            e.printStackTrace();
        }finally {
            if (fileWriter != null){
                try {
                    fileWriter.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
