package com.remus.dwanjcex;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.remus.dwanjcex.wallet.mapper")  // Mapper 接口包
public class DwanjCexApplication {

	public static void main(String[] args) {
		SpringApplication.run(DwanjCexApplication.class, args);
	}

}
