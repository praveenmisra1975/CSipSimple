
package com.csipsimple.pjsip;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.SparseArray;

import com.csipsimple.R;
import com.csipsimple.api.SipConfigManager;
import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipProfile;
import com.csipsimple.api.SipUri;
import com.csipsimple.api.SipUri.ParsedSipContactInfos;
import com.csipsimple.service.SipNotifications;
import com.csipsimple.service.SipService;
import com.csipsimple.service.SipService.SameThreadException;
import com.csipsimple.service.SipService.SipRunnable;
import com.csipsimple.api.SipCallSessionImpl;
import com.csipsimple.sipservice.Logger;
import com.csipsimple.sipservice.SipCall;
import com.csipsimple.utils.CallLogHelper;
import com.csipsimple.utils.Compatibility;
import com.csipsimple.utils.Threading;


import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallMediaInfo;
import org.pjsip.pjsua2.Media;
import org.pjsip.pjsua2.OnIncomingCallParam;
import org.pjsip.pjsua2.TimeVal;
import org.pjsip.pjsua2.pjmedia_type;
import org.pjsip.pjsua2.pjsua_call_media_status;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class UAStateReceiver  {
    private final static String THIS_FILE = "UAStateReceiver";
    private final static String ACTION_PHONE_STATE_CHANGED = "android.intent.action.PHONE_STATE";

    private SipNotifications notificationManager;
    public  PjSipService pjService;
    // private ComponentName remoteControlResponder;

    // Time in ms during which we should not relaunch call activity again
    final static long LAUNCH_TRIGGER_DELAY = 2000;
    private long lastLaunchCallHandler = 0;

    private int eventLockCount = 0;
    private boolean mIntegrateWithCallLogs;
    public boolean mPlayWaittone;
    private int mPreferedHeadsetAction;
    public boolean mAutoRecordCalls;
    private int mMicroSource;

    public void lockCpu() {
        if (eventLock != null) {
            Logger.debug(THIS_FILE, "< LOCK CPU");
            eventLock.acquire();
            eventLockCount++;
        }
    }

    public  void unlockCpu() {
        if (eventLock != null && eventLock.isHeld()) {
            eventLock.release();
            eventLockCount--;
            Logger.debug(THIS_FILE, "> UNLOCK CPU " + eventLockCount);
        }
    }


    // public String sasString = "";
    // public boolean zrtpOn = false;

    public int on_validate_audio_clock_rate(int clockRate) {
        if (pjService != null) {
            return pjService.validateAudioClockRate(clockRate);
        }
        return -1;
    }


    /**
     * Map callId to known  This is cache of known
     * session maintained by the UA state receiver. The UA state receiver is in
     * charge to maintain calls list integrity for {@link PjSipService}. All
     * information it gets comes from the stack. Except recording status that
     * comes from service.
     */
    public SparseArray<SipCallSessionImpl> callsList = new SparseArray<SipCallSessionImpl>();



    /**
     * Get call info for a given call id.
     *
     * @param callId the id of the call we want infos for
     * @return the call session infos.
     */
    public SipCallSessionImpl getCallInfo(Integer callId) {
        SipCallSessionImpl callInfo;
        synchronized (callsList) {
            callInfo = callsList.get(callId);
        }
        return callInfo;
    }

    /**
     * Get list of calls session available.
     *
     * @return List of calls.
     */
    public SipCallSessionImpl[] getCalls() {
        if (callsList != null) {
            List<SipCallSessionImpl> calls = new ArrayList<SipCallSessionImpl>();

            synchronized (callsList) {
                Logger.debug(THIS_FILE,"UA receiver get callsList size =="+callsList.size());
                for (int i = 0; i < callsList.size(); i++) {
                    SipCallSessionImpl callInfo = callsList.valueAt(i);
                    if (callInfo != null) {
                        calls.add(callInfo);
                    }
                }
            }
            Logger.debug(THIS_FILE,"UA receiver get calls size =="+calls.size());
            return calls.toArray(new SipCallSessionImpl[calls.size()]);
        }
        return new SipCallSessionImpl[0];
    }

    public WorkerHandler msgHandler;
    public HandlerThread handlerThread;
    public WakeLock ongoingCallLock;
    public WakeLock eventLock;

    // private static final int ON_INCOMING_CALL = 1;
    public static final int ON_CALL_STATE = 2;
    public static final int ON_MEDIA_STATE = 3;

    // private static final int ON_REGISTRATION_STATE = 4;
    // private static final int ON_PAGER = 5;

    public static class WorkerHandler extends Handler {
        WeakReference<UAStateReceiver> sr;

        public WorkerHandler(Looper looper, UAStateReceiver stateReceiver) {
            super(looper);
            Logger.debug(THIS_FILE, "Create async worker !!!");
            sr = new WeakReference<UAStateReceiver>(stateReceiver);
        }

        public void handleMessage(Message msg) {
            Logger.debug(THIS_FILE, "Create async worker handleMessage  >>" +msg.what);
            UAStateReceiver stateReceiver = sr.get();
            if (stateReceiver == null) {
                return;
            }
            stateReceiver.lockCpu();
            switch (msg.what) {
                case ON_CALL_STATE: {
                    SipCallSessionImpl callInfo = (SipCallSessionImpl) msg.obj;
                    final int callState = callInfo.getCallState();

                    Logger.debug(THIS_FILE, "Create async worker handleMessage ON_CALL_STATE !!!" +callState);

                    switch (callState) {
                        case SipCallSessionImpl.InvState.INCOMING:
                        case SipCallSessionImpl.InvState.CALLING:
                            Logger.debug(THIS_FILE, "Create async worker handleMessage inside callState !!!");

                            stateReceiver.notificationManager.showNotificationForCall(callInfo);
                            stateReceiver.launchCallHandler(callInfo);
                            stateReceiver.broadCastAndroidCallState("RINGING",
                                    callInfo.getRemoteContact());
                            break;
                        case SipCallSessionImpl.InvState.EARLY:
                        case SipCallSessionImpl.InvState.CONNECTING:
                        case SipCallSessionImpl.InvState.CONFIRMED:
                            // As per issue #857 we should re-ensure
                            // notification + callHandler at each state
                            // cause we can miss some states due to the fact
                            // treatment of call state is threaded
                            // Anyway if we miss the call early + confirmed we
                            // do not need to show the UI.
                            stateReceiver.notificationManager.showNotificationForCall(callInfo);
                            stateReceiver.launchCallHandler(callInfo);
                            stateReceiver.broadCastAndroidCallState("OFFHOOK",
                                    callInfo.getRemoteContact());

                            if (stateReceiver.pjService.mediaManager != null) {
                                if (callState == SipCallSessionImpl.InvState.CONFIRMED) {
                                    // Don't unfocus here
                                    stateReceiver.pjService.mediaManager.stopRing();
                                }
                            }
                            // Auto send pending dtmf
                            if (callState == SipCallSessionImpl.InvState.CONFIRMED) {
                                stateReceiver.sendPendingDtmf(callInfo.getCallId());
                            }
                            // If state is confirmed and not already intialized
                            if (callState == SipCallSessionImpl.InvState.CONFIRMED
                                    && callInfo.getCallStart() == 0) {
                                callInfo.setCallStart(System.currentTimeMillis());
                            }
                            break;
                         case SipCallSessionImpl.InvState.DISCONNECTED:
                            if (stateReceiver.pjService.mediaManager != null && stateReceiver.getRingingCall() == null) {
                                stateReceiver.pjService.mediaManager.stopRing();
                            }

                            stateReceiver.broadCastAndroidCallState("IDLE",
                                    callInfo.getRemoteContact());

                            // If no remaining calls, cancel the notification
                            if (stateReceiver.getActiveCallInProgress() == null) {
                                stateReceiver.notificationManager.cancelCalls();
                                // We should now ask parent to stop if needed
                                if (stateReceiver.pjService != null
                                        && stateReceiver.pjService.service != null) {
                                    stateReceiver.pjService.service
                                            .treatDeferUnregistersForOutgoing();
                                }
                            }

                            // CallLog
                            ContentValues cv = CallLogHelper.logValuesForCall(
                                    stateReceiver.pjService.service, callInfo,
                                    callInfo.getCallStart());

                            // Fill our own database
                            stateReceiver.pjService.service.getContentResolver().insert(
                                    SipManager.CALLLOG_URI, cv);
                            Integer isNew = cv.getAsInteger(CallLog.Calls.NEW);
                            if (isNew != null && isNew == 1) {
                                stateReceiver.notificationManager.showNotificationForMissedCall(cv);
                            }

                            // If the call goes out in error...
                            if (callInfo.getLastStatusCode() != 200 && callInfo.getLastReasonCode() != 200) {
                                // We notify the user with toaster
                                stateReceiver.pjService.service.notifyUserOfMessage(callInfo
                                        .getLastStatusCode()
                                        + " / "
                                        + callInfo.getLastStatusComment());
                            }

                            // If needed fill native database
                            if (stateReceiver.mIntegrateWithCallLogs) {
                                // Don't add with new flag
                                cv.put(CallLog.Calls.NEW, false);
                                // Remove csipsimple custom entries
                                cv.remove(SipManager.CALLLOG_PROFILE_ID_FIELD);
                                cv.remove(SipManager.CALLLOG_STATUS_CODE_FIELD);
                                cv.remove(SipManager.CALLLOG_STATUS_TEXT_FIELD);

                                // Reformat number for callogs
                                ParsedSipContactInfos callerInfos = SipUri.parseSipContact(cv
                                        .getAsString(Calls.NUMBER));
                                if (callerInfos != null) {
                                    String phoneNumber = SipUri.getPhoneNumber(callerInfos);

                                    // Only log numbers that can be called by
                                    // GSM too.
                                    // TODO : if android 2.3 add sip uri also
                                    if (!TextUtils.isEmpty(phoneNumber)) {
                                        cv.put(Calls.NUMBER, phoneNumber);
                                        // For log in call logs => don't add as
                                        // new calls... we manage it ourselves.
                                        cv.put(Calls.NEW, false);
                                        ContentValues extraCv = new ContentValues();

                                        if (callInfo.getAccId() != SipProfile.INVALID_ID) {
                                            SipProfile acc = stateReceiver.pjService.service
                                                    .getAccount(callInfo.getAccId());
                                            if (acc != null && acc.display_name != null) {
                                                extraCv.put(CallLogHelper.EXTRA_SIP_PROVIDER,
                                                        acc.display_name);
                                            }
                                        }
                                        CallLogHelper.addCallLog(stateReceiver.pjService.service,
                                                cv, extraCv);
                                    }
                                }
                            }
                            callInfo.applyDisconnect();
                            break;
                        default:
                            break;
                    }
                    stateReceiver.onBroadcastCallState(callInfo);
                    break;
                }
                case ON_MEDIA_STATE: {
                    Logger.debug(THIS_FILE,"ON_MEDIA_STATE for a call");
                    SipCallSessionImpl mediaCallInfo = (SipCallSessionImpl) msg.obj;
                    SipCallSessionImpl callInfo = stateReceiver.callsList.get(mediaCallInfo
                            .getCallId());
                    callInfo.setMediaStatus(mediaCallInfo.getMediaStatus());
                    stateReceiver.callsList.put(mediaCallInfo.getCallId(), callInfo);
                    stateReceiver.onBroadcastCallState(callInfo);
                    break;
                }
            }
            stateReceiver.unlockCpu();
        }
    };

    // -------
    // Public configuration for receiver
    // -------

    public void initService(PjSipService srv) {
        pjService = srv;
        notificationManager = pjService.service.notificationManager;

        if (handlerThread == null) {
            handlerThread = new HandlerThread("UAStateAsyncWorker");
            handlerThread.start();
        }
        if (msgHandler == null) {
            msgHandler = new WorkerHandler(handlerThread.getLooper(), this);
        }

        if (eventLock == null) {
            PowerManager pman = (PowerManager) pjService.service.getSystemService(Context.POWER_SERVICE);
            eventLock = pman.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"com.csipsimple.inEventLock:");
            eventLock.setReferenceCounted(true);

        }
        if (ongoingCallLock == null) {
            PowerManager pman = (PowerManager) pjService.service
                    .getSystemService(Context.POWER_SERVICE);
            ongoingCallLock = pman.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"com.csipsimple.ongoingCallLock:");
            ongoingCallLock.setReferenceCounted(false);
        }
    }

    public void stopService() {

        Threading.stopHandlerThread(handlerThread, true);
        handlerThread = null;
        msgHandler = null;

        // Ensure lock is released since this lock is a ref counted one.
        if (eventLock != null) {
            while (eventLock.isHeld()) {
                eventLock.release();
            }
        }
        if (ongoingCallLock != null) {
            if (ongoingCallLock.isHeld()) {
                ongoingCallLock.release();
            }
        }
    }

    public void reconfigure(Context ctxt) {
        mIntegrateWithCallLogs = SipConfigManager.getPreferenceBooleanValue(ctxt,
                SipConfigManager.INTEGRATE_WITH_CALLLOGS);
        mPreferedHeadsetAction = SipConfigManager.getPreferenceIntegerValue(ctxt,
                SipConfigManager.HEADSET_ACTION, SipConfigManager.HEADSET_ACTION_CLEAR_CALL);
        mAutoRecordCalls = SipConfigManager.getPreferenceBooleanValue(ctxt,
                SipConfigManager.AUTO_RECORD_CALLS);
        mMicroSource = SipConfigManager.getPreferenceIntegerValue(ctxt,
                SipConfigManager.MICRO_SOURCE);
        mPlayWaittone = SipConfigManager.getPreferenceBooleanValue(ctxt,
                SipConfigManager.PLAY_WAITTONE_ON_HOLD, false);
    }

    // --------
    // Private methods
    // --------

    /**
     * Broadcast csipsimple intent about the fact we are currently have a sip
     * call state change.<br/>
     * This may be used by third party applications that wants to track
     * csipsimple call state
     *
     * @param callInfo the new call state infos
     */
    private void onBroadcastCallState(final SipCallSessionImpl callInfo) {
        Logger.debug(THIS_FILE, "onBroadcastCallState ");
        SipCallSessionImpl publicCallInfo = new SipCallSessionImpl(callInfo);
        Intent callStateChangedIntent = new Intent(SipManager.ACTION_SIP_CALL_CHANGED);
        Bundle bundle = new Bundle();
        bundle.putParcelable(SipManager.EXTRA_CALL_INFO, publicCallInfo);
        callStateChangedIntent.putExtra("EXTRA_CALL_INFO",bundle);
        pjService.service.sendBroadcast(callStateChangedIntent);

    }

    /**
     * Broadcast to android system that we currently have a phone call. This may
     * be managed by other sip apps that want to keep track of incoming calls
     * for example.
     *
     * @param state The state of the call
     * @param number The corresponding remote number
     */
    public void broadCastAndroidCallState(String state, String number) {
        // Android normalized event
        if(!Compatibility.isCompatible(19)) {
            // Not allowed to do that from kitkat
            Intent intent = new Intent(ACTION_PHONE_STATE_CHANGED);
            intent.putExtra(TelephonyManager.EXTRA_STATE, state);
            if (number != null) {
                intent.putExtra(TelephonyManager.EXTRA_INCOMING_NUMBER, number);
            }
            intent.putExtra(pjService.service.getString(R.string.app_name), true);
            pjService.service.sendBroadcast(intent, android.Manifest.permission.READ_PHONE_STATE);
        }
    }

    /**
     * Start the call activity for a given Sip Call Session. <br/>
     * The call activity should take care to get any ongoing calls when started
     * so the currentCallInfo2 parameter is indication only. <br/>
     * This method ensure that the start of the activity is not fired too much
     * in short delay and may just ignore requests if last time someone ask for
     * a launch is too recent
     *
     * @param currentCallInfo2 the call info that raise this request to open the
     *            call handler activity
     */
    public synchronized void launchCallHandler(SipCallSessionImpl currentCallInfo2) {
        long currentElapsedTime = SystemClock.elapsedRealtime();
        // Synchronized ensure we do not get this launched several time
        // We also ensure that a minimum delay has been consumed so that we do
        // not fire this too much times
        // Specially for EARLY - CONNECTING states
        if (lastLaunchCallHandler + LAUNCH_TRIGGER_DELAY < currentElapsedTime) {

            Context ctxt = pjService.service;

            // Launch activity to choose what to do with this call
            Intent callHandlerIntent = SipService.buildCallUiIntent(ctxt, currentCallInfo2);

            Logger.debug(THIS_FILE, "Anounce call activity");
            ctxt.startActivity(callHandlerIntent);
            lastLaunchCallHandler = currentElapsedTime;
        } else {
            Logger.debug(THIS_FILE, "Ignore extra launch handler");
        }
    }

    /**
     * Check if any of call infos indicate there is an active call in progress.
     *
     *
     */
    public SipCallSessionImpl getActiveCallInProgress() {
        // Go through the whole list of calls and find the first active state.
        synchronized (callsList) {
            for (int i = 0; i < callsList.size(); i++) {
                SipCallSessionImpl callInfo = callsList.valueAt(i);
                if (callInfo != null && callInfo.isActive()) {
                    return callInfo;
                }
            }
        }
        return null;
    }

    /**
     * Check if any of call infos indicate there is an active call in progress.
     *
     *
     */
    public SipCallSessionImpl getActiveCallOngoing() {
        // Go through the whole list of calls and find the first active state.
        synchronized (callsList) {
            for (int i = 0; i < callsList.size(); i++) {
                SipCallSessionImpl callInfo = callsList.valueAt(i);
                if (callInfo != null && callInfo.isActive() && callInfo.isOngoing()) {
                    return callInfo;
                }
            }
        }
        return null;
    }

    public SipCallSessionImpl getRingingCall() {
        // Go through the whole list of calls and find the first ringing state.
        synchronized (callsList) {
            for (int i = 0; i < callsList.size(); i++) {
                SipCallSessionImpl callInfo = callsList.valueAt(i);
                if (callInfo != null && callInfo.isActive() && callInfo.isBeforeConfirmed() && callInfo.isIncoming()) {
                    return callInfo;
                }
            }
        }
        return null;

    }

    /**
     * Broadcast the Headset button press event internally if there is any call
     * in progress. TODO : register and unregister only while in call
     */
    public boolean handleHeadsetButton() {
        final SipCallSessionImpl callInfo = getActiveCallInProgress();
        if (callInfo != null) {
            // Headset button has been pressed by user. If there is an
            // incoming call ringing the button will be used to answer the
            // call. If there is an ongoing call in progress the button will
            // be used to hangup the call or mute the microphone.
            int state = callInfo.getCallState();
            if (callInfo.isIncoming() &&
                    (state == SipCallSessionImpl.InvState.INCOMING ||
                            state == SipCallSessionImpl.InvState.EARLY)) {
                if (pjService != null && pjService.service != null) {
                    pjService.service.getExecutor().execute(new SipRunnable() {
                        @Override
                        protected void doRun() throws SameThreadException {

                         ///   pjService.callAnswer(callInfo.getCallId(),
                            //        pjsip_status_code.PJSIP_SC_OK.swigValue());
                        }
                    });
                }
                return true;
            } else if (state == SipCallSessionImpl.InvState.INCOMING ||
                    state == SipCallSessionImpl.InvState.EARLY ||
                    state == SipCallSessionImpl.InvState.CALLING ||
                    state == SipCallSessionImpl.InvState.CONFIRMED ||
                    state == SipCallSessionImpl.InvState.CONNECTING) {
                //
                // In the Android phone app using the media button during
                // a call mutes the microphone instead of terminating the call.
                // We check here if this should be the behavior here or if
                // the call should be cleared.
                //
                if (pjService != null && pjService.service != null) {
                    pjService.service.getExecutor().execute(new SipRunnable() {

                        @Override
                        protected void doRun() throws SameThreadException {
                            if (mPreferedHeadsetAction == SipConfigManager.HEADSET_ACTION_CLEAR_CALL) {
                               // pjService.callHangup(callInfo.getCallId(), 0);
                            } else if (mPreferedHeadsetAction == SipConfigManager.HEADSET_ACTION_HOLD) {
                             //   pjService.callHold(callInfo.getCallId());
                            } else if (mPreferedHeadsetAction == SipConfigManager.HEADSET_ACTION_MUTE) {
                                pjService.mediaManager.toggleMute();
                            }
                        }
                    });
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Update status of call recording info in call session info
     *
     * @param callId The call id to modify
     * @param canRecord if we can now record the call
     * @param isRecording if we are currently recording the call
     */
    public void updateRecordingStatus(int callId, boolean canRecord, boolean isRecording) {
        SipCallSessionImpl callInfo = getCallInfo(callId);
        callInfo.setCanRecord(canRecord);
        callInfo.setIsRecording(isRecording);
        synchronized (callsList) {
            // Re-add it just to be sure
            callsList.put(callId, callInfo);
        }
        onBroadcastCallState(callInfo);
    }

    private void sendPendingDtmf(final int callId) {
        pjService.service.getExecutor().execute(new SipRunnable() {
            @Override
            protected void doRun() throws SameThreadException {
                Thread.currentThread().setName("MyPendDTMF");
                pjService.sendPendingDtmf(callId);
            }
        });
    }

    public void deleteCall(int callId)
    {
        synchronized (callsList) {

            callsList.remove(callId);
        }

    }
    public void updateCallStatus(Integer callId,int callstate)
    {
        SipCallSessionImpl callInfo;
        Logger.debug(THIS_FILE, "updateCallStatus");
        synchronized (callsList) {
            callInfo = callsList.get(callId);
            if (callInfo != null) {
                callInfo.setCallState(callstate);
            }
        }
    }



    public void setIncomingCallType(Integer callId,boolean isIncoming)
    {
        SipCallSessionImpl callInfo;
        Logger.debug(THIS_FILE, "setIncomingCallType");
        synchronized (callsList) {
            callInfo = callsList.get(callId);
            if (callInfo != null) {
                callInfo.setIncoming(isIncoming);

            }
        }
     }

    public SipCallSessionImpl updateCallInfoFromStack(Integer callId, CallInfo currentCallinfo) {
        SipCallSessionImpl callInfo;
        Logger.debug(THIS_FILE, "Updating call infos from the stack");
        synchronized (callsList) {
            callInfo = callsList.get(callId);
            if (callInfo == null) {
                callInfo = new SipCallSessionImpl();
                callInfo.setCallId(callId);
                if(currentCallinfo !=null) {
                    callInfo.setCallInfo(currentCallinfo);
                }
            }
        }
        try {
            if (currentCallinfo != null) {
                for (int i = 0; i < currentCallinfo.getMedia().size(); i++) {

                    CallMediaInfo mediaInfo = currentCallinfo.getMedia().get(i);
                    if (mediaInfo.getType() == pjmedia_type.PJMEDIA_TYPE_AUDIO) {
                        callInfo.setMediaStatus(mediaInfo.getStatus());
                    }
                }
            }
            }catch(Exception e)
            {
                e.printStackTrace();
            }



        // We update session infos. callInfo is both in/out and will be updated
        callInfo=   updateSessionFromPj(callInfo, currentCallinfo,pjService.service);
        // We update from our current recording state
        callInfo.setIsRecording(pjService.isRecording(callId));
        callInfo.setCanRecord(pjService.canRecord(callId));

        synchronized (callsList) {
            // Re-add to list mainly for case newly added session
            callsList.put(callId, callInfo);
        }
        return callInfo;
    }
    public  SipCallSessionImpl updateSessionFromPj(SipCallSessionImpl session, CallInfo callInfo ,Context context) {
        try{
            Logger.debug(THIS_FILE, "updateSessionFromPj call " + session.getCallId());

            // Should be unecessary cause we usually copy infos from a valid
            session.setCallId(callInfo.getId());

            // Nothing to think about here cause we have a
            // bijection between int / state
            session.setCallState(callInfo.getState());
            session.setLastStatusCode(callInfo.getLastStatusCode());
            session.setRemoteContact(callInfo.getRemoteContact());


            // Try to retrieve sip account related to this call
            int pjAccId = callInfo.getAccId();
            session.setAccId(PjSipService.getAccountIdForPjsipId(context, pjAccId));

            TimeVal duration = callInfo.getConnectDuration();
            session.setConnectStart(SystemClock.elapsedRealtime() - duration.getSec() * 1000 - duration.getMsec());


            // Update state here because we have pjsip_event here and can get q.850 state
            int status_code = callInfo.getLastStatusCode();
            session.setLastStatusCode(status_code);
            Logger.debug(THIS_FILE, "Last status code is " + status_code);
            // TODO - get comment from q.850 state as well
            String status_text = callInfo.getLastReason();
            session.setLastStatusComment(status_text);

            // Reason code
            int reason_code = callInfo.getLastStatusCode();
            if (reason_code != 0) {
                session.setLastReasonCode(reason_code);
            }

        }catch(Exception e)
        {
            e.getStackTrace();
        }
        return session;
    }

    public  void fillRDataHeader(String hdrName, OnIncomingCallParam rdata, Bundle out)
            throws SameThreadException {
        String valueHdr =  rdata.getRdata().getPjRxData().toString();
        if (!TextUtils.isEmpty(valueHdr)) {
            out.putString(hdrName, valueHdr);
        }
    }

    public void updateCallMediaState(int callId) throws SameThreadException {
        SipCallSessionImpl callInfo = updateCallInfoFromStack(callId, null);
        msgHandler.sendMessage(msgHandler.obtainMessage(ON_MEDIA_STATE, callInfo));
    }
}
