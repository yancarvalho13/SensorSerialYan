package org.Yan.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Configuration
public class RequestFilter extends OncePerRequestFilter {
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private final Logger logger = LoggerFactory.getLogger(RequestFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request
            , HttpServletResponse response
            , FilterChain filterChain)
            throws ServletException, IOException {
        long RequestStart = System.currentTimeMillis();

        String RequestId = UUID.randomUUID().toString();
        LocalDateTime RequestDate = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        String RequestDateFormatted = RequestDate.format(formatter);

        try{
            filterChain.doFilter(request, response);
        }finally {
            long totalMs = System.currentTimeMillis()  - RequestStart;
            String RequestMethod =  request.getMethod();
            String Path = request.getRequestURI();
            String Query = request.getQueryString();
            String RequestIpAdress = request.getRemoteAddr();
            int status = response.getStatus();

            long totalSec = totalMs / 1000;
            long min = totalSec / 60;
            long sec = totalSec % 60;
            long ms = totalMs % 1000;

            String fullPath = Path + (Query != null ? "?" + Query : "");


            logger.info(
                    "Usuário de IP {} solicitou {} {} às {} e obteve status {} em {}m {}s ({} ms). reqId={}",
                    RequestIpAdress, RequestMethod, fullPath, RequestDateFormatted, status, min, sec, ms, RequestId
            );
        }


    }
}
