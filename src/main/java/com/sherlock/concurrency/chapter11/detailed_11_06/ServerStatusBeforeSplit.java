package com.sherlock.concurrency.chapter11.detailed_11_06;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.HashSet;
import java.util.Set;

/**
 * 锁分解的候选类。
 *
 * <p>这是《Java 并发编程实战》中的 11.6。</p>
 *
 * <p>这个类维护服务器的两类状态：</p>
 *
 * <p>1. 当前用户集合 {@link #users}；</p>
 * <p>2. 当前查询集合 {@link #queries}。</p>
 *
 * <p>这两个集合彼此独立：
 * 添加用户不需要访问 queries，添加查询也不需要访问 users。
 * 但是在这个版本中，所有方法都使用同一把锁，也就是当前对象的 this 锁。</p>
 *
 * <p>这意味着：</p>
 *
 * <p>1. 一个线程正在执行 {@link #addUser(String)} 时；</p>
 * <p>2. 另一个线程即使只是想执行 {@link #addQuery(String)}；</p>
 * <p>3. 也必须等待第一个线程释放 this 锁。</p>
 *
 * <p>所以这个类虽然是线程安全的，但可伸缩性不好。
 * 它把两个原本互不相关的状态集合绑定到了同一把锁上，
 * 人为制造了不必要的锁竞争。</p>
 *
 * <p>11.7 会展示“锁分解”：用一把锁保护 users，
 * 另一把锁保护 queries，让两类互不相关的操作可以并发执行。</p>
 */
@ThreadSafe
public class ServerStatusBeforeSplit {

    /**
     * 当前用户集合。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private final Set<String> users;

    /**
     * 当前查询集合。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private final Set<String> queries;

    public ServerStatusBeforeSplit() {
        users = new HashSet<String>();
        queries = new HashSet<String>();
    }

    /**
     * 添加用户。
     *
     * <p>这个方法只访问 users，但由于它是 synchronized，
     * 会占用整个 ServerStatusBeforeSplit 对象锁。</p>
     */
    public synchronized void addUser(String user) {
        users.add(user);
    }

    /**
     * 添加查询。
     *
     * <p>这个方法只访问 queries，但它也使用同一把 this 锁。
     * 因此它会和 addUser/removeUser 互相阻塞。</p>
     */
    public synchronized void addQuery(String query) {
        queries.add(query);
    }

    /**
     * 删除用户。
     */
    public synchronized void removeUser(String user) {
        users.remove(user);
    }

    /**
     * 删除查询。
     */
    public synchronized void removeQuery(String query) {
        queries.remove(query);
    }

    /**
     * 返回用户快照。
     *
     * <p>不能直接返回内部 HashSet，否则外部代码可以绕过锁修改集合。
     * 这里返回副本，保持封装。</p>
     */
    public synchronized Set<String> getUsersSnapshot() {
        return new HashSet<String>(users);
    }

    /**
     * 返回查询快照。
     */
    public synchronized Set<String> getQueriesSnapshot() {
        return new HashSet<String>(queries);
    }

    /**
     * 简单演示。
     */
    public static void main(String[] args) {
        ServerStatusBeforeSplit status = new ServerStatusBeforeSplit();

        status.addUser("alice");
        status.addUser("bob");
        status.addQuery("select * from orders");
        status.addQuery("select * from users");
        status.removeUser("bob");

        System.out.println("users=" + status.getUsersSnapshot());
        System.out.println("queries=" + status.getQueriesSnapshot());
    }
}
