package com.sherlock.concurrency.chapter04.detailed_04_02;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.Set;
import java.util.TreeSet;

/**
 * 通过封闭机制来确保线程安全
 *
 * 被封闭对象一定不能超出它们既定的作用域。对象可以封闭在类的一个实例(例如作为类的一个私有成员)中，
 * 或者封闭在某个作用域内(例如作为一个局部变量)，再或者封闭在线程内(例如在某个线程中将对象从一个方法传递到另一个方法，
 * 而不是在多个线程之间共享该对象)。当然，对象本身不会逸出-出现逸出情况的原因通常是由于开发人员在发布对象时超出了对象既定的作用域。
 *
 * 程序中的PersonSet说明了如何通过封闭与加锁等机制使一个类成为线程安全的(即使这个类的状态变量并不是线程安全的)。
 * PersonSet的状态由HashSet来管理的，而HashSet并非线程安全的。但由于mySet是私有的并且不会逸出，因此HashSet被封闭在PersonSet中
 * 。唯一能访问mySet的代码路径是addPerson与containsPerson,在执行它们时都要获得PersonSet上的锁。
 * PersonSet的状态完全由它的内置锁保护，因而 PersonSet是一个线
 */
@ThreadSafe
public class PersonSet {

    private final Set<Person> mySet = new TreeSet<Person>();

    public synchronized void addPerson(Person p){
        mySet.add(p);
    }

    public synchronized boolean containsPerson(Person person){
        return mySet.contains(person);
    }

}
