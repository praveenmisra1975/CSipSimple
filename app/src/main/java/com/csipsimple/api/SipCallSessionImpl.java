/**
 * Copyright (C) 2010-2012 Regis Montoya (aka r3gis - www.r3gis.fr)
 * This file is part of CSipSimple.
 *
 *  CSipSimple is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  If you own a pjsip commercial license you can also redistribute it
 *  and/or modify it under the terms of the GNU Lesser General Public License
 *  as an android library.
 *
 *  CSipSimple is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with CSipSimple.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.csipsimple.api;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

import com.csipsimple.sipservice.SipCall;

import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallMediaInfoVector;

public class SipCallSessionImpl implements Parcelable {


    public CallInfo getCallInfo() {
        return callInfo;
    }

    public void setCallInfo(CallInfo callInfo) {
        this.callInfo = callInfo;
    }

    public CallInfo callInfo;
    /**
     * Set the call id of this serializable holder
     * 
     * @param callId2 the call id to setup
     */
    public void setCallId(int callId2) {
        callId = callId2;
    }

    /**
     * @param callStart the callStart to set
     */
    public void setCallStart(long callStart) {
        this.callStart = callStart;
    }

    /**
     * @param callState the new invitation state
     * @see InvState
     */
    public void setCallState(int callState) {
        this.callState = callState;
        ;
    }

    /**
     * Set the account id for this call of this serializable holder
     * 
     * @param accId2 The {@link SipProfile#id} of the account use for this call
     * @see #getAccId()
     */
    public void setAccId(long accId2) {
        accId = accId2;
    }
    
    /**
     * Set the signaling secure transport level.
     * @param transportSecure2
     */
    public void setSignalisationSecure(int transportSecure2) {
        transportSecure = transportSecure2;
    }

    /**
     * Set the media security level for this call of this serializable holder
     * 
     * @param mediaSecure2 true if the call has a <b>media</b> encrypted
     * @see #isMediaSecure()
     */
    public void setMediaSecure(boolean mediaSecure2) {
        mediaSecure = mediaSecure2;
    }

    /**
     * Set the media security info for this call of this serializable holder
     * 
     * @param aInfo the information about the <b>media</b> security
     * @see #getMediaSecureInfo()
     */
    public void setMediaSecureInfo(String aInfo) {
        mediaSecureInfo = aInfo;
    }

    /**
     * Set the latest status code for this call of this serializable holder
     * 
     * @param status_code The code of the latest known sip dialog
     * @see #getLastStatusCode()
     * @see StatusCode
     */
    public void setLastStatusCode(int status_code) {
        lastStatusCode = status_code;
    }

    /**
     * Set the last status comment for this call
     * 
     * @param lastStatusComment the lastStatusComment to set
     */
    public void setLastStatusComment(String lastStatusComment) {
        this.lastStatusComment = lastStatusComment;
    }
    
    /**
     * Set the last status comment for this call
     * 
     * @param lastReasonCode the lastReasonCode to set
     */
    public void setLastReasonCode(int lastReasonCode) {
        this.lastReasonCode = lastReasonCode;
    }

    /**
     * Set the remote contact of this serializable holder
     * 
     * @param remoteContact2 the new remote contact representation string
     * @see #getRemoteContact()
     */
    public void setRemoteContact(String remoteContact2) {
        remoteContact = remoteContact2;
    }

    /**
     * Set the fact that this call was initiated by the remote party
     * 
     * @param isIncoming the isIncoming to set
     * @see #isIncoming()
     */
    public void setIncoming(boolean isIncoming) {
        this.isIncoming = isIncoming;
    }

    /**
     * Set the time of the beginning of the call as a connected call
     * 
     * @param connectStart2 the new connected start time for this call
     * @see #getConnectStart()
     */
    public void setConnectStart(long connectStart2) {
        connectStart = connectStart2;
    }

    /**
     * Set the conf port of this serializable holder
     * 
     * @param confPort2
     * @see #getConfPort()
     */
    public void setConfPort(int confPort2) {
        confPort = confPort2;
    }

    /**
     * Set the media video stream flag <br/>
     * 
     * @param mediaHasVideo pass true if the media of the underlying call has a
     *            video stream
     */
    public void setMediaHasVideo(boolean mediaHasVideo) {
        this.mediaHasVideoStream = mediaHasVideo;
    }

    /**
     * Set the can record flag <br/>
     * 
     * @param canRecord pass true if the audio can be recorded
     */
    public void setCanRecord(boolean canRecord) {
        this.canRecord = canRecord;
    }
    
    /**
     * Set the is record flag <br/>
     * 
     * @param isRecording pass true if the audio is currently recording
     */
    public void setIsRecording(boolean isRecording) {
        this.isRecording = isRecording;
    }

    /**
     * @param zrtpSASVerified the zrtpSASVerified to set
     */
    public void setZrtpSASVerified(boolean zrtpSASVerified) {
        this.zrtpSASVerified = zrtpSASVerified;
    }

    /**
     * @param hasZrtp the hasZrtp to set
     */
    public void setHasZrtp(boolean hasZrtp) {
        this.hasZrtp = hasZrtp;
    }

    /**
     * Set the sip media state of this serializable holder
     * 
     * @param mediaStatus2 the new media status
     */
    public void setMediaStatus(int mediaStatus2) {
        mediaStatus = mediaStatus2;
    }

    public void applyDisconnect() {
        isIncoming = false;
        mediaStatus = MediaState.NONE;
        mediaSecure = false;
        mediaHasVideoStream = false;
        callStart = 0;
        mediaSecureInfo = "";
        canRecord = false;
        isRecording = false;
        zrtpSASVerified = false;
        hasZrtp = false;
    }

    /**
     * Describe the control state of a call <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/group__PJSIP__INV.htm#ga083ffd9c75c406c41f113479cc1ebc1c"
     * >Pjsip documentation</a>
     */
    public static class InvState {
        /**
         * The call is in an invalid state not syncrhonized with sip stack
         */
        public static final int INVALID = -1;
        /**
         * Before INVITE is sent or received
         */
        public static final int NULL = 0;
        /**
         * After INVITE is sent
         */
        public static final int CALLING = 1;
        /**
         * After INVITE is received.
         */
        public static final int INCOMING = 2;
        /**
         * After response with To tag.
         */
        public static final int EARLY = 3;
        /**
         * After 2xx is sent/received.
         */
        public static final int CONNECTING = 4;
        /**
         * After ACK is sent/received.
         */
        public static final int CONFIRMED = 5;
        /**
         * Session is terminated.
         */
        public static final int DISCONNECTED = 6;

        // Should not be constructed, just an older for int values
        // Not an enum because easier to pass to Parcelable
        private InvState() {
        }
    }

    /**
     * Option key to flag video use for the call. <br/>
     * The value must be a boolean.
     *
     * @see Boolean
     */
    public static final String OPT_CALL_VIDEO = "opt_call_video";
    /**
     * Option key to add custom headers (with X- prefix). <br/>
     * The value must be a bundle with key representing header name, and value representing header value.
     *
     * @see Bundle
     */
    public static final String OPT_CALL_EXTRA_HEADERS = "opt_call_extra_headers";

    /**
     * Describe the media state of the call <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/group__PJSUA__LIB__CALL.htm#ga0608027241a5462d9f2736e3a6b8e3f4"
     * >Pjsip documentation</a>
     */
    public static class MediaState {
        /**
         * Call currently has no media
         */
        public static final int NONE = 0;
        /**
         * The media is active
         */
        public static final int ACTIVE = 1;
        /**
         * The media is currently put on hold by local endpoint
         */
        public static final int LOCAL_HOLD = 2;
        /**
         * The media is currently put on hold by remote endpoint
         */
        public static final int REMOTE_HOLD = 3;
        /**
         * The media has reported error (e.g. ICE negotiation)
         */
        public static final int ERROR = 4;

        // Should not be constructed, just an older for int values
        // Not an enum because easier to pass to Parcelable
        private MediaState() {
        }
    }

    /**
     * Status code of the sip call dialog Actually just shortcuts to SIP codes<br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/group__PJSIP__MSG__LINE.htm#gaf6d60351ee68ca0c87358db2e59b9376"
     * >Pjsip documentation</a>
     */
    public static class StatusCode {
        public static final int TRYING = 100;
        public static final int RINGING = 180;
        public static final int CALL_BEING_FORWARDED = 181;
        public static final int QUEUED = 182;
        public static final int PROGRESS = 183;
        public static final int OK = 200;
        public static final int ACCEPTED = 202;
        public static final int MULTIPLE_CHOICES = 300;
        public static final int MOVED_PERMANENTLY = 301;
        public static final int MOVED_TEMPORARILY = 302;
        public static final int USE_PROXY = 305;
        public static final int ALTERNATIVE_SERVICE = 380;
        public static final int BAD_REQUEST = 400;
        public static final int UNAUTHORIZED = 401;
        public static final int PAYMENT_REQUIRED = 402;
        public static final int FORBIDDEN = 403;
        public static final int NOT_FOUND = 404;
        public static final int METHOD_NOT_ALLOWED = 405;
        public static final int NOT_ACCEPTABLE = 406;
        public static final int INTERVAL_TOO_BRIEF = 423;
        public static final int BUSY_HERE = 486;
        public static final int INTERNAL_SERVER_ERROR = 500;
        public static final int DECLINE = 603;
    }

    /**
     * The call signaling is not secure
     */
    public static int TRANSPORT_SECURE_NONE = 0;
    /**
     * The call signaling is secure until it arrives on server. After, nothing ensures how it goes.
     */
    public static int TRANSPORT_SECURE_TO_SERVER = 1;
    /**
     * The call signaling is supposed to be secured end to end.
     */
    public static int TRANSPORT_SECURE_FULL = 2;


    /**
     * Id of an invalid or not existant call
     */
    public static final int INVALID_CALL_ID = -1;

    /**
     * Primary key for the parcelable object
     */
    public int primaryKey = -1;
    /**
     * The starting time of the call
     */
    protected long callStart = 0;

    protected int callId = INVALID_CALL_ID;
    protected int callState = SipCallSessionImpl.InvState.INVALID;
    protected String remoteContact;
    protected boolean isIncoming;
    protected int confPort = -1;
    protected long accId = SipProfile.INVALID_ID;
    protected int mediaStatus = SipCallSessionImpl.MediaState.NONE;
    protected boolean mediaSecure = false;
    protected int transportSecure = 0;
    protected boolean mediaHasVideoStream = false;
    protected long connectStart = 0;
    protected int lastStatusCode = 0;
    protected String lastStatusComment = "";
    protected int lastReasonCode = 0;
    protected String mediaSecureInfo = "";
    protected boolean canRecord = false;
    protected boolean isRecording = false;
    protected boolean zrtpSASVerified = false;
    protected boolean hasZrtp = false;

    /**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     *
     * @param in parcelable to build from
     */
    private SipCallSessionImpl(Parcel in) {
        initFromParcel(in);
    }

    /**
     * Constructor for a sip call session state object <br/>
     * It will contains default values for all flags This class as no
     * setter/getter for members flags <br/>
     * It's aim is to allow to serialize/deserialize easily the state of a sip
     * call, <n>not to modify it</b>
     */
    public SipCallSessionImpl() {
        // Nothing to do in default constructor
    }

    /**
     * Constructor by copy
     * @param callInfo
     */
    public SipCallSessionImpl(SipCallSessionImpl callInfo) {
        Parcel p = Parcel.obtain();
        callInfo.writeToParcel(p, 0);
        p.setDataPosition(0);
        initFromParcel(p);
        p.recycle();
    }

    private void initFromParcel(Parcel in) {
        primaryKey = in.readInt();
        callId = in.readInt();
        callState = in.readInt();
        mediaStatus = in.readInt();
        remoteContact = in.readString();
        isIncoming = (in.readInt() == 1);
        confPort = in.readInt();
        accId = in.readInt();
        lastStatusCode = in.readInt();
        mediaSecureInfo = in.readString();
        connectStart = in.readLong();
        mediaSecure = (in.readInt() == 1);
        lastStatusComment = in.readString();
        mediaHasVideoStream = (in.readInt() == 1);
        canRecord = (in.readInt() == 1);
        isRecording = (in.readInt() == 1);
        hasZrtp = (in.readInt() == 1);
        zrtpSASVerified = (in.readInt() == 1);
        transportSecure = (in.readInt());
        lastReasonCode = in.readInt();
    }

    /**
     * @see Parcelable#describeContents()
     */
    @Override
    public int describeContents() {
        return 0;
    }


    /**
     * @see Parcelable#writeToParcel(Parcel, int)
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(primaryKey);
        dest.writeInt(callId);
        dest.writeInt(callState);
        dest.writeInt(mediaStatus);
        dest.writeString(remoteContact);
        dest.writeInt(isIncoming() ? 1 : 0);
        dest.writeInt(confPort);
        dest.writeInt((int) accId);
        dest.writeInt(lastStatusCode);
        dest.writeString(mediaSecureInfo);
        dest.writeLong(connectStart);
        dest.writeInt(mediaSecure ? 1 : 0);
        dest.writeString(getLastStatusComment());
        dest.writeInt(mediaHasVideo() ? 1 : 0);
        dest.writeInt(canRecord ? 1 : 0);
        dest.writeInt(isRecording ? 1 : 0);
        dest.writeInt(hasZrtp ? 1 : 0);
        dest.writeInt(zrtpSASVerified ? 1 : 0);
        dest.writeInt(transportSecure);
        dest.writeInt(lastReasonCode);
    }

    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface
     */
    public static final Parcelable.Creator<SipCallSessionImpl> CREATOR = new Parcelable.Creator<SipCallSessionImpl>() {
        public SipCallSessionImpl createFromParcel(Parcel in) {
            return new SipCallSessionImpl(in);
        }

        public SipCallSessionImpl[] newArray(int size) {
            return new SipCallSessionImpl[size];
        }
    };




    /**
     * A sip call session is equal to another if both means the same callId
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SipCallSessionImpl)) {
            return false;
        }
        SipCallSessionImpl ci = (SipCallSessionImpl) o;
        if (ci.getCallId() == callId) {
            return true;
        }
        return false;
    }

    // Getters / Setters
    /**
     * Get the call id of this call info
     *
     * @return id of this call
     */
    public int getCallId() {
        return callId;
    }

    /**
     * Get the call state of this call info
     *
     * @return the invitation state
     *
     */
    public int getCallState() {
        return callState;
    }

    public int getMediaStatus() {
        return mediaStatus;
    }

    /**
     * Get the remote Contact for this call info
     *
     * @return string representing the remote contact
     */
    public String getRemoteContact() {
        return remoteContact;
    }

    /**
     * Get the call way
     *
     * @return true if the remote party was the caller
     */
    public boolean isIncoming() {
        return isIncoming;
    }

    /**
     * Get the start time of the connection of the call
     *
     * @return duration in milliseconds
     * @see SystemClock#elapsedRealtime()
     */
    public long getConnectStart() {
        return connectStart;
    }

    /**
     * Check if the call state indicates that it is an active call in
     * progress.
     * This is equivalent to state incoming or early or calling or confirmed or connecting
     *
     * @return true if the call can be considered as in progress/active
     */
    public boolean isActive() {
        return (callState == SipCallSessionImpl.InvState.INCOMING || callState == SipCallSessionImpl.InvState.EARLY ||
                callState == SipCallSessionImpl.InvState.CALLING || callState == SipCallSessionImpl.InvState.CONFIRMED || callState == SipCallSessionImpl.InvState.CONNECTING);
    }

    /**
     * Chef if the call state indicates that it's an ongoing call.
     * This is equivalent to state confirmed.
     * @return true if the call can be considered as ongoing.
     */
    public boolean isOngoing() {
        return callState == SipCallSessionImpl.InvState.CONFIRMED;
    }

    /**
     * Get the sounds conference board port <br/>
     * <a target="_blank" href=
     * "http://www.pjsip.org/pjsip/docs/html/group__PJSUA__LIB__BASE.htm#gaf5d44947e4e62dc31dfde88884534385"
     * >Pjsip documentation</a>
     *
     * @return the conf port of the audio media of this call
     */
    public int getConfPort() {
        return confPort;
    }

    /**
     * Get the identifier of the account corresponding to this call <br/>
     * This identifier is the one you have in {@link SipProfile#id} <br/>
     * It may return {@link SipProfile#INVALID_ID} if no account detected for
     * this call. <i>Example, case of peer to peer call</i>
     *
     * @return The {@link SipProfile#id} of the account use for this call
     */
    public long getAccId() {
        return accId;
    }

    /**
     * Get the secure level of the signaling of the call.
     *
     * @return one of {@link #TRANSPORT_SECURE_NONE}, {@link #TRANSPORT_SECURE_TO_SERVER}, {@link #TRANSPORT_SECURE_FULL}
     */
    public int getTransportSecureLevel() {
        return transportSecure;
    }

    /**
     * Get the secure level of the media of the call
     *
     * @return true if the call has a <b>media</b> encrypted
     */
    public boolean isMediaSecure() {
        return mediaSecure;
    }

    /**
     * Get the information about the <b>media</b> security of this call
     *
     * @return the information about the <b>media</b> security
     */
    public String getMediaSecureInfo() {
        return mediaSecureInfo;
    }

    /**
     * Get the information about local held state of this call
     *
     * @return the information about local held state of media
     */
    public boolean isLocalHeld() {
        return mediaStatus == SipCallSessionImpl.MediaState.LOCAL_HOLD;
    }

    /**
     * Get the information about remote held state of this call
     *
     * @return the information about remote held state of media
     */
    public boolean isRemoteHeld() {
        return (mediaStatus == SipCallSessionImpl.MediaState.NONE && isActive() && !isBeforeConfirmed());
    }

    /**
     * Check if the specific call info indicates that it is a call that has not yet been confirmed by both ends.<br/>
     * In other worlds if the call is in state, calling, incoming early or connecting.
     *
     * @return true if the call can be considered not yet been confirmed
     */
    public boolean isBeforeConfirmed() {
        return (callState == SipCallSessionImpl.InvState.CALLING || callState == SipCallSessionImpl.InvState.INCOMING
                || callState == SipCallSessionImpl.InvState.EARLY || callState == SipCallSessionImpl.InvState.CONNECTING);
    }


    /**
     * Check if the specific call info indicates that it is a call that has been ended<br/>
     * In other worlds if the call is in state, disconnected, invalid or null
     *
     * @return true if the call can be considered as already ended
     */
    public boolean isAfterEnded() {
        return (callState == SipCallSessionImpl.InvState.DISCONNECTED || callState == SipCallSessionImpl.InvState.INVALID || callState == SipCallSessionImpl.InvState.NULL);
    }

    /**
     * Get the latest status code of the sip dialog corresponding to this call
     * call
     *
     * @return the status code
     */
    public int getLastStatusCode() {
        return lastStatusCode;
    }

    /**
     * Get the last status comment of the sip dialog corresponding to this call
     *
     * @return the last status comment string from server
     */
    public String getLastStatusComment() {
        return lastStatusComment;
    }

    /**
     * Get the latest SIP reason code if any.
     * For now only supports 200 (if SIP reason is set to 200) or 0 in other cases (no SIP reason / sip reason set to something different).
     *
     * @return the status code
     */
    public int getLastReasonCode() {
        return lastReasonCode;
    }

    /**
     * Get whether the call has a video media stream connected
     *
     * @return true if the call has a video media stream
     */
    public boolean mediaHasVideo() {
        return mediaHasVideoStream;
    }

    /**
     * Get the current call recording status for this call.
     *
     * @return true if we are currently recording this call to a file
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Get the capability to record the call to a file.
     *
     * @return true if it should be possible to record the call to a file
     */
    public boolean canRecord() {
        return canRecord;
    }

    /**
     * @return the zrtpSASVerified
     */
    public boolean isZrtpSASVerified() {
        return zrtpSASVerified;
    }

    /**
     * @return whether call has Zrtp encryption active
     */
    public boolean getHasZrtp() {
        return hasZrtp;
    }

    /**
     * Get the start time of the call.
     * @return the callStart start time of the call.
     */
    public long getCallStart() {
        return callStart;
    }

}
