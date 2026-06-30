package com.sherlock.concurrency.chapter10.detailed_10_06;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.lang.management.ManagementFactory;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 使用开放调用避免协作对象之间的死锁。
 *
 * <p>这是《Java 并发编程实战》中的 10.6。</p>
 *
 * <p>10.5 中的死锁来自两个协作对象之间的嵌套调用：</p>
 *
 * <p>1. {@code Taxi.setLocation} 持有 Taxi 锁时调用 Dispatcher 的同步方法；</p>
 * <p>2. {@code Dispatcher.getImage} 持有 Dispatcher 锁时调用 Taxi 的同步方法；</p>
 * <p>3. 两条路径形成 Taxi -> Dispatcher 和 Dispatcher -> Taxi 的相反锁顺序。</p>
 *
 * <p>10.6 的修复方法叫“开放调用”（open call）：
 * 调用外部方法时，不要持有当前对象的锁。</p>
 *
 * <p>本例中有两个关键调整：</p>
 *
 * <p>1. {@link Taxi#setLocation(Point)} 只在 Taxi 锁内更新位置并计算是否到达目的地，
 * 释放 Taxi 锁以后再调用 {@link Dispatcher#notifyAvailable(Taxi)}；</p>
 * <p>2. {@link Dispatcher#getImage()} 只在 Dispatcher 锁内复制出租车集合，
 * 释放 Dispatcher 锁以后再逐个调用 {@link Taxi#getLocation()}。</p>
 *
 * <p>这样就不会出现“持有一把锁时再请求另一个协作对象的锁”的交叉等待。</p>
 */
public class CooperatingNoDeadlock {

    /**
     * 出租车。
     */
    @ThreadSafe
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
         * <p>这个方法是 10.6 的重点：
         * 它不会把整个方法声明为 synchronized。
         * 方法只在一个很小的 synchronized 块里更新 Taxi 自己的状态，
         * 然后释放 Taxi 锁，再根据局部变量 reachedDestination 决定是否通知 Dispatcher。</p>
         *
         * <p>因此调用 dispatcher.notifyAvailable(this) 时，当前线程已经不再持有 Taxi 锁。</p>
         */
        public void setLocation(Point location) {
            boolean reachedDestination;

            synchronized (this) {
                this.location = location;
                reachedDestination = location.equals(destination);
            }

            if (reachedDestination) {
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
     */
    @ThreadSafe
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
         * <p>这个方法也使用开放调用。
         * 它先在 Dispatcher 锁内复制一份 taxis 快照，
         * 然后释放 Dispatcher 锁，再调用每辆出租车的 getLocation。</p>
         *
         * <p>这样即使 getLocation 需要获取 Taxi 锁，
         * 当前线程也不会同时持有 Dispatcher 锁。</p>
         */
        public Image getImage() {
            Set<Taxi> copy;

            synchronized (this) {
                copy = new HashSet<Taxi>(taxis);
            }

            Image image = new Image();
            for (Taxi taxi : copy) {
                image.drawMarker(taxi.getLocation());
            }
            return image;
        }
    }

    /**
     * 调度图像。
     */
    class Image {

        public void drawMarker(Point point) {
        }
    }

    /**
     * 简单不可变坐标。
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
     * 简单演示。
     *
     * <p>两个线程分别反复更新出租车位置和生成调度图像。
     * 在 10.5 中，这两条路径会形成相反锁顺序；
     * 在本例中，由于两个路径都使用开放调用，不会同时持有 Taxi 锁和 Dispatcher 锁。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        CooperatingNoDeadlock demo = new CooperatingNoDeadlock();
        final Dispatcher dispatcher = demo.new Dispatcher();
        final Taxi taxi = demo.new Taxi(dispatcher);
        final Point destination = new Point(10, 20);
        final CountDownLatch startGate = new CountDownLatch(1);

        taxi.setDestination(destination);
        dispatcher.addTaxi(taxi);

        Thread updateLocation = new Thread(new Runnable() {
            @Override
            public void run() {
                awaitQuietly(startGate);
                for (int i = 0; i < 10000; i++) {
                    taxi.setLocation(destination);
                }
            }
        }, "taxi-set-location");

        Thread renderImage = new Thread(new Runnable() {
            @Override
            public void run() {
                awaitQuietly(startGate);
                for (int i = 0; i < 10000; i++) {
                    dispatcher.getImage();
                }
            }
        }, "dispatcher-get-image");

        updateLocation.start();
        renderImage.start();
        startGate.countDown();

        updateLocation.join(TimeUnit.SECONDS.toMillis(5));
        renderImage.join(TimeUnit.SECONDS.toMillis(5));

        long[] deadlockedThreadIds = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
        if (deadlockedThreadIds == null && !updateLocation.isAlive() && !renderImage.isAlive()) {
            System.out.println("finished without deadlock");
        } else if (deadlockedThreadIds != null) {
            System.out.println("deadlock detected");
        } else {
            System.out.println("threads did not finish in time, but no deadlock was detected");
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
