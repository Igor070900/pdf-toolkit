package com.iedev.pdftoolkit.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ConversionRequestListener {

    @KafkaListener(topics = "course", groupId = "my_consumer")
    public void listen(String message) {
//        public void listen(ConsumerRecord<String, String> record) {
        System.out.println("Recieved message = " + message);
    }
}
