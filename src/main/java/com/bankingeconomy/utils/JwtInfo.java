package com.bankingeconomy.utils;

import lombok.*;

import java.util.Date;


@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class JwtInfo {
    private String userId;
    private String role;
    private String jwtId;
    private Date issueTime;
    private Date expirationTime;
}
