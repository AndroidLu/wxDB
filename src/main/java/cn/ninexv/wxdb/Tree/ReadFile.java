package cn.ninexv.wxdb.Tree;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.SerializationUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/* 
 *
 * @since 2016年6月27日
 * @author wwhhff11
 * @comment 读取大文件
 */
public class ReadFile {

    public static String PATH = "C:\\Users\\王旭\\Desktop\\笔记总结\\Demo.txt";
    public static int num = 1;
    public static int BUFFER_SIZE = 0x200000;// 缓冲区大小为3M

    /* 
     *
     * @since 2016年6月27日
     * @author wwhhff11
     * @comment mmap
     */
    public static void mmap() throws Exception {
        File file = new File(PATH);
        RandomAccessFile randomAccessFile = new RandomAccessFile(PATH, "r");
        MappedByteBuffer buff = null;
        long file_size = file.length();//文件大小
        long rows = file_size / num;//行数
        int total = num + (file_size % num == 0 ? 0 : 1);

        long start_pos = 0l;
        long size = 0l;
        byte[] dst = null;
        for (int i = 1; i <= total; i++) {
            start_pos = (i - 1) * rows;
            size = (i == total ? file_size - start_pos : rows);
            buff = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_ONLY, start_pos, size);
            dst = new byte[BUFFER_SIZE];
            // 每次读出3M的内容
            for (int offset = 0; offset < buff.capacity(); offset += BUFFER_SIZE) {
                if (buff.capacity() - offset >= BUFFER_SIZE) {
                    // 剩余文件大小大于等于3M
                    for (int j = 0; j < BUFFER_SIZE; j++)
                        dst[j] = buff.get(offset + j);
                } else {
                    // 剩余文件大小小于3M
                    for (int j = 0; j < buff.capacity() - offset; j++)
                        dst[j] = buff.get(offset + j);
                }
                int length = (buff.capacity() % BUFFER_SIZE == 0) ? BUFFER_SIZE : buff.capacity() % BUFFER_SIZE;

//                BTest b = SerializationUtils.deserialize(dst);
//                System.out.println(b.find(3));

                String s = new String(dst, 0, length);
                System.out.println(s);
                Map<String,Object> map = (Map<String, Object>) JSON.parse(s);
                System.out.println(map);
                Product product = new Product();
                List<Integer> list = (List<Integer>) map.get("list");
                System.out.println(list.get(0));
                product.setList(list);
                System.out.println(product.getList());
            }
        }

    }

    public static void writeMap() throws IOException {
        File file = new File(PATH);
        RandomAccessFile randomAccessFile = new RandomAccessFile(PATH, "rw");
        MappedByteBuffer buff = null;
        long bufSize = 1024;
        for (int i = 0; i < 4; i++) {
            Product product = new Product(i,"wang",20.0);
            buff = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE,i*bufSize,bufSize);
            buff.put(JSON.toJSONBytes(product));

        }

    }

    public static void writeTest() throws IOException {

//        File file = new File(PATH);
        RandomAccessFile randomAccessFile = new RandomAccessFile(PATH, "rw");

        ByteBuffer buffer = null;
        FileChannel channel = randomAccessFile.getChannel();
        long pos = 256;
        for (int i = 0; i < 2; i++) {
            Product product = new Product(i,"wang",1.0);
            buffer = ByteBuffer.allocate(1024);
            buffer.put(JSON.toJSONBytes(product));
            buffer.flip();
            channel.write(buffer,pos*i);
        }


//        while(buffer.hasRemaining()) {
//            // 将 Buffer 中的内容写入文件
//            channel.write(buffer);
//        }
    }


    /* 
     *
     * @since 2016年6月27日
     * @author wwhhff11
     * @comment 按行读取
     */
    public static void randomRead() throws IOException {
        RandomAccessFile randomAccessFile = new RandomAccessFile(PATH, "rw");
        MappedByteBuffer buff = null;
        try {
            buff = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE,0,1024);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] bt = new byte[1024];
        if (buff != null) {
            buff.get(bt);
        }
//        Product product = SerializationUtils.deserialize(bt);
//        System.out.println(product.getId());
        String s = new String(bt);
        System.out.println(s);
        Map<String,Object> map = (Map<String, Object>) JSON.parse(s);
        System.out.println(map);
//        Product product = new Product();
//        List<Integer> list = (List<Integer>) map.get("list");
//        System.out.println(list.get(0));
//        product.setList(list);
//        System.out.println(product.getList());

    }

    public static void main(String[] args) throws Exception {
//        writeMap();
//        writeTest();
//        Awrite();
//        System.out.println(" q ");
//      mmap();
        randomRead();
    }

}