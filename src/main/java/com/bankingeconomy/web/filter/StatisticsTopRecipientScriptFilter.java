package com.bankingeconomy.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Component
public class StatisticsTopRecipientScriptFilter extends OncePerRequestFilter {

    private static final String SCRIPT_TAG = "<script src=\"/js/statistics-top5.js\"></script>";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equalsIgnoreCase(request.getMethod()) || !"/statistics".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, responseWrapper);

        byte[] originalBody = responseWrapper.getContentAsByteArray();
        String contentType = responseWrapper.getContentType();

        if (originalBody.length == 0 || contentType == null || !contentType.contains("text/html")) {
            responseWrapper.copyBodyToResponse();
            return;
        }

        Charset charset = resolveCharset(responseWrapper.getCharacterEncoding());
        String html = new String(originalBody, charset);
        String updatedHtml = injectScript(html);
        byte[] updatedBody = updatedHtml.getBytes(charset);

        responseWrapper.resetBuffer();
        responseWrapper.setContentLength(updatedBody.length);
        responseWrapper.getOutputStream().write(updatedBody);
        responseWrapper.copyBodyToResponse();
    }

    private String injectScript(String html) {
        if (html.contains(SCRIPT_TAG)) {
            return html;
        }

        if (html.contains("</body>")) {
            return html.replace("</body>", SCRIPT_TAG + "\n</body>");
        }

        return html + "\n" + SCRIPT_TAG;
    }

    private Charset resolveCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }

        try {
            return Charset.forName(encoding);
        } catch (Exception ignored) {
            return StandardCharsets.UTF_8;
        }
    }
}
