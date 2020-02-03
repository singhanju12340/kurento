package com.example.kurentowebrtc.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.concurrent.ConcurrentHashMap;


public class KurentoWebrtcHandler extends TextWebSocketHandler {

    private final Logger log = LoggerFactory.getLogger(KurentoWebrtcHandler.class);
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-S");
    private static final Gson gson = new GsonBuilder().create();

    private final ConcurrentHashMap<String, CallMediaPipeline> pipelines = new ConcurrentHashMap<>();


    @Autowired
    private KurentoClient kurento;

    @Autowired
    private UserSessionsRegistry registry;



    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UserSession userSession = registry.getBySession(session);
        final String sessionId = session.getId();
        try {
            JsonObject jsonMessage = gson.fromJson(message.getPayload(),
                    JsonObject.class);
            String messageId = jsonMessage.get("id").getAsString();
            log.info("message Id receivegetBySessiond is : {}",messageId);

            switch (messageId) {
                case "register":
                    // register user session
                    register(session, jsonMessage);
                    break;
                case "call":
                    // Start: Create user session and process SDP Offer
                    log.info("user registering with name {}",userSession.getName());
                    call(userSession, jsonMessage);
                    break;
                case "incomingCallResponse":
                    incomingCallResponse(userSession, jsonMessage);
                    break;
                case "PROCEsdpOfferSS_SDP_OFFER":
                    // Start: Create user session and process SDP Offer
                    handleProcessSdpOffer(session, jsonMessage);
                    break;
                case "ADD_ICE_CANDIDATE":
                    handleAddIceCandidate(session, jsonMessage);
                    break;
                case "STOP":
                    stop(session);
                    break;
                case "ERROR":
                    //handleError(session, jsonMessage);
                    break;
                default:
                    // Ignore the message
                    log.warn("[Handler::handleTextMessage] Skip, invalid message, id: {}",
                            messageId);
                    break;
            }
        } catch (Throwable ex) {
            log.error("[Handler::handleTextMessage] Exception: {}, sessionId: {}",
                    ex, sessionId);
        }

    }

    private void register(WebSocketSession session, JsonObject jsonMessage) throws IOException {
        String name = jsonMessage.getAsJsonPrimitive("name").getAsString();

        UserSession caller = new UserSession(session, name);
        String responseMsg = "accepted";
        if (name.isEmpty()) {
            responseMsg = "rejected: empty user name";
        } else if (registry.exists(name)) {
            responseMsg = "rejected: user '" + name + "' already registered";
        } else {
            registry.register(caller);
        }
    }

    private void incomingCallResponse(UserSession callee, JsonObject jsonMessage)
      throws IOException {
            String callResponse = jsonMessage.get("callResponse").getAsString();
            String from = jsonMessage.get("from").getAsString();
            final UserSession calleer = registry.getByName(from);
            String to = calleer.getCallingTo();

            if ("accept".equals(callResponse)) {
                log.debug("Accepted call from '{}' to '{}'", from, to);

                CallMediaPipeline pipeline = null;
                try {
                    pipeline = new CallMediaPipeline(kurento);
                    pipelines.put(calleer.getSessionId(), pipeline);
                    pipelines.put(callee.getSessionId(), pipeline);

                    callee.setWebRtcEndpoint(pipeline.getCalleeWebRtcEp());
                    pipeline.getCalleeWebRtcEp().addIceCandidateFoundListener(
                            new EventListener<IceCandidateFoundEvent>() {

                                @Override
                                public void onEvent(IceCandidateFoundEvent event) {
                                    JsonObject response = new JsonObject();
                                    response.addProperty("id", "ADD_ICE_CANDIDATE");
                                    response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                                    try {
                                        synchronized (callee.getSession()) {
                                            callee.getSession().sendMessage(new TextMessage(response.toString()));
                                        }
                                    } catch (IOException e) {
                                        log.debug(e.getMessage());
                                    }
                                }
                            });

                    calleer.setWebRtcEndpoint(pipeline.getCallerWebRtcEp());
                    pipeline.getCallerWebRtcEp().addIceCandidateFoundListener(
                            new EventListener<IceCandidateFoundEvent>() {

                                @Override
                                public void onEvent(IceCandidateFoundEvent event) {
                                    JsonObject response = new JsonObject();
                                    response.addProperty("id", "ADD_ICE_CANDIDATE");
                                    response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
                                    try {
                                        synchronized (calleer.getSession()) {
                                            calleer.getSession().sendMessage(new TextMessage(response.toString()));
                                        }
                                    } catch (IOException e) {
                                        log.debug(e.getMessage());
                                    }
                                }
                            });

                    String calleeSdpOffer = jsonMessage.get("sdpOffer").getAsString();
                    String calleeSdpAnswer = pipeline.generateSdpAnswerForCallee(calleeSdpOffer);
                    JsonObject startCommunication = new JsonObject();
                    startCommunication.addProperty("id", "startCommunication");
                    startCommunication.addProperty("sdpAnswer", calleeSdpAnswer);

                    synchronized (callee) {
                        callee.sendMessage(startCommunication);
                    }

                    pipeline.getCalleeWebRtcEp().gatherCandidates();

                    String callerSdpOffer = registry.getByName(from).getSdpOffer();
                    String callerSdpAnswer = pipeline.generateSdpAnswerForCaller(callerSdpOffer);
                    JsonObject response = new JsonObject();
                    response.addProperty("id", "callResponse");
                    response.addProperty("response", "accepted");
                    response.addProperty("sdpAnswer", callerSdpAnswer);

                    synchronized (calleer) {
                        calleer.sendMessage(response);
                    }

                    pipeline.getCallerWebRtcEp().gatherCandidates();

                } catch (Throwable t) {
                    log.error(t.getMessage(), t);

                    if (pipeline != null) {
                        pipeline.release();
                    }

                    pipelines.remove(calleer.getSessionId());
                    pipelines.remove(callee.getSessionId());

                    JsonObject response = new JsonObject();
                    response.addProperty("id", "callResponse");
                    response.addProperty("response", "rejected");
                    calleer.sendMessage(response);

                    response = new JsonObject();
                    response.addProperty("id", "stopCommunication");
                    callee.sendMessage(response);
                }

            } else {
                JsonObject response = new JsonObject();
                response.addProperty("id", "callResponse");
                response.addProperty("response", "rejected");
                calleer.sendMessage(response);
            }
        }




    // Process Call
    private void call(UserSession caller, JsonObject jsonMessage){
        String to = jsonMessage.get("to").getAsString();
        String from = jsonMessage.get("from").getAsString();
        JsonObject response = new JsonObject();
        log.info("received call request from {}", from);
        caller.setSdpOffer(jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString());
        caller.setCallingTo(to);

        response.addProperty("id", "incomingCall");
        response.addProperty("from", from);

        UserSession callee = registry.getByName(to);
        try {
            callee.sendMessage(response);
        } catch (IOException e) {
            e.printStackTrace();
        }
        callee.setCallingFrom(from);
    }


    // ADD_ICE_CANDIDATE ---------------------------------------------------------

    private void handleAddIceCandidate(final WebSocketSession session,
                                       JsonObject jsonMessage)
    {
        final UserSession user = registry.getBySession(session);
        final JsonObject jsonCandidate =
                jsonMessage.get("candidate").getAsJsonObject();
        final IceCandidate candidate =
                new IceCandidate(jsonCandidate.get("candidate").getAsString(),
                    jsonCandidate.get("sdpMid").getAsString(),
                        jsonCandidate.get("sdpMLineIndex").getAsInt());

//        WebRtcEndpoint webRtcEp = user.getWebRtcEndpoint();
//        webRtcEp.addIceCandidate(candidate);
        user.addCandidate(candidate);
    }


    private void stop(WebSocketSession session) {
        log.info("stoping websocket session");

    }



    private synchronized void sendMessage(final WebSocketSession session,
                                          String message) {
        log.debug("[Handler::sendMessage] {}", message);

        if (!session.isOpen()) {
            log.warn("[Handler::sendMessage] Skip, WebSocket session isn't open");
            return;
        }

        final String sessionId = session.getId();

        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException ex) {
            log.error("[Handler::sendMessage] Exception: {}", ex.getMessage());
        }
    }



    // Handle SDP offer
    private void handleProcessSdpOffer(final WebSocketSession session,
                                       JsonObject jsonMessage) {
        // ---- Session handling

        final String sessionId = session.getId();

        log.info("[Handler::handleStart] User count: {}", registry.usersBySessionId.size());
        log.info("[Handler::handleStart] New user, id: {}", sessionId);

        final UserSession user = new UserSession(session, "abc");
        registry.usersBySessionId.put(sessionId, user);


        // ---- Media pipeline

        log.info("[Handler::handleStart] Create Media Pipeline");

        final MediaPipeline pipeline = kurento.createMediaPipeline();
        user.setMediaPipeline(pipeline);

        final WebRtcEndpoint webRtcEp =
                new WebRtcEndpoint.Builder(pipeline).build();
        user.setWebRtcEndpoint(webRtcEp);
        webRtcEp.connect(webRtcEp);

        // ---- Endpoint configuration
        String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
        initWebRtcEndpoint(session, webRtcEp, sdpOffer);

        log.info("[Handler::handleStart] New WebRtcEndpoint: {}",
                webRtcEp.getName());

        // ---- Endpoint startup
        startWebRtcEndpoint(webRtcEp);
    }

    private void startWebRtcEndpoint(WebRtcEndpoint webRtcEp) {
        webRtcEp.gatherCandidates();
    }


    private void initWebRtcEndpoint(final WebSocketSession session,
                                    final WebRtcEndpoint webRtcEp, String sdpOffer) {
        initBaseEventListeners(session, webRtcEp, "WebRtcEndpoint");
        initWebRtcEventListeners(session, webRtcEp);

        final String sessionId = session.getId();
        final String name = "user" + sessionId + "_webrtcendpoint";
        webRtcEp.setName(name);

    /*
    OPTIONAL: Force usage of an Application-specific STUN server.
    Usually this is configured globally in KMS WebRTC settings file:
    /etc/kurento/modules/kurento/WebRtcEndpoint.conf.ini

    But it can also be configured per-application, as shown:

    log.info("[Handler::initWebRtcEndpoint] Using STUN server: 193.147.51.12:3478");
    webRtcEp.setStunServerAddress("193.147.51.12");
    webRtcEp.setStunServerPort(3478);
    */

        // Continue the SDP Negotiation: Generate an SDP Answer
        final String sdpAnswer = webRtcEp.processOffer(sdpOffer);

        log.info("[Handler::initWebRtcEndpoint] name: {}, SDP Offer from browser to KMS:\n{}",
                name, sdpOffer);
        log.info("[Handler::initWebRtcEndpoint] name: {}, SDP Answer from KMS to browser:\n{}",
                name, sdpAnswer);

        JsonObject message = new JsonObject();
        message.addProperty("id", "PROCESS_SDP_ANSWER");
        message.addProperty("sdpAnswer", sdpAnswer);
        sendMessage(session, message.toString());
    }

    private void initBaseEventListeners(final WebSocketSession session,
                                        BaseRtpEndpoint baseRtpEp, final String className)
    {
        log.info("[Handler::initBaseEventListeners] name: {}, class: {}, sessionId: {}",
                baseRtpEp.getName(), className, session.getId());

        // Event: Some error happened
        baseRtpEp.addErrorListener(new EventListener<ErrorEvent>() {
            @Override
            public void onEvent(ErrorEvent ev) {
                log.error("[{}::ErrorEvent] Error code {}: '{}', source: {}, timestamp: {}, tags: {}, description: {}",
                        className, ev.getErrorCode(), ev.getType(), ev.getSource().getName(),
                        ev.getTimestamp(), ev.getTags(), ev.getDescription());

                //sendError(session, "[Kurento] " + ev.getDescription());
                stop(session);
            }
        });

        // Event: Media is flowing into this sink
        baseRtpEp.addMediaFlowInStateChangeListener(
                new EventListener<MediaFlowInStateChangeEvent>() {
                    @Override
                    public void onEvent(MediaFlowInStateChangeEvent ev) {
                        log.info("[{}::{}] source: {}, timestamp: {}, tags: {}, state: {}, padName: {}, mediaType: {}",
                                className, ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                                ev.getTags(), ev.getState(), ev.getPadName(), ev.getMediaType());
                    }
                });

        // Event: Media is flowing out of this source
        baseRtpEp.addMediaFlowOutStateChangeListener(
                new EventListener<MediaFlowOutStateChangeEvent>() {
                    @Override
                    public void onEvent(MediaFlowOutStateChangeEvent ev) {
                        log.info("[{}::{}] source: {}, timestamp: {}, tags: {}, state: {}, padName: {}, mediaType: {}",
                                className, ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                                ev.getTags(), ev.getState(), ev.getPadName(), ev.getMediaType());
                    }
                });

        // Event: [TODO write meaning of this event]
        baseRtpEp.addConnectionStateChangedListener(
                new EventListener<ConnectionStateChangedEvent>() {
                    @Override
                    public void onEvent(ConnectionStateChangedEvent ev) {
                        log.info("[{}::{}] source: {}, timestamp: {}, tags: {}, oldState: {}, newState: {}",
                                className, ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                                ev.getTags(), ev.getOldState(), ev.getNewState());
                    }
                });

        // Event: [TODO write meaning of this event]
        baseRtpEp.addMediaStateChangedListener(
                new EventListener<MediaStateChangedEvent>() {
                    @Override
                    public void onEvent(MediaStateChangedEvent ev) {
                        log.info("[{}::{}] source: {}, timestamp: {}, tags: {}, oldState: {}, newState: {}",
                                className, ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                                ev.getTags(), ev.getOldState(), ev.getNewState());
                    }
                });

        // Event: This element will (or will not) perform media transcoding
        baseRtpEp.addMediaTranscodingStateChangeListener(
                new EventListener<MediaTranscodingStateChangeEvent>() {
                    @Override
                    public void onEvent(MediaTranscodingStateChangeEvent ev) {
                        log.info("[{}::{}] source: {}, timestamp: {}, tags: {}, state: {}, binName: {}, mediaType: {}",
                                className, ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                                ev.getTags(), ev.getState(), ev.getBinName(), ev.getMediaType());
                    }
                });
    }

    private void initWebRtcEventListeners(final WebSocketSession session,
                                          final WebRtcEndpoint webRtcEp)
    {
        log.info("[Handler::initWebRtcEventListeners] name: {}, sessionId: {}",
                webRtcEp.getName(), session.getId());

        // Event: The ICE backend found a local candidate during Trickle ICE
        webRtcEp.addIceCandidateFoundListener(
                new EventListener<IceCandidateFoundEvent>() {
                    @Override
                    public void onEvent(IceCandidateFoundEvent ev) {
                        log.debug("[WebRtcEndpoint::{}] source: {}, timestamp: {}, tags: {}, candidate: {}",
                                ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                                ev.getTags(), JsonUtils.toJson(ev.getCandidate()));

                        JsonObject message = new JsonObject();
                        message.addProperty("id", "ADD_ICE_CANDIDATE");
                        message.add("candidate", JsonUtils.toJsonObject(ev.getCandidate()));
                        sendMessage(session, message.toString());
                    }
                });

        // Event: The ICE backend changed state
        webRtcEp.addIceComponentStateChangeListener(
                new EventListener<IceComponentStateChangeEvent>() {
                    @Override
                    public void onEvent(IceComponentStateChangeEvent ev) {
                        log.debug("[WebRtcEndpoint::{}] source: {}, timestamp: {}, tags: {}, streamId: {}, componentId: {}, state: {}",
                                ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                                ev.getTags(), ev.getStreamId(), ev.getComponentId(), ev.getState());
                    }
                });

        // Event: The ICE backend finished gathering ICE candidates
        webRtcEp.addIceGatheringDoneListener(
                new EventListener<IceGatheringDoneEvent>() {
                    @Override
                    public void onEvent(IceGatheringDoneEvent ev) {
                        log.info("[WebRtcEndpoint::{}] source: {}, timestamp: {}, tags: {}",
                                ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                                ev.getTags());
                    }
                });

        // Event: The ICE backend selected a new pair of ICE candidates for use
        webRtcEp.addNewCandidatePairSelectedListener(
                new EventListener<NewCandidatePairSelectedEvent>() {
                    @Override
                    public void onEvent(NewCandidatePairSelectedEvent ev) {
                        log.info("[WebRtcEndpoint::{}] name: {}, timestamp: {}, tags: {}, streamId: {}, local: {}, remote: {}",
                                ev.getType(), ev.getSource().getName(), ev.getTimestamp(),
                                ev.getTags(), ev.getCandidatePair().getStreamID(),
                                ev.getCandidatePair().getLocalCandidate(),
                                ev.getCandidatePair().getRemoteCandidate());
                    }
                });
    }

}
