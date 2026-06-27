package com.sherlock.concurrency.chapter10.detailed_10_05;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 协作对象之间的锁顺序死锁。
 *
 * <p>这是《Java 并发编程实战》中的 10.5。</p>
 *
 * <p>10.1、10.2 的死锁都比较直接：代码中可以明显看到两把锁的获取顺序不一致。
 * 10.5 更隐蔽，因为死锁发生在两个相互协作的对象之间：</p>
 *
 * <p>1. {@link Taxi#setLocation(Point)} 是 synchronized 方法，
 * 调用时会先持有 Taxi 对象锁；</p>
 * <p>2. 当出租车到达目的地时，setLocation 会调用
 * {@link Dispatcher#notifyAvailable(Taxi)}；</p>
 * <p>3. notifyAvailable 也是 synchronized 方法，
 * 调用时需要获取 Dispatcher 对象锁；</p>
 * <p>4. 另一边，{@link Dispatcher#getImage()} 是 synchronized 方法，
 * 调用时会先持有 Dispatcher 对象锁；</p>
 * <p>5. getImage 在遍历出租车时会调用 {@link Taxi#getLocation()}，
 * 这个方法又需要获取 Taxi 对象锁。</p>
 *
 * <p>于是锁顺序变成：</p>
 *
 * <p>setLocation：Taxi 锁 -> Dispatcher 锁；</p>
 * <p>getImage：Dispatcher 锁 -> Taxi 锁。</p>
 *
 * <p>两个调用路径使用相反的锁顺序，因此可能死锁。</p>
 */
public class CooperatingDeadlock {

    /**
     * 只用于 main 演示的同步门闩。
     *
     * <p>实际业务代码不需要这个字段。它的作用是让两个演示线程稳定地各自拿到第一把锁，
     * 从而稳定复现死锁。</p>
     */
    private static volatile CountDownLatch demoFirstLocksAcquired;

    /**
     * 出租车。
     *
     * <p>警告：这个类有死锁风险。
     * 问题不在于 synchronized 本身，而在于持有 Taxi 锁时调用了 Dispatcher 的同步方法。</p>
     */
    class Taxi {

        /**
         * 当前位置。
         *
         * <p>由 Taxi 对象锁保护。</p>
         */
        private Point location;

        /**
         * 目的地。
         *
         * <p>由 Taxi 对象锁保护。</p>
         */
        private Point destination;

        private final Dispatcher dispatcher;

        public Taxi(Dispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        public synchronized Point getLocation() {
            return location;
        }

        /**
         * 更新出租车位置。
         *
         * <p>这个方法持有 Taxi 锁。
         * 当出租车到达目的地时，它会继续调用 dispatcher.notifyAvailable(this)，
         * 也就是在持有 Taxi 锁的情况下尝试获取 Dispatcher 锁。</p>
         */
        public synchronized void setLocation(Point location) {
            this.location = location;
            if (location.equals(destination)) {
                awaitDemoFirstLockIfNeeded();
                dispatcher.notifyAvailable(this);
            }
        }

        public synchronized Point getDestination() {
            return destination;
        }

        public synchronized void setDestination(Point destination) {
            this.destination = destination;
        }
    }

    /**
     * 出租车调度器。
     *
     * <p>警告：这个类也有死锁风险。
     * 问题在于 getImage 持有 Dispatcher 锁时调用了 Taxi 的同步方法。</p>
     */
    class Dispatcher {

        /**
         * 所有出租车。
         *
         * <p>由 Dispatcher 对象锁保护。</p>
         */
        private final Set<Taxi> taxis;

        /**
         * 当前可用出租车。
         *
         * <p>由 Dispatcher 对象锁保护。</p>
         */
        private final Set<Taxi> availableTaxis;

        public Dispatcher() {
            taxis = new HashSet<Taxi>();
            availableTaxis = new HashSet<Taxi>();
        }

        public synchronized void addTaxi(Taxi taxi) {
            taxis.add(taxi);
        }

        public synchronized void notifyAvailable(Taxi taxi) {
            availableTaxis.add(taxi);
        }

        /**
         * 获取调度视图。
         *
         * <p>这个方法持有 Dispatcher 锁。
         * 它遍历所有出租车并调用 t.getLocation()，
         * 也就是在持有 Dispatcher 锁的情况下尝试获取 Taxi 锁。</p>
         */
        public synchronized Image getImage() {
            Image image = new Image();
            for (Taxi taxi : taxis) {
                awaitDemoFirstLockIfNeeded();
                image.drawMarker(taxi.getLocation());
            }
            return image;
        }
    }

    /**
     * 调度图像。
     *
     * <p>这里只是示例占位，真实系统可能会把出租车位置画到地图上。</p>
     */
    class Image {

        public void drawMarker(Point point) {
        }
    }

    /**
     * 简单不可变坐标。
     *
     * <p>官方示例使用 Point 表示位置。
     * 这里提供一个最小实现，避免依赖 AWT 类型。</p>
     */
    static final class Point {

        private final int x;

        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Point)) {
                return false;
            }
            Point point = (Point) other;
            return x == point.x && y == point.y;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            return result;
        }
    }

    /**
     * 演示协作对象死锁。
     *
     * <p>线程一调用 taxi.setLocation(destination)，先持有 Taxi 锁，
     * 然后尝试调用 dispatcher.notifyAvailable 获取 Dispatcher 锁。</p>
     *
     * <p>线程二调用 dispatcher.getImage()，先持有 Dispatcher 锁，
     * 然后尝试调用 taxi.getLocation() 获取 Taxi 锁。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        CooperatingDeadlock demo = new CooperatingDeadlock();
        final Dispatcher dispatcher = demo.new Dispatcher();
        final Taxi taxi = demo.new Taxi(dispatcher);
        final Point destination = new Point(10, 20);

        taxi.setDestination(destination);
        dispatcher.addTaxi(taxi);

        demoFirstLocksAcquired = new CountDownLatch(2);

        Thread updateLocation = new Thread(new Runnable() {
            @Override
            public void run() {
                taxi.setLocation(destination);
            }
        }, "taxi-set-location");

        Thread renderImage = new Thread(new Runnable() {
            @Override
            public void run() {
                dispatcher.getImage();
            }
        }, "dispatcher-get-image");

        updateLocation.setDaemon(true);
        renderImage.setDaemon(true);

        updateLocation.start();
        renderImage.start();

        printDetectedDeadlock();
        demoFirstLocksAcquired = null;
    }

    private static void awaitDemoFirstLockIfNeeded() {
        CountDownLatch latch = demoFirstLocksAcquired;
        if (latch == null) {
            return;
        }

        latch.countDown();
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printDetectedDeadlock() throws InterruptedException {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        for (int i = 0; i < 20; i++) {
            long[] deadlockedThreadIds = threadMXBean.findDeadlockedThreads();
            if (deadlockedThreadIds != null) {
                ThreadInfo[] threadInfos =
                        threadMXBean.getThreadInfo(deadlockedThreadIds, true, true);
                System.out.println("deadlock detected:");
                for (ThreadInfo threadInfo : threadInfos) {
                    System.out.println(threadInfo.getThreadName()
                            + " waiting for " + threadInfo.getLockName());
                }
                return;
            }

            TimeUnit.MILLISECONDS.sleep(100);
        }

        System.out.println("deadlock not detected in time");
    }
}
