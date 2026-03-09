package com.sherlock.concurrency.chapter04.detailed_04_08;

import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter04.detailed_04_06.Point;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * 返回locations的静态拷贝而非实时拷贝
 *
 * 如果需要一个不发生变化的车辆视图，那么getLocations可以返回对locations这个Map对象的一个浅拷贝(Shallow Copy)。
 * 由于Map的内容是不可变的，因此只需复制Map的结构，而不用复制它的内容，如程序所示(其中只返回一个HashMap，因为getLocations并不能保证返回一个线程安全的Map)。
 */
@ThreadSafe
public class DelegatingVehicleTrackerShallowCopy {
    private final ConcurrentMap<String, Point> locations;

    public DelegatingVehicleTrackerShallowCopy(ConcurrentMap<String, Point> locations) {
        this.locations = locations;
    }

    public Map<String,Point> getLocations(){
        return Collections.unmodifiableMap(new HashMap<>(locations));
    }
}
