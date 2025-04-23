package org.saidone.config;

import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Configuration
public class FeignConfig {

    @Bean
    public HttpMessageConverters messageConverters() {
        return new HttpMessageConverters(new MappingJackson2HttpMessageConverter());
    }

}