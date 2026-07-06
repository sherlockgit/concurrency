package com.sherlock.concurrency.chapter11.detailed_11_07;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.HashSet;
import java.util.Set;

/**
 * 使用锁分解重构后的服务器状态。
 *
 * <p>这是《Java 并发编程实战》中的 11.7。</p>
 *
 * <p>11.6 中，users 和 queries 两个彼此独立的集合都由同一把 this 锁保护。
 * 这样虽然线程安全，但用户操作和查询操作会互相阻塞。</p>
 *
 * <p>本例使用锁分解：</p>
 *
 * <p>1. users 集合由 users 自身的锁保护；</p>
 * <p>2. queries 集合由 queries 自身的锁保护；</p>
 * <p>3. 用户相关操作只竞争 users 锁；</p>
 * <p>4. 查询相关操作只竞争 queries 锁。</p>
 *
 * <p>这样，如果一个线程正在添加用户，另一个线程正在添加查询，
 * 它们访问的是不同状态，也竞争不同锁，因此可以并发执行。</p>
 *
 * <p>锁分解适用的前提是：被不同锁保护的状态之间没有必须同时维护的不变性。
 * 如果 users 和 queries 之间存在一个跨集合不变性，
 * 例如“每个 query 必须属于一个已登录 user”，那么拆锁时就要更谨慎。</p>
 */
@ThreadSafe
public class ServerStatusAfterSplit {

    /**
     * 当前用户集合。
     *
     * <p>由 users 自身的对象锁保护。</p>
     */
    private final Set<String> users;

    /**
     * 当前查询集合。
     *
     * <p>由 queries 自身的对象锁保护。</p>
     */
    private final Set<String> queries;

    public ServerStatusAfterSplit() {
        users = new HashSet<String>();
        queries = new HashSet<String>();
    }

    /**
     * 添加用户。
     */
    public void addUser(String user) {
        synchronized (users) {
            users.add(user);
        }
    }

    /**
     * 添加查询。
     */
    public void addQuery(String query) {
        synchronized (queries) {
            queries.add(query);
        }
    }

    /**
     * 删除用户。
     */
    public void removeUser(String user) {
        synchronized (users) {
            users.remove(user);
        }
    }

    /**
     * 删除查询。
     *
     * <p>官方清单中这个方法锁住 users 后操作 queries。
     * 按本例“users 由 users 锁保护，queries 由 queries 锁保护”的同步策略，
     * 这里应该锁住 queries。否则查询集合的删除操作就没有使用正确的保护锁。</p>
     */
    public void removeQuery(String query) {
        synchronized (queries) {
            queries.remove(query);
        }
    }

    /**
     * 返回用户快照。
     */
    public Set<String> getUsersSnapshot() {
        synchronized (users) {
            return new HashSet<String>(users);
        }
    }

    /**
     * 返回查询快照。
     */
    public Set<String> getQueriesSnapshot() {
        synchronized (queries) {
            return new HashSet<String>(queries);
        }
    }

    /**
     * 简单演示。
     */
    public static void main(String[] args) {
        ServerStatusAfterSplit status = new ServerStatusAfterSplit();

        status.addUser("alice");
        status.addUser("bob");
        status.addQuery("select * from orders");
        status.addQuery("select * from users");
        status.removeUser("bob");
        status.removeQuery("select * from users");

        System.out.println("users=" + status.getUsersSnapshot());
        System.out.println("queries=" + status.getQueriesSnapshot());
    }
}
