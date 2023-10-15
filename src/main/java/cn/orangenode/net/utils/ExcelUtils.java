package cn.orangenode.net.utils;

import cn.orangenode.net.entity.ConnectionInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

@Slf4j
public class ExcelUtils {

    public static ArrayList<ConnectionInfo> importExcel(String path) {
        // 创建文件并指定文件路径
        /*File file = new File(System.getProperty("user.dir") + "\\test.xlsx");*/
        File file = new File(path);
        ArrayList<ConnectionInfo> excelConnects = new ArrayList<>();
        try {
            // 创建改文件的输入流
            FileInputStream stream = new FileInputStream(file);
            // 创建工作簿
            XSSFWorkbook workbook = new XSSFWorkbook(stream);
            // 获取一个工作表，下标从0开始
            XSSFSheet sheet = workbook.getSheetAt(0);
            // 通过循环，逐行取出表中每行数据,不取标题行
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                // 获取行
                XSSFRow row = sheet.getRow(i);
                // 获取行中列的数据
                String sysName = row.getCell(0).getStringCellValue();
                // ip
                String ip = row.getCell(1).getStringCellValue();
                // 用户名
                String username = row.getCell(2).getStringCellValue();
                // 密码
                String password = row.getCell(3).getStringCellValue();
                //特权密码
                XSSFCell cell = row.getCell(4, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                cell.setCellType(CellType.STRING);
                String enablePassword = cell.getStringCellValue();
                // 厂商
                String brand = new DataFormatter().formatCellValue(row.getCell(5));
                // type
                Integer type = (int) row.getCell(6).getNumericCellValue();
                // port
                Integer port = (int) row.getCell(7).getNumericCellValue();
                ConnectionInfo connectionInfo = new ConnectionInfo(
                        sysName, ip, username, password, port, type, enablePassword, brand
                );
                excelConnects.add(connectionInfo);
            }
        } catch (FileNotFoundException e) {
            log.error("excel文件不存在");
        } catch (IOException e) {
            log.error("获取excel表时发生错误");
        }
        return excelConnects;
    }


}
