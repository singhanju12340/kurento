package com.example.kurentowebrtc.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

public class UserSessionsRegistry {
    private static final Logger log = LoggerFactory.getLogger(UserSessionsRegistry.class);
    private ConcurrentHashMap<String, UserSession> usersByName = new ConcurrentHashMap<>();


    //public ConcurrentHashMap<String, UserSession> users = new ConcurrentHashMap<>();
    public ConcurrentHashMap<String, UserSession> usersBySessionId = new ConcurrentHashMap<>();

    public UserSession getBySession(WebSocketSession session) {
        return usersBySessionId.get(session.getId());
    }

    public  void register(UserSession user){
        usersByName.put(user.getName(), user);
        usersBySessionId.put(user.getSession().getId(), user);
        log.info("user added to user register, register size{}", usersBySessionId.size());
    }

    public UserSession getByName(String name) {
        return usersByName.get(name);
    }

    public boolean exists(String name){
        return usersByName.containsKey(name);
    }

}
