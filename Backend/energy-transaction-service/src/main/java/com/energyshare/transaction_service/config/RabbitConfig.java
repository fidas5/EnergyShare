package com.energyshare.transaction_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange transactionExchange(EventsProperties props) {
        return new TopicExchange(props.exchange(), true, false);
    }

    /** File consommee par le Marketplace Service (declaree ici pour faciliter le developpement local). */
    @Bean
    public Queue marketplaceQueue(EventsProperties props) {
        return new Queue(props.queue(), true);
    }

    @Bean
    public Binding confirmedBinding(Queue marketplaceQueue, TopicExchange transactionExchange, EventsProperties props) {
        return BindingBuilder.bind(marketplaceQueue).to(transactionExchange).with(props.confirmedKey());
    }

    @Bean
    public Binding cancelledBinding(Queue marketplaceQueue, TopicExchange transactionExchange, EventsProperties props) {
        return BindingBuilder.bind(marketplaceQueue).to(transactionExchange).with(props.cancelledKey());
    }

    /** Les evenements sont serialises en JSON (et non en serialisation Java). */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
