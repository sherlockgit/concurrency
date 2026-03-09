package com.sherlock.concurrency.chapter04.detailed_04_15;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 通过客户端加锁来实现“若没有则添加”
 * * *
 *  要想使这个方法能正确执行，必须使List在实现客户端加锁或外部加锁时使用同一个锁。
 *  *  客户端加锁是指，对于使用某个对象X的客户端代码，使用X本身用于保护其状态的锁来保护这段客户代码。
 *  *  要使用客户端加锁，你必须知道对象X使用的是哪一个锁。
 *
 *  通过添加一个原子操作来扩展类是脆弱的，因为它将类的加锁代码分布到多个类中。然而，客户端加锁却更加脆弱，
 *  *  因为它将类C的加锁代码放到与C完全无关的其他类中。当在那些并不承诺遵循加锁策略的类上使用客户端加锁时，要特别小心。
 *  *
 * 客户端加锁机制与扩展类机制有许多共同点，二者都是将派生类的行为与基类的实现耦合在一起。
 * 正如扩展会破坏实现的封装性[EJItem14]，客户端加锁同样会破坏同步策略的封装性。
 */
@ThreadSafe
public class ListHelper<E>{
    public List<E> list = Collections.synchronizedList(new ArrayList<>());

    public boolean putIfAbsent(E x){
        synchronized (list){
            boolean absent = !list.contains(x);
            if (absent) {
                list.add(x);
            }
            return absent;
        }
    }
}
