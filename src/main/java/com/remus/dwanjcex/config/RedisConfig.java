package com.remus.dwanjcex.config;

import com.remus.dwanjcex.websocket.RedisMessageSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.afterPropertiesSet();
        
        return template;
    }

    /**
     * 【关键修复】创建 MessageListenerAdapter 来包装我们的订阅者POJO。
     * 默认情况下，适配器会寻找一个名为 "handleMessage" 的方法。
     * 如果我们的方法名不同（例如 "onMessage"），我们需要明确指定。
     */
    @Bean
    public MessageListenerAdapter listenerAdapter(RedisMessageSubscriber subscriber) {
        // 告诉适配器，当收到消息时，调用 subscriber 对象的 onMessage 方法
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) { // 【关键修复】注入适配器，而不是原始的订阅者
        
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // 将适配器注册为监听器
        container.addMessageListener(listenerAdapter, new PatternTopic("channel:orderbook:*"));
        container.addMessageListener(listenerAdapter, new PatternTopic("channel:ticker:*"));
        
        return container;
    }
}
