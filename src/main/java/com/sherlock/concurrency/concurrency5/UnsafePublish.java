package com.sherlock.concurrency.concurrency5;

import com.sherlock.concurrency.annoations.NotThreadSafe;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * author: 小宇宙
 * date: 2018/6/27
 */
@Slf4j
@NotThreadSafe
public class UnsafePublish {

    private String[] states = {"a","b","c"};

    public String[] getStates () {
        return states;
    }

    public static void main(String[] args) {
        UnsafePublish unsafePublish = new UnsafePublish();
        log.info("{}", Arrays.toString(unsafePublish.getStates()));

        unsafePublish.getStates()[0]="d";
        log.info("{}", Arrays.toString(unsafePublish.getStates()));
    }

}
