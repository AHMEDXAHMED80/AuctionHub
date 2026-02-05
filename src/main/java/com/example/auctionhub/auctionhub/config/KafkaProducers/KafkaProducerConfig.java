package com.example.auctionhub.auctionhub.config.KafkaProducers;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import com.example.auctionhub.auctionhub.events.dto.AuctionEndedEvent;
import com.example.auctionhub.auctionhub.events.dto.BidEvent;

@Configuration
public class KafkaProducerConfig {

    private Map<String, Object> getCommonProducerConfigs() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonSerializer");
        return configProps;
    }

    // Producer factory for Bid events
    @Bean
    public ProducerFactory<String, BidEvent> bidProducerFactory() {
        return new DefaultKafkaProducerFactory<>(getCommonProducerConfigs());
    }

    // Producer factory for Auction events
    @Bean
    public ProducerFactory<String, AuctionEndedEvent> auctionProducerFactory() {
        return new DefaultKafkaProducerFactory<>(getCommonProducerConfigs());
    }

    // KafkaTemplate for bid events
    @Bean("bidKafkaTemplate")
    public KafkaTemplate<String, BidEvent> bidKafkaTemplate() {
        return new KafkaTemplate<>(bidProducerFactory());
    }

    // KafkaTemplate for auction events
    @Bean("auctionKafkaTemplate")
    public KafkaTemplate<String, AuctionEndedEvent> auctionKafkaTemplate() {
        return new KafkaTemplate<>(auctionProducerFactory());
    }



}