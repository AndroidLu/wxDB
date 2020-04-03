package cn.ninexv.wxdb.Tree;

import lombok.Data;

import java.util.Map;

/**
 * 将B+树中所有需要保存到第一页的数据封装成一个对象
 */
@Data
public class TreeFirstPage {
    public int root;
    public Map<String,Integer> nodeList;
    public int count;

    public TreeFirstPage(int root, Map<String,Integer> nodeList, int count) {
        this.root = root;
        this.nodeList = nodeList;
        this.count = count;
    }
}
