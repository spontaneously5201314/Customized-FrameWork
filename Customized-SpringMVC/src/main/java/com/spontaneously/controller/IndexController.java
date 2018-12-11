package com.spontaneously.controller;

import com.spontaneously.annotation.MyAutowired;
import com.spontaneously.annotation.MyController;
import com.spontaneously.annotation.MyRequestMapping;
import com.spontaneously.annotation.MyRequestParam;
import com.spontaneously.service.IndexService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@MyController
@MyRequestMapping("/index")
public class IndexController {

    @MyAutowired("indexService")
    private IndexService indexService;

    @MyRequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response,
                      @MyRequestParam("name") String name, @MyRequestParam("age") int age) {
        try {
            PrintWriter writer = response.getWriter();
            indexService.query(name, age);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
