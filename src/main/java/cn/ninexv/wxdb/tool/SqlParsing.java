package cn.ninexv.wxdb.tool;

import org.springframework.stereotype.Component;

@Component
public class SqlParsing {
    public  String Distinguish(String sql){
        String[] s = sql.split(" ");
        if (s.length==0 || s.length >3){
            return "error";
        }
        return s[0];
    }

    public  String[] SqlSet(String sql){
        String[] s = sql.split(" ");
        return s;
    }

    public String SqlGet(String sql){
        return sql.split(" ")[1];
    }
}
