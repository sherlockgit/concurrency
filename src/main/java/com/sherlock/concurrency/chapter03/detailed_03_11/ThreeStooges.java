package com.sherlock.concurrency.chapter03.detailed_03_11;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.HashSet;
import java.util.Set;

/**
 * 在可变对象基础上构建的不可变类
 *
 * 在不可变对象的内部仍可以使用可变对象来管理它们的状态，如程序ThreeStooges所示。
 * 尽管保存姓名的 Set 对象是可变的，但从 ThreeStooges 的设计中可以看到在 Set 对象构造完成后无法对其进行修改。
 * stooges是一个final类型的引用变量，因此所有的对象状态都通过一个 final域来访问。
 * 最后一个要求是“正确地构造对象”，这个要求很容易满足,因为构造函数能使该引用由除了构造函数及其调用者之外的代码来访问。
 */
@ThreadSafe
public class ThreeStooges {

    private final Set<String> stooges = new HashSet<>();

    public ThreeStooges(){
        stooges.add("Moe");
        stooges.add("Larry");
        stooges.add("Curly");
    }

    public boolean isStooge(String name){
        return stooges.contains(name);
    }

}
