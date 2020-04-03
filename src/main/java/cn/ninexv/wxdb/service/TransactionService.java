//package cn.ninexv.wxdb.service;
//
//import cn.ninexv.wxdb.Tree.BPlusTree;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.util.Date;
//import java.util.concurrent.locks.ReentrantLock;
//
//@SuppressWarnings("ALL")
//@Service
//public class TransactionService <T, V extends Comparable<V>>{
//    ReentrantLock lock = new ReentrantLock();
//
//    @Autowired
//    BPlusTree bPlusTree;
//
//    public volatile String MyIp;
//    private long time;
//
//    public String Judge(String sql,String ip){
//        while ( MyIp != null || !MyIp.equals(ip) ){
//            long now = new Date().getTime();
//            if ( (now - time) < 10000){
//                return "服务器正忙...";
//            }else{
//                MyIp = ip;
//            }
//        }
//        MyIp = ip;
//        time = new Date().getTime();
//        return null;
//    }
//}
