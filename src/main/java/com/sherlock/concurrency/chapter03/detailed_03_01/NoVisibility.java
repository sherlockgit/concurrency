package com.sherlock.concurrency.chapter03.detailed_03_01;

import com.sherlock.concurrency.annoations.NotThreadSafe;

/**
 * 在没有同步的情况下共享变量
 *
 * 可见性是一种复杂的属性，因为可见性中的错误总是会违背我们的直觉。
 * 在单线程环境中，如果向某个变量先写入值，然后在没有其他写入操作的情况下读取这个变量，
 * 那么总能得到相同的值。这看起来很自然。然而，当读操作和写操作在不同的线程中执行时，情况却并非如此，
 * 这听起来或许有些难以接受。通常，我们无法确保执行读操作的线程能适时地看到其他线程写入的值，有时甚至是根本不可能的事情。
 * 为了确保多个线程之间对内存写入操作的可见性，必须使用同步机制。
 *
 *
 * 在程序NoVisibility说明了当多个线程在没有同步的情况下共享数据时出现的错误。
 * 在代码中，主线程和读线程都将访问共享变量ready和number。主线程启动读线程，
 * 然后将 number设为42，并将ready设为true。读线程一直循环直到发现ready的值变为true,
 * 然后输出 number 的值。虽然NoVisibility 看起来会输出42，但事实上很可能输出0，或者根本无法终止。
 * 这是因为在代码中没有使用足够的同步机制，因此无法保证主线程写入的ready值和 number 值对于读线程来说是可见的。
 */
@NotThreadSafe
public class NoVisibility {

    private static boolean ready;

    private static int number;

    public static class ReaderThread extends Thread{
        public void run(){
            while (!ready){
                /*
                提示而非强制：yield() 只是一个提示（hint），线程调度器可以忽略这个提示
                相同优先级：通常只对具有相同优先级的线程有效
                状态变化：使线程从运行状态转为就绪状态（Runnable），而非阻塞状态
                */
                Thread.yield();//提示线程调度器，当前线程愿意让出 CPU 使用权，让其他具有相同优先级的线程有机会运行。
            }
            System.out.println(number);
        }
    }

    public static void main(String[] args) {
        new ReaderThread().start();
        number = 42;
        ready = true;
    }

}
