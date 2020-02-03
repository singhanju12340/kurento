package com.example.kurentowebrtc.impl;

import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;

public class CallMediaPipeline {
    private MediaPipeline pipeline;
    private WebRtcEndpoint callerWebRtcEp;
    private WebRtcEndpoint calleeWebRtcEp;

    public CallMediaPipeline(KurentoClient kurento) {
        try {
            this.pipeline = kurento.createMediaPipeline();
            this.callerWebRtcEp = new WebRtcEndpoint.Builder(pipeline).build();
            this.calleeWebRtcEp = new WebRtcEndpoint.Builder(pipeline).build();

            this.callerWebRtcEp.connect(this.calleeWebRtcEp);
            this.calleeWebRtcEp.connect(this.callerWebRtcEp);
        } catch (Throwable t) {
            if (this.pipeline != null) {
                pipeline.release();
            }
        }
    }

    public MediaPipeline getPipeline() {
        return pipeline;
    }

    public void setPipeline(MediaPipeline pipeline) {
        this.pipeline = pipeline;
    }

    public WebRtcEndpoint getCallerWebRtcEp() {
        return callerWebRtcEp;
    }

    public void setCallerWebRtcEp(WebRtcEndpoint callerWebRtcEp) {
        this.callerWebRtcEp = callerWebRtcEp;
    }

    public WebRtcEndpoint getCalleeWebRtcEp() {
        return calleeWebRtcEp;
    }

    public void setCalleeWebRtcEp(WebRtcEndpoint calleeWebRtcEp) {
        this.calleeWebRtcEp = calleeWebRtcEp;
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
}
