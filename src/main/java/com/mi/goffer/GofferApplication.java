package com.mi.goffer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.mi.goffer.dao.mapper")
public class GofferApplication {
    public static void main(String[] args) {
        SpringApplication.run(GofferApplication.class, args);
    }
}
