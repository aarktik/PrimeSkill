package com.example.toolhub;

import org.springframework.boot.SpringApplication;

/** Disposable UI test server. All database settings are explicit; no external DB is used. */
public class UiAcceptancePreviewApplication {
    public static void main(String[] args) {
        int port = args.length == 0 ? 18088 : Integer.parseInt(args[0]);
        new SpringApplication(ToolHubApplication.class, Sprint3PreviewApplication.Fixtures.class, DisposableMarker.class).run(
                "--spring.profiles.active=test", "--server.address=127.0.0.1", "--server.port=" + port,
                "--spring.datasource.url=jdbc:h2:mem:ui_acceptance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa",
                "--spring.datasource.password=", "--server.servlet.session.cookie.secure=false",
                "--spring.thymeleaf.cache=false", "--logging.level.root=WARN", "--debug=false",
                "--logging.level.org.springframework=WARN", "--logging.level.org.hibernate=WARN");
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class DisposableMarker {
        @org.springframework.context.annotation.Bean
        org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter> uiTestMarker() {
            var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<jakarta.servlet.Filter>();
            registration.setFilter((request,response,chain) -> {
                ((jakarta.servlet.http.HttpServletResponse)response).setHeader("X-PrimeSkill-UI-Test", "disposable");
                chain.doFilter(request,response);
            });
            registration.setOrder(Integer.MIN_VALUE); return registration;
        }
    }
}
