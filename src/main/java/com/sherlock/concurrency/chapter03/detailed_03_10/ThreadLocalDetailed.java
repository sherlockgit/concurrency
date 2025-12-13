package com.sherlock.concurrency.chapter03.detailed_03_10;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * 使用ThreadLocal来维持线程封闭性
 *
 * 维持线程封闭性的一种更规范方法是使用ThreadLocal，这个类能使线程中的某个值与保存值的对象关联起来。
 * ThreadLocal提供了get与set等访问接口或方法，这些方法为每个使用该变量的线程都存有一份独立的副本，
 * 因此 get 总是返回由当前执行线程在调用set时设置的最新值。
 *
 * ThreadLocal对象通常用于防止对可变的单实例变量(Singleton)或全局变量进行共享。例如，
 * 在单线程应用程序中可能会维持一个全局的数据库连接，并在程序启动时初始化这个连接对象，
 * 从而避免在调用每个方法时都要传递一个Connection对象。由于JDBC的连接对象不一定是线程安全的，
 * 因此，当多线程应用程序在没有协同的情况下使用全局变量时，就不是线程安全的。
 * 通过将JDBC的连接保存到ThreadLocal对象中，每个线程都会拥有属于自己的连接，如程序ConnectionHolder 所示。
 */
public class ThreadLocalDetailed {

    private static ThreadLocal<Connection> connectionHolder
            = new ThreadLocal<Connection>(){
        public Connection initialValue() {
            try {
                return DriverManager.getConnection("DB_URL");
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
    };

    public static Connection getConnection(){
        return connectionHolder.get();
    }

}


