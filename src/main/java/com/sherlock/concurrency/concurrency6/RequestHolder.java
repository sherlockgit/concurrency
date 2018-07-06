package com.sherlock.concurrency.concurrency6;

/**
 * author: 小宇宙
 * date: 2018/7/6
 * 线程封闭ThredLocal
 */
public class RequestHolder {

    private final static ThreadLocal<Long> requestHolder = new ThreadLocal<>();

    public static void add(Long id) {
        requestHolder.set(id);
    }

    public static Long getId() {
        return requestHolder.get();
    }

    public static void remove() {
        requestHolder.remove();
    }

}
