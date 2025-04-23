package org.saidone.config;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@ConfigurationProperties(prefix = "management.security.basic-auth")
@Setter
@EnableWebFluxSecurity // Utilizziamo WebFluxSecurity invece di EnableWebSecurity
public class SecurityConfig {

    private String username;
    private String password;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**").hasRole("ACTUATOR_ADMIN")
                        .anyExchange().authenticated()
                )
                .httpBasic().and()
                .build();
    }

    @Bean
    public ReactiveUserDetailsService userDetailsService() {
        UserDetails user = User.builder()
                .username(username)
                .password(passwordEncoder().encode(password))
                .roles("ACTUATOR_ADMIN")
                .build();
        return new MapReactiveUserDetailsService(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}