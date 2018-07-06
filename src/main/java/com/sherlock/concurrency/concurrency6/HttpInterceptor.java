package com.sherlock.concurrency.concurrency6;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * author: 小宇宙
 * date: 2018/7/6
 * 拦截器
 */
@Slf4j
public class HttpInterceptor extends HandlerInterceptorAdapter {

    /**
     * 该类会在请求具体接口前进行操作
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        log.info("preHandle");
        return true;
    }

    /**
     * 该类会在请求完成后进行操作（）
     * @param request
     * @param response
     * @param handler
     * @param ex
     * @throws Exception
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        log.info("afterCompletion");
        RequestHolder.remove();
        return;
    }
}
