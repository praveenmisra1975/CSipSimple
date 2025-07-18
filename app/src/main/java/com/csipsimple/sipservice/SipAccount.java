package com.csipsimple.sipservice;

import android.os.Bundle;
import android.telephony.TelephonyManager;

import com.csipsimple.api.SipProfile;
import com.csipsimple.models.CallerInfo;
import com.csipsimple.pjsip.PjSipAccount;
import com.csipsimple.service.MediaManager;
import com.csipsimple.service.SipService;
import com.csipsimple.api.SipCallSessionImpl;

import org.pjsip.pjsua2.Account;
import org.pjsip.pjsua2.CallOpParam;
import org.pjsip.pjsua2.OnIncomingCallParam;
import org.pjsip.pjsua2.OnRegStateParam;
import org.pjsip.pjsua2.pjsip_status_code;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper around PJSUA2 Account object.
 * @author gotev (Aleksandar Gotev)
 */
public class SipAccount extends Account {

    private static final String LOG_TAG = SipAccount.class.getSimpleName();

  //  private final HashMap<Integer, SipCall> activeCalls = new HashMap<>();

    private final PjSipAccount data;
    private final SipService service;
    private boolean isGuest = false;

    public SipAccount(SipService service, PjSipAccount data) {
        super();
        this.service = service;
        this.data = data;
    }

    public SipService getService() {
        return service;
    }

    public PjSipAccount getData() {
        return data;
    }


    public void create() throws Exception {
        create(data.getAccountConfig(),true);
    }

    public void modify() throws Exception {
        modify(data.getAccountConfig());
    }


    public void createGuest() throws Exception {
        isGuest = true;
        create(data.getGuestAccountConfig());
    }




    public SipCall addOutgoingCall(SipCall call ,final String numberToDial, boolean isVideo, boolean isVideoConference, boolean isTransfer) {
        Logger.debug(LOG_TAG, "In addOutgoingCall of Sip Account");

        if (service != null) {
            Logger.debug(LOG_TAG, "In addOutgoingCall of service");
           call= multiCallSupport(call ,service.supportMultipleCalls,numberToDial,isVideo, isVideoConference,  isTransfer);

        }
           return call;
    }
    public SipCall multiCallSupport(SipCall call ,boolean supportMultipleCalls,final String numberToDial, boolean isVideo, boolean isVideoConference, boolean isTransfer)
    {
        if (!supportMultipleCalls) {
            Logger.debug(LOG_TAG,
                    "Settings to not support two call at the same time !!!");

            if (isTransfer == false) {

                call.setVideoParams(isVideo, isVideoConference);

                CallOpParam callOpParam = new CallOpParam();
                try {
                    if (numberToDial.startsWith("sip:")) {
                        call.makeCall(numberToDial, callOpParam);
                    } else {
                        if ("*".equals(data.getRealm())) {
                            call.makeCall("sip:" + numberToDial, callOpParam);
                        } else {
                            call.makeCall("sip:" + numberToDial + "@" + data.getRealm(), callOpParam);
                        }
                    }

                 //   service.getUAStateReceiver().updateCallInfoFromStack(call.getId(),call.getInfo());

                } catch (Exception exc) {
                    Logger.error(LOG_TAG, "Error while making outgoing call", exc);
                    return null;
                }

            }

        }
        return call;

    }

    public void addOutgoingCall(SipCall call,final String numberToDial) {
        addOutgoingCall(call,numberToDial, false, false, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SipAccount that = (SipAccount) o;

        return data.equals(that.data);

    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }

    @Override
    public void onRegState(OnRegStateParam prm) {
        Logger.info(LOG_TAG, "Sip Reg Info - Code: " + prm.getCode() +
                ", Reason: " + prm.getReason() + ", Exp: " + prm.getExpiration() + ", Status: " + prm.getStatus()
        );


        if(prm.getCode() > -1) {
            service.getBroadcastEmitter().registrationState(data.getIdUri(), prm.getCode());
            //praveen
            service.updateProfileStateFromService((data.id).intValue(), data.pjprofile.reg_uri,prm);
            service.updateRegistrationsState();
        }
    }


    @Override
    public void onIncomingCall(OnIncomingCallParam prm)  {

        if (service != null) {
                SipCall onCall = new SipCall(this, prm.getCallId());

        try {
            service.getUAStateReceiver().setIncomingCallType(onCall.getId(), true);
            SipCallSessionImpl sipCallSessionImpl = service.getUAStateReceiver().updateCallInfoFromStack(onCall.getId(), onCall.getInfo());
            Logger.debug(LOG_TAG, "Added incoming call with ID " + onCall.getId() + " to " +  onCall.getInfo().getAccId());

            // Send 603 Decline if in DND mode
            if (service.isDND()) {

                CallerInfo contactInfo = new CallerInfo(onCall.getInfo());
                service.getBroadcastEmitter().missedCall(contactInfo.getDisplayName(), contactInfo.getRemoteUri());
                onCall.declineIncomingCall();
                service.getUAStateReceiver().updateCallStatus(onCall.getId(), pjsip_status_code.PJSIP_SC_DECLINE);
                service.getUAStateReceiver().deleteCall(onCall.getId());
                Logger.debug(LOG_TAG, "DND - Decline call with ID: " + onCall.getId());

            } else {
                // Send 486 Busy Here if there's an already ongoing call

                boolean hasOngoingSipCall = false;

                    SipCallSessionImpl[] calls = getCalls();
                    if (calls != null) {
                        for (SipCallSessionImpl existingCall : calls) {
                            if (!existingCall.isAfterEnded() && existingCall.getCallId() != prm.getCallId()) {
                                if (!service.supportMultipleCalls) {
                                    Logger.debug(LOG_TAG,
                                            "Settings to not support two call at the same time !!!");
                                    // If there is an ongoing call and we do not support
                                    // multiple calls
                                    // Send busy here
                                    try {
                                        CallerInfo contactInfo = new CallerInfo(onCall.getInfo());
                                        service.getBroadcastEmitter().missedCall(contactInfo.getDisplayName(), contactInfo.getRemoteUri());
                                        onCall.sendBusyHereToIncomingCall();
                                        service.getUAStateReceiver().updateCallStatus(onCall.getId(), pjsip_status_code.PJSIP_SC_BUSY_HERE);
                                        service.getUAStateReceiver().deleteCall(onCall.getId());
                                        Logger.debug(LOG_TAG, "Sending busy to call ID: " + prm.getCallId());

                                    } catch (Exception ex) {
                                        Logger.error(LOG_TAG, "Error while getting missed call info", ex);
                                    }

                                    return;
                                } else {
                                    hasOngoingSipCall = true;
                                }
                            }
                        }
                    } else {
                        SipCallSessionImpl callInfo = service.getUAStateReceiver().updateCallInfoFromStack(onCall.getId(), onCall.getInfo());

                        Logger.debug(LOG_TAG, "Incoming call << for account " + prm.getCallId());

                        final String remContact = callInfo.getRemoteContact();
                        service.notificationManager.showNotificationForCall(callInfo);
                          // Ring and inform remote about ringing with 180/RINGING

                        if (service.getUAStateReceiver().pjService.mediaManager != null) {
                            if (service.getUAStateReceiver().pjService.service.getGSMCallState() == TelephonyManager.CALL_STATE_IDLE
                                        && !hasOngoingSipCall) {
                                    service.getUAStateReceiver().pjService.mediaManager.startRing(remContact);
                            } else {
                                    service.getUAStateReceiver().pjService.mediaManager.playInCallTone(MediaManager.TONE_CALL_WAITING);
                                }
                            }
                            service.getUAStateReceiver().broadCastAndroidCallState("RINGING", remContact);
                            // Or by api
                            service.getUAStateReceiver().launchCallHandler(callInfo);
                            Logger.debug(LOG_TAG, "Incoming call >>");
                    }
                }
            } catch(Exception ex){
                ex.getStackTrace();
                // That's fine we are in a pjsip thread
            }
        }

    }

/// ////////////////////

public SipCallSessionImpl[] getCalls() {
    if (service.getUAStateReceiver().callsList != null) {
        List<SipCallSessionImpl> calls = new ArrayList<SipCallSessionImpl>();

        synchronized (service.getUAStateReceiver().callsList) {
            for (int i = 0; i < service.getUAStateReceiver().callsList.size(); i++) {
                SipCallSessionImpl callInfo = getCallInfo(i);
                if (callInfo != null) {
                    calls.add(callInfo);
                }
            }
        }
        return calls.toArray(new SipCallSessionImpl[calls.size()]);
    }
    return new SipCallSessionImpl[0];
}
    public SipCallSessionImpl getCallInfo(Integer callId) {
        SipCallSessionImpl callInfo;
        synchronized (service.getUAStateReceiver().callsList) {
            callInfo = service.getUAStateReceiver().callsList.get(callId);
        }
        return callInfo;

    }


}
