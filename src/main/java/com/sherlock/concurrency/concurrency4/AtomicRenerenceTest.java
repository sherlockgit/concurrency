package com.sherlock.concurrency.concurrency4;

import com.sherlock.concurrency.annoations.ThreadSafe;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

/**
 * author: 小宇宙
 * date: 2018/6/27
 * AtomicReference类
 */
@Slf4j
@ThreadSafe
public class AtomicRenerenceTest {

    public static AtomicReference<Integer> atomicReference = new AtomicReference<>(0);

    public static void main(String[] args) {
        atomicReference.compareAndSet(0,2);
        atomicReference.compareAndSet(0,1);
        atomicReference.compareAndSet(2,4);
        atomicReference.compareAndSet(1,5);
        log.info("count={}",atomicReference.get());
    }
}
