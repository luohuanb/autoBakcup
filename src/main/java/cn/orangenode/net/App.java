package cn.orangenode.net;

import cn.orangenode.net.connect.Connection;
import cn.orangenode.net.entity.ConnectionInfo;
import cn.orangenode.net.utils.ExcelUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class App {


    public static void main(String[] args) throws Exception {
        System.out.println("\n" +
                "                  _             ____                   _                    \n" +
                "                 | |           |  _ \\                 | |                   \n" +
                "   __ _   _   _  | |_    ___   | |_) |   __ _    ___  | | __  _   _   _ __  \n" +
                "  / _` | | | | | | __|  / _ \\  |  _ <   / _` |  / __| | |/ / | | | | | '_ \\ \n" +
                " | (_| | | |_| | | |_  | (_) | | |_) | | (_| | | (__  |   <  | |_| | | |_) |\n" +
                "  \\__,_|  \\__,_|  \\__|  \\___/  |____/   \\__,_|  \\___| |_|\\_\\  \\__,_| | .__/ \n" +
                "                                                                     | |    \n" +
                "                                                                     |_|    \n" +
                "\n");
        /*在启动参数指定excel路径*/
        if (args.length < 1) {
            throw new Exception("缺少必要参数，请指定excel文件绝对路径");
        }
        String path = args[0];//获取文件路径
        //读取excel
        ArrayList<ConnectionInfo> excelConnects = ExcelUtils.importExcel(path);
        //使用计数器，等待程序运行结束
        Global.COUNT_DOWN_LATCH = new CountDownLatch(excelConnects.size());
        /*遍历excel表*/
        excelConnects.forEach(connectionInfo -> {
            ArrayList<String> shellList = null;
            if (connectionInfo.getBrand().equalsIgnoreCase("huawei")) {
                shellList = huaweiShell(connectionInfo);
            }
            Connection connection = new Connection(connectionInfo, shellList);
            //提交线程池
            Global.EXECUTOR_SERVICE.execute(connection);
        });
        Global.EXECUTOR_SERVICE.shutdown();


        Global.COUNT_DOWN_LATCH.await();//使用计数器,等待线程池运行完毕
        log.error(String.format("备份失败数量%s", Global.BACKUP_FAIL_ARRAY_LIST.size()));
    }

    //华为命令集
    public static ArrayList<String> huaweiShell(ConnectionInfo connectionInfo) {
        ArrayList<String> shellList = new ArrayList<>();
        //判断是否有特权密码
        if (connectionInfo.getEnablePassword() != null) shellList.add(connectionInfo.getEnablePassword());
        shellList.add("screen-length 0 temporary");
        shellList.add("display cu");
        /*针对没有取消分页的品牌设备，可以发送空格*/
        /*for (int i = 0; i < 10; i++) {
            shellList.add(" ");
        }*/
        return shellList;
    }
}
