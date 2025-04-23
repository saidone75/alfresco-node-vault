package org.saidone.config;

import com.mongodb.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.NonNull;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Configuration
public class MongoDBConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String connectionString;
    @Value("${spring.data.mongodb.auth-database}")
    private String authDatabase;
    @Value("${spring.data.mongodb.database}")
    private String database;
    @Value("${spring.data.mongodb.username}")
    private String username;
    @Value("${spring.data.mongodb.password}")
    private String password;

    @Bean
    @NonNull
    public MongoClient mongoClient() {
        var pojoCodecRegistry = fromProviders(PojoCodecProvider.builder().automatic(true).build());
        var codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry);
        var serverApi = ServerApi.builder()
                .version(ServerApiVersion.V1)
                .build();
        var credentials = MongoCredential.createCredential(username, authDatabase, password.toCharArray());
        return MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .serverApi(serverApi)
                .credential(credentials)
                .retryWrites(true)
                .writeConcern(WriteConcern.MAJORITY)
                .codecRegistry(codecRegistry)
                .build());
    }

    @Override
    public boolean autoIndexCreation() {
        return true;
    }

    @Override
    @NonNull
    protected String getDatabaseName() {
        return database;
    }

}