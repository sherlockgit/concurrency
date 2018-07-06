package com.sherlock.concurrency.concurrency6;

import com.sherlock.concurrency.annoations.NotThreadSafe;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * author: 小宇宙
 * date: 2018/7/5
 * 不可变对象1
 */
@Slf4j
@NotThreadSafe
public class ImmutableExample {

    private final static Integer a = 1;
    private final static String b = "2";
    private final static Map<Integer,Integer> map = new HashMap<>();

    static {
        map.put(1,2);
        map.put(3,4);
        map.put(5,6);
    }

    public static void main(String[] args) {

        /*当变量被final关键字修饰时是不能修改的，当对象引用被final关键字修饰时不能重新指向另外一个变量的应用但是能修改其值*/
//        a = 2;
//        b = 1;
//        map = new HashMap<>();
        map.put(1,3);
        log.info("{}",map.get(1));
    }

    /*final还可以修饰某个方法传进来的变量*/
    private void test(final int a){
//        a = 1;
    }
}
