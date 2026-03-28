package com.bankingeconomy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.bankingeconomy.service.HdfsService;

@SpringBootApplication
public class BankingEconomyApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingEconomyApplication.class, args);
    }

}
