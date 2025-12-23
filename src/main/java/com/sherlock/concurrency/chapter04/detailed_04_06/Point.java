package com.sherlock.concurrency.chapter04.detailed_04_06;

/**
 * 在DelegatingVehicleTracker中使用的不可变Point类
 */
public class Point {

    public final int x,y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
