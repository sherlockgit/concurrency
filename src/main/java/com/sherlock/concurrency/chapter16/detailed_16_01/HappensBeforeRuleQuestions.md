# Happens-Before 常见规则问答

## 问题列表

这篇笔记整理以下几个问题：

- 什么是“一个锁的 unlock happens-before 后续对同一把锁的 lock”？
- 什么是“对 volatile 变量的写 happens-before 后续对同一个 volatile 变量的读”？
- 什么是“Thread.start() happens-before 新线程中的动作”？
- 什么是“线程中的所有动作 happens-before 其他线程从 join() 成功返回”？
- 什么是“一个线程调用 interrupt() happens-before 被中断线程检测到中断”？

## 先记住一句话

`Happens-Before` 不是单纯表示真实时间上的先后，而是表示：

```text
如果 A happens-before B，
那么 A 的结果必须对 B 可见。
```

所以它主要解决的是：

```text
一个线程做过的修改，另一个线程什么时候必须看得见？
```

## 一个锁的 unlock happens-before 后续对同一把锁的 lock

### 问题

什么是“一个锁的 `unlock` happens-before 后续对同一把锁的 `lock`”？

### 通俗理解

这句话的意思是：

```text
线程 A 在释放某把锁之前做过的修改，
线程 B 后续拿到同一把锁之后，必须能看到。
```

注意关键词是：

```text
同一把锁
```

### synchronized 示例

```java
class Demo {
    private final Object lock = new Object();
    private int value = 0;

    public void write() {
        synchronized (lock) {
            value = 3;
        } // 这里释放 lock
    }

    public void read() {
        synchronized (lock) { // 这里获取同一个 lock
            System.out.println(value);
        }
    }
}
```

如果线程 A 执行 `write()`，线程 B 后续执行 `read()`，那么有：

```text
线程 A 退出 synchronized(lock)
happens-before
线程 B 进入 synchronized(lock)
```

所以线程 B 进入同步块后，必须能看到线程 A 在释放锁之前写入的：

```text
value = 3
```

### Lock 示例

`ReentrantLock` 也一样：

```java
lock.lock();
try {
    value = 3;
} finally {
    lock.unlock();
}
```

另一个线程：

```java
lock.lock();
try {
    System.out.println(value);
} finally {
    lock.unlock();
}
```

只要是同一把 `lock`，前一个线程 `unlock()` 之前的写入，对后一个线程 `lock()` 之后可见。

### 反例：不是同一把锁

```java
Object lock1 = new Object();
Object lock2 = new Object();
int value = 0;

// 线程 A
synchronized (lock1) {
    value = 3;
}

// 线程 B
synchronized (lock2) {
    System.out.println(value);
}
```

这里不成立，因为不是同一把锁：

```text
unlock(lock1)
不会 happens-before
lock(lock2)
```

所以线程 B 不保证看到 `value = 3`。

### 小结

```text
同一把锁的 unlock -> 后续 lock，
会把前一个线程在释放锁前的修改，安全交给后一个拿锁的线程。
```

## 对 volatile 变量的写 happens-before 后续对同一个 volatile 变量的读

### 问题

什么是“对 `volatile` 变量的写 happens-before 后续对同一个 `volatile` 变量的读”？

### 通俗理解

这句话的意思是：

```text
线程 A 写某个 volatile 变量之前做过的修改，
线程 B 后续读到这个 volatile 变量之后，必须能看到。
```

注意关键词是：

```text
同一个 volatile 变量
```

### 示例

```java
class Demo {
    private int data = 0;
    private volatile boolean ready = false;

    public void write() {
        data = 3;
        ready = true; // volatile 写
    }

    public void read() {
        if (ready) {  // volatile 读
            System.out.println(data);
        }
    }
}
```

如果线程 A 执行：

```java
data = 3;
ready = true;
```

线程 B 后续读到：

```text
ready == true
```

那么有：

```text
线程 A 写 ready = true
happens-before
线程 B 读 ready == true
```

再结合程序顺序规则：

```text
data = 3
happens-before
ready = true
```

通过传递性得到：

```text
data = 3
happens-before
线程 B 读取 data
```

因此线程 B 必须看到 `data == 3`。

### 可以理解成通知牌

```text
线程 A：
先把 data 准备好
再把 volatile 通知牌 ready 改成 true

线程 B：
先看 ready
如果看到 true，就能看到 A 准备好的 data
```

### 反例：不是同一个 volatile 变量

```java
volatile boolean ready1;
volatile boolean ready2;
int data;

// 线程 A
data = 3;
ready1 = true;

// 线程 B
if (ready2) {
    System.out.println(data);
}
```

这里不成立，因为线程 A 写的是 `ready1`，线程 B 读的是 `ready2`。

```text
写 ready1
不会 happens-before
读 ready2
```

### volatile 不保证复合操作原子性

`volatile` 保证可见性和有序性，但不保证复合操作原子性。

例如：

```java
volatile int count = 0;
count++;
```

`count++` 包含三个动作：

```text
读取 count
加 1
写回 count
```

多个线程同时执行仍然可能丢失更新。

### 小结

```text
volatile 写像发布通知；
volatile 读像接收通知；
读到通知后，必须能看到通知之前发布的数据。
```

## Thread.start() happens-before 新线程中的动作

### 问题

什么是“`Thread.start()` happens-before 新线程中的动作”？

### 通俗理解

这句话的意思是：

```text
主线程在调用 thread.start() 之前做过的修改，
新线程启动后必须能看到。
```

### 示例

```java
class Demo {
    private int data = 0;

    public void runDemo() {
        data = 3;

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println(data);
            }
        });

        thread.start();
    }
}
```

这里有：

```text
data = 3
happens-before
thread.start()

thread.start()
happens-before
新线程中的 run()
```

通过传递性：

```text
data = 3
happens-before
新线程读取 data
```

所以新线程必须能看到 `data == 3`。

### start 是安全发布

可以把 `start()` 理解成：

```text
把 start() 之前准备好的数据，安全发布给新线程。
```

### 反例：start 之后才写

```java
thread.start();
data = 3;
```

这里 `data = 3` 发生在 `start()` 之后。

因此不能用 `Thread.start()` 规则保证新线程一定看到 `data == 3`。

### 小结

```text
调用 start() 之前准备好的东西，新线程开始运行后必须看得见；
调用 start() 之后才修改的东西，不靠 start() 保证可见性。
```

## 线程中的所有动作 happens-before 其他线程从 join() 成功返回

### 问题

什么是“线程中的所有动作 happens-before 其他线程从 `join()` 成功返回”？

### 通俗理解

这句话的意思是：

```text
线程 A 执行完之前做过的所有修改，
线程 B 只要成功从 A.join() 返回，
就必须能看到这些修改。
```

### 示例

```java
class Demo {
    private int data = 0;

    public void runDemo() throws InterruptedException {
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                data = 3;
            }
        });

        worker.start();
        worker.join();

        System.out.println(data);
    }
}
```

这里有：

```text
worker 线程中的 data = 3
happens-before
主线程从 worker.join() 成功返回
```

所以 `join()` 返回后，主线程必须能看到：

```text
data == 3
```

### join 可以理解成拿回结果

```text
start() 是把数据安全交给新线程；
join() 是等新线程结束后，把它的结果安全拿回来。
```

### 注意：必须是成功等待线程结束

如果使用带超时的 `join`：

```java
worker.join(1);
```

但 `worker` 还没有结束，`join(1)` 因超时返回，那么不能用这条规则保证看到 worker 的所有结果。

因为被 `join` 的线程还没结束。

### 小结

```text
join() 成功返回后，
调用 join 的线程必须能看到被 join 线程结束前做过的修改。
```

## 一个线程调用 interrupt() happens-before 被中断线程检测到中断

### 问题

什么是“一个线程调用 `interrupt()` happens-before 被中断线程检测到中断”？

### 通俗理解

这句话的意思是：

```text
线程 A 在调用线程 B.interrupt() 之前做过的修改，
线程 B 在检测到自己被中断后，必须能看到这些修改。
```

### 示例

```java
class Demo {
    private int data = 0;
    private Thread worker;

    public void start() {
        worker = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    // 正常工作
                }

                System.out.println(data);
            }
        });

        worker.start();
    }

    public void stop() {
        data = 3;
        worker.interrupt();
    }
}
```

这里有：

```text
线程 A 写 data = 3
happens-before
线程 A 调用 worker.interrupt()

线程 A 调用 worker.interrupt()
happens-before
worker 线程检测到中断
```

通过传递性：

```text
data = 3
happens-before
worker 检测中断后读取 data
```

所以 worker 在检测到中断以后，必须能看到 `data == 3`。

### 什么叫检测到中断

检测中断包括：

```java
Thread.currentThread().isInterrupted()
Thread.interrupted()
```

也包括可中断阻塞方法抛出 `InterruptedException`：

```java
Thread.sleep(...)
BlockingQueue.take()
CountDownLatch.await()
Thread.join()
```

例如：

```java
try {
    Thread.sleep(10_000);
} catch (InterruptedException e) {
    System.out.println(data);
}
```

如果这个异常是因为另一个线程调用了 `interrupt()`，那么进入 `catch` 后，也能看到 `interrupt()` 之前发布的写入。

### interrupt 不等于强制停止

`interrupt()` 只是发出中断请求。

它不会强制杀死线程。

被中断线程必须：

- 主动检查中断状态；
- 或者在可中断阻塞方法中被唤醒；
- 然后自己决定如何结束、清理或继续执行。

### 小结

```text
interrupt() 不只是通知中断，也是一种可见性发布；
被中断线程一旦检测到中断，就能看到 interrupt() 之前的写入。
```

## 总结对比

| 规则 | 可以怎么理解 |
| --- | --- |
| `unlock` happens-before 后续同一把锁的 `lock` | 释放锁前的修改，交给后续拿到同一把锁的线程 |
| `volatile` 写 happens-before 后续同一个 `volatile` 读 | 写通知前准备的数据，读通知后必须看得见 |
| `Thread.start()` happens-before 新线程动作 | 启动前准备的数据，新线程必须看得见 |
| 线程动作 happens-before `join()` 成功返回 | 子线程结束前留下的结果，join 返回后必须看得见 |
| `interrupt()` happens-before 检测到中断 | 发中断前发布的数据，被中断线程检测中断后必须看得见 |

## 最终记忆

```text
Happens-Before 不是“时间先后”的口头描述，
而是 Java 内存模型给出的“可见性保证”。
```

更简单地说：

```text
有 Happens-Before，就必须看得见；
没有 Happens-Before，就不能假设看得见。
```

