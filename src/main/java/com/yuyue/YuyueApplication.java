package com.yuyue;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class YuyueApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuyueApplication.class, args);
    }
}
