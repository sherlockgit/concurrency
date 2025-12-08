package com.sherlock.concurrency.chapter02.detailed_02_07;

/**
 *
 * 如果内置锁不是可重入的，那么这段代码将发生死锁*
 *
 *
 * 当某个线程请求一个由其他线程持有的锁时，发出请求的线程就会阻塞。
 * 然而，由于内置锁是可重入的，因此如果某个线程试图获得一个已经由它自己持有的锁，那么这个请求就会成功。
 * “重人”意味着获取锁的操作的粒度是“线程”，而不是“调用”。重入的一种实现方法是，为每个锁关联一个获取计数值和一个所有者线程。
 * 当计数值为0时，这个锁就被认为是没有被任何线程持有。当线程请求一个未被持有的锁时，JVM将记下锁的持有者，并且将获取计数值置为1。
 * 如果同一个线程再次获取这个锁，计数值将递增，而当线程退出同步代码块时，计数器会相应地递减。当计数值为0时，这个锁将被释放。
 *
 * 重入进一步提升了加锁行为的封装性，因此简化了面向对象并发代码的开发。下面
 * 子类改写了父类的synchronized方法，然后调用父类中的方法，此时如果没有可重入的锁，那么这段代码将产生死锁。
 * 由于Widget和LoggingWidget中doSomething方法都是synchronized方法，因此每个doSomething方法在执行前都会获取 Widget上的锁。
 * 然而，如果内置锁不是可重人的，那么在调用super.doSomething时将无法获得 Widget 上的锁，
 * 因为这个锁已经被持有，从而线程将永远停顿下去，等待一个永远也无法获得的锁。重入则避免了这种死锁情况的发生。
 */
public class LoggingWidget extends Widget {

    @Override
    public synchronized void doSomething() {
        System.out.println(toString() + ": calling doSomething");
        super.doSomething();
    }
}
