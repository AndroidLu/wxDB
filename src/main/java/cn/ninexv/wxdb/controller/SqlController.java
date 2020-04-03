package cn.ninexv.wxdb.controller;

import cn.ninexv.wxdb.service.DeleteService;
import cn.ninexv.wxdb.service.GetService;
import cn.ninexv.wxdb.service.SetService;

import cn.ninexv.wxdb.tool.SqlParsing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
public class SqlController {
    @Autowired
    HttpServletRequest request;
    @Autowired
    SetService setService;
    @Autowired
    GetService getService;
    @Autowired
    DeleteService deleteService;
    @Autowired
    SqlParsing sqlParsing;
//    @Autowired
//    TransactionService transactionService;

    public String getIP(){
        // 获取请求主机IP地址,如果通过代理进来，则透过防火墙获取真实IP地址
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
        } else if (ip.length() > 15) {
            String[] ips = ip.split(",");
            for (int index = 0; index < ips.length; index++) {
                String strIp = (String) ips[index];
                if (!("unknown".equalsIgnoreCase(strIp))) {
                    ip = strIp;
                    break;
                }
            }
        }
        return ip;
    }

    @GetMapping("/sql/{sql}")
    public String sql(@PathVariable("sql") String sql){
//        String ip1 = getIP();
//        if (ip1.equals(transactionService.MyIp)){
//            return transactionService.Judge(sql,ip1);
//        }
        System.out.println(sql);
        String head = sqlParsing.Distinguish(sql);
        if (head.equals("error")){
            return "请检查SQL语句";
        }
        if (head.equals("set")){
            String[] strings = sqlParsing.SqlSet(sql);
            int result = setService.insert(strings[1], strings[2]);
            if (result == -1){
                return "失败，数据库异常";
            }
            return "插入成功";
        }
        if (head.equals("get")){
            String key = sqlParsing.SqlGet(sql);
            Object o = getService.get(key);
            return o.toString();
        }
        if (head.equals("delete")){
            String key = sqlParsing.SqlGet(sql);
            int delete = deleteService.delete(key);
            if (delete == 0){
                return "提醒：没有目标对象";
            }
            return "删除成功";
        }
        return "error";
    }
}
