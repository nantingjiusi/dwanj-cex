package com.remus.dwanjcex.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // 【推荐】设置Key的序列化器，避免乱码
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // 可以根据需要设置Value的序列化器，例如Jackson2JsonRedisSerializer
        // template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        // template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        
        return template;
    }
}
