package com.sherlock.concurrency.chapter04.detailed_04_04;

import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter04.detailed_04_05.MutablePoint;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于监视器模式的车辆追踪
 *
 * 虽然类MutablePoint不是线程安全的，但追踪器类是线程安全的。它所包含的Map对象和可变的Point对象都未曾发布。
 * 当需要返回车辆的位置时，通过MutablePoint拷贝构造函数或者deepCopy方法来复制正确的值，从而生成一个新的Map对象，
 * 并且该对象中的值与原有Map对象中的key值和value值都相同。
 *
 * 在某种程度上，这种实现方式是通过在返回客户代码之前复制可变的数据来维持线程安全性的。通常情况下，这并不存在性能问题，
 * 但在车辆容器非常大的情况下将极大地降低性能。此外，由于每次调用getLocation就要复制数据，因此将出现一种错误情况虽然车辆的实际位置发生了变化，
 * 但返回的信息却保持不变。这种情况是好还是坏，要取决于你的需求。如果在location集合上存在内部的一致性需求，那么这就是优点，
 * 在这种情况下返回一致的快照就非常重要。然而，如果调用者需要每辆车的最新信息，那么这就是缺点，因为这需要非常频繁地刷新快照。
 *
 * 大多数对象都是组合对象。当从头开始构建一个类，或者将多个非线程安全的类组合为一个类时，Java监视器模式是非常有用的
 */
@ThreadSafe
public class MonitorVehicleTracker {
    private final Map<String, MutablePoint> locations;

    public MonitorVehicleTracker(Map<String, MutablePoint> locations) {
        this.locations = deepCopy(locations);
    }

    public synchronized Map<String, MutablePoint> getLocations() {
        return deepCopy(locations);
    }

    public synchronized MutablePoint getLocation(String id) {
        MutablePoint loc = locations.get(id);
        return loc == null ? null : new MutablePoint(loc);
    }

    public synchronized void setLocation(String id, int x, int y) {
        MutablePoint loc = locations.get(id);
        if (loc == null)
            throw new IllegalArgumentException("No such ID: " + id);
        loc.x = x;
        loc.y = y;
    }

    private static Map<String, MutablePoint> deepCopy(
            Map<String, MutablePoint> m) {
        Map<String, MutablePoint> result =
                new HashMap<>();
        for (String id : m.keySet())
            result.put(id, new MutablePoint(m.get(id)));
        return Collections
                .unmodifiableMap(result);
    }
}
