package com.sherlock.concurrency.concurrency6;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.sherlock.concurrency.annoations.ThreadSafe;

/**
 * author: 小宇宙
 * date: 2018/7/5
 * 不可变对象2（guava类）
 */
@ThreadSafe
public class ImmutableExample3 {

    private final  static ImmutableList<Integer> list = ImmutableList.of(1,2,3);

    private final  static ImmutableSet set = ImmutableSet.copyOf(list);

    private final static ImmutableMap<Integer,Integer> map = ImmutableMap.of(1,2,3,4);

    private final static ImmutableMap<Integer,Integer> map2 = ImmutableMap.<Integer,Integer>builder().put(1,2).put(3,4).build();
}
