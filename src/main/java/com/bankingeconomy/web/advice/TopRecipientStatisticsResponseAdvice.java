package com.bankingeconomy.web.advice;

import com.bankingeconomy.controller.ReportController;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.service.TopRecipientStatisticsService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
public class TopRecipientStatisticsResponseAdvice implements ResponseBodyAdvice<Object> {

    private final TopRecipientStatisticsService topRecipientStatisticsService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getContainingClass() == ReportController.class
                && returnType.getMethod() != null
                && "myTransactionStatistics".equals(returnType.getMethod().getName());
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!(body instanceof ResponseData<?> responseData) || responseData.getData() == null) {
            return body;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User currentUser)) {
            return body;
        }

        Map<String, Object> data = objectMapper.convertValue(
                responseData.getData(),
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
        );

        data.put(
                "topRecipients",
                topRecipientStatisticsService.getTopRecipients(
                        currentUser.getId(),
                        parseDateTime(request.getURI().getQuery(), "start"),
                        parseDateTime(request.getURI().getQuery(), "end")
                )
        );

        return new ResponseData<>(responseData.getStatus(), responseData.getMessage(), data);
    }

    private LocalDateTime parseDateTime(String rawQuery, String name) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }

            String key = pair.substring(0, separator);
            if (!name.equals(key)) {
                continue;
            }

            String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            if (value.isBlank()) {
                return null;
            }

            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }

        return null;
    }
}
