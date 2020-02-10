const ws = new WebSocket('wss://' + location.host + '/hello');

let webRtcPeer;

// UI
let uiLocalVideo;
let uiRemoteVideo;

let uiState = null;
const UI_IDLE = 0;
const UI_STARTING = 1;
const UI_STARTED = 2;

/* ================== */
/* ==== UI state ==== */
/* ================== */



function uiEnableElement(id, onclickHandler)
{
    console.log(id.attr)
    //id.attr('disabled', false);
    //id.setAttribute("disabled", false);
    if (onclickHandler) {
        id.attr('onclick', onclickHandler);
    }
}

function uiDisableElement(id)
{
    //id.setAttribute("disabled", true);
    id.removeAttr('onclick');
}

function uiSetState(newState)
{
    switch (newState) {
        case UI_IDLE:
            uiEnableElement('#uiStartBtn', 'Start()');
            uiDisableElement('#uiStopBtn');
            break;
        case UI_STARTING:
            uiDisableElement('#uiStartBtn');
            uiDisableElement('#uiStopBtn');
            break;
        case UI_STARTED:
            uiDisableElement('#uiStartBtn');
            uiEnableElement('#uiStopBtn', 'uiStop()');
            break;
        default:
            console.warn("[setState] Skip, invalid state: " + newState);
            return;
    }
    uiState = newState;
};

window.onload = function()
{
    console.log("Page loaded");
    uiLocalVideo = document.getElementById('uiLocalVideo');
    uiRemoteVideo = document.getElementById('uiRemoteVideo');
    uiSetState(UI_IDLE);
}





window.onbeforeunload = function()
{
    console.log("Page unload - Close WebSocket");
    ws.close();
}


// PROCESS_SDP_ANSWER ----------------------------------------------------------

function handleProcessSdpAnswer(jsonMessage)
{
    console.log("[handleProcessSdpAnswer] SDP Answer from Kurento, process in WebRTC Peer");

    if (webRtcPeer == null) {
        console.warn("[handleProcessSdpAnswer] Skip, no WebRTC Peer");
        return;
    }

    webRtcPeer.processAnswer(jsonMessage.sdpAnswer, (err) => {
        if (err) {
            sendError("[handleProcessSdpAnswer] Error: " + err);
            stop();
            return;
        }

        console.log("[handleProcessSdpAnswer] SDP Answer ready; start remote video");
        startVideo(uiRemoteVideo);

        //uiSetState(UI_STARTED);
    });
}

// ADD_ICE_CANDIDATE -----------------------------------------------------------

function handleAddIceCandidate(jsonMessage)
{
    if (webRtcPeer == null) {
        console.warn("[handleAddIceCandidate] Skip, no WebRTC Peer");
        return;
    }

    webRtcPeer.addIceCandidate(jsonMessage.candidate, (err) => {
        if (err) {
            console.error("[handleAddIceCandidate] " + err);
            return;
        }
    });


}


ws.onmessage = function(message)
{
    const jsonMessage = JSON.parse(message.data);
    console.log("[onmessage] Received message: " + jsonMessage.id);

    switch (jsonMessage.id) {
        case 'incomingCall':
            incomingCall(jsonMessage);
            break;
        case 'PROCESS_SDP_ANSWER':
            handleProcessSdpAnswer(jsonMessage);
            break;
        case 'ADD_ICE_CANDIDATE':
            handleAddIceCandidate(jsonMessage);
            break;
        case 'startCommunication':
            startCommunication(jsonMessage);
            break;
        case 'callResponse':
            callResponse(jsonMessage);
            break;
        case 'playResponse':
            playResponse(jsonMessage);
            break
        case 'stop':
            stopcom();
            break
        case 'ERROR':
            handleError(jsonMessage);
            break;
        default:
            // Ignore the message
            console.warn("[onmessage] Invalid message, id: " + jsonMessage.id);
            break;
    }
}


function playResponse(message) {
    if (message.response != 'accepted') {
        document.getElementById('uiLocalVideo').style.display = 'block';
        alert(message.error);
        document.getElementById('peer').focus();
    } else {
        webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
            if (error)
                return console.error(error);
        });
    }
}

function startCommunication(message) {
    //process callee answer
    webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
        if (error)
            return console.error(error);
    });
}


function callResponse(message) {
    if (message.response != 'accepted') {
        console.info('Call not accepted by peer. Closing call');
        var errorMessage = message.message ? message.message
            : 'Unknown reason for call rejection.';
        console.log(errorMessage);
        stop();
    } else {
        //process caller answer
        webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
            if (error)
                return console.error(error);
        });
    }
}

function incomingCall(message) {

    from = message.from;
    console.log("incoming call from "+ from)
    var options = {
        localVideo : uiLocalVideo,
        remoteVideo : uiRemoteVideo,
        onicecandidate : onIceCandidate,
    }
    webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
        function(error) {
            if (error) {
                return console.error(error);
            }
            webRtcPeer.generateOffer((err, sdpOffer) => {
                if (err) {
                    console.log("[start/WebRtcPeerSendrecv/generateOffer] Error: " + err);
                    stop();
                    return;
                }
                console.log("[start/WebRtcPeerSendrecv/generateOffer] Done!");
                var response = {
                    id : 'incomingCallResponse',
                    from : from,
                    callResponse : 'accept',
                    sdpOffer : sdpOffer
                };
                console.log('Invoking incoming call response with SDP offer retrived from getOffer');
                sendMessage(response);
            });
        });



}


function startVideo(video)
{
    // Manually start the <video> HTML element
    // This is used instead of the 'autoplay' attribute, because iOS Safari
    // requires a direct user interaction in order to play a video with audio.
    // Ref: https://developer.mozilla.org/en-US/docs/Web/HTML/Element/video
    video.play().catch((err) => {
        if (err.name === 'NotAllowedError') {
            console.error("[start] Browser doesn't allow playing video: " + err);
        }
        else {
            console.error("[start] Error in video.play(): " + err);
        }
    });
}

function sendMessage(message)
{
    if (ws.readyState !== ws.OPEN) {
        console.warn("[sendMessage] Skip, WebSocket session isn't open");
        return;
    }
    const jsonMessage = JSON.stringify(message);
    console.log("[sendMessage] message: " + jsonMessage.id);
    ws.send(jsonMessage);
}


function start()
{
    console.log("[start] Create WebRtcPeerSendrecv");
    //uiSetState(UI_STARTING);
    //showSpinner(uiLocalVideo, uiRemoteVideo);

    const options = {
        localVideo: uiLocalVideo,
        remoteVideo: uiRemoteVideo,
        mediaConstraints: { audio: true, video: true },
        onicecandidate: (candidate) => sendMessage({
            id: 'ADD_ICE_CANDIDATE',
            candidate: candidate,
        }),
    };

    webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
        function(err)
        {
            if (err) {
                console.log("[start/WebRtcPeerSendrecv] Error: ");
                    //+ explainUserMediaError(err));
                stop();
                return;
            }

            console.log("[start/WebRtcPeerSendrecv] Created; start local video");
            startVideo(uiLocalVideo);

            console.log("[start/WebRtcPeerSendrecv] Generate SDP Offer");
            webRtcPeer.generateOffer((err, sdpOffer) => {
                if (err) {
                    console.log("[start/WebRtcPeerSendrecv/generateOffer] Error: " + err);
                    stop();
                    return;
                }

                sendMessage({
                    id: 'PROCEsdpOfferSS_SDP_OFFER',
                    sdpOffer: sdpOffer,
                });

                console.log("[start/WebRtcPeerSendrecv/generateOffer] Done!");
                uiSetState(UI_STARTED);
            });
        });
}


function register() {
    var name = document.getElementById('name').value;
    if (name == '') {
        window.alert('You must insert your user name');
        return;
    }
    var message = {
        id : 'register',
        name : name
    };
    sendMessage(message);
    document.getElementById('peer').focus();
}

function onIceCandidate(candidate) {
    var message = {
        id : 'ADD_ICE_CANDIDATE',
        candidate : candidate
    };
    sendMessage(message);
}

function call() {
    var peer = document.getElementById('peer').value;
    var name = document.getElementById('name').value;

    if (name == '') {
        window.alert('You must insert peer name');
        return;
    }
    const options = {
        localVideo: uiLocalVideo,
        remoteVideo: uiRemoteVideo,
        onicecandidate: onIceCandidate
    };

    webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options,
        function(err)
        {
            if (err) {
                console.log("[start/WebRtcPeerSendrecv] Error: ");
                //+ explainUserMediaError(err));
                stop();
                return;
            }
            console.log("[start/WebRtcPeerSendrecv] Generate SDP Offer");
            webRtcPeer.generateOffer((err, sdpOffer) => {
                if (err) {
                    console.log("[start/WebRtcPeerSendrecv/generateOffer] Error: " + err);
                    stop();
                    return;
                }
                console.log("[start/WebRtcPeerSendrecv/generateOffer] Done!");
                var message = {
                    id : 'call',
                    to:peer,
                    from:name,
                    sdpOffer:sdpOffer
                };
                console.log('Invoking call with SDP offer retrived from getOffer');
                sendMessage(message);
            });
        });
}

function play() {
    peername = document.getElementById("peer").value;
    var options = {
        remoteVideo : uiRemoteVideo,
        onicecandidate : onIceCandidate
    }
    webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
        function(error) {
            if (error) {
                return console.error(error);
            }
            this.generateOffer((error, offerSdp) =>{
                console.log('Invoking SDP offer callback function');
                var message = {
                    id : 'play',
                    user : document.getElementById('peer').value,
                    sdpOffer : offerSdp
                };
                sendMessage(message);
            });

        });
}


function stopcom() {
    if (webRtcPeer) {
        webRtcPeer.dispose();
        webRtcPeer = null;
        var message = {
            id : 'stop'
        }
        sendMessage(message);
    }
    document.getElementById('uiLocalVideo').style.display = 'block';
    document.getElementById('uiRemoteVideo').style.display = 'block';

}