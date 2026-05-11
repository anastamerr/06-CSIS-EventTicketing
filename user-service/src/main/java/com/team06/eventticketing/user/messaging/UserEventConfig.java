package com.team06.eventticketing.user.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserEventConfig {

    public static final String USER_EVENTS_EXCHANGE = "user.events";
    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";
    public static final String USER_DEACTIVATED_ROUTING_KEY = "user.deactivated";

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(USER_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
