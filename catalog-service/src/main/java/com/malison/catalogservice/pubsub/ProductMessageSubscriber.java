package com.malison.catalogservice.pubsub;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class ProductMessageSubscriber implements MessageListener {

    private final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String msgStr = new String(message.getBody());
        String channelStr = new String(message.getChannel());
        log.info("Received Redis Pub/Sub message: '{}' on channel: '{}'", msgStr, channelStr);
        messages.add(msgStr);
    }

    public List<String> getMessages() {
        return new ArrayList<>(messages);
    }

    public void clearMessages() {
        messages.clear();
    }
}
