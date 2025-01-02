package com.zhite.zhite;

import com.zhite.zhite.service.HkClientCamera;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.Resource;

@SpringBootApplication
public class ZhiteApplication implements CommandLineRunner {
	@Resource
	HkClientCamera hkClientCamera;

	public static void main(String[] args) {
		SpringApplication.run(ZhiteApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		hkClientCamera.addClient("192.168.88.88", (short) 8000, "admin", "1238");
	}
}
