
package com.csipsimple.pjsip;


import static com.csipsimple.service.SipService.mActiveSipAccounts;
import static com.csipsimple.sipservice.SipServiceConstants.BACK_CAMERA_CAPTURE_DEVICE;
import static com.csipsimple.sipservice.SipServiceConstants.FRONT_CAMERA_CAPTURE_DEVICE;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Surface;

import com.csipsimple.R;
import com.csipsimple.api.SipConfigManager;
import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipProfile;
import com.csipsimple.api.SipProfileState;
import com.csipsimple.api.SipUri;

import com.csipsimple.models.CallerInfo;
import com.csipsimple.pjsip.player.IPlayerHandler;
import com.csipsimple.pjsip.player.impl.SimpleWavPlayerHandler;
import com.csipsimple.pjsip.recorder.IRecorderHandler;
import com.csipsimple.pjsip.recorder.impl.SimpleWavRecorderHandler;

import com.csipsimple.service.MediaManager;
import com.csipsimple.service.SipService;
import com.csipsimple.service.SipService.ToCall;
import com.csipsimple.service.SipService.SameThreadException;
import com.csipsimple.api.SipCallSessionImpl;
import com.csipsimple.sipservice.CallReconnectionState;
import com.csipsimple.sipservice.MediaState;
import com.csipsimple.sipservice.SipAccount;
import com.csipsimple.sipservice.SipCall;
import com.csipsimple.utils.PreferencesProviderWrapper;
import com.csipsimple.utils.TimerWrapper;


import com.csipsimple.sipservice.BroadcastEventEmitter;
import com.csipsimple.sipservice.CodecPriority;
import com.csipsimple.sipservice.Logger;
import com.csipsimple.sipservice.SipEndpoint;
import com.csipsimple.sipservice.SipServiceUtils;
import com.csipsimple.sipservice.SipTlsUtils;

import org.pjsip.pjsua2.AudDevManager;
import org.pjsip.pjsua2.Call;
import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallOpParam;
import org.pjsip.pjsua2.CallSendDtmfParam;
import org.pjsip.pjsua2.CallSetting;
import org.pjsip.pjsua2.CallVidSetStreamParam;
import org.pjsip.pjsua2.CodecInfo;
import org.pjsip.pjsua2.CodecInfoVector2;
import org.pjsip.pjsua2.EpConfig;
import org.pjsip.pjsua2.IpChangeParam;
import org.pjsip.pjsua2.TransportConfig;
import org.pjsip.pjsua2.VidDevManager;
import org.pjsip.pjsua2.pj_constants_;
import org.pjsip.pjsua2.pj_qos_type;
import org.pjsip.pjsua2.pjmedia_orient;
import org.pjsip.pjsua2.pjmedia_srtp_use;
import org.pjsip.pjsua2.pjsip_inv_state;
import org.pjsip.pjsua2.pjsip_status_code;
import org.pjsip.pjsua2.pjsip_transport_type_e;
import org.pjsip.pjsua2.pjsua_call_flag;
import org.pjsip.pjsua2.pjsua_call_vid_strm_op;
import org.pjsip.pjsua2.pjsua_destroy_flag;
import org.pjsip.pjsua2.pjsua_dtmf_method;
import org.pjsip.pjsua2.pjsua_vid_req_keyframe_method;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class PjSipService {
    private static final String TAG = com.csipsimple.pjsip.PjSipService.class.getSimpleName();

    private static final String THIS_FILE = TAG;
    private static int DTMF_TONE_PAUSE_LENGTH = 300;
    private static int DTMF_TONE_WAIT_LENGTH = 2000;
    public SipService service;

    private boolean created = false;

    private boolean hasSipStack = false;
    private boolean sipStackIsCorrupted = false;
    private Integer localUdpAccPjId, localUdp6AccPjId,
            localTcpAccPjId, localTcp6AccPjId,
            localTlsAccPjId, localTls6AccPjId;
    public PreferencesProviderWrapper prefsWrapper;

    private Integer hasBeenHoldByGSM = null;
    private Integer hasBeenChangedRingerMode = null;

    public UAStateReceiver userAgentReceiver;
    //public ZrtpStateReceiver zrtpReceiver;
    public MediaManager mediaManager;

    private Timer tasksTimer;
    private SparseArray<String> dtmfToAutoSend = new SparseArray<String>(5);
    private SparseArray<TimerTask> dtmfTasks = new SparseArray<TimerTask>(5);
    private SparseArray<PjStreamDialtoneGenerator> dtmfDialtoneGenerators = new SparseArray<PjStreamDialtoneGenerator>(5);
    private SparseArray<PjStreamDialtoneGenerator> waittoneGenerators = new SparseArray<PjStreamDialtoneGenerator>(5);
    private String mNatDetected = "";

    // -------
    // Locks
    // -------

    public volatile boolean mStarted;
    public SipEndpoint mEndpoint;

    private BroadcastEventEmitter mBroadcastEmitter;

    private int callStatus;

    public PjSipService() {

    }


    public void setService(SipService aService) {
        service = aService;
        prefsWrapper = service.getPrefs();


    }

    public void checkThread() {
        try {
            if (mEndpoint != null && !mEndpoint.libIsThreadRegistered()) {
                mEndpoint.libRegisterThread(Thread.currentThread().getName());
                Logger.debug(THIS_FILE, ": libRegisterThread done: ");
            }
        } catch (Exception e) {
            Logger.debug(THIS_FILE, "Threading: libRegisterThread failed: " + e.getMessage());
        }
    }


    public boolean isCreated() {
        return created;
    }

    public boolean tryToLoadStack() {
        if (hasSipStack) {
            return true;
        }

        // File stackFile = NativeLibManager.getStackLibFile(service);
        if (!sipStackIsCorrupted) {
            try {
                loadNativeLibraries();
                hasSipStack = true;
                return true;
            } catch (UnsatisfiedLinkError e) {
                // If it fails we probably are running on a special hardware
                Logger.debug(THIS_FILE,
                        "We have a problem with the current stack.... NOT YET Implemented");
                hasSipStack = false;
                sipStackIsCorrupted = true;

                service.notifyUserOfMessage("Can't load native library. CPU arch invalid for this build");
                return false;
            } catch (Exception e) {
                Logger.debug(THIS_FILE, "We have a problem with the current stack....");
            }
        }
        return false;
    }

    private void loadNativeLibraries() {
        try {
            System.loadLibrary("c++_shared");
            Logger.debug(TAG, "libc++_shared loaded");
        } catch (UnsatisfiedLinkError error) {
            Logger.error(TAG, "Error while loading libc++_shared native library", error);
            throw new RuntimeException(error);
        }

        try {
            System.loadLibrary("openh264");
            Logger.debug(TAG, "OpenH264 loaded");
        } catch (UnsatisfiedLinkError error) {
            Logger.error(TAG, "Error while loading OpenH264 native library", error);
            throw new RuntimeException(error);
        }

        try {
            System.loadLibrary("pjsua2");
            Logger.debug(TAG, "PJSIP pjsua2 loaded");
        } catch (UnsatisfiedLinkError error) {
            Logger.error(TAG, "Error while loading PJSIP pjsua2 native library", error);
            throw new RuntimeException(error);
        }
    }


    // Start the sip stack according to current settings

    /**
     * Start the sip stack Thread safing of this method must be ensured by upper
     * layer Every calls from pjsip that require start/stop/getInfos from the
     * underlying stack must be done on the same thread
     */
    public boolean sipStart()  {


        if (!hasSipStack) {
            Logger.debug(THIS_FILE, "We have no sip stack, we can't start");
            return false;
        }

        // Ensure the stack is not already created or is being created
        if (!created) {
            Logger.debug(THIS_FILE, "Starting sip stack");

            // Pj timer
            TimerWrapper.create(service);

            if (userAgentReceiver == null) {
                Logger.debug(THIS_FILE, "create ua receiver");
                userAgentReceiver = new UAStateReceiver();
                userAgentReceiver.initService(this);
            }
            userAgentReceiver.reconfigure(service);

            if (mediaManager == null) {
                mediaManager = new MediaManager(service);
            }
            mediaManager.startService();

            //initModules();

            DTMF_TONE_PAUSE_LENGTH = prefsWrapper
                    .getPreferenceIntegerValue(SipConfigManager.DTMF_PAUSE_TIME);
            DTMF_TONE_WAIT_LENGTH = prefsWrapper
                    .getPreferenceIntegerValue(SipConfigManager.DTMF_WAIT_TIME);

            // pjsua.setCallbackObject(userAgentReceiver);
            // pjsua.setZrtpCallbackObject(zrtpReceiver);

            Logger.debug(THIS_FILE, "Attach is done to callback");

            startStack();



            // Init media codecs
          //  initCodecs();
          //  setCodecsPriorities();

            created = true;

            return true;
        }

        return false;
    }

    /**
     * Stop sip service
     *
     * @return true if stop has been performed
     */
    public boolean sipStop()  {
        Logger.debug(THIS_FILE, ">> SIP STOP <<");


        if (service.notificationManager != null) {
            service.notificationManager.cancelRegisters();
        }

        if (tasksTimer != null) {
            tasksTimer.cancel();
            tasksTimer.purge();
            tasksTimer = null;
        }
        stopStack();

        return true;
    }

    public void startStack() {
        if (mStarted) {
            Logger.info(TAG, "SipService already started");
            return;
        }

        try {
            Logger.debug(TAG, "Starting PJSIP");
            mEndpoint = new SipEndpoint(service);
            mEndpoint.libCreate();
            EpConfig epConfig = new EpConfig();
            SipServiceUtils.setSipLogger(epConfig);


            epConfig.getUaConfig().setUserAgent("CSIPSimple");
            epConfig.getMedConfig().setHasIoqueue(true);
            epConfig.getMedConfig().setClockRate(16000);
            epConfig.getMedConfig().setQuality(10);
            epConfig.getMedConfig().setEcOptions(1);
            epConfig.getMedConfig().setEcTailLen(200);
            epConfig.getMedConfig().setThreadCnt(2);
            epConfig.getUaConfig().setMainThreadOnly(false);
            epConfig.getUaConfig().setThreadCnt(1);

            mEndpoint.libInit(epConfig);

            TransportConfig udpTransport = new TransportConfig();
            udpTransport.setQosType(pj_qos_type.PJ_QOS_TYPE_VOICE);
            udpTransport.setPort(5060);
            TransportConfig tcpTransport = new TransportConfig();
            tcpTransport.setPort(5060);
            tcpTransport.setQosType(pj_qos_type.PJ_QOS_TYPE_VOICE);
            TransportConfig tlsTransport = new TransportConfig();
            tlsTransport.setPort(5060);
            tlsTransport.setQosType(pj_qos_type.PJ_QOS_TYPE_VOICE);
            SipTlsUtils.setTlsConfig(service, false, tlsTransport);

            mEndpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, udpTransport);
            mEndpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP, tcpTransport);
            mEndpoint.transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS, tlsTransport);




            mEndpoint.libStart();

            ArrayList<CodecPriority> codecPriorities = getCodecPriorityList();
            SipServiceUtils.setAudioCodecPriorities(codecPriorities, mEndpoint);

            SipServiceUtils.setVideoCodecPriorities(mEndpoint);

            Logger.debug(TAG, "PJSIP started!");
            mStarted = true;
            mBroadcastEmitter=service.getBroadcastEmitter();
            mBroadcastEmitter.stackStatus(true);


        } catch (Exception exc) {
            Logger.error(TAG, "Error while starting PJSIP", exc);
            mStarted = false;
            service.notifyUserOfMessage("Could Not start PJSIP Stack");
            cleanPjsua();
        }

    }

    public void stopStack() {

        if (!mStarted) {
            Logger.error(TAG, "SipService not started");
            return;
        }

        try {
            Logger.debug(TAG, "Stopping PJSIP");

            /*
             * Do not remove accounts on service stop anymore
             * They should have already been removed (unregistered)
             * In case they have not, it is ok, it means app has been just killed
             * or service force stopped
             *
             * *************************************
             * removeAllActiveAccounts();
             * *************************************
             */

            /* Try to force GC to do its job before destroying the library
             * since it's recommended to do that by PJSUA examples
             */
            Runtime.getRuntime().gc();

            mEndpoint.libDestroy(pjsua_destroy_flag.PJSUA_DESTROY_NO_NETWORK);
            mEndpoint.delete();
            mEndpoint = null;

            Logger.debug(TAG, "PJSIP stopped");
            mBroadcastEmitter.stackStatus(false);

        } catch (Exception exc) {
            Logger.error(TAG, "Error while stopping PJSIP", exc);

        } finally {
            mStarted = false;
            mEndpoint = null;
        }
    }

    private void cleanPjsua() {
        Logger.debug(THIS_FILE, "Detroying...");
        // This will destroy all accounts so synchronize with accounts
        // management lock
        // long flags = 1; /*< Lazy disconnect : only RX */
        // Try with TX & RX if network is considered as available
        long flags = 0;
        if (!prefsWrapper.isValidConnectionForOutgoing(false)) {
            // If we are current not valid for outgoing,
            // it means that we don't want the network for SIP now
            // so don't use RX | TX to not consume data at all
            flags = 3;
        }

        service.getContentResolver().delete(SipProfile.ACCOUNT_STATUS_URI, null, null);
        if (userAgentReceiver != null) {
            userAgentReceiver.stopService();
            userAgentReceiver = null;
        }

        if (mediaManager != null) {
            mediaManager.stopService();
            mediaManager = null;
        }

        TimerWrapper.destroy();

        created = false;
    }

    private ArrayList<CodecPriority> getCodecPriorityList() {
       // startStack();

        if (!mStarted) {
            Logger.error(TAG, "Can't get codec priority list! The SIP Stack has not been " +
                    "initialized! Add an account first!");
            return null;
        }

        try {
            CodecInfoVector2 codecs = mEndpoint.codecEnum2();
            if (codecs == null || codecs.size() == 0) return null;

            ArrayList<CodecPriority> codecPrioritiesList = new ArrayList<>(codecs.size());

            for (int i = 0; i < codecs.size(); i++) {
                CodecInfo codecInfo = codecs.get(i);
                CodecPriority newCodec = new CodecPriority(codecInfo.getCodecId(),
                        codecInfo.getPriority());
                if (!codecPrioritiesList.contains(newCodec))
                    codecPrioritiesList.add(newCodec);
                codecInfo.delete();
            }

            codecs.delete();

            Collections.sort(codecPrioritiesList);
            return codecPrioritiesList;

        } catch (Exception exc) {
            Logger.error(TAG, "Error while getting codec priority list!", exc);
            return null;
        }
    }



    private static ArrayList<String> codecs = new ArrayList<String>();

    private static boolean codecs_initialized = false;

    /**
     * Reset the list of codecs stored
     */
    public static void resetCodecs() {
        synchronized (codecs) {
            if (codecs_initialized) {
                codecs.clear();
                codecs_initialized = false;
            }
        }
    }


    private void initCodecs()  {
        synchronized (codecs) {
            try {
                if (!codecs_initialized) {
                    CodecInfoVector2 mcodecs = mEndpoint.codecEnum2();
                    if (mcodecs != null || mcodecs.size() != 0) {
                        for (int i = 0; i < codecs.size(); i++) {
                            CodecInfo codecInfo = mcodecs.get(i);
                            String codecId = codecInfo.getCodecId();
                            codecs.add(codecId);
                        }
                    }
                }
            } catch (Exception e) {
                Logger.error(TAG, "Error while getting codec list!", e);

            }
            // Set it in prefs if not already set correctly
            prefsWrapper.setCodecList(codecs);
            codecs_initialized = true;
            // We are now always capable of tls and srtp !
            prefsWrapper.setLibCapability(PreferencesProviderWrapper.LIB_CAP_TLS, true);
            prefsWrapper.setLibCapability(PreferencesProviderWrapper.LIB_CAP_SRTP, true);
        }
    }

    public void setNoSnd()  {
        if (!created) {
            return;
        }
        //ll see
        //  pjsua.set_no_snd_dev();
    }

    public void setSnd()  {
        if (!created) {
            return;
        }
        //ll see
        //   pjsua.set_snd_dev(0, 0);
    }


    /**
     * Append log for the codec in String builder
     *
     * @param sb    the buffer to be appended with the codec info
     * @param codec the codec name
     * @param prio  the priority of the codec
     */
    private void buffCodecLog(StringBuilder sb, String codec, short prio) {
        if (prio > 0 ) {
            sb.append(codec);
            sb.append(" (");
            sb.append(prio);
            sb.append(") - ");
        }
    }

    /**
     * Set the codec priority in pjsip stack layer based on preference store
     *
     * @throws SameThreadException
     */
    private void setCodecsPriorities()  {
        ConnectivityManager cm = ((ConnectivityManager) service
                .getSystemService(Context.CONNECTIVITY_SERVICE));


        if (codecs_initialized) {
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null) {
                StringBuilder audioSb = new StringBuilder();
                audioSb.append("Audio codecs : ");
                String currentBandType = prefsWrapper.getPreferenceStringValue(
                        SipConfigManager.getBandTypeKey(ni.getType(), ni.getSubtype()),
                        SipConfigManager.CODEC_WB);

                synchronized (codecs) {
                    for (String codec : codecs) {
                        short aPrio = prefsWrapper.getCodecPriority(codec, currentBandType,
                                "-1");
                        buffCodecLog(audioSb, codec, aPrio);
                        try {
                            if (aPrio >= 0) {
                                mEndpoint.codecSetPriority(codec, aPrio);
                            }
                        } catch (Exception e) {
                            Logger.error(TAG, "Error while getting codec list!", e);

                        }
                        String codecKey = SipConfigManager.getCodecKey(codec,
                                SipConfigManager.FRAMES_PER_PACKET_SUFFIX);
                        Integer frmPerPacket = SipConfigManager.getPreferenceIntegerValue(
                                service, codecKey);
                        if (frmPerPacket != null && frmPerPacket > 0) {
                            Logger.debug(THIS_FILE, "Set codec " + codec + " fpp : " + frmPerPacket);

                        }
                    }
                }
                Logger.debug(THIS_FILE, audioSb.toString());
            }
        }
    }


    public boolean addAccount(SipProfile profile)  {

        Logger.debug(THIS_FILE, "in Add account");
        int status= 0;
       try {
           if (!created) {
               Logger.debug(THIS_FILE, "PJSIP is not started here, nothing can be done");
               return true;
           }

           PjSipAccount account = new PjSipAccount(profile);
           Logger.debug(THIS_FILE, "Made PjSipAccount");
           account.applyExtraParams(service);

           SipProfileState currentAccountStatus = getProfileState(profile);
           account.getAccountConfig().getRegConfig().setRegisterOnAdd(false);


           if (currentAccountStatus.isAddedToStack()) {
               Logger.debug(THIS_FILE, "isAddedToStack :"+currentAccountStatus.isAddedToStack());

               if (!mActiveSipAccounts.isEmpty() && mActiveSipAccounts.containsKey(profile.acc_id)) {

                   SipAccount sipAccount = mActiveSipAccounts.get(profile.acc_id);
                   sipAccount.modify();
                   sipAccount.setRegistration(true);
                   status = 1;

                   ContentValues cv = new ContentValues();
                   cv.put(SipProfileState.ADDED_STATUS, status);
                   service.getContentResolver().update(
                           ContentUris.withAppendedId(SipProfile.ACCOUNT_STATUS_ID_URI_BASE, profile.id),
                           cv, null, null);
               }

           } else {
               Logger.debug(THIS_FILE, "In else of add account:");

               SipAccount sipAccount = new SipAccount(service, account);
               sipAccount.create();
               sipAccount.setRegistration(true);

               mActiveSipAccounts.put(profile.acc_id, sipAccount);

               SipProfileState ps = new SipProfileState(profile);
               ps.setAddedStatus(1);
               ps.setPjsuaId(1);
               service.getContentResolver().insert(
                       ContentUris.withAppendedId(SipProfile.ACCOUNT_STATUS_ID_URI_BASE,
                               account.id), ps.getAsContentValue());
               account.getAccountConfig().getPresConfig().setPublishEnabled(true);


           }
       }catch(Exception e)
       {
           e.printStackTrace();
           Logger.debug(THIS_FILE, "In exception of add account:"+e.getStackTrace());

       }

        ////////////////////////
       return true;
    }

    private String[] getNameservers() {
        String[] nameservers = new String[]{};

        if (prefsWrapper.enableDNSSRV()) {
            String prefsDNS = prefsWrapper
                    .getPreferenceStringValue(SipConfigManager.OVERRIDE_NAMESERVER);
            if (TextUtils.isEmpty(prefsDNS)) {
                String ipv6Escape = "[ \\[\\]]";
                String ipv4Matcher = "^\\d+(\\.\\d+){3}$";
                String ipv6Matcher = "^[0-9a-f]+(:[0-9a-f]*)+:[0-9a-f]+$";
                List<String> dnsServers;
                List<String> dnsServersAll = new ArrayList<String>();
                List<String> dnsServersIpv4 = new ArrayList<String>();
                for (int i = 1; i <= 2; i++) {
                    String dnsName = prefsWrapper.getSystemProp("net.dns" + i);
                    if (!TextUtils.isEmpty(dnsName)) {
                        dnsName = dnsName.replaceAll(ipv6Escape, "");
                        if (!TextUtils.isEmpty(dnsName) && !dnsServersAll.contains(dnsName)) {
                            if (dnsName.matches(ipv4Matcher) || dnsName.matches(ipv6Matcher)) {
                                dnsServersAll.add(dnsName);
                            }
                            if (dnsName.matches(ipv4Matcher)) {
                                dnsServersIpv4.add(dnsName);
                            }
                        }
                    }
                }

                if (dnsServersIpv4.size() > 0) {
                    // Prefer pure ipv4 list since pjsua doesn't manage ipv6
                    // resolution yet
                    dnsServers = dnsServersIpv4;
                } else {
                    dnsServers = dnsServersAll;
                }

                if (dnsServers.size() == 0) {
                    // This is the ultimate fallback... we should never be there
                    // !
                    nameservers = new String[]{"127.0.0.1"};
                } else if (dnsServers.size() == 1) {
                    nameservers = new String[]{dnsServers.get(0)};
                } else {
                    nameservers = new String[]{dnsServers.get(0), dnsServers.get(1)};
                }
            } else {
                nameservers = new String[]{prefsDNS};
            }
        }
        return nameservers;
    }
    public SipCallSessionImpl getActiveCallInProgress() {
        if (created && userAgentReceiver != null) {
            return userAgentReceiver.getActiveCallInProgress();
        }
        return null;
    }

    public int makeCall(String callee, int accountId, Bundle b)  {

      //  checkThread();
        if (!created) {
            return -1;
        }
        final ToCall toCall = sanitizeSipUri(callee, accountId);
        if (toCall != null) {

        Uri uri =  Uri.parse(toCall.getCallee());
        String toNumber=toCall.getCallee();
        if (uri == null) return -1;

        String sipServer = uri.getHost();
        String name = uri.getUserInfo();
        boolean isVideo=false;
        if (b != null) {
             isVideo = b.getBoolean(SipCallSessionImpl.OPT_CALL_VIDEO, false);
        }
        boolean isVideoConference = false;
        boolean isTransfer = false;
            SipCall call=null;

        if (isVideo) {
                isVideoConference = false;
                // do not allow attended transfer on video call for now
        }
       try {

           SipProfile srofile = service.getAccount(accountId);

           if (!mActiveSipAccounts.isEmpty() && mActiveSipAccounts.containsKey(srofile.acc_id))
           {
              SipAccount account = mActiveSipAccounts.get(srofile.acc_id);
               call = new SipCall(account);
              call= account.addOutgoingCall(call, toNumber, isVideo, isVideoConference, isTransfer);
               if(call !=null)
               {
                 mBroadcastEmitter.outgoingCall("" + accountId, call.getId(), toNumber, isVideo, isVideoConference, isTransfer);
                 dtmfToAutoSend.put(call.getId(), toCall.getDtmf());
                  Logger.debug(THIS_FILE, "DTMF - Store for " + call.getId() + " - " + toCall.getDtmf());
               }
           }
       } catch (Exception exc) {
                Logger.error(TAG, "Error while making outgoing call", exc);
                mBroadcastEmitter.outgoingCall(""+accountId, -1, toNumber, false, false, false);
            }

            return 1;
        } else {
            service.notifyUserOfMessage(service.getString(R.string.invalid_sip_uri) + " : "
                    + callee);
        }
        return -1;
    }

    private ToCall sanitizeSipUri(String callee, long accountId)  {
        // accountId is the id in term of csipsimple database
        // pjsipAccountId is the account id in term of pjsip adding
        int pjsipAccountId = (int) SipProfile.INVALID_ID;

        // Fake a sip profile empty to get it's profile state
        // Real get from db will be done later
        SipProfile account = new SipProfile();
        account.id = accountId;
        SipProfileState profileState = getProfileState(account);
       // If the account is valid
         pjsipAccountId = profileState.getPjsuaId();
        if (pjsipAccountId == SipProfile.INVALID_ID) {
            Logger.debug(THIS_FILE, "Unable to find a valid account for this call");
            return null;
        }

        // Check integrity of callee field
        // Get real account information now
        account = service.getAccount((int) accountId);
        SipUri.ParsedSipContactInfos finalCallee = account.formatCalleeNumber(callee);
        String digitsToAdd = null;
        if (!TextUtils.isEmpty(finalCallee.userName) &&
                (finalCallee.userName.contains(",") || finalCallee.userName.contains(";"))) {
            int commaIndex = finalCallee.userName.indexOf(",");
            int semiColumnIndex = finalCallee.userName.indexOf(";");
            if (semiColumnIndex > 0 && semiColumnIndex < commaIndex) {
                commaIndex = semiColumnIndex;
            }
            digitsToAdd = finalCallee.userName.substring(commaIndex);
            finalCallee.userName = finalCallee.userName.substring(0, commaIndex);
        }

        Logger.debug(THIS_FILE, "will call " + finalCallee);
      return new SipService.ToCall(pjsipAccountId, finalCallee.toString(true), digitsToAdd);


    }

    public SipProfileState getProfileState(SipProfile account) {
        if (!created || account == null) {
            return null;
        }
        if (account.id == SipProfile.INVALID_ID) {
            return null;
        }
        SipProfileState accountInfo = new SipProfileState(account);
        Cursor c = service.getContentResolver().query(
                ContentUris.withAppendedId(SipProfile.ACCOUNT_STATUS_ID_URI_BASE, account.id),
                null, null, null, null);
        if (c != null) {
            try {
                if (c.getCount() > 0) {
                    c.moveToFirst();
                    accountInfo.createFromDb(c);
                }
            } catch (Exception e) {
                Logger.debug(THIS_FILE, "Error on looping over sip profiles states");
            } finally {
                c.close();
            }
        }
        return accountInfo;
    }

    public SipProfile getAccountForPjsipId(int pjId) {
        long accId = getAccountIdForPjsipId(service, pjId);
        if (accId == SipProfile.INVALID_ID) {
            return null;
        } else {
            return service.getAccount(accId);
        }
    }

    @SuppressLint("Range")
    public static long getAccountIdForPjsipId(Context ctxt, int pjId) {
        long accId = SipProfile.INVALID_ID;

        Cursor c = ctxt.getContentResolver().query(SipProfile.ACCOUNT_STATUS_URI, null, null,
                null, null);
        if (c != null) {
            try {
                c.moveToFirst();
                do {
                    @SuppressLint("Range") int pjsuaId = c.getInt(c.getColumnIndex(SipProfileState.PJSUA_ID));
                    Logger.debug(THIS_FILE, "Found pjsua " + pjsuaId + " searching " + pjId);
                    if (pjsuaId == pjId) {
                        accId = c.getInt(c.getColumnIndex(SipProfileState.ACCOUNT_ID));
                        break;
                    }
                } while (c.moveToNext());
            } catch (Exception e) {
                Logger.debug(THIS_FILE, "Error on looping over sip profiles");
            } finally {
                c.close();
            }
        }
        return accId;
    }

 /*   public ToCall sendMessage(String callee, String message, long accountId)
            throws SameThreadException {
        if (!created) {
            return null;
        }

        ToCall toCall = sanitizeSipUri(callee, accountId);
        if (toCall != null) {
            Uri uri = Uri.parse(toCall.getCallee());
            String text = message;

            // Nothing to do with this values
            byte[] userData = new byte[1];
            int status=0;
         //   int status = pjsua.im_send(toCall.getPjsipAccountId(), uri, null, text, null, userData);
            return (status == 1) ? toCall : null;
        }
        return toCall;
    }
*/
    public int validateAudioClockRate(int aClockRate) {
        if (mediaManager != null) {
            return mediaManager.validateAudioClockRate(aClockRate);
        }
        return -1;
    }



    public void sendPendingDtmf(int callId) {
        if (dtmfToAutoSend.get(callId) != null) {
            Logger.debug(THIS_FILE, "DTMF - Send pending dtmf " + dtmfToAutoSend.get(callId) + " for "
                    + callId);
            sendDtmf(callId, dtmfToAutoSend.get(callId));
        }
    }

    public int sendDtmf(final int callId, String keyPressed)  {


        if (TextUtils.isEmpty(keyPressed)) {
            return 1;
        }
        int res = 0;
        try {




        SipCallSessionImpl sipCallSessionImpl= service.getUAStateReceiver().getCallInfo(callId);
       // SipCall sipCall = sipCallSessionImpl.getSipCall();

        int callStatus= handleGetCallStatus(callId);

        if(callStatus ==-1)
            return -1;

        String dtmfToDial = keyPressed;
        String remainingDtmf = "";
        int pauseBeforeRemaining = 0;
        boolean foundSeparator = false;
        if (keyPressed.contains(",") || keyPressed.contains(";")) {
            dtmfToDial = "";
            for (int i = 0; i < keyPressed.length(); i++) {
                char c = keyPressed.charAt(i);
                if (!foundSeparator) {
                    if (c == ',' || c == ';') {
                        pauseBeforeRemaining += (c == ',') ? DTMF_TONE_PAUSE_LENGTH
                                : DTMF_TONE_WAIT_LENGTH;
                        foundSeparator = true;
                    } else {
                        dtmfToDial += c;
                    }
                } else {
                    if ((c == ',' || c == ';') && TextUtils.isEmpty(remainingDtmf)) {
                        pauseBeforeRemaining += (c == ',') ? DTMF_TONE_PAUSE_LENGTH
                                : DTMF_TONE_WAIT_LENGTH;
                    } else {
                        remainingDtmf += c;
                    }
                }
            }

        }


        if (!TextUtils.isEmpty(dtmfToDial)) {
            String pjKeyPressed = dtmfToDial;
            res = -1;
            if (prefsWrapper.useSipInfoDtmf()) {
                CallSendDtmfParam  callSendDtmfParam =new CallSendDtmfParam();
                callSendDtmfParam.setMethod(pjsua_dtmf_method.PJSUA_DTMF_METHOD_SIP_INFO);
              //  sipCall.sendDtmf(callSendDtmfParam);


                Logger.debug(THIS_FILE, "Has been sent DTMF INFO : " + res);
            } else {
                if (!prefsWrapper.forceDtmfInBand()) {
                    // Generate using RTP
                    CallSendDtmfParam  callSendDtmfParam =new CallSendDtmfParam();
                    callSendDtmfParam.setMethod(pjsua_dtmf_method.PJSUA_DTMF_METHOD_RFC2833);
                  //  sipCall.sendDtmf(callSendDtmfParam);
                    Logger.debug(THIS_FILE, "Has been sent in RTP DTMF : " + res);
                }

                if (res != 1 && !prefsWrapper.forceDtmfRTP()) {
                    // Generate using analogic inband
                    if (dtmfDialtoneGenerators.get(callId) == null) {
                        dtmfDialtoneGenerators.put(callId, new PjStreamDialtoneGenerator(callId));
                    }
                    res = dtmfDialtoneGenerators.get(callId).sendPjMediaDialTone(dtmfToDial);
                    CallSendDtmfParam  callSendDtmfParam =new CallSendDtmfParam();
                    callSendDtmfParam.setMethod(res);
                  //  sipCall.sendDtmf(callSendDtmfParam);

                    Logger.debug(THIS_FILE, "Has been sent DTMF analogic : " + res);
                }
            }
        }

        // Finally, push remaining DTMF in the future
        if (!TextUtils.isEmpty(remainingDtmf)) {
            dtmfToAutoSend.put(callId, remainingDtmf);

            if (tasksTimer == null) {
                tasksTimer = new Timer("com.csipsimple.PjSipServiceTasks");
            }
            TimerTask tt = new TimerTask() {
                @Override
                public void run() {
                    service.getExecutor().execute(new SipService.SipRunnable() {
                        @Override
                        protected void doRun() throws Exception {
                            Thread.currentThread().setName("MyDTMF");
                            Logger.debug(THIS_FILE, "Running pending DTMF send");
                            sendPendingDtmf(callId);
                        }
                    });
                }
            };
            dtmfTasks.put(callId, tt);
            Logger.debug(THIS_FILE, "Schedule DTMF " + remainingDtmf + " in " + pauseBeforeRemaining);
            tasksTimer.schedule(tt, pauseBeforeRemaining);
        } else {
            if (dtmfToAutoSend.get(callId) != null) {
                dtmfToAutoSend.put(callId, null);
            }
            if (dtmfTasks.get(callId) != null) {
                dtmfTasks.put(callId, null);
            }
        }
        } catch(Exception e)
        {
             e.getStackTrace();
        }

        return res;
    }

    /**
     * Are we currently recording the call?
     *
     * @param callId The call id to test for a recorder presence
     * @return true if recording this call
     */
    // Recorder
    private SparseArray<List<IRecorderHandler>> callRecorders = new SparseArray<List<IRecorderHandler>>();

    public boolean isRecording(int callId)  {
        List<IRecorderHandler> recorders = callRecorders.get(callId, null);
        if (recorders == null) {
            return false;
        }
        return recorders.size() > 0;
    }

    /**
     * Can we record for this call id ?
     *
     * @param callId The call id to record to a file
     * @return true if seems to be possible to record this call.
     */
    public boolean canRecord(int callId) {
        if (!created) {
            // Not possible to record if service not here
            return false;
        }
        SipCallSessionImpl callInfo = getCallInfo(callId);
        if (callInfo == null) {
            // Not possible to record if no call info for given call id
            return false;
        }
        int ms = callInfo.getMediaStatus();
        if (ms != SipCallSessionImpl.MediaState.ACTIVE &&
                ms != SipCallSessionImpl.MediaState.REMOTE_HOLD) {
            // We can't record if media state not running on our side
            return false;
        }
        return true;
    }

    public SipCallSessionImpl getCallInfo(int callId) {
        if (created/* && !creating */&& userAgentReceiver != null) {
            SipCallSessionImpl callInfo = userAgentReceiver.getCallInfo(callId);
            return callInfo;
        }
        return null;
    }



/// ///////////////////










    public int updateCallOptions(int callId, Bundle options) {
        // TODO : if more options we should redesign this part.
        if (options.containsKey(SipCallSessionImpl.OPT_CALL_VIDEO)) {
            boolean add = options.getBoolean(SipCallSessionImpl.OPT_CALL_VIDEO);
            SipCallSessionImpl ci = getCallInfo(callId);
            if (add && ci.mediaHasVideo()) {
                // We already have one video running -- refuse to send another
                return -1;
            } else if (!add && !ci.mediaHasVideo()) {
                // We have no current video, no way to remove.
                return -1;
            }
            //   pjsua_call_vid_strm_op op = add ? pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_ADD
            //          : pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_REMOVE;
            if (!add) {
                // TODO : manage remove case
            }
            //  return pjsua.call_set_vid_strm(callId, op, null);
        }

        return -1;
    }




    public void stopDialtoneGenerator(int callId)  {
        if (dtmfDialtoneGenerators.get(callId) != null) {
            dtmfDialtoneGenerators.get(callId).stopDialtoneGenerator();
            dtmfDialtoneGenerators.put(callId, null);
        }
        if (dtmfToAutoSend.get(callId) != null) {
            dtmfToAutoSend.put(callId, null);
        }
        if (dtmfTasks.get(callId) != null) {
            dtmfTasks.get(callId).cancel();
            dtmfTasks.put(callId, null);
        }
    }

    public void startWaittoneGenerator(int callId)  {
        if (waittoneGenerators.get(callId) == null) {
            waittoneGenerators.put(callId, new PjStreamDialtoneGenerator(callId, false));
        }
        waittoneGenerators.get(callId).startPjMediaWaitingTone();
    }

    public void stopWaittoneGenerator(int callId)  {
        if (waittoneGenerators.get(callId) != null) {
            waittoneGenerators.get(callId).stopDialtoneGenerator();
            waittoneGenerators.put(callId, null);
        }
    }


    public SipCallSessionImpl getPublicCallInfo(int callId) {
        SipCallSessionImpl internalCallSession = getCallInfo(callId);
        if( internalCallSession == null) {
            return null;
        }
        return new SipCallSessionImpl(internalCallSession);
    }

    public void setBluetoothOn(boolean on) throws SameThreadException {
        if (created && mediaManager != null) {
            mediaManager.setBluetoothOn(on);
        }
    }

    /**
     * Mute microphone
     *
     * @param on true if microphone has to be muted
     * @throws SameThreadException
     */
    public void setMicrophoneMute(boolean on) throws SameThreadException {
        if (created && mediaManager != null) {
            mediaManager.setMicrophoneMute(on);
        }
    }

    /**
     * Change speaker phone mode
     *
     * @param on true if the speaker mode has to be on.
     * @throws SameThreadException
     */
    public void setSpeakerphoneOn(boolean on) throws SameThreadException {
        if (created && mediaManager != null) {
            mediaManager.setSpeakerphoneOn(on);
        }
    }

    public SipCallSessionImpl[] getCalls() {
        if (userAgentReceiver != null) {
                Logger.debug(THIS_FILE,"PJSIP Service getcalls");
            SipCallSessionImpl[] callsInfo = userAgentReceiver.getCalls();
            return callsInfo;
        }
        return new SipCallSessionImpl[0];
    }



    public void setEchoCancellation(boolean on) throws SameThreadException {
        if (created && userAgentReceiver != null) {
            Logger.debug(THIS_FILE, "set echo cancelation " + on);
            //  pjsua.set_ec(on ? prefsWrapper.getEchoCancellationTail() : 0,
            //        prefsWrapper.getPreferenceIntegerValue(SipConfigManager.ECHO_MODE));
        }
    }

    public void adjustStreamVolume(int stream, int direction, int flags) {
        if (mediaManager != null) {
            mediaManager.adjustStreamVolume(stream, direction, AudioManager.FLAG_SHOW_UI);
        }
    }

    public void silenceRinger() {
        if (mediaManager != null) {
            mediaManager.stopRingAndUnfocus();
        }
    }

    /**
     * Change account registration / adding state
     *
     * @param account The account to modify registration
     * @param renew if 0 we ask for deletion of this account; if 1 we ask for
     *            registration of this account (and add if necessary)
     * @param forceReAdd if true, we will first remove the account and then
     *            re-add it
     * @return true if the operation get completed without problem
     * @throws SameThreadException
     */
    public boolean setAccountRegistration(SipProfile account, int renew, boolean forceReAdd)
            throws Exception {
        int status = -1;
        if (!created || account == null) {
            Logger.debug(THIS_FILE, "PJSIP is not started here, nothing can be done");
            return false;
        }
        if (account.id == SipProfile.INVALID_ID) {
            Logger.debug(THIS_FILE, "Trying to set registration on a deleted account");
            return false;
        }


        SipProfileState profileState = getProfileState(account);

        // In case of already added, we have to act finely
        // If it's local we can just consider that we have to re-add account
        // since it will actually just touch the account with a modify
        if (profileState != null && profileState.isAddedToStack()) {
            // The account is already there in accounts list
            service.getContentResolver().delete(
                    ContentUris.withAppendedId(SipProfile.ACCOUNT_STATUS_URI, account.id), null,
                    null);
            Logger.debug(THIS_FILE, "Account already added to stack, remove and re-load or delete");
            if (renew == 1) {
                if (forceReAdd) {
                    if (!mActiveSipAccounts.isEmpty() && mActiveSipAccounts.containsKey(account.acc_id))
                    {
                        SipAccount sipAccount = mActiveSipAccounts.get(account.acc_id);
                        sipAccount.delete();
                    }
                    Logger.debug(THIS_FILE, " going to add account setAccountRegistration !!");
                    addAccount(account);
                    Logger.debug(THIS_FILE, "Added  account from setAccountRegistration !!");
                } else {
                    if (!mActiveSipAccounts.isEmpty() && mActiveSipAccounts.containsKey(account.acc_id))
                    {
                        SipAccount sipAccount = mActiveSipAccounts.get(account.acc_id);
                        sipAccount.setRegistration(true);
                        getOnlineForStatus(service.getPresence());
                    }
                }
            } else {
                if (!mActiveSipAccounts.isEmpty() && mActiveSipAccounts.containsKey(account.acc_id))
                {
                    SipAccount sipAccount = mActiveSipAccounts.get(account.acc_id);
                    sipAccount.delete();
                    Logger.debug(THIS_FILE, "Delete account !!");
                }
            }
        } else {
            if (renew == 1) {
                addAccount(account);
                Logger.debug(THIS_FILE, "Added account from setAccountRegistration from renew !!");
            } else {
                Logger.debug(THIS_FILE, "Ask to unregister an unexisting account !!" + account.id);
            }

        }
        // PJ_SUCCESS = 0
        return status == 0;
    }

    /**
     * Set self presence
     *
     * @param presence the SipManager.SipPresence
     * @param statusText the text of the presence
     * @throws SameThreadException
     */
    public void setPresence(SipManager.PresenceStatus presence, String statusText, long accountId)
             {
        if (!created) {
            Logger.debug(THIS_FILE, "PJSIP is not started here, nothing can be done");
            return;
        }
        SipProfile account = new SipProfile();
        account.id = accountId;
        SipProfileState profileState = getProfileState(account);

        // In case of already added, we have to act finely
        // If it's local we can just consider that we have to re-add account
        // since it will actually just touch the account with a modify
        if (profileState != null && profileState.isAddedToStack()) {
            // The account is already there in accounts list
            //  pjsua.acc_set_online_status(profileState.getPjsuaId(), getOnlineForStatus(presence));
        }

    }

    private int getOnlineForStatus(SipManager.PresenceStatus presence) {
        return presence == SipManager.PresenceStatus.ONLINE ? 1 : 0;
    }



    public void setAudioInCall(int beforeInit) {
        if (mediaManager != null) {
            mediaManager.setAudioInCall(beforeInit == pj_constants_.PJ_TRUE);
        }
    }

    public void unsetAudioInCall() {

        if (mediaManager != null) {
            mediaManager.unsetAudioInCall();
        }
    }



    public void onGSMStateChanged(int state, String incomingNumber)  {
        // Avoid ringing if new GSM state is not idle
        if (state != TelephonyManager.CALL_STATE_IDLE && mediaManager != null) {
            mediaManager.stopRingAndUnfocus();
        }

        // If new call state is not idle
        if (state != TelephonyManager.CALL_STATE_IDLE && userAgentReceiver != null) {
            SipCallSessionImpl currentActiveCall = userAgentReceiver.getActiveCallOngoing();
            // If we have a sip call on our side
            if (currentActiveCall != null) {
                AudioManager am = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    // GSM is now off hook => hold current sip call
                    hasBeenHoldByGSM = currentActiveCall.getCallId();
                    //   callHold(hasBeenHoldByGSM);
                    //  pjsua.set_no_snd_dev();

                    am.setMode(AudioManager.MODE_IN_CALL);
                } else {
                    // We have a ringing incoming call.
                    // Avoid ringing
                    hasBeenChangedRingerMode = am.getRingerMode();
                    am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    // And try to notify with tone
                    if (mediaManager != null) {
                        mediaManager.playInCallTone(MediaManager.TONE_CALL_WAITING);
                    }
                }
            }
        } else {
            // GSM is now back to an IDLE state, resume previously stopped SIP
            // calls
            if (hasBeenHoldByGSM != null && isCreated()) {

                callReinvite(hasBeenHoldByGSM, true);
                hasBeenHoldByGSM = null;
            }

            // GSM is now back to an IDLE state, reset ringerMode if was
            // changed.
            if (hasBeenChangedRingerMode != null) {
                AudioManager am = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
                am.setRingerMode(hasBeenChangedRingerMode);
                hasBeenChangedRingerMode = null;
            }
        }
    }

    /*
     * public void sendKeepAlivePackets() throws SameThreadException {
     * ArrayList<SipProfileState> accounts = getActiveProfilesState(); for
     * (SipProfileState acc : accounts) {
     * pjsua.send_keep_alive(acc.getPjsuaId()); } }
     */

    protected void setDetectedNatType(String natName, int status) {
        // Maybe we will need to treat status to eliminate some set (depending of unknown string fine for 3rd part dev)
        mNatDetected = natName;
    }

    /**
     * @return nat type name detected by pjsip. Empty string if nothing detected
     */
    public String getDetectedNatType() {
        return mNatDetected;
    }


    private int getUseSrtp() {
        try {
            int use_srtp = Integer.parseInt(prefsWrapper
                    .getPreferenceStringValue(SipConfigManager.USE_SRTP));
            if (use_srtp >= 0) {
                return pjmedia_srtp_use.PJMEDIA_SRTP_MANDATORY;
            }
        } catch (NumberFormatException e) {
            Logger.debug(THIS_FILE, "Transport port not well formated");
        }
        return pjmedia_srtp_use.PJMEDIA_SRTP_DISABLED;
    }




    /**
     * Start recording of a call.
     *
     * @param callId the call id of the call to record
     * @throws SameThreadException virtual exception to be sure we are calling
     *             this from correct thread
     */
    public void startRecording(int callId, int way) throws SameThreadException {
        // Make sure we are in a valid state for recording
        if (!canRecord(callId)) {
            return;
        }
        // Sanitize call way : if 0 assume all
        if (way == 0) {
            way = SipManager.BITMASK_ALL;
        }

        try {
            File recFolder = PreferencesProviderWrapper.getRecordsFolder(service);
            IRecorderHandler recoder = new SimpleWavRecorderHandler(getCallInfo(callId), recFolder,
                    way);
            List<IRecorderHandler> recordersList = callRecorders.get(callId,
                    new ArrayList<IRecorderHandler>());
            recordersList.add(recoder);
            callRecorders.put(callId, recordersList);
            recoder.startRecording();
            userAgentReceiver.updateRecordingStatus(callId, false, true);
        } catch (IOException e) {
            service.notifyUserOfMessage(R.string.cant_write_file);

        } catch (Exception e) {
            Logger.debug(THIS_FILE, "Impossible to record ");
        }
    }

    /**
     * Stop recording of a call.
     *
     * @param callId the call to stop record for.
     * @throws SameThreadException virtual exception to be sure we are calling
     *             this from correct thread
     */
    public void stopRecording(int callId) throws Exception {
        if (!created) {
            return;
        }
        List<IRecorderHandler> recoders = callRecorders.get(callId, null);
        if (recoders != null) {
            for (IRecorderHandler recoder : recoders) {
                recoder.stopRecording();
                // Broadcast to other apps the a new sip record has been done
                SipCallSessionImpl callInfo = getPublicCallInfo(callId);
                Intent it = new Intent(SipManager.ACTION_SIP_CALL_RECORDED);
                Bundle bundle = new Bundle();
                bundle.putParcelable(SipManager.EXTRA_CALL_INFO, callInfo);
                it.putExtra("EXTRA_CALL_INFO",bundle);
                recoder.fillBroadcastWithInfo(it);
                service.sendBroadcast(it, SipManager.PERMISSION_USE_SIP);
            }
            // In first case we drop everything
            callRecorders.delete(callId);
            userAgentReceiver.updateRecordingStatus(callId, true, false);
        }
    }

    // Stream players
    // We use a list for future possible extensions. For now api only manages
    // one
    private SparseArray<List<IPlayerHandler>> callPlayers = new SparseArray<List<IPlayerHandler>>();

    /**
     * Play one wave file in call stream.
     *
     * @param filePath The path to the file we'd like to play
     * @param callId The call id we want to play to. Even if we only use
     *            {@link SipManager#BITMASK_IN} this must correspond to some
     *            call since it's used to identify internally created player.
     * @param way The way we want to play this file to. Bitmasked value that
     *            could be compounded of {@link SipManager#BITMASK_IN} (read
     *            local) and {@link SipManager#BITMASK_OUT} (read to remote
     *            party of the call)
     * @throws SameThreadException virtual exception to be sure we are calling
     *             this from correct thread
     */
    public void playWaveFile(String filePath, int callId, int way)  {
        if (!created) {
            return;
        }
        // Stop any current player
        stopPlaying(callId);
        if (TextUtils.isEmpty(filePath)) {
            // Nothing to do if we have not file path
            return;
        }
        if (way == 0) {
            way = SipManager.BITMASK_ALL;
        }

        // We create a new player conf port.
        try {
            IPlayerHandler player = new SimpleWavPlayerHandler(getCallInfo(callId), filePath, way);
            List<IPlayerHandler> playersList = callPlayers.get(callId,
                    new ArrayList<IPlayerHandler>());
            playersList.add(player);
            callPlayers.put(callId, playersList);

            player.startPlaying();
        } catch (IOException e) {
            // TODO : add a can't read file txt
            service.notifyUserOfMessage(R.string.cant_write_file);
        } catch (Exception e) {
            Logger.debug(THIS_FILE, "Impossible to play file");
        }
    }

    /**
     * Stop eventual player for a given call.
     *
     * @param callId the call id corresponding to player previously created with
     *            {@link #playWaveFile(String, int, int)}
     * @throws SameThreadException virtual exception to be sure we are calling
     *             this from correct thread
     */
    public void stopPlaying(int callId)  {
      try {
          List<IPlayerHandler> players = callPlayers.get(callId, null);
          if (players != null) {
              for (IPlayerHandler player : players) {
                  player.stopPlaying();
              }
              callPlayers.delete(callId);
          }
      }catch(Exception e)
      {
          e.getStackTrace();
      }
    }

    public void updateTransportIp(String oldIPAddress) throws SameThreadException {
        if (!created) {
            return;
        }
        Logger.debug(THIS_FILE, "Trying to update my address in the current call to " + oldIPAddress);
        //   pjsua.update_transport(pjsua.pj_str_copy(oldIPAddress));
    }


    private static int boolToPjsuaConstant(boolean v) {
        return v ? 1 : 0;
    }



    /***   Sip Calls Management    ***/

    private SipCall getCall(String accountID, int callID) {

        SipProfile srofile = service.getAccount(Long.parseLong(accountID));

        if (!mActiveSipAccounts.isEmpty() && mActiveSipAccounts.containsKey(srofile.acc_id)) {
            SipAccount account = mActiveSipAccounts.get(srofile.acc_id);
            if (account != null) {
                SipCall sipCall = new SipCall(account, callID);
                if (sipCall != null) {
                    return sipCall;
                } else {
                    notifyCallDisconnected(accountID, callID);
                    return null;
                }
            }
        }
        return null;
    }

    private void notifyCallDisconnected(String accountID, int callID) {
        mBroadcastEmitter.callState(accountID, callID,
                pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED,
                callStatus, 0);
    }

    private int handleGetCallStatus(int callID) {
        int callStatusCode=-1;
        SipCallSessionImpl sipCallSessionImpl= service.getUAStateReceiver().getCallInfo(callID);
        CallInfo sipCallinfo = sipCallSessionImpl.getCallInfo();
        if (sipCallinfo != null) {
            try {
                callStatusCode = sipCallinfo.getLastStatusCode();
            } catch (Exception ex) {
                callStatusCode=-1;
                ex.printStackTrace();
            }

            mBroadcastEmitter.callStats(callID, sipCallinfo.getState(), "", sipCallinfo.getTotalDuration().getMsec(),null,null);
        }
        return callStatusCode;
    }

    private void handleSendDTMF(String accountID,int callID,String dtmf) {

        SipCall sipCall = getCall(accountID, callID);
        if (sipCall != null) {
            try {
                sipCall.dialDtmf(dtmf);
            } catch (Exception exc) {
                Logger.error(TAG, "Error while dialing dtmf: " + dtmf + ". AccountID: "
                        + accountID + ", CallID: " + callID);
            }
        }
    }






    private void handleAttendedTransferCall(String accountID,int callIdOrig , int dest) {

        try {
            SipCall sipCallOrig = getCall(accountID, callIdOrig);
            if (sipCallOrig != null) {
                int callIdDest = dest;
                SipCall sipCallDest = getCall(accountID, callIdDest);
                sipCallOrig.xferReplaces(sipCallDest, new CallOpParam());
            }
        } catch (Exception exc) {
            Logger.error(TAG, "Error while finalizing attended transfer", exc);
            notifyCallDisconnected(accountID, callIdOrig);
        }
    }

    private void handleSetIncomingVideoFeed(String accountID,int callID , Surface param) {

        SipCall sipCall = getCall(accountID, callID);
        if (sipCall != null) {

                Surface surface = param;
                sipCall.setIncomingVideoFeed(surface);

        }
    }

    private void handleSetSelfVideoOrientation(String accountID,int callID ,int orient) {

        SipProfile srofile= service.getAccount(Long.parseLong(accountID));
        PjSipAccount pjsipaccount= new PjSipAccount(srofile);
        SipAccount sipAccount= new SipAccount(service,pjsipaccount);

        if (sipAccount != null) {
            SipCall sipCall = getCall(accountID, callID);
            if (sipCall != null) {
                int orientation = orient;
                setSelfVideoOrientation(sipCall, orientation);
            }
        }
    }

    void setSelfVideoOrientation(SipCall sipCall, int orientation) {
        try {
            int pjmediaOrientation;

            switch (orientation) {
                case Surface.ROTATION_0:   // Portrait
                    pjmediaOrientation = pjmedia_orient.PJMEDIA_ORIENT_ROTATE_270DEG;
                    break;
                case Surface.ROTATION_90:  // Landscape, home button on the right
                    pjmediaOrientation = pjmedia_orient.PJMEDIA_ORIENT_NATURAL;
                    break;
                case Surface.ROTATION_180:
                    pjmediaOrientation = pjmedia_orient.PJMEDIA_ORIENT_ROTATE_90DEG;
                    break;
                case Surface.ROTATION_270: // Landscape, home button on the left
                    pjmediaOrientation = pjmedia_orient.PJMEDIA_ORIENT_ROTATE_180DEG;
                    break;
                default:
                    pjmediaOrientation = pjmedia_orient.PJMEDIA_ORIENT_UNKNOWN;
            }

            if (pjmediaOrientation != pjmedia_orient.PJMEDIA_ORIENT_UNKNOWN)
                // set orientation to the correct current device
                getVidDevManager().setCaptureOrient(
                        sipCall.isFrontCamera()
                                ? FRONT_CAMERA_CAPTURE_DEVICE
                                : BACK_CAMERA_CAPTURE_DEVICE,
                        pjmediaOrientation, true);

        } catch (Exception iex) {
            Logger.error(TAG, "Error while changing video orientation");
        }
    }

    private void handleSetVideoMute(String accountID,int callID ,boolean ifmute) {

        SipCall sipCall = getCall(accountID, callID);
        if (sipCall != null) {
            boolean mute = ifmute;
            sipCall.setVideoMute(mute);
        }
    }

    private void handleStartVideoPreview(String accountID,int callID,Surface surf) {


        SipCall sipCall = getCall(accountID, callID);
        if (sipCall != null) {


                Surface surface = surf;
                sipCall.startPreviewVideoFeed(surface);

        }
    }

    private void handleStopVideoPreview(String accountID,int callID) {

        SipCall sipCall = getCall(accountID, callID);
        if (sipCall != null) {
            sipCall.stopPreviewVideoFeed();
        }
    }

    // Switch Camera
    private void handleSwitchVideoCaptureDevice(String accountID,int callID) {

        final SipCall sipCall = getCall(accountID, callID);
        if (sipCall != null) {
            try {
                CallVidSetStreamParam callVidSetStreamParam = new CallVidSetStreamParam();
                callVidSetStreamParam.setCapDev(sipCall.isFrontCamera()
                        ? BACK_CAMERA_CAPTURE_DEVICE
                        : FRONT_CAMERA_CAPTURE_DEVICE);
                sipCall.setFrontCamera(!sipCall.isFrontCamera());
                sipCall.vidSetStream(pjsua_call_vid_strm_op.PJSUA_CALL_VID_STRM_CHANGE_CAP_DEV, callVidSetStreamParam);
            } catch (Exception ex) {
                Logger.error(TAG, "Error while switching capture device", ex);
            }
        }
    }



    private void handleReconnectCall() {
        try {
            mBroadcastEmitter.callReconnectionState(CallReconnectionState.PROGRESS);
            mEndpoint.handleIpChange(new IpChangeParam());
            Logger.info(TAG, "Call reconnection started");
        } catch (Exception exc) {
            Logger.error(TAG, "Error while reconnecting the call", exc);
        }
    }

    protected synchronized AudDevManager getAudDevManager() {
        return mEndpoint.audDevManager();
    }

    protected synchronized VidDevManager getVidDevManager() {
        return mEndpoint.vidDevManager();
    }

    public int callAnswer(int callId, int code) {
        if (created) {
            try {
              Call call=  Call.lookup(callId);

                // Answer with 180 Ringing
                CallOpParam callOpParam = new CallOpParam();
                callOpParam.setStatusCode(pjsip_status_code.PJSIP_SC_OK);
                CallSetting callSetting = callOpParam.getOpt();
                callSetting.setAudioCount(1);
                callSetting.setReqKeyframeMethod(pjsua_vid_req_keyframe_method.PJSUA_VID_REQ_KEYFRAME_RTCP_PLI);

                if (!prefsWrapper.getPreferenceBooleanValue(SipConfigManager.USE_VIDEO)) {
                    callSetting.setVideoCount(0);
                    callSetting.setFlag(pjsua_call_flag.PJSUA_CALL_INCLUDE_DISABLED_MEDIA);
                }else
                {
                    callSetting.setVideoCount(1);
                }
                service.getUAStateReceiver().updateCallStatus(callId,code);
                     call.answer(callOpParam);



                Logger.debug(TAG, "Sending 180 ringing");

                String displayName, remoteUri;
                try {
                    CallerInfo contactInfo = new CallerInfo(call.getInfo());
                    displayName = contactInfo.getDisplayName();
                    remoteUri = contactInfo.getRemoteUri();
                } catch (Exception ex) {
                    Logger.error(TAG, "Error while getting caller info", ex);
                    throw ex;
                }

                // check for video in remote SDP
                CallInfo vcallInfo = call.getInfo();
                boolean isVideo = (vcallInfo.getRemOfferer() && vcallInfo.getRemVideoCount() > 0);

                service.getBroadcastEmitter().incomingCall(vcallInfo.getRemoteContact(), callId,
                        displayName, remoteUri, isVideo);
              return pj_constants_.PJ_TRUE;
            } catch (Exception ex) {
                Logger.error(TAG, "Error while getting caller info", ex);
            }

        }
        return -1;
    }

    public int callReinvite(int callId, boolean unhold)  {
        if (created) {

            try {
                CallOpParam param = new CallOpParam();
                Call call=Call.lookup(callId);

                if (!unhold) {
                    Logger.debug(TAG, "holding call with ID " + callId);
                    callHold(callId);

                } else {
                    // http://lists.pjsip.org/pipermail/pjsip_lists.pjsip.org/2015-March/018246.html
                    Logger.debug(TAG, "un-holding call with ID " + callId);
                    setMediaParams(param);
                    CallSetting opt = param.getOpt();
                    opt.setFlag(pjsua_call_flag.PJSUA_CALL_UNHOLD);

                    call.reinvite(param);
                }
                service.getBroadcastEmitter().callMediaState(
                        call.getInfo().getRemoteUri(), callId, MediaState.LOCAL_HOLD, unhold);
            } catch (Exception exc) {
                String operation = unhold ? "hold" : "unhold";
                Logger.error(TAG, "Error while trying to " + operation + " call", exc);
            }
        }
        return -1;
    }

    private void setMediaParams(CallOpParam param) {
        CallSetting callSetting = param.getOpt();
        callSetting.setAudioCount(1);
        //ll be back
    //    callSetting.setVideoCount(videoCall ? 1 : 0);
      //  callSetting.setReqKeyframeMethod(pjsua_vid_req_keyframe_method.PJSUA_VID_REQ_KEYFRAME_RTCP_PLI);
    }

    public int callHold(int callId)  {
        if (created) {
            try {
                Call call=  Call.lookup(callId);
                CallOpParam callOpParam = new CallOpParam();
                call.setHold(callOpParam);
                return pj_constants_.PJ_TRUE;
            }  catch(Exception e)
            {
                e.getStackTrace();
            }
        }
        return -1;
    }


    public int callHangup(int callId, int code)  {
        if (created) {
            try {
                 Call cll=Call.lookup(callId);
                CallOpParam param = new CallOpParam();
                param.setStatusCode(code);

                cll.hangup(param);
                service.getUAStateReceiver().updateCallStatus(callId,code);
                service.getUAStateReceiver().deleteCall(callId);
                return pj_constants_.PJ_TRUE;
            }  catch(Exception e)
            {
                e.getStackTrace();
            }
        }
        return pj_constants_.PJ_FALSE;
    }

    public int callXfer(int callId, String callee)  {
        if (created) {
            try {
                SipCallSessionImpl callInfoSession = service.getUAStateReceiver().updateCallInfoFromStack(callId, null);
             //   SipCall call= callInfoSession.getSipCall();
             //   call.transferTo(callee);
                return 1;
            }  catch(Exception e)
            {
                e.getStackTrace();
            }
        }
        return -1;
    }
    public int callXferReplace(int callId, int otherCallId, int options)  {
        if (created) {
            try {
                Call call= Call.lookup(callId);
                Call otherCall= Call.lookup(otherCallId);
                CallOpParam param = new CallOpParam();
               call.xferReplaces(otherCall,param);
                return 1;
            }  catch(Exception e)
            {
                e.getStackTrace();
            }
        }
        return -1;
    }

    public long getRxTxLevel(int port) {
        long[] rx_level = new long[1];
        long[] tx_level = new long[1];
       // pjsua.conf_get_signal_level(port, tx_level, rx_level);
        return (rx_level[0] << 8 | tx_level[0]);
    }

    public static ConcurrentHashMap<String, SipAccount> getActiveSipAccounts() {
        return mActiveSipAccounts;
    }




}
