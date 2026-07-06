package com.sherlock.concurrency.chapter11.detailed_11_05;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 缩短锁持有时间。
 *
 * <p>这是《Java 并发编程实战》中的 11.5。</p>
 *
 * <p>11.4 的 {@code AttributeStore} 把整个 {@code userLocationMatches} 方法声明为
 * synchronized，导致正则匹配也在锁内执行。
 * 但正则匹配只依赖局部变量，不需要访问共享 Map。</p>
 *
 * <p>本例的改进是：</p>
 *
 * <p>1. 在同步块内只读取共享状态 {@code attributes}；</p>
 * <p>2. 把读取到的 {@code location} 保存到局部变量；</p>
 * <p>3. 退出同步块；</p>
 * <p>4. 在锁外执行可能较慢的正则匹配。</p>
 *
 * <p>这样做不会改变线程安全性，因为 Map 的访问仍然受到锁保护；
 * 但它缩短了锁持有时间，让其他线程能更早进入同步块访问 Map，
 * 从而降低锁竞争，提高可伸缩性。</p>
 */
@ThreadSafe
public class BetterAttributeStore {

    /**
     * 属性表。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private final Map<String, String> attributes = new HashMap<String, String>();

    /**
     * 设置用户位置。
     *
     * <p>写入共享 Map，必须持有 this 锁。</p>
     */
    public synchronized void setUserLocation(String name, String location) {
        attributes.put(userLocationKey(name), location);
    }

    /**
     * 判断用户位置是否匹配某个正则表达式。
     *
     * <p>和 11.4 相比，这个方法不再整体 synchronized。
     * 它只在读取 attributes 时加锁。
     * 读取完成后，location 是局部变量，不再需要锁保护，
     * 因此正则匹配可以放在锁外执行。</p>
     *
     * @param name 用户名
     * @param regexp 位置匹配正则
     * @return 如果用户位置存在并匹配 regexp，返回 true
     */
    public boolean userLocationMatches(String name, String regexp) {
        String key = userLocationKey(name);
        String location;

        synchronized (this) {
            location = attributes.get(key);
        }

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
        BetterAttributeStore store = new BetterAttributeStore();
        store.setUserLocation("alice", "Shanghai");
        store.setUserLocation("bob", "Beijing");

        System.out.println(store.userLocationMatches("alice", "Shang.*"));
        System.out.println(store.userLocationMatches("bob", "Shang.*"));
        System.out.println(store.userLocationMatches("carol", ".*"));
    }
}
