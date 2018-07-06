package com.sherlock.concurrency.concurrency6;

import com.sherlock.concurrency.annoations.NotThreadSafe;
import com.sherlock.concurrency.annoations.ThreadSafe;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * author: 小宇宙
 * date: 2018/7/5
 * 不可变对象1(java 本类提供)
 */
@Slf4j
@ThreadSafe
public class ImmutableExample2 {

    private static Map<Integer,Integer> map = new HashMap<>();

    static {
        map.put(1,2);
        map.put(3,4);
        map.put(5,6);
        /*当map被该类修饰时map里面的值不可变*/
        map = Collections.unmodifiableMap(map);
    }

    public static void main(String[] args) {
        map.put(1,3);
        log.info("{}",map.get(1));
    }


}
