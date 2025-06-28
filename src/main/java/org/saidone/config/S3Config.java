package org.saidone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@ConfigurationProperties(prefix = "application.service.s3")
@Data
public class S3Config {

    private String bucket;
    private String region;
    private String endpoint;

    @Bean
    public S3Client s3Client() {
        S3Client.Builder builder = S3Client.builder().region(Region.of(region));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
