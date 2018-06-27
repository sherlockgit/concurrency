package com.sherlock.concurrency.concurrency4;

import com.sherlock.concurrency.annoations.ThreadSafe;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * author: 小宇宙
 * date: 2018/6/27
 * AtomicIntegerFieldUpdate类
 */
@Slf4j
@ThreadSafe
public class AtomicIntegerFieldUpdateTest {

    public static AtomicIntegerFieldUpdater<AtomicIntegerFieldUpdateTest> atomicIntegerFieldUpdater = AtomicIntegerFieldUpdater.newUpdater(AtomicIntegerFieldUpdateTest.class,"count");

    @Getter
    public volatile int count = 100;

    public static void main(String[] args) {

        AtomicIntegerFieldUpdateTest atomicIntegerFieldUpdateTest = new AtomicIntegerFieldUpdateTest();
        if (atomicIntegerFieldUpdater.compareAndSet(atomicIntegerFieldUpdateTest,100,120)) {
            log.info("update success and count = {}",atomicIntegerFieldUpdateTest.getCount());
        }

        if (atomicIntegerFieldUpdater.compareAndSet(atomicIntegerFieldUpdateTest,100,120)) {
            log.info("update success and count = {}",atomicIntegerFieldUpdateTest.getCount());
        }else {
            log.info("update error and count = {}",atomicIntegerFieldUpdateTest.getCount());
        }
    }
}
