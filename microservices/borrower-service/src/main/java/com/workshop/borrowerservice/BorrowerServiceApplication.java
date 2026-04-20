package com.workshop.borrowerservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.workshop.borrowerservice", "com.workshop.common"})
public class BorrowerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BorrowerServiceApplication.class, args);
    }
}
