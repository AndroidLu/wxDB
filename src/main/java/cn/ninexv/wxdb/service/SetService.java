package cn.ninexv.wxdb.service;

import cn.ninexv.wxdb.Tree.BPlusTree;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Component
public class SetService<T, V extends Comparable<V>>{

    ReentrantLock lock = new ReentrantLock();

    @Autowired
    BPlusTree bPlusTree;

    public int insert(V key,T value){
        int insert = 0;
        lock.lock();
        try {
            insert = bPlusTree.insert(value, key);
            bPlusTree.commit();
        }catch (Exception e) {
            bPlusTree.rollBack();
        }finally {
            lock.unlock();
        }
        return insert;
    }
}
