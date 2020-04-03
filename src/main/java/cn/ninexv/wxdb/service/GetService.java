package cn.ninexv.wxdb.service;

import cn.ninexv.wxdb.Tree.BPlusTree;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GetService<T, V extends Comparable<V>>{
    @Autowired
    BPlusTree bPlusTree;
    public T get(V key){
        T o = (T) bPlusTree.find(key);
        return o;
    }
}