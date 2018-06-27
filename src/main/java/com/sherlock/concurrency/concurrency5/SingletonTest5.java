package com.sherlock.concurrency.concurrency5;

import com.sherlock.concurrency.annoations.NotThreadSafe;
import lombok.extern.slf4j.Slf4j;

/**
 * 懒汉模式单例
 * author: 小宇宙
 * date: 2018/6/27
 * 双层检查
 */
@Slf4j
@NotThreadSafe
public class SingletonTest5 {

    private SingletonTest5(){

    }

    public static volatile SingletonTest5 instance = null;


    public static synchronized SingletonTest5 getInstance(){
        if (instance == null) {
            synchronized (SingletonTest5.class) {
                if (instance == null) {
                    instance = new SingletonTest5();
                }
            }
        }
        return instance;
    }

}
