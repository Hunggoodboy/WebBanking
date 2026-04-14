package com.bankingeconomy.entity;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

import java.util.concurrent.TimeUnit;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RedisHash("RedisToken")
@Builder
public class RedisToken {

    @Id
    private String jwtID;

    @TimeToLive(unit = TimeUnit.SECONDS)
    private Long expiredTime;
}
