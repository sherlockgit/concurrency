package com.sherlock.concurrency.chapter03.detailed_03_05;

import java.util.ArrayList;
import java.util.List;

/**
 * 发布一个对象
 *
 * 发布对象的最简单方法是将对象的引用保存到一个公有的静态变量中，
 * 以便任何类和线程都能看见该对象，在initialize方法中实例化一个新的 HashSet对象，
 * 并将对象的引用保存到 knownSecrets中以发布该对象。
 */
public class Publisher {
    public static List<Secret> secrets; // 公共静态变量，发布Secret对象

    public void initialize() {
        secrets = new ArrayList<>();
    }

    public class Secret{}
}
