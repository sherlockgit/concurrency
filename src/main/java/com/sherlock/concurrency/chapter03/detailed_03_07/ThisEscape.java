package com.sherlock.concurrency.chapter03.detailed_03_07;

import com.sherlock.concurrency.annoations.NotRecommend;
import com.sun.jdi.event.Event;

/**
 * 使内部可变状态逸出（不要这么做）
 *
 *  如果按照上述方式来发布states，就会出现问题，因为任何调用者都能修改这个数组的内容。
 *  在这个示例中，数组 states已经逸出了它所在的作用域，因为这个本应是私有的变量已经被发布了。
 */
@NotRecommend
public class ThisEscape {
    private int value;

    public ThisEscape(EventSource source){
        source.registerListener(
                new EventListener() {
                    @Override
                    public void onEvent(Event event) {
                        doSomething(event);
                    }
                }

        );
        value = 42;
    }
    private void doSomething(Event e) {
        // 此时value可能还是0（默认值）！
        System.out.println(value);
    }
}
