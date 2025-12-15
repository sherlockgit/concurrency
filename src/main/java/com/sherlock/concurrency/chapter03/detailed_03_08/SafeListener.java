package com.sherlock.concurrency.chapter03.detailed_03_08;

import com.sherlock.concurrency.annoations.NotRecommend;
import com.sun.jdi.event.Event;

/**
 * 使用工厂方法来防止this引用在构造过程中逸出
 *
 *  如果想在构造函数中注册一个事件监听器或启动线程，那么可以使用一个私有的构造函数和一个公共的工厂方法(Factory Method)，从而避免不正确的构造过程
 */
@NotRecommend
public class SafeListener {
    private EventListener eventListener;

    private SafeListener(){
        eventListener = new EventListener() {
            @Override
            public void onEvent(Event event) {
                doSomething(event);
            }
        };
    }
    private void doSomething(Event e) {
        System.out.println("do something");
    }

    public static SafeListener newInstance(EventSource eventSource){
        SafeListener safeListener = new SafeListener();
        eventSource.registerListener(safeListener.eventListener);
        return safeListener;
    }
}
