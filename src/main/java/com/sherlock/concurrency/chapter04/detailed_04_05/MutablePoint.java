package com.sherlock.concurrency.chapter04.detailed_04_05;

import com.sherlock.concurrency.annoations.NotThreadSafe;

/**
 * 与Java.awt.Point类似的可变Point类(不要这么做)
 */
@NotThreadSafe
public class MutablePoint {
    public int x, y;

    public MutablePoint() {
        x = 0;
        y = 0;
    }

    public MutablePoint(MutablePoint p) {
        this.x = p.x;
        this.y = p.y;
    }
}