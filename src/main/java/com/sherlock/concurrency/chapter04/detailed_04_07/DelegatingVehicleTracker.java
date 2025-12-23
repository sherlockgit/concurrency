package com.sherlock.concurrency.chapter04.detailed_04_07;

import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter04.detailed_04_06.Point;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 将线程安全委托给ConcurrentHashMap
 *
 * 由于Point类是不可变的，因而它是线程安全的。不可变的值可以被自由地共享与发布，因此在返回location时不需要复制。
 *
 * DelegatingVehicleTracker中没有使用任何显式的同步，所有对状态的访问都由ConcurrentHashMap来管理，而且Map所有的键和值都是不可变的。
 *
 *
 * 如果使用最初的MutablePoint类而不是Point类，就会破坏封装性，因为getLocations会发布一个指向可变状态的引用，
 * 而这个引用不是线程安全的。需要注意的是，我们稍微改变了车辆追踪器类的行为。在使用监视器模式的车辆追踪器中返回的是车辆位置的快照，
 * 而在使用委托的车辆追踪器中返回的是一个不可修改但却实时的车辆位置视图。这意味着，如果线程A调用getLocations，
 * 而线程B在随后修改了某些点的位置，那么在返回给线程A的Map中将反映出这些变化。在前面提到过，这可能是一种优点(更新的数据)，
 * 也可能是一种缺点(可能导致不一致的车辆位置视图)，具体情况取决于你的需求。
 */
@ThreadSafe
public class DelegatingVehicleTracker {
    private final ConcurrentMap<String, Point> locations;
    private final Map<String, Point> unmodifiableMap;

    // 构造函数
    public DelegatingVehicleTracker(Map<String, Point> points) {
        locations = new ConcurrentHashMap<>(points);
        unmodifiableMap = Collections.unmodifiableMap(locations);
    }

    // 获取所有位置（返回实时视图）
    public Map<String, Point> getLocations() {
        return unmodifiableMap;  // 只读，但能反映实时变化
    }

    // 获取单个位置
    public Point getLocation(String id) {
        return locations.get(id);
    }

    // 设置位置
    public void setLocation(String id, int x, int y) {
        if (locations.replace(id, new Point(x, y)) == null)
            throw new IllegalArgumentException(
                    "invalid vehicle name: " + id);
    }
}
