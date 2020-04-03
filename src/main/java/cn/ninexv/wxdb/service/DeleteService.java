package cn.ninexv.wxdb.service;

import cn.ninexv.wxdb.Tree.BPlusTree;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeleteService <T, V extends Comparable<V>>{
    @Autowired
    BPlusTree bPlusTree;

    public int delete(V key){
        return bPlusTree.delete(key);
    }
}
