package com.sherlock.concurrency.chapter03.detailed_03_08;

import com.sun.jdi.event.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description:
 * @Author: linmuyu
 * @Date: 2025/12/12 16:22
 */
public class EventSource {

    // 存储监听器列表
    private List<EventListener> listeners = new ArrayList<>();

    // 注册监听器
    public void registerListener(EventListener listener) {
        listeners.add(listener);
    }

    // 移除监听器
    public void unregisterListener(EventListener listener) {
        listeners.remove(listener);
    }

    // 触发事件，通知所有监听器
    public void fireEvent(Event event) {
        for (EventListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    // 可能还有异步触发事件的方法
    public void fireEventAsync(Event event) {
        new Thread(() -> {
            fireEvent(event);
        }).start();
    }

}
