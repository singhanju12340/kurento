package com.example.kurentowebrtc.impl;

import org.kurento.client.*;

import java.text.SimpleDateFormat;
import java.util.Date;

public class CallMediaPipeline {
    private MediaPipeline pipeline;
    private WebRtcEndpoint callerWebRtcEp;
    private WebRtcEndpoint calleeWebRtcEp;
    private RecorderEndpoint callerRecordEp;
    private RecorderEndpoint calleeRecordEp;

    //final static String RECORDING_PATH="/tmp/kurento-recordings/";
    private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-S");
    public static final String RECORDING_PATH = "file:///tmp/abc" + df.format(new Date()) + "-";
    final static  String RECORDING_EXT=".webm";

    public CallMediaPipeline(KurentoClient kurento, String from, String to) {
        try {

            pipeline = kurento.createMediaPipeline();

            // Media Elements (WebRtcEndpoint, RecorderEndpoint, FaceOverlayFilter)
//            callerWebRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
//            calleeWebRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
//
//            callerRecordEp = new RecorderEndpoint.Builder(pipeline, RECORDING_PATH + from + RECORDING_EXT)
//                    .build();
//            calleeRecordEp = new RecorderEndpoint.Builder(pipeline, RECORDING_PATH + to + RECORDING_EXT)
//                    .build();

            this.pipeline = kurento.createMediaPipeline();
            this.callerWebRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
            this.calleeWebRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
            this.calleeRecordEp = new RecorderEndpoint.Builder(pipeline, RECORDING_PATH + to + RECORDING_EXT)
                    .build();
            this.callerRecordEp = new RecorderEndpoint.Builder(pipeline, RECORDING_PATH + from + RECORDING_EXT)
                    .build();
            this.callerWebRtcEp.connect(this.calleeWebRtcEp);
            this.callerWebRtcEp.connect(this.callerRecordEp);

            this.calleeWebRtcEp.connect(this.callerWebRtcEp);
            this.calleeWebRtcEp.connect(this.calleeRecordEp);


//            String appServerUrl = "http://files.openvidu.io";
//            FaceOverlayFilter faceOverlayFilterCaller = new FaceOverlayFilter.Builder(pipeline).build();
//            faceOverlayFilterCaller.setOverlayedImage(appServerUrl + "/img/mario-wings.png", -0.35F, -1.2F,
//                    1.6F, 1.6F);
//
//            FaceOverlayFilter faceOverlayFilterCallee = new FaceOverlayFilter.Builder(pipeline).build();
//            faceOverlayFilterCallee.setOverlayedImage(appServerUrl + "/img/Hat.png", -0.2F, -1.35F, 1.5F,
//                    1.5F);
//
//            // Connections
//            callerWebRtcEp.connect(faceOverlayFilterCaller);
//            faceOverlayFilterCaller.connect(calleeWebRtcEp);
//            faceOverlayFilterCaller.connect(callerRecordEp);
//
//            calleeWebRtcEp.connect(faceOverlayFilterCallee);
//            faceOverlayFilterCallee.connect(callerWebRtcEp);
//            faceOverlayFilterCallee.connect(calleeRecordEp);

        } catch (Throwable t) {
            if (this.pipeline != null) {
                pipeline.release();
            }
        }
    }


    public void record() {
        callerRecordEp.record();
        calleeRecordEp.record();
    }


    public WebRtcEndpoint getCallerWebRtcEp() {
        return callerWebRtcEp;
    }

    public WebRtcEndpoint getCalleeWebRtcEp() {
        return calleeWebRtcEp;
    }


    public String generateSdpAnswerForCallee(String sdpOffer) {
        return calleeWebRtcEp.processOffer(sdpOffer);
    }

    public String generateSdpAnswerForCaller(String sdpOffer) {
        return callerWebRtcEp.processOffer(sdpOffer);
    }

    public void release() {
        if (pipeline != null) {
            pipeline.release();
        }
    }

    public MediaPipeline getPipeline() {
        return pipeline;
    }
}
