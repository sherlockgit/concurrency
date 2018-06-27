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
@NotThreadSafe
public class SingletonTest4 {

    private SingletonTest4(){

    }

    public static SingletonTest4 instance = null;

    //创建对象的步骤1.分配内存空间，2.实例化对象，3.将instance指向分配的内存，当发生指令重排时2和3可能会交换

    /*该方法由于cpu会产生指令重排，所以是不安全的*/
    public static synchronized SingletonTest4 getInstance(){
        if (instance == null) {
            synchronized (SingletonTest4.class) {
                if (instance == null) {
                    instance = new SingletonTest4();
                }
            }
        }
        return instance;
    }

}
