package cn.orangenode.net;

import cn.orangenode.net.entity.ConnectionInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Global {
    //连接超时时间
    public static final Integer CONNECT_TIMEOUT = 3000;
    //命令执行间隔
    public static final Integer SHELL_WAIT_TIME = 500;
    //连接线程池
    public static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(50);
    //备份失败列表，涉及多线程操作，所以使用Collections.synchronizedList加锁
    public static List<ConnectionInfo> BACKUP_FAIL_ARRAY_LIST = Collections.synchronizedList(new ArrayList<>());
    //计数器等待线程池运行完毕
    public static CountDownLatch COUNT_DOWN_LATCH;
}