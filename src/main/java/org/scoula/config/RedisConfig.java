package org.scoula.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Value("${redis.host}")
    private String redisHost;

    @Value("${redis.port}")
    private int redisPort;

    @Value("${redis.password:}")
    private String redisPassword;

    @Value("${redis.database:0}")
    private int redisDatabase;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration();

        config.setHostName(redisHost);
        config.setPort(redisPort);
        config.setDatabase(redisDatabase);

        if (
                redisPassword != null
                        && !redisPassword.trim().isEmpty()
        ) {
            config.setPassword(redisPassword);
        }

        return new LettuceConnectionFactory(config);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        return new StringRedisTemplate(
                connectionFactory
        );
    }
}