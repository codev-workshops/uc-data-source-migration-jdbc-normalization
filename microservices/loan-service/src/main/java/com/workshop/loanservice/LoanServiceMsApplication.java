package com.workshop.loanservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.workshop.loanservice", "com.workshop.common"})
public class LoanServiceMsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoanServiceMsApplication.class, args);
    }
}
