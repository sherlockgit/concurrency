package com.sherlock.concurrency.chapter03.detailed_03_04;

import com.sherlock.concurrency.annoations.NotThreadSafe;
import lombok.SneakyThrows;

/**
 * 数绵羊
 *
 * volatile变量的一种典型用法:检查某个状态标记以判断是否退出循环。在这个示例中，线程试图通过类似于数绵羊的传统方法进入休眠状态。
 * 为了使这个示例能正确执行，asleep 必须为volatile变量。否则，当asleep被另一个线程修改时，执行判断的线程却发现不了。
 * 我们也可以用锁来确保 asleep更新操作的可见性，但这将使代码变得更加复杂。
 *
 * 当且仅当满足以下所有条件时，才应该使用volatile变量:
 * 对变量的写入操作不依赖变量的当前值，或者你能确保只有单个线程更新变量的值。
 * 该变量不会与其他状态变量一起纳人不变性条件中。
 * 在访问变量时不需要加锁。
 */
@NotThreadSafe
public class CountSomeSheep {

    private static volatile boolean asleep;


    public static class AsleepThread extends Thread{
        @SneakyThrows
        public void run(){
            while (!asleep){
                System.out.println("等待休眠");
                Thread.sleep(1000);
            }
            System.out.println("进入休眠");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new AsleepThread().start();
        Thread.sleep(10000);
        asleep = true;
    }

}
