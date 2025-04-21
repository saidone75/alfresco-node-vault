package org.saidone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class AlfrescoNodeVaultApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlfrescoNodeVaultApplication.class, args);
	}

}
