package cn.ninexv.wxdb.Tree;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.SerializationUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

public class Test {
    public static final String PATH = "C:\\Users\\王旭\\Desktop\\笔记总结\\Demo.txt";
    public static void main(String[] args) throws IOException {

        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        Product p1 = new Product(1,"test",1.0);
        p1.setList(list);
        System.out.println(p1.getId());

        Product clone = (Product) p1.clone();
        clone.setId(888);
        System.out.println(p1.getId());
        System.out.println(clone.getId());

    }
}

