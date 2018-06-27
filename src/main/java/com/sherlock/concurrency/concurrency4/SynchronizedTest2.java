package com.sherlock.concurrency.concurrency4;

import com.sherlock.concurrency.annoations.ThreadSafe;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * author: 小宇宙
 * date: 2018/6/27
 */
@Slf4j
@ThreadSafe
public class SynchronizedTest2 {

    public void test1(){
        synchronized (SynchronizedTest2.class) {
            for (int i = 1;i<10;i++){
                log.info("i={}",i);
            }
        }
    }

    public static synchronized void test2() {
        for (int i = 1;i<10;i++){
            log.info("i={}",i);
        }
    }

    public static void main(String[] args) {
        SynchronizedTest2 synchronizedTest1 = new SynchronizedTest2();
        SynchronizedTest2 synchronizedTest2 = new SynchronizedTest2();
        ExecutorService executorService = Executors.newCachedThreadPool();
//        executorService.execute(()->{
//            synchronizedTest.test1();
//        });
//
//        executorService.execute(()->{
//            synchronizedTest.test1();
//        });

        executorService.execute(()->{
            synchronizedTest1.test2();
        });

        executorService.execute(()->{
            synchronizedTest2.test2();
        });
    }
}
