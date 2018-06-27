package com.sherlock.concurrency.concurrency5;

import com.sherlock.concurrency.annoations.NotThreadSafe;
import lombok.extern.slf4j.Slf4j;

/**
 * 懒汉模式单例
 * author: 小宇宙
 * date: 2018/6/27
 */
@Slf4j
@NotThreadSafe
public class SingletonTest {

    private SingletonTest (){

    }

    public static SingletonTest instance = null;

    public static SingletonTest getInstance(){
        if (instance == null) {
            instance = new SingletonTest();
        }
        return instance;
    }

}
