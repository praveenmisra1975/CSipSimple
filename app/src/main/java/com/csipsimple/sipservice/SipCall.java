package com.csipsimple.sipservice;

import static com.csipsimple.pjsip.UAStateReceiver.ON_CALL_STATE;
import static com.csipsimple.pjsip.UAStateReceiver.ON_MEDIA_STATE;

import android.media.ToneGenerator;
import android.view.Surface;

import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipCallSessionImpl;

import org.pjsip.pjsua2.AudDevManager;
import org.pjsip.pjsua2.AudioMedia;
import org.pjsip.pjsua2.Call;
import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallMediaInfo;
import org.pjsip.pjsua2.CallOpParam;
import org.pjsip.pjsua2.CallSetting;
import org.pjsip.pjsua2.CallVidSetStreamParam;
import org.pjsip.pjsua2.Media;
import org.pjsip.pjsua2.MediaFmtChangedEvent;
import org.pjsip.pjsua2.OnCallMediaEventParam;
import org.pjsip.pjsua2.OnCallMediaStateParam;
import org.pjsip.pjsua2.OnCallStateParam;
import org.pjsip.pjsua2.OnStreamDestroyedParam;
import org.pjsip.pjsua2.RtcpStreamStat;
import org.pjsip.pjsua2.StreamInfo;
import org.pjsip.pjsua2.StreamStat;
import org.pjsip.pjsua2.VideoPreview;
import org.pjsip.pjsua2.VideoPreviewOpParam;
import org.pjsip.pjsua2.VideoWindow;
import org.pjsip.pjsua2.VideoWindowHandle;
import org.pjsip.pjsua2.pjmedia_dir;
import org.pjsip.pjsua2.pjmedia_event_type;
import org.pjsip.pjsua2.pjmedia_rtcp_fb_type;
import org.pjsip.pjsua2.pjmedia_type;
import org.pjsip.pjsua2.pjsip_inv_state;
import org.pjsip.pjsua2.pjsip_status_code;
import org.pjsip.pjsua2.pjsua2;
import org.pjsip.pjsua2.pjsua_call_flag;
import org.pjsip.pjsua2.pjsua_call_media_status;
import org.pjsip.pjsua2.pjsua_call_vid_strm_op;
import org.pjsip.pjsua2.pjsua_vid_req_keyframe_method;

/**
 * Wrapper around PJSUA2 Call object.
 * @author gotev (Aleksandar Gotev)
 */
@SuppressWarnings("unused")
public class SipCall extends Call {

    private static final String LOG_TAG = SipCall.class.getSimpleName();

    private final SipAccount account;
    private boolean localHold = false;
    private boolean localMute = false;
    private boolean localVideoMute = false;
    private long connectTimestamp = 0;
    private ToneGenerator toneGenerator;
    private boolean videoCall = false;
    private boolean videoConference = false;
    private boolean frontCamera = true;

    private VideoWindow mVideoWindow;
    private VideoPreview mVideoPreview;

    private StreamInfo streamInfo = null;
    private StreamStat streamStat = null;

    /**
     * Incoming call constructor.
     * @param account the account which own this call
     * @param callID the id of this call
     */
    public SipCall(SipAccount account, int callID) {
        super(account, callID);
        this.account = account;
        mVideoPreview = null;
        mVideoWindow = null;
    }

    /**
     * Outgoing call constructor.
     * @param account account which owns this call
     */
    public SipCall(SipAccount account) {
        super(account);
        this.account = account;
    }

    public int getCurrentState() {
        try {
            CallInfo info = getInfo();
            return info.getState();
        } catch (Exception exc) {
            Logger.error(getClass().getSimpleName(), "Error while getting call Info", exc);
            return pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED;
        }
    }


    @Override
    public void onCallState(OnCallStateParam prm) {

        Logger.debug(LOG_TAG, " in SIPCall onCallState() >>");

        account.getService().getUAStateReceiver().lockCpu();
        try {

            CallInfo info = getInfo();
            int callID = info.getId();
            int callState = info.getState();
            int callStatus = pjsip_status_code.PJSIP_SC_NULL;

            SipCallSessionImpl callInfo = account.getService().getUAStateReceiver().updateCallInfoFromStack(callID, info);

            Logger.debug(LOG_TAG, "Call state else getCallState >>" + callInfo.getCallState());
            Logger.debug(LOG_TAG, " in SIPCall call status >> "+callStatus);

            if (callState == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                    Logger.debug(LOG_TAG, " in SIPCall call state >> PJSIP_INV_STATE_DISCONNECTED");

                    if (account.getService().getPjService().mediaManager != null) {
                        if (account.getService().getUAStateReceiver().getRingingCall() == null) {
                            account.getService().getPjService().mediaManager.stopRingAndUnfocus();
                            account.getService().getPjService().mediaManager.resetSettings();
                        }
                    }

                    //  checkAndStopLocalRingBackTone();
                    if (account.getService().getUAStateReceiver().ongoingCallLock != null && account.getService().getUAStateReceiver().ongoingCallLock.isHeld()) {
                        account.getService().getUAStateReceiver().ongoingCallLock.release();
                    }
                    // Call is now ended
                    account.getService().getPjService().stopDialtoneGenerator(callID);
                    account.getService().getPjService().stopRecording(callID);
                    account.getService().getPjService().stopPlaying(callID);
                    account.getService().getPjService().stopWaittoneGenerator(callID);

                    stopVideoFeeds();


                    if (connectTimestamp > 0 && streamInfo != null && streamStat != null) {
                        try {
                            sendCallStats(callID, info.getConnectDuration().getSec(), callStatus);
                        } catch (Exception ex) {
                            Logger.error(LOG_TAG, "Error while sending call stats", ex);
                            throw ex;
                        }
                    }

                } else {
                    Logger.debug(LOG_TAG, " in SIPCall call status >> PJSIP_INV_STATE_CONFIRMED");

                    if (account.getService().getUAStateReceiver().ongoingCallLock != null && !account.getService().getUAStateReceiver().ongoingCallLock.isHeld()) {
                        account.getService().getUAStateReceiver().ongoingCallLock.acquire();
                    }

                    // checkAndStopLocalRingBackTone();
                    connectTimestamp = System.currentTimeMillis();
                    if (videoCall) {
                        setVideoMute(false);
                    }

                // check whether the 183 has arrived or not
            }
            account.getService().getUAStateReceiver().msgHandler.sendMessage(account.getService().getUAStateReceiver().msgHandler.obtainMessage(ON_CALL_STATE, callInfo));

         //   account.getService().getBroadcastEmitter()
           //         .callState(account.getData().getIdUri(), callID, callState, callStatus, connectTimestamp);

            if (callState == pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED) {
                Logger.debug(LOG_TAG, " in SIPCall call status >> PJSIP_INV_STATE_DISCONNECTED");

                account.getService().getUAStateReceiver().deleteCall(callID);
                delete();
            }

        } catch (Exception exc) {
            Logger.error(LOG_TAG, "onCallState: error while getting call info", exc);
        } finally{
        // Unlock CPU anyway
        account.getService().getUAStateReceiver().unlockCpu();
    }
    }


    @Override
    public void onCallMediaState(OnCallMediaStateParam prm) {

        Logger.debug(LOG_TAG, "Inside onCallMediaState:");

        account.getService().getUAStateReceiver().lockCpu();

        if (account.getService().getUAStateReceiver().pjService.mediaManager != null) {
            // Do not unfocus here since we are probably in call.
            // Unfocus will be done anyway on call disconnect
            account.getService().getUAStateReceiver().pjService.mediaManager.stopRing();
        }

        CallInfo info;
        try {
            info = getInfo();
        } catch (Exception exc) {
            Logger.error(LOG_TAG, "onCallMediaState: error while getting call info", exc);
            return;
        }

         SipCallSessionImpl callInfo = account.getService().getUAStateReceiver().updateCallInfoFromStack(info.getId(), info);


        /*
         * Connect ports appropriately when media status is ACTIVE or REMOTE
         * HOLD, otherwise we should NOT connect the ports.
         */
        boolean connectToOtherCalls = false;

        int mediaStatus = callInfo.getMediaStatus();
        if (mediaStatus == SipCallSessionImpl.MediaState.ACTIVE ||
                mediaStatus == SipCallSessionImpl.MediaState.REMOTE_HOLD) {

            connectToOtherCalls = true;


            // Adjust software volume
            if (account.getService().getUAStateReceiver().pjService.mediaManager != null) {
                account.getService().getUAStateReceiver().pjService.mediaManager.setSoftwareVolume();
            }

            // Auto record
            if (account.getService().getUAStateReceiver().mAutoRecordCalls && account.getService().getUAStateReceiver().pjService.canRecord(info.getId())
                    && !account.getService().getUAStateReceiver().pjService.isRecording(info.getId())) {
              try {
                  account.getService().getUAStateReceiver().pjService.startRecording(info.getId(), SipManager.BITMASK_IN | SipManager.BITMASK_OUT);
              }catch(Exception e)
              {}
            }
        }

        for (int i = 0; i < info.getMedia().size(); i++) {
            Media media = getMedia(i);
            CallMediaInfo mediaInfo = info.getMedia().get(i);

            if (mediaInfo.getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO
                    && media != null
                    && mediaInfo.getStatus() == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) {

                account.getService().getUAStateReceiver().pjService.setAudioInCall(mediaInfo.getStatus());
                
                handleAudioMedia(media);


            } else if (mediaInfo.getType() == pjmedia_type.PJMEDIA_TYPE_VIDEO
                    && mediaInfo.getStatus() == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE
                    && mediaInfo.getVideoIncomingWindowId() != pjsua2.INVALID_ID) {

                handleVideoMedia(mediaInfo);

            }
        }


        // Play wait tone
        if(account.getService().getUAStateReceiver().mPlayWaittone) {
            if(mediaStatus == SipCallSessionImpl.MediaState.REMOTE_HOLD) {
                account.getService().getUAStateReceiver().pjService.startWaittoneGenerator(info.getId());
            }else {
                account.getService().getUAStateReceiver().pjService.stopWaittoneGenerator(info.getId());
            }
        }

        account.getService().getUAStateReceiver().msgHandler.sendMessage(account.getService().getUAStateReceiver().msgHandler.obtainMessage(ON_MEDIA_STATE, callInfo));

        account.getService().getUAStateReceiver().unlockCpu();
    }

    @Override
    public void onCallMediaEvent(OnCallMediaEventParam prm) {
        Logger.debug(LOG_TAG,"onCallMediaEvent >>");
        int evType = prm.getEv().getType();
        switch (evType) {
            case pjmedia_event_type.PJMEDIA_EVENT_FMT_CHANGED:
                try {
                    CallInfo callInfo = getInfo();
                    CallMediaInfo mediaInfo = callInfo.getMedia().get((int)prm.getMedIdx());
                    if (mediaInfo.getType() == pjmedia_type.PJMEDIA_TYPE_VIDEO &&
                            mediaInfo.getDir() == pjmedia_dir.PJMEDIA_DIR_DECODING) {
                        MediaFmtChangedEvent fmtEvent = prm.getEv().getData().getFmtChanged();
                        Logger.info(LOG_TAG, "Notify new video size");
                        account.getService().getBroadcastEmitter().videoSize(
                                (int) fmtEvent.getNewWidth(),
                                (int) fmtEvent.getNewHeight()
                        );
                    }
                } catch (Exception ex) {
                    Logger.error(LOG_TAG, "Unable to get video dimensions", ex);
                }
                break;
            case pjmedia_event_type.PJMEDIA_EVENT_RX_RTCP_FB:
                Logger.debug(LOG_TAG, "Keyframe request received");
                if (prm.getEv().getData() != null &&
                        prm.getEv().getData().getRtcpFb().getFbType() == pjmedia_rtcp_fb_type.PJMEDIA_RTCP_FB_NACK &&
                        prm.getEv().getData().getRtcpFb().getIsParamLengthZero()
                ) {
                    Logger.info(LOG_TAG, "Sending new keyframe");
                    sendKeyFrame();
                }
        }
        super.onCallMediaEvent(prm);
    }

    @Override
    public void onStreamDestroyed(OnStreamDestroyedParam prm) {
        Logger.debug(LOG_TAG,"onStreamDestroyed >>");
        long idx = prm.getStreamIdx();
        try {
            CallInfo callInfo = getInfo();
            if (getInfo().getMedia().get((int)idx).getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO) {
                streamInfo = getStreamInfo(idx);
                streamStat = getStreamStat(idx);
            }
        } catch (Exception ex) {
            Logger.error(LOG_TAG, "onStreamDestroyed: error while getting call stats", ex);
        }
        super.onStreamDestroyed(prm);
    }

    /**
     * Get the total duration of the call.
     * @return the duration in milliseconds or 0 if the call is not connected.
     */
    public long getConnectTimestamp() {
        return connectTimestamp;
    }

    public void acceptIncomingCall() {
        CallOpParam param = new CallOpParam();
        param.setStatusCode(pjsip_status_code.PJSIP_SC_OK);
        setMediaParams(param);
        if (!videoCall) {
            CallSetting callSetting = param.getOpt();
            callSetting.setFlag(pjsua_call_flag.PJSUA_CALL_INCLUDE_DISABLED_MEDIA);
        }
        try {
            answer(param);
        } catch (Exception exc) {
            Logger.error(LOG_TAG, "Failed to accept incoming call", exc);
        }
    }

    public void sendBusyHereToIncomingCall() {
        CallOpParam param = new CallOpParam();
        param.setStatusCode(pjsip_status_code.PJSIP_SC_BUSY_HERE);

        try {
            answer(param);
        } catch (Exception exc) {
            Logger.error(LOG_TAG, "Failed to send busy here", exc);
        }
    }

    public void declineIncomingCall() {
        CallOpParam param = new CallOpParam();
        param.setStatusCode(pjsip_status_code.PJSIP_SC_DECLINE);

        try {
            answer(param);
        } catch (Exception exc) {
            Logger.error(LOG_TAG, "Failed to decline incoming call", exc);
        }
    }

    public void hangUp() {
        CallOpParam param = new CallOpParam();
        param.setStatusCode(pjsip_status_code.PJSIP_SC_DECLINE);

        try {
            hangup(param);
        } catch (Exception exc) {
            Logger.error(LOG_TAG, "Failed to hangUp call", exc);
        }
    }

    /**
     * Utility method to mute/unmute the device microphone during a call.
     * @param mute true to mute the microphone, false to un-mute it
     */
    public void setMute(boolean mute) {
        // return immediately if we are not changing the current state
        if (localMute == mute) return;

        CallInfo info;
        try {
            info = getInfo();
        } catch (Exception exc) {
            Logger.error(LOG_TAG, "setMute: error while getting call info", exc);
            return;
        }

        for (int i = 0; i < info.getMedia().size(); i++) {
            Media media = getMedia(i);
            CallMediaInfo mediaInfo = info.getMedia().get(i);

            if (mediaInfo.getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO
                    && media != null
                    && mediaInfo.getStatus() == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) {
                AudioMedia audioMedia = AudioMedia.typecastFromMedia(media);

                // connect or disconnect the captured audio
                try {
                    AudDevManager mgr = account.getService().getAudDevManager();


                    if (mute)
                    {
                        mgr.getCaptureDevMedia().stopTransmit(audioMedia);
                    }
                    else {
                        mgr.getCaptureDevMedia().startTransmit(audioMedia);
                    }
                    localMute = mute;
                    account.getService().getBroadcastEmitter().callMediaState(
                            account.getData().getIdUri(), getId(), MediaState.LOCAL_MUTE, localMute);
                } catch (Exception exc) {
                    Logger.error(LOG_TAG, "setMute: error while connecting audio media to sound device", exc);
                }
            }
        }
    }

    public boolean isLocalMute() {
        return localMute;
    }

    public void toggleMute() {
        setMute(!localMute);
    }

    /**
     * Utility method to transfer a call to a number in the same realm as the account to
     * which this call belongs to. If you want to transfer the call to a different realm, you
     * have to pass the full string in this format: sip:NUMBER@REALM. E.g. sip:200@mycompany.com
     * @param destination destination to which to transfer the call.
     * @throws Exception if an error occurs during the call transfer
     */
    public void transferTo(String destination) throws Exception {
        String transferString;

        if (destination.startsWith("sip:")) {
            transferString = "<" + destination + ">";
        } else {
            if ("*".equals(account.getData().getRealm())) {
                transferString = "<sip:" + destination + ">";
            } else {
                transferString = "<sip:" + destination + "@" + account.getData().getRealm() + ">";
            }
        }

        CallOpParam param = new CallOpParam();

        xfer(transferString, param);
    }
    public void xferReplaces(SipCall destination) throws Exception {
        CallOpParam param = new CallOpParam();
        xferReplaces(destination, param);
    }

    // check if Local RingBack Tone has started, if so, stop it.
    private void checkAndStopLocalRingBackTone(){
        if (toneGenerator != null){
            toneGenerator.stopTone();
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    // disable video programmatically
    @Override
    public void makeCall(String dst_uri, CallOpParam prm) throws Exception {
        setMediaParams(prm);
        if (!videoCall) {
            CallSetting callSetting = prm.getOpt();
            callSetting.setFlag(pjsua_call_flag.PJSUA_CALL_INCLUDE_DISABLED_MEDIA);
        }
        super.makeCall(dst_uri, prm);
    }

    private void handleAudioMedia(Media media) {
        AudioMedia audioMedia = AudioMedia.typecastFromMedia(media);


        // connect the call audio media to sound device
        try {
            AudDevManager audDevManager = account.getService().getAudDevManager();
            if (audioMedia != null) {
                try {
                    audioMedia.adjustRxLevel((float) 1.5);
                    audioMedia.adjustTxLevel((float) 1.5);
                } catch (Exception exc) {
                    Logger.error(LOG_TAG, "Error while adjusting levels", exc);
                }

                audioMedia.startTransmit(audDevManager.getPlaybackDevMedia());
                audDevManager.getCaptureDevMedia().startTransmit(audioMedia);
            }
        } catch (Exception exc) {
            Logger.error(LOG_TAG, "Error while connecting audio media to sound device", exc);
        }
    }

    private void handleVideoMedia(CallMediaInfo mediaInfo) {
        if (mVideoWindow != null) {
            mVideoWindow.delete();
        }
        if (mVideoPreview != null) {
            mVideoPreview.delete();
        }
        if (!videoConference) {
            // Since 2.9 pjsip will not start capture device if autoTransmit is false
            // thus mediaInfo.getVideoCapDev() always returns -3 -> NULL
            // mVideoPreview = new VideoPreview(mediaInfo.getVideoCapDev());
            mVideoPreview = new VideoPreview(SipServiceConstants.FRONT_CAMERA_CAPTURE_DEVICE);
        }
        mVideoWindow = new VideoWindow(mediaInfo.getVideoIncomingWindowId());
    }

    public VideoWindow getVideoWindow() {
        return mVideoWindow;
    }

    public void setVideoWindow(VideoWindow mVideoWindow) {
        this.mVideoWindow = mVideoWindow;
    }

    public VideoPreview getVideoPreview() {
        return mVideoPreview;
    }

    public void setVideoPreview(VideoPreview mVideoPreview) {
        this.mVideoPreview = mVideoPreview;
    }

    private void stopVideoFeeds() {
        stopIncomingVideoFeed();
        stopPreviewVideoFeed();
    }

    public void setIncomingVideoFeed(Surface surface) {
        if (mVideoWindow != null) {
            VideoWindowHandle videoWindowHandle = new VideoWindowHandle();
            videoWindowHandle.getHandle().setWindow(surface);
            try {
                mVideoWindow.setWindow(videoWindowHandle);
                account.getService().getBroadcastEmitter().videoSize(
                        (int) mVideoWindow.getInfo().getSize().getW(),
                        (int) mVideoWindow.getInfo().getSize().getH());

                // start video again if not mute
                setVideoMute(localVideoMute);
            } catch (Exception ex) {
                Logger.error(LOG_TAG, "Unable to setup Incoming Video Feed", ex);
            }
        }
    }

    public void startPreviewVideoFeed(Surface surface) {
        if (mVideoPreview != null) {
            VideoWindowHandle videoWindowHandle = new VideoWindowHandle();
            videoWindowHandle.getHandle().setWindow(surface);
            VideoPreviewOpParam videoPreviewOpParam = new VideoPreviewOpParam();
            videoPreviewOpParam.setWindow(videoWindowHandle);
            try {
                mVideoPreview.start(videoPreviewOpParam);
            } catch (Exception ex) {
                Logger.error(LOG_TAG, "Unable to start Video Preview", ex);
            }
        }
    }

    public void stopIncomingVideoFeed() {
        VideoWindow videoWindow = getVideoWindow();
        if (videoWindow != null) {
            try {
                videoWindow.delete();
            } catch (Exception ex) {
                Logger.error(LOG_TAG, "Unable to stop remote video feed", ex);
            }
        }
    }

    public void stopPreviewVideoFeed() {
        VideoPreview videoPreview = getVideoPreview();
        if (videoPreview != null) {
            try {
                videoPreview.stop();
            } catch (Exception ex) {
                Logger.error(LOG_TAG, "Unable to stop preview video feed", ex);
            }
        }
    }

    public boolean isVideoCall() {
        return videoCall;
    }

    public boolean isVideoConference() {
        return videoConference;
    }

    public void setVideoParams(boolean videoCall, boolean videoConference) {
        this.videoCall = videoCall;
        this.videoConference = videoConference;
    }

    private void setMediaParams(CallOpParam param) {
        CallSetting callSetting = param.getOpt();
        callSetting.setAudioCount(1);
        callSetting.setVideoCount(videoCall ? 1 : 0);
        callSetting.setReqKeyframeMethod(pjsua_vid_req_keyframe_method.PJSUA_VID_REQ_KEYFRAME_RTCP_PLI);
    }

    public void setVideoMute(boolean videoMute) {
        try {
            vidSetStream(videoMute
                    ? pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_STOP_TRANSMIT
                    : pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_START_TRANSMIT,
                new CallVidSetStreamParam());
            localVideoMute = videoMute;
            account.getService().getBroadcastEmitter().callMediaState(
                    account.getData().getIdUri(), getId(), MediaState.LOCAL_VIDEO_MUTE, localVideoMute);
        } catch(Exception ex) {
            Logger.error(LOG_TAG, "Error while toggling video transmission", ex);
        }
    }

    public boolean isLocalVideoMute() {
        return localVideoMute;
    }

    public boolean isFrontCamera() {
        return frontCamera;
    }

    public void setFrontCamera(boolean frontCamera) {
        this.frontCamera = frontCamera;
    }

    private void sendKeyFrame() {
        try {
            vidSetStream(pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_SEND_KEYFRAME, new CallVidSetStreamParam());
        } catch (Exception ex) {
            Logger.error(LOG_TAG, "Error sending keyframe", ex);
        }
    }

    private void sendCallStats(int callID, int duration, int callStatus) {
        String audioCodec = streamInfo.getCodecName().toLowerCase()+"_"+streamInfo.getCodecClockRate();

        RtcpStreamStat rxStat = streamStat.getRtcp().getRxStat();
        RtcpStreamStat txStat = streamStat.getRtcp().getTxStat();

        Jitter rxJitter = new Jitter(
                rxStat.getJitterUsec().getMax(),
                rxStat.getJitterUsec().getMean(),
                rxStat.getJitterUsec().getMin());

        Jitter txJitter = new Jitter(
                txStat.getJitterUsec().getMax(),
                txStat.getJitterUsec().getMean(),
                txStat.getJitterUsec().getMin());

        RtpStreamStats rx = new RtpStreamStats(
                (int)rxStat.getPkt(),
                (int)rxStat.getDiscard(),
                (int)rxStat.getLoss(),
                (int)rxStat.getReorder(),
                (int)rxStat.getDup(),
                rxJitter
        );

        RtpStreamStats tx = new RtpStreamStats(
                (int)txStat.getPkt(),
                (int)txStat.getDiscard(),
                (int)txStat.getLoss(),
                (int)txStat.getReorder(),
                (int)txStat.getDup(),
                txJitter
        );

        account.getService().getBroadcastEmitter().callStats(callID, duration, audioCodec, callStatus, rx, tx);
        streamInfo = null;
        streamStat = null;
    }
}
