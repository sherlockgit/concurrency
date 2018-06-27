package com.sherlock.concurrency.concurrency5;

import com.sherlock.concurrency.annoations.NotThreadSafe;
import com.sherlock.concurrency.annoations.ThreadSafe;
import lombok.extern.slf4j.Slf4j;

/**
 * 懒汉模式单例
 * author: 小宇宙
 * date: 2018/6/27
 */
@Slf4j
@ThreadSafe
public class SingletonTest3 {

    private SingletonTest3(){

    }

    public static SingletonTest3 instance = null;

    public static synchronized SingletonTest3 getInstance(){
        if (instance == null) {
            instance = new SingletonTest3();
        }
        return instance;
    }

}
