package com.spontaneously.service.impl;

import com.spontaneously.annotation.MyService;
import com.spontaneously.service.IndexService;

@MyService("indexService")
public class IndexServiceImpl implements IndexService {
    @Override
    public String query(String name, int age) {
        return "name=" + name + ", age=" + age;
    }
}
