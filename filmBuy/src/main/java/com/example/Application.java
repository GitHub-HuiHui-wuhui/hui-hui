package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import tk.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@SpringBootApplication
@MapperScan("com.example.dao")
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        logger.warn("项目启动后请访问：http://localhost:8088/front/index.html");
        logger.warn("登录账号：admin   登录密码：12345");
    }
}