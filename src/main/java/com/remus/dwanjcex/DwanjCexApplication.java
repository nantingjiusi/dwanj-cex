package com.remus.dwanjcex;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.remus.dwanjcex.**.mapper")
@EnableScheduling // 【关键】启用定时任务支持
public class DwanjCexApplication {

    public static void main(String[] args) {
        SpringApplication.run(DwanjCexApplication.class, args);
    }

}
