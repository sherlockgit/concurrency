package com.sherlock.concurrency.concurrency6;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * author: 小宇宙
 * date: 2018/7/6
 */
@RestController
@RequestMapping("/threadLocal")
public class ThreadLocalController {

    @RequestMapping("/test")
    public Long test () {
        return RequestHolder.getId();
    }

}
