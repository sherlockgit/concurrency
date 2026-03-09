package com.sherlock.concurrency.chapter04.detailed_04_12;

import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter04.detailed_04_11.SafePoint;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全发布底层状态的车辆追踪器
 * *
 * PublishingVehicleTracker 将其线程安全性委托给底层的 ConcurrentHashMap，
 * * 只是 Map 中的元素是线程安全的且可变的Point，而并非不可变的。getLocation方法返回底层Map对象的一个不可变副本。
 * * 调用者不能增加或删除车辆，但却可以通过修改返回Map中的SafePoint值来改变车辆的位置。再次指出，Map的这种“实时”特性究竟是带来好处还是坏处，
 * * 仍然取决于实际的需求。PublishingVehicleTracker 是线程安全的，但如果它在车辆位置的有效值上施加了任何约束，
 * * 那么就不再是线程安全的。如果需要对车辆位置的变化进行判断或者当位置变化时执行一些操作，那么PublishingVehicleTracker中采用的方法并不合适。
 */
@ThreadSafe
public class PublishingVehicleTracker {
    private final Map<String, SafePoint> locations;
    private final Map<String, SafePoint> unmodifiableMap;

    public PublishingVehicleTracker(
            Map<String, SafePoint> locations) {
        this.locations = new ConcurrentHashMap<>(locations);
        this.unmodifiableMap = Collections.unmodifiableMap(this.locations);
    }

    public Map<String, SafePoint> getLocations() {
        return unmodifiableMap;
    }

    public SafePoint getLocation(String id) {
        return locations.get(id);
    }

    public void setLocation(String id, int x, int y) {
        if (!locations.containsKey(id)) {
            throw new IllegalArgumentException(
                    "invalid vehicle name: " + id);
        }
        locations.get(id).set(x,y);
    }
}
