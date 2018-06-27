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
public class SynchronizedTest {

    public void test1(){
        synchronized (this) {
            for (int i = 1;i<10;i++){
                log.info("i={}",i);
            }
        }
    }

    public synchronized void test2() {
        for (int i = 1;i<10;i++){
            log.info("i={}",i);
        }
    }

    public static void main(String[] args) {
        SynchronizedTest synchronizedTest = new SynchronizedTest();
        ExecutorService executorService = Executors.newCachedThreadPool();
//        executorService.execute(()->{
//            synchronizedTest.test1();
//        });
//
//        executorService.execute(()->{
//            synchronizedTest.test1();
//        });

        executorService.execute(()->{
            synchronizedTest.test2();
        });

        executorService.execute(()->{
            synchronizedTest.test2();
        });
    }
}
