package cn.orangenode.net.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
