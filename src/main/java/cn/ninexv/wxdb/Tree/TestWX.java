package cn.ninexv.wxdb.Tree;

import java.util.Date;

public class TestWX {
    public static void main(String[] args) throws InterruptedException {
        BPlusTree b = new BPlusTree();
//        BPlusTree2 b = new BPlusTree2();
////        System.out.println(b.find(1));
        long start = new Date().getTime();
        for (int i = 1; i <= 1000; i++) {
            b.insert(i, i);
//            b.commit();
//            System.out.println(i);
        }
        long end = new Date().getTime();
        System.out.println(end - start);

//        b.insert(222,12);
//        System.out.println(b.find(4));
//        for (int i = 1; i <= 6; i++) {
//            System.out.println(b.find(i));
//        }
    }
}
