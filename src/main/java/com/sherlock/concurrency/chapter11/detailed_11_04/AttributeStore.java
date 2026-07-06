package com.sherlock.concurrency.chapter11.detailed_11_04;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 持有锁的时间过长。
 *
 * <p>这是《Java 并发编程实战》中的 11.4。</p>
 *
 * <p>这个类维护一个属性表，属性表本身是普通 {@link HashMap}，
 * 因此需要用同步来保护。</p>
 *
 * <p>问题出在 {@link #userLocationMatches(String, String)}：
 * 整个方法都被声明为 synchronized。
 * 这意味着当前线程从 Map 中读取 location 以后，
 * 仍然继续持有 AttributeStore 的对象锁去执行正则匹配。</p>
 *
 * <p>正则匹配可能比一次 Map 读取慢得多。
 * 如果很多线程同时调用这个方法，那么它们会在同一把锁上排队，
 * 即使它们只是想读取不同用户的位置，也必须等前一个线程完成正则匹配。</p>
 *
 * <p>所以这个例子的重点不是“同步错了”，而是：</p>
 *
 * <p>1. 共享状态 attributes 确实需要同步保护；</p>
 * <p>2. 但正则匹配不访问共享状态，不应该放在锁内；</p>
 * <p>3. 锁持有时间越长，锁竞争越严重，可伸缩性越差。</p>
 *
 * <p>11.5 会展示如何缩短锁持有时间。</p>
 */
@ThreadSafe
public class AttributeStore {

    /**
     * 属性表。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private final Map<String, String> attributes = new HashMap<String, String>();

    /**
     * 设置用户位置。
     *
     * <p>官方 11.4 只展示读取方法。
     * 这里补一个写入方法，方便 main 方法演示。</p>
     */
    public synchronized void setUserLocation(String name, String location) {
        attributes.put(userLocationKey(name), location);
    }

    /**
     * 判断用户位置是否匹配某个正则表达式。
     *
     * <p>警告：这个方法持有锁的时间比实际需要更长。</p>
     *
     * <p>真正需要锁保护的是 {@code attributes.get(key)}，
     * 因为 attributes 是共享的可变 Map。
     * 但 {@link Pattern#matches(String, CharSequence)} 只处理局部变量 regexp 和 location，
     * 不需要访问 attributes。
     * 把它放在 synchronized 方法里，会让其他线程在正则匹配期间也无法访问 attributes。</p>
     *
     * @param name 用户名
     * @param regexp 位置匹配正则
     * @return 如果用户位置存在并匹配 regexp，返回 true
     */
    public synchronized boolean userLocationMatches(String name, String regexp) {
        String key = userLocationKey(name);
        String location = attributes.get(key);
        if (location == null) {
            return false;
        } else {
            return Pattern.matches(regexp, location);
        }
    }

    private static String userLocationKey(String name) {
        return "users." + name + ".location";
    }

    /**
     * 简单演示。
     */
    public static void main(String[] args) {
        AttributeStore store = new AttributeStore();
        store.setUserLocation("alice", "Shanghai");
        store.setUserLocation("bob", "Beijing");

        System.out.println(store.userLocationMatches("alice", "Shang.*"));
        System.out.println(store.userLocationMatches("bob", "Shang.*"));
        System.out.println(store.userLocationMatches("carol", ".*"));
    }
}
