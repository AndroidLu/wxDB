package cn.ninexv.wxdb.Tree;

import com.alibaba.fastjson.JSON;

import lombok.Data;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

@Data
public class Product implements Serializable,Cloneable {
    private Integer id;
    private String name;
    private Double price;
//    public static String PATH = "C:\\Users\\王旭\\Desktop\\笔记总结\\Demo.txt";

    public List<Integer> list;
    public Product next = null;
    public Product(){

    }
    public Product(Integer id, String name, Double price) {
        this.id = id;
        this.name = name;
        this.price = price;
        list = new ArrayList<>();
//        RandomAccessFile randomAccessFile = null;
//        try {
//            randomAccessFile = new RandomAccessFile(PATH, "rw");
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//        MappedByteBuffer buff = null;
//        long bufSize = 1024;
//        try {
//            buff = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE,0,bufSize);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        buff.put(JSON.toJSONBytes(this));

    }
    @Override
    public Object clone() {
        Product stu = null;
        try{
            stu = (Product) super.clone();   //浅复制
        }catch(CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return stu;
    }

}

