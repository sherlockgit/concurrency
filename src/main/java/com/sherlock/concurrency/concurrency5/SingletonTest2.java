package com.sherlock.concurrency.concurrency5;

import com.sherlock.concurrency.annoations.ThreadSafe;
import lombok.extern.slf4j.Slf4j;

/**
 * 饿汉模式单例
 * author: 小宇宙
 * date: 2018/6/27
 */
@Slf4j
@ThreadSafe
public class SingletonTest2 {

    private SingletonTest2(){

    }

    public static SingletonTest2 instance = new SingletonTest2();

    public static SingletonTest2 getInstance(){
        return instance;
    }

}
