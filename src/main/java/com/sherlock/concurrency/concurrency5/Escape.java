package com.sherlock.concurrency.concurrency5;

import com.sherlock.concurrency.annoations.NotRecommend;
import com.sherlock.concurrency.annoations.NotThreadSafe;
import lombok.extern.slf4j.Slf4j;

/**
 * author: 小宇宙
 * date: 2018/6/27
 */
@Slf4j
@NotThreadSafe
@NotRecommend
public class Escape {

    private int thisCanBeEscape = 0;

    public Escape () {
         new InnerClass();
    }

    private class InnerClass{
        int a = 0;
        public  InnerClass(){
            log.info("{}",Escape.this.thisCanBeEscape);

        }
    }

    public static void main(String[] args) {
        new Escape();
    }

}
