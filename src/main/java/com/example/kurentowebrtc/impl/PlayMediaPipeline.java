package com.example.kurentowebrtc.impl;

import com.google.gson.JsonObject;
import org.kurento.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

public class PlayMediaPipeline {

    private MediaPipeline pipeline;
    private WebRtcEndpoint webRtcEp;
    private PlayerEndpoint playerEp;
    private final Logger log = LoggerFactory.getLogger(PlayMediaPipeline.class);


    public void play() {
        playerEp.play();
    }

    public PlayMediaPipeline(KurentoClient kurento, String user, WebSocketSession session){
        this.pipeline = kurento.createMediaPipeline();
        this.webRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
        this.playerEp = new PlayerEndpoint.Builder(pipeline, CallMediaPipeline.RECORDING_PATH + user + CallMediaPipeline.RECORDING_EXT).build();
        // connects endpoints
        playerEp.connect(webRtcEp);

        // Player listeners
        playerEp.addErrorListener(new EventListener<ErrorEvent>() {
            @Override
            public void onEvent(ErrorEvent event) {
                log.info("ErrorEvent: {}", event.getDescription());
                sendPlayEnd(session);
            }
        });
    }


    public MediaPipeline getPipeline() {
        return pipeline;
    }

    public WebRtcEndpoint getWebRtcEp() {
        return webRtcEp;
    }

    public PlayerEndpoint getPlayerEp() {
        return playerEp;
    }


    public void release(UserSession session) {
        if (pipeline != null) {
            pipeline.release();
        }
    }

    public void sendPlayEnd(WebSocketSession session) {
        try {
            JsonObject response = new JsonObject();
            response.addProperty("id", "playEnd");
            session.sendMessage(new TextMessage(response.toString()));
        } catch (IOException e) {
            log.error("Error sending playEndOfStream message", e);
        }

        // Release pipeline
        pipeline.release();
        this.webRtcEp = null;
    }

    public String generateSdpAnswer(String sdpOffer) {
        return webRtcEp.processOffer(sdpOffer);
    }

    public String generateSdpAnswerForCaller(String sdpOffer) {
        return webRtcEp.processOffer(sdpOffer);
    }
}
