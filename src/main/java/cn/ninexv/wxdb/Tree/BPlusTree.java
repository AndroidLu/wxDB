package cn.ninexv.wxdb.Tree;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * B+树
 * 将树的各个节点利用mmap映射到磁盘，重启应用时根据每个节点的偏移量从磁盘中映射获取，再次生成节点对象，实现树的持久化
 *
 * 如果表的记录数是N，每一个BTREE节点平均有B个索引KEY，那么B+TREE索引树的高度就是logNB(等价于logN/logB)
 */
@SuppressWarnings("ALL")
@Component
@Data
public class BPlusTree<T, V extends Comparable<V>> implements Serializable{

    //磁盘存储的位置
    public static String PATH = "C:\\Users\\王旭\\Desktop\\笔记总结\\wxdb.txt";

    public static long LEAFNODE_SIZE = 1024*2;//规定一个节点的所占用的存储大小
    public static long ROOT_SIZE = 1024*32;//保存root节点在几号偏移量
//    public static long NS_SIZE = 1024*4;//规定节点类型记录表所占用的存储大小

    RandomAccessFile randomAccessFile = null;
    FileChannel channel = null;

    //偏移量计数器,0被我们用来存储root节点位置和节点类型记录表，所以从1开始
    static int count = 1;
    //存储buffer缓存区，方便flush刷新磁盘内容
    Map<Integer,MappedByteBuffer> buffers = new HashMap<>();
    //节点类型记录(1代表非叶子节点，2代表叶子节点)，用来确认每一段的磁盘内容是什么类型节点
    Map<String,Integer> nodeList = null;
    //节点，存储已经创建好的节点
    Map<Integer,Node> nodes = null;

    //copyOnWrite节点，用来保存正在被修改的节点
    Map<Integer,Node> copyNodes = null;
    //记录缓存的列表
    List<Integer> cacheList = new LinkedList<>();

    ////
    //B+树的阶
    private Integer bTreeOrder;
    //B+树的非叶子节点最小拥有的子节点数量（同时也是键的最小数量）
    //private Integer minNUmber;
    //B+树的非叶子节点最大拥有的节点数量（同时也是键的最大数量）
    private Integer maxNumber;

    private Node<T, V> root;

    private LeafNode<T, V> left;

    //无参构造方法，默认阶为3
    public BPlusTree(){
        this(3);
    }

    //有参构造方法，可以设定B+树的阶
    public BPlusTree(Integer bTreeOrder){
        try {
            randomAccessFile = new RandomAccessFile(PATH, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        channel = randomAccessFile.getChannel();
        //初始化阶数
        this.bTreeOrder = bTreeOrder;
        //因为插入节点过程中可能出现超过上限的情况,所以这里要加1
        this.maxNumber = bTreeOrder + 1;
        //初始化nodes
        this.nodes = new HashMap<>();
        //初始化copyNodes
        this.copyNodes = new HashMap<>();

        MappedByteBuffer rootbuff = null;
        try {
            rootbuff = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE,0,ROOT_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] rootbt = new byte[(int) ROOT_SIZE];
        if (rootbuff != null) {
            buffers.put(0,rootbuff);
            rootbuff.get(rootbt);
            String s = new String(rootbt, 0, rootbt.length);

            try {
                Map<String,Object> map = (Map<String, Object>) JSON.parse(s);
                int root = (int) map.get("root");
                Map<String,Integer> ns = (Map<String, Integer>) map.get("nodeList");
                int count = (int) map.get("count");
                this.nodeList = ns;
                this.count = count;
                if (nodeList.get(root) == 1){
                    this.root = getBPlusNode(root);
                }
                if (nodeList.get(root) == 2){
                    this.root = getLeafNode(root);
                }
                this.left = (LeafNode<T, V>)this.root.refreshLeft();
//                flush(leafNode);
            }catch (JSONException e){
//                e.printStackTrace();
                System.out.println("-----------------------------");
                System.out.println("未找到数据文件或文件已出错，默认重新初始化");
                //初始化nodeList
                this.nodeList = new HashMap<>();
                nodeList.put(String.valueOf(0),0);
                //
                this.nodes = new HashMap<>();
                nodes.put(0,null);
                this.root = new LeafNode<T, V>();
                this.left = null;
            }
        }
        else{
            System.out.println("失败:B+树创建异常");
        }

    }

    //查询
    public T find(V key){
        T t = this.root.find(key);
//        if(t == null){
//            return null;
//        }
        return t;
    }

    //插入
    public int insert(T value, V key){
        if(key == null)
            return -1;
        Node<T, V> t = this.root.insert(value, key);
        if(t != null){
            this.root = t;
        }
        flushRoot();
        this.left = (LeafNode<T, V>)this.root.refreshLeft();
//        flushRoot();
        return 1;
    }

    public int delete(V key){
        T t = find(key);
        if (t == null){
            return 0;
        }
        insert(null,key);
        return 1;
    }

    public void commit(){
        for (int i = 0; i < cacheList.size(); i++) {
            copyNodes.remove(cacheList.get(i));
        }
    }

    public void rollBack(){
        for (int i = 0; i < cacheList.size(); i++) {
            flush(copyNodes.get(cacheList.get(i)));
            nodes.replace(copyNodes.get(cacheList.get(i)).offset,copyNodes.get(cacheList.get(i)));
        }
    }


    //更新节点的数据
    public void flush(Node node){
        MappedByteBuffer buff = buffers.get(node.offset);
        buff.clear();
        byte[] bts = new byte[(int) LEAFNODE_SIZE];
        buff.put(bts);
        buff.clear();
        bts = JSON.toJSONBytes(node, SerializerFeature.WriteMapNullValue);
        buff.put(bts);
//        buff.force();
    }

    //更新root记录区的数据
    public void flushRoot(){
        MappedByteBuffer buff = buffers.get(0);
        buff.clear();
        byte[] bts = new byte[(int) ROOT_SIZE];
        buff.put(bts);
        buff.clear();
        TreeFirstPage page = new TreeFirstPage(this.root.offset,this.nodeList,this.count);
        bts = JSON.toJSONBytes(page, SerializerFeature.WriteMapNullValue);
        buff.put(bts);
    }


    public BPlusNode getBPlusNode(int ofs){
        MappedByteBuffer buff = null;
//        if (buffers.get(ofs) != null){
//            buff = buffers.get(ofs);
//        }
//        else{
            try {
                buff = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE,ROOT_SIZE+(ofs-1)*LEAFNODE_SIZE,LEAFNODE_SIZE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            buffers.put(ofs,buff);
//        }
        ///
        byte[] bt = new byte[(int) LEAFNODE_SIZE];
        if (buff != null) {
            buff.get(bt);
            String s = new String(bt, 0, bt.length);
            try {
                Map<String, Object> map = (Map<String, Object>) JSON.parse(s);
//                    System.out.println(map);

                List keys = (List) map.get("keys");
                Integer number = (Integer) map.get("number");
                List<Integer> childs = (List<Integer>) map.get("childs");
                int parent = (int) map.get("parent");
                int offset = (int) map.get("offset");
                BPlusNode b = new BPlusNode(offset);
                b.setKeys(keys.toArray());
                b.setChilds(childs.stream().mapToInt(Integer::valueOf).toArray());
                b.setParent(parent);
                b.setNumber(number);
//                b.setOffset(offset);
                nodes.put(b.offset,b);
                flush(b);
                return b;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        BPlusNode bPlusNode = new BPlusNode();
        flush(bPlusNode);
        return bPlusNode;

    }
    public LeafNode getLeafNode(int ofs){
        MappedByteBuffer buff = null;
            try {
                buff = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE,ROOT_SIZE+(ofs-1)*LEAFNODE_SIZE,LEAFNODE_SIZE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            buffers.put(ofs,buff);

        byte[] bt = new byte[(int) LEAFNODE_SIZE];
        if (buff != null) {
            buff.get(bt);
            String s = new String(bt, 0, bt.length);
            try {
                Map<String, Object> map = (Map<String, Object>) JSON.parse(s);
                List keys = (List) map.get("keys");
                List values = (List) map.get("values");
                Integer number = (Integer) map.get("number");
                List<Integer> childs = (List<Integer>) map.get("childs");
                int left = (int) map.get("left");
                int right = (int) map.get("right");
                int parent = (int) map.get("parent");
                int offset = (int) map.get("offset");
                LeafNode b = new LeafNode(offset);
                b.setKeys(keys.toArray());
                b.setChilds(childs.stream().mapToInt(Integer::valueOf).toArray());
                b.setParent(parent);
                b.setNumber(number);
                b.setLeft(left);
                b.setRight(right);
                b.setValues(values.toArray());
                nodes.put(b.offset,b);
                flush(b);
                return b;
            } catch (JSONException e) {
                e.printStackTrace();
            }
            ///
        }
        LeafNode leafNode = new LeafNode();
        flushRoot();
        return leafNode;
    }

    public LeafNode leafNodeCache(LeafNode node){
        cacheList.add(node.offset);
        LeafNode leafNode = null;
        leafNode = (LeafNode) node.clone();
        leafNode.keys = new Object[node.keys.length];
        leafNode.values = new Object[node.values.length];
        System.arraycopy(node.keys,0,leafNode.keys,0,node.keys.length);
        System.arraycopy(node.values,0,leafNode.values,0,node.values.length);
        copyNodes.put(leafNode.offset,leafNode);
        return leafNode;
    }
    public BPlusNode bPlusNodeCache(BPlusNode node){
        cacheList.add(node.offset);
        BPlusNode bn = null;
        bn = (BPlusNode) node.clone();
        bn.keys = new Object[node.keys.length];
        System.arraycopy(node.keys,0,bn.keys,0,node.keys.length);
        System.arraycopy(node.childs,0,bn.childs,0,node.childs.length);
        copyNodes.put(bn.offset,bn);
        return bn;
    }


    /**
     * 节点父类，因为在B+树中，非叶子节点不用存储具体的数据，只需要把索引作为键就可以了
     * 所以叶子节点和非叶子节点的类不太一样，但是又会公用一些方法，所以用Node类作为父类,
     * 而且因为要互相调用一些公有方法，所以使用抽象类
     *
     * @param <T> 同BPlusTree
     * @param <V>
     */
    @Data
    abstract class Node<T, V extends Comparable<V>> implements Serializable {
        //父节点
        protected int parent;
        //子节点
        protected int[] childs;
        //键（子节点）数量
        protected Integer number;
        //键
        protected Object keys[];
        //偏移量计数器
        protected int offset;

        //构造方法
        public Node(){
            this.keys = new Object[maxNumber];
            this.childs = new int[maxNumber];
            this.number = 0;
            this.parent = -1;
            this.offset = count;

            MappedByteBuffer buff = null;
            try {
                buff = randomAccessFile.getChannel().map(FileChannel.MapMode.READ_WRITE,ROOT_SIZE+(count-1)*LEAFNODE_SIZE,LEAFNODE_SIZE);
                count++;
            } catch (IOException e) {
                e.printStackTrace();
            }
            buff.put(JSON.toJSONBytes(this,SerializerFeature.WriteMapNullValue));
            buffers.put(this.offset,buff);
        }
        public Node(int ofs){
            this.offset = ofs;
        }

        //查找
        abstract T find(V key);

        //事务状态下的查找
        abstract T Transactionfind(V key);

        //插入
        abstract Node<T, V> insert(T value, V key);

        abstract LeafNode<T, V> refreshLeft();
    }


    /**
     * 非叶节点类
     * @param <T>
     * @param <V>
     */

    @Data
    @EqualsAndHashCode(callSuper = true)
    class BPlusNode <T, V extends Comparable<V>> extends Node<T, V> implements Serializable,Cloneable{

        public BPlusNode() {
            super();
            nodeList.put(String.valueOf(this.offset),1);
            nodes.put(this.offset,this);
        }

        public BPlusNode(int ofs){
            super(ofs);
        }

        @Override
        public Object clone() {
            BPlusNode bn = null;
            try{
                bn = (BPlusNode) super.clone();   //浅复制
            }catch(CloneNotSupportedException e) {
                e.printStackTrace();
            }
            return bn;
        }

        /**
         * 递归查找,这里只是为了确定值究竟在哪一块,真正的查找到叶子节点才会查
         * @param key
         * @return
         */
        T find(V key) {
            if (copyNodes.get(this.offset) != null){
                BPlusNode node1 = (BPlusNode) copyNodes.get(this.offset);
                int i = 0;
                while(i < node1.number){
                    if(key.compareTo((V) node1.keys[i]) <= 0)
                        break;
                    i++;
                }
                if(node1.number == i)
                    return null;

                Node node = null;
                if (nodes.get(node1.childs[i]) != null){
                    node = nodes.get(node1.childs[i]);
                }else{
                    if (nodeList.get(node1.childs[i]) == 1){
                        node = getBPlusNode(node1.childs[i]);
                    }else {
                        node = getLeafNode(node1.childs[i]);
                    }
                }
                return (T) node.find(key);
            }
            int i = 0;
            while(i < this.number){
                if(key.compareTo((V) this.keys[i]) <= 0)
                    break;
                i++;
            }
            if(this.number == i)
                return null;

            Node node = null;
            if (nodes.get(this.childs[i]) != null){
                node = nodes.get(this.childs[i]);
            }else{
                if (nodeList.get(this.childs[i]) == 1){
                    node = getBPlusNode(this.childs[i]);
                }else {
                    node = getLeafNode(this.childs[i]);
                }
            }
            return (T) node.find(key);
        }

        T Transactionfind(V key) {
            int i = 0;
            while(i < this.number){
                if(key.compareTo((V) this.keys[i]) <= 0)
                    break;
                i++;
            }
            if(this.number == i)
                return null;

            Node node = null;
            if (nodes.get(this.childs[i]) != null){
                node = nodes.get(this.childs[i]);
            }else{
                if (nodeList.get(this.childs[i]) == 1){
                    node = getBPlusNode(this.childs[i]);
                }else {
                    node = getLeafNode(this.childs[i]);
                }
            }
            return (T) node.Transactionfind(key);
        }



        /**
         * 递归插入,把值插入到对应的叶子节点
         * @param value
         * @param key
         */
        @Override
        Node<T, V> insert(T value, V key) {
            int i = 0;
            while(i < this.number){
                if(key.compareTo((V) this.keys[i]) < 0)
                    break;
                i++;
            }
            if(key.compareTo((V) this.keys[this.number - 1]) >= 0) {
                i--;
            }
            Node node = null;
            if (nodes.get(this.childs[i]) != null){
//                System.out.println("1");
                node = nodes.get(this.childs[i]);

            }else{
//                System.out.println("2");
                if (nodeList.get(this.childs[i]) == 1){
                    node = getBPlusNode(this.childs[i]);
                }else {
                    node = getLeafNode(this.childs[i]);
                }
//                System.out.println(node.getClass());
            }
            return node.insert(value, key);
        }

        @Override
        LeafNode<T, V> refreshLeft() {
            Node node = nodes.get(this.childs[0]);
            return node.refreshLeft();
        }

        /**
         * 当叶子节点插入成功完成分解时,递归地向父节点插入新的节点以保持平衡
         * @param node1
         * @param node2
         * @param key
         */
        Node<T, V> insertNode(Node<T, V> node1, Node<T, V> node2, V key){
            bPlusNodeCache(this);
//            System.out.println("非叶子节点,插入key: " + node1.keys[node1.number - 1] + " " + node2.keys[node2.number - 1]);

            V oldKey = null;
            if(this.number > 0)
                oldKey = (V) this.keys[this.number - 1];
            //如果原有key为null,说明这个非节点是空的,直接放入两个节点即可
            if(key == null || this.number <= 0){
//                System.out.println("非叶子节点,插入key: " + node1.keys[node1.number - 1] + " " + node2.keys[node2.number - 1] + "直接插入");
                this.keys[0] = node1.keys[node1.number - 1];
                this.keys[1] = node2.keys[node2.number - 1];
                this.childs[0] = node1.offset;
                this.childs[1] = node2.offset;
                this.number += 2;
                flush(this);
                return this;
            }

            //原有节点不为空,则应该先寻找原有节点的位置,然后将新的节点插入到原有节点中
            int i = 0;

//            System.out.println("begin "+key);

            while(key.compareTo((V)this.keys[i]) != 0){
                i++;
            }
            //左边节点的最大值可以直接插入,右边的要挪一挪再进行插入
            this.keys[i] = node1.keys[node1.number - 1];
            this.childs[i] = node1.offset;

            Object tempKeys[] = new Object[maxNumber];
            int[] tempChilds = new int[maxNumber];

            System.arraycopy(this.keys, 0, tempKeys, 0, i + 1);
            System.arraycopy(this.childs, 0, tempChilds, 0, i + 1);
            System.arraycopy(this.keys, i + 1, tempKeys, i + 2, this.number - i - 1);
            System.arraycopy(this.childs, i + 1, tempChilds, i + 2, this.number - i - 1);
            tempKeys[i + 1] = node2.keys[node2.number - 1];
            tempChilds[i + 1] = node2.offset;

            this.number++;

            //判断是否需要拆分
            //如果不需要拆分,把数组复制回去,直接返回
            if(this.number <= bTreeOrder){
                System.arraycopy(tempKeys, 0, this.keys, 0, this.number);
                System.arraycopy(tempChilds, 0, this.childs, 0, this.number);

//                System.out.println("非叶子节点,插入key: " + node1.keys[node1.number - 1] + " " + node2.keys[node2.number - 1] + ", 不需要拆分");

                flush(this);
                return null;
            }

//            System.out.println("非叶子节点,插入key: " + node1.keys[node1.number - 1] + " " + node2.keys[node2.number - 1] + ",需要拆分");

            //如果需要拆分,和拆叶子节点时类似,从中间拆开
            Integer middle = this.number / 2;

            //新建非叶子节点,作为拆分的右半部分
            BPlusNode<T, V> tempNode = new BPlusNode<T, V>();
            nodes.put(tempNode.offset,tempNode);

            //非叶节点拆分后应该将其子节点的父节点指针更新为正确的指针
            tempNode.number = this.number - middle;
            tempNode.parent = this.parent;
            //如果父节点为空,则新建一个非叶子节点作为父节点,并且让拆分成功的两个非叶子节点的指针指向父节点
            if(this.parent == -1) {
                BPlusNode<T, V> tempBPlusNode = new BPlusNode<>();
                tempNode.parent = tempBPlusNode.offset;
                this.parent = tempBPlusNode.offset;
                oldKey = null;
                copyNodes.put(tempBPlusNode.offset,tempBPlusNode);
                nodes.put(tempBPlusNode.offset,tempBPlusNode);
                flush(tempBPlusNode);
            }
            System.arraycopy(tempKeys, middle, tempNode.keys, 0, tempNode.number);
            System.arraycopy(tempChilds, middle, tempNode.childs, 0, tempNode.number);


            for(int j = 0; j < tempNode.number; j++){
                Node node = null;
                if (nodes.get(tempNode.childs[j]) != null){
                    node = nodes.get(tempNode.childs[j]);
                }else{
                    if (nodeList.get(this.childs[i]) == 1){
                        node = getBPlusNode(this.childs[i]);
                    }else {
                        node = getLeafNode(this.childs[i]);
                    }
                }
                node.parent = tempNode.offset;
            }
            //让原有非叶子节点作为左边节点
            this.number = middle;
            this.keys = new Object[maxNumber];
            this.childs = new int[maxNumber];
            System.arraycopy(tempKeys, 0, this.keys, 0, middle);
            System.arraycopy(tempChilds, 0, this.childs, 0, middle);

            //叶子节点拆分成功后,需要把新生成的节点插入父节点
            BPlusNode parentNode = null;
//            if (nodes.get(this.parent) != null){
                parentNode = (BPlusNode) nodes.get(this.parent);
//            }else{
//                parentNode = getBPlusNode(this.parent);
//            }
            flush(this);
            flush(tempNode);
            //
            return parentNode.insertNode(this, tempNode, oldKey);
        }

    }

    /**
     * 叶节点类
     * @param <T>
     * @param <V>
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    class LeafNode <T, V extends Comparable<V>> extends Node<T, V> implements Serializable,Cloneable{

        protected Object values[];
        protected int left;
        protected int right;

        public LeafNode(){
            super();
            this.values = new Object[maxNumber];
            this.left = -1;
            this.right = -1;
            nodeList.put(String.valueOf(this.offset),2);
            nodes.put(this.offset,this);
        }
        public LeafNode(int ofs){
            super(ofs);
        }
        @Override
        public Object clone() {
            LeafNode bn = null;
            try{
                bn = (LeafNode) super.clone();   //浅复制
            }catch(CloneNotSupportedException e) {
                e.printStackTrace();
            }
            return bn;
        }

        /**
         * 进行二分查找
         * @param key
         * @return
         */
        T find(V key) {
            if (copyNodes.get(this.offset) != null){
                LeafNode node = (LeafNode) copyNodes.get(this.offset);

                if(node.number <=0)
                    return null;
                Integer left = 0;
                Integer right = node.number;
                Integer middle = (left + right) / 2;
                while(left < right){
                    V middleKey = (V) node.keys[middle];
                    if(key.compareTo(middleKey) == 0){
                        return (T) node.values[middle];
                    }
                    else if(key.compareTo(middleKey) < 0)
                        right = middle;
                    else
                        left = middle;
                    middle = (left + right) / 2;
                }
                return null;
            }

            if(this.number <=0)
                return null;
            Integer left = 0;
            Integer right = this.number;
            Integer middle = (left + right) / 2;
            while(left < right){
                V middleKey = (V) this.keys[middle];
                if(key.compareTo(middleKey) == 0){
                    return (T) this.values[middle];
                }
                else if(key.compareTo(middleKey) < 0)
                    right = middle;
                else
                    left = middle;
                middle = (left + right) / 2;
            }
            return null;
        }

        T Transactionfind(V key) {
            if(this.number <=0)
                return null;
            Integer left = 0;
            Integer right = this.number;
            Integer middle = (left + right) / 2;
            while(left < right){
                V middleKey = (V) this.keys[middle];
                if(key.compareTo(middleKey) == 0){
                    return (T) this.values[middle];
                }
                else if(key.compareTo(middleKey) < 0)
                    right = middle;
                else
                    left = middle;
                middle = (left + right) / 2;
            }
            return null;
        }

        /**
         *
         * @param value
         * @param key
         */
        @Override
        Node<T, V> insert(T value, V key) {
            //将需要修改的节点保存到缓存中
            leafNodeCache(this);
            //保存原始存在父节点的key值
            V oldKey = null;
            if(this.number > 0)
                oldKey = (V) this.keys[this.number - 1];
            //先插入数据
            int i = 0;
            while(i < this.number){
                if(key.compareTo( (V)this.keys[i] ) == 0){
                    this.values[i] = value;
                    flush(this);
                    return null;
                }
                if(key.compareTo( (V)this.keys[i] ) < 0){
                    break;
                }
                i++;
            }
            //复制数组,完成添加
            Object tempKeys[] = new Object[maxNumber];
            Object tempValues[] = new Object[maxNumber];
            System.arraycopy(this.keys, 0, tempKeys, 0, i);
            System.arraycopy(this.values, 0, tempValues, 0, i);
            System.arraycopy(this.keys, i, tempKeys, i + 1, this.number - i);
            System.arraycopy(this.values, i, tempValues, i + 1, this.number - i);
            tempKeys[i] = key;
            tempValues[i] = value;
            this.number++;

            //判断是否需要拆分
            //如果不需要拆分完成复制后直接返回
            if(this.number <= bTreeOrder){
                System.arraycopy(tempKeys, 0, this.keys, 0, this.number);
                System.arraycopy(tempValues, 0, this.values, 0, this.number);

                //有可能虽然没有节点分裂，但是实际上插入的值大于了原来的最大值，所以所有父节点的边界值都要进行更新
                Node node = this;
                while (node.parent != -1){
                    V tempkey = (V)node.keys[node.number - 1];
                    Node parentNode = (BPlusNode) nodes.get(node.parent);
                    if(tempkey.compareTo( (V)parentNode.keys[parentNode.number - 1] ) > 0){
                        parentNode.keys[parentNode.number - 1] = tempkey;
                        node = parentNode;
                        //刷新
                        flush(node);
                    }
                    else {
                    	break;
                    }
                }
                flush(this);
                return null;
            }

            //如果需要拆分,则从中间把节点拆分差不多的两部分
            Integer middle = this.number / 2;

            //新建叶子节点,作为拆分的右半部分
            LeafNode<T, V> tempNode = new LeafNode<T, V>();
            tempNode.number = this.number - middle;
            tempNode.parent = this.parent;
            //如果父节点为空,则新建一个非叶子节点作为父节点,并且让拆分成功的两个叶子节点的指针指向父节点
            if(this.parent == -1) {
                BPlusNode<T, V> tempBPlusNode = new BPlusNode<>();
                tempNode.parent = tempBPlusNode.offset;
                this.parent = tempBPlusNode.offset;
                oldKey = null;
                flush(tempBPlusNode);
            }
            System.arraycopy(tempKeys, middle, tempNode.keys, 0, tempNode.number);
            System.arraycopy(tempValues, middle, tempNode.values, 0, tempNode.number);

            //让原有叶子节点作为拆分的左半部分
            this.number = middle;
            this.keys = new Object[maxNumber];
            this.values = new Object[maxNumber];
            System.arraycopy(tempKeys, 0, this.keys, 0, middle);
            System.arraycopy(tempValues, 0, this.values, 0, middle);

            this.right = tempNode.offset;
            tempNode.left = this.offset;

            //叶子节点拆分成功后,需要把新生成的节点插入父节点

            //
            BPlusNode parentNode = null;
            if (nodes.get(this.parent) != null){
                parentNode = (BPlusNode) nodes.get(this.parent);
            }else{
                parentNode = getBPlusNode(this.parent);
            }

            flush(this);
            flush(tempNode);
            flushRoot();
            //
            return parentNode.insertNode(this, tempNode, oldKey);
        }

        @Override
        LeafNode<T, V> refreshLeft() {
            if(this.number <= 0)
                return null;
            return this;
        }
    }
}

