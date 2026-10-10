package com.example.toolhub.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

/** Shared sessions are opt-in; local servlet sessions remain the default. */
@Configuration(proxyBeanMethods = false)
@Profile({"vercel", "jdbc-session-test"})
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 1800)
public class JdbcSessionConfiguration {
    @Bean
    DefaultCookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("JSESSIONID");
        serializer.setCookiePath("/");
        serializer.setUseSecureCookie(secure);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    PostgreSqlJdbcIndexedSessionRepositoryCustomizer postgresSessionQueries() {
        return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
    }

    @Bean
    FilterRegistrationBean<SessionRepositoryFilter<?>> sharedSessionFilterRegistration(
            SessionRepositoryFilter<?> springSessionRepositoryFilter) {
        FilterRegistrationBean<SessionRepositoryFilter<?>> registration = new FilterRegistrationBean<>(springSessionRepositoryFilter);
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC);
        registration.setAsyncSupported(true);
        return registration;
    }
}
