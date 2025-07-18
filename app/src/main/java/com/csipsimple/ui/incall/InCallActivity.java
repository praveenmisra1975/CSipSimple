/**
 * Copyright (C) 2010-2012 Regis Montoya (aka r3gis - www.r3gis.fr)
 * This file is part of CSipSimple.
 *  CSipSimple is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  If you own a pjsip commercial license you can also redistribute it
 *  and/or modify it under the terms of the GNU Lesser General Public License
 *  as an android library.
 *  CSipSimple is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  You should have received a copy of the GNU General Public License
 *  along with CSipSimple.  If not, see <http://www.gnu.org/licenses/>.
 */


package com.csipsimple.ui.incall;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RemoteException;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

//import com.actionbarsherlock.app.SherlockFragmentActivity;
import android.support.v7.app.AppCompatActivity;
import com.csipsimple.R;
import com.csipsimple.api.ISipService;
import com.csipsimple.api.MediaState;
import com.csipsimple.api.SipConfigManager;
import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipProfile;
import com.csipsimple.service.SipService;
import com.csipsimple.api.SipCallSessionImpl;
import com.csipsimple.sipservice.Logger;
import com.csipsimple.ui.PickupSipUri;
import com.csipsimple.ui.incall.CallProximityManager.ProximityDirector;
import com.csipsimple.ui.incall.DtmfDialogFragment.OnDtmfListener;
import com.csipsimple.ui.incall.locker.IOnLeftRightChoice;
import com.csipsimple.ui.incall.locker.InCallAnswerControls;
import com.csipsimple.ui.incall.locker.ScreenLocker;
import com.csipsimple.utils.CallsUtils;
import com.csipsimple.utils.DialingFeedback;
import com.csipsimple.utils.Log;
import com.csipsimple.utils.PreferencesProviderWrapper;
import com.csipsimple.utils.Theme;
import com.csipsimple.utils.keyguard.KeyguardWrapper;


import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class InCallActivity extends AppCompatActivity implements IOnCallActionTrigger,
        IOnLeftRightChoice, ProximityDirector, OnDtmfListener {
    private static final int QUIT_DELAY = 300;
    private final static String THIS_FILE = "InCallActivity";
    //private final static int DRAGGING_DELAY = 150;
    

    private Object callMutex = new Object();
    private SipCallSessionImpl[] callsInfo = null;
    private MediaState lastMediaState;
    
    
    private ViewGroup mainFrame;
    private InCallControls inCallControls;

    // Screen wake lock for incoming call
    private WakeLock wakeLock;
    // Screen wake lock for video
    private WakeLock videoWakeLock;

    private InCallInfoGrid activeCallsGrid;
    private Timer quitTimer;

    // private LinearLayout detailedContainer, holdContainer;

    // True if running unit tests
    // private boolean inTest;


    private DialingFeedback dialFeedback;
    private PowerManager powerManager;
    private PreferencesProviderWrapper prefsWrapper;

    // Dnd views
    //private ImageView endCallTarget, holdTarget, answerTarget, xferTarget;
    //private Rect endCallTargetRect, holdTargetRect, answerTargetRect, xferTargetRect;
    

    private SurfaceView cameraPreview;
    private CallProximityManager proximityManager;
    private KeyguardWrapper keyguardManager;
    
    private boolean useAutoDetectSpeaker = false;
    private InCallAnswerControls inCallAnswerControls;
    private CallsAdapter activeCallsAdapter;
    private InCallInfoGrid heldCallsGrid;
    private CallsAdapter heldCallsAdapter;

    private final static int PICKUP_SIP_URI_XFER = 0;
    private final static int PICKUP_SIP_URI_NEW_CALL = 1;
    private static final String CALL_ID = "call_id";
    

    @SuppressLint({"InvalidWakeLockTag", "UnspecifiedRegisterReceiverFlag"})
    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //handler.setActivityInstance(this);
        Logger.debug(THIS_FILE, "Create in call");
        setContentView(R.layout.in_call_main);
        Bundle b = getIntent().getBundleExtra("EXTRA_CALL_INFO");
        SipCallSessionImpl initialSession = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            initialSession = b.getParcelable(SipManager.EXTRA_CALL_INFO, SipCallSessionImpl.class);
        } else if(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
        {
            initialSession = b.getParcelable(SipManager.EXTRA_CALL_INFO);

        }
        Logger.debug(THIS_FILE,"befoe callmutex" );

          synchronized (callMutex) {
            callsInfo = new SipCallSessionImpl[1];
            callsInfo[0] = initialSession;
        }
        Logger.debug(THIS_FILE,"initialSession of SipCallSessionImpl incallactivity >> session state" +initialSession.getCallState());


        bindService(new Intent(this, SipService.class), connection, Context.BIND_AUTO_CREATE);

        Logger.debug(THIS_FILE,"incallactivity bind bindService()");


        prefsWrapper = new PreferencesProviderWrapper(this);


        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                "com.csipsimple.onIncomingCall");
        wakeLock.setReferenceCounted(false);
        
        
        takeKeyEvents(true);
        
        // Cache findViews
        mainFrame = (ViewGroup) findViewById(R.id.mainFrame);
        inCallControls = (InCallControls) findViewById(R.id.inCallControls);
        inCallAnswerControls = (InCallAnswerControls) findViewById(R.id.inCallAnswerControls);
        activeCallsGrid = (InCallInfoGrid) findViewById(R.id.activeCallsGrid);
        heldCallsGrid = (InCallInfoGrid) findViewById(R.id.heldCallsGrid);

        // Bind
        //attachVideoPreview();

        inCallControls.setOnTriggerListener(this);
        inCallAnswerControls.setOnTriggerListener(this);

        if(activeCallsAdapter == null) {
            activeCallsAdapter = new CallsAdapter(true);
        }
        activeCallsGrid.setAdapter(activeCallsAdapter);
        

        if(heldCallsAdapter == null) {
            heldCallsAdapter = new CallsAdapter(false);
        }
        heldCallsGrid.setAdapter(heldCallsAdapter);

        
        ScreenLocker lockOverlay = (ScreenLocker) findViewById(R.id.lockerOverlay);
        lockOverlay.setActivity(this);
        lockOverlay.setOnLeftRightListener(this);

        // Listen to media & sip events to update the UI
        registerReceiver(callStateReceiver, new IntentFilter(SipManager.ACTION_SIP_CALL_CHANGED));
        registerReceiver(callStateReceiver, new IntentFilter(SipManager.ACTION_SIP_MEDIA_CHANGED));

        
        proximityManager = new CallProximityManager(this, this, lockOverlay);
        keyguardManager = KeyguardWrapper.getKeyguardManager(this);

        dialFeedback = new DialingFeedback(this, true);

        if (prefsWrapper.getPreferenceBooleanValue(SipConfigManager.PREVENT_SCREEN_ROTATION)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        if (quitTimer == null) {
            quitTimer = new Timer("Quit-timer");
        }

        
        useAutoDetectSpeaker = prefsWrapper.getPreferenceBooleanValue(SipConfigManager.AUTO_DETECT_SPEAKER);
        
        applyTheme();
        proximityManager.startTracking();
        
        inCallControls.setCallState(initialSession);
        inCallAnswerControls.setCallState(initialSession);
    }
    

    @Override
    protected void onStart() {
        Logger.debug(THIS_FILE, "Start in call");
        super.onStart();
        
        keyguardManager.unlock();
    }

    @Override
    protected void onResume() {
        super.onResume();

        dialFeedback.resume();
        runOnUiThread(new UpdateUIFromCallRunnable());
        
    }

    @Override
    protected void onPause() {
        super.onPause();
        dialFeedback.pause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        keyguardManager.lock();
    }

    @Override
    protected void onDestroy() {

        if(infoDialog != null) {
            infoDialog.dismiss();
        }
        
        if (quitTimer != null) {
            quitTimer.cancel();
            quitTimer.purge();
            quitTimer = null;
        }


        try {
            unbindService(connection);
        } catch (Exception e) {
            // Just ignore that
        }
        service = null;
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        proximityManager.stopTracking();
        proximityManager.release(0);
        try {
            unregisterReceiver(callStateReceiver);
        } catch (IllegalArgumentException e) {
            // That's the case if not registered (early quit)
        }
        
        if(activeCallsGrid != null) {
            activeCallsGrid.terminate();
        }
        
        //detachVideoPreview();
        //handler.setActivityInstance(null);
        super.onDestroy();
    }
    
    @SuppressWarnings("deprecation")
  /*  private void attachVideoPreview() {
        // Video stuff
        if(prefsWrapper.getPreferenceBooleanValue(SipConfigManager.USE_VIDEO)) {
            if(cameraPreview == null) {
                Logger.debug(THIS_FILE, "Create Local Renderer");
                cameraPreview = ViERenderer.CreateLocalRenderer(this);
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(256, 256);
                //lp.leftMargin = 2;
                //lp.topMargin= 4;
                lp.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
                cameraPreview.setVisibility(View.GONE);
                mainFrame.addView(cameraPreview, lp);
            }else {
                Logger.debug(THIS_FILE, "NO NEED TO Create Local Renderer");
            }
            
            if(videoWakeLock == null) {
                videoWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "com.csipsimple.videoCall");
                videoWakeLock.setReferenceCounted(false);
            }
        }

        if(videoWakeLock != null && videoWakeLock.isHeld()) {
            videoWakeLock.release();
        }
    }*/
    
  /*  private void detachVideoPreview() {
        if(mainFrame != null && cameraPreview != null) {
            mainFrame.removeView(cameraPreview);
        }
        if(videoWakeLock != null && videoWakeLock.isHeld()) {
            videoWakeLock.release();
        }
        if(cameraPreview != null) {
            cameraPreview = null;
        }
    }*/

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        // TODO : update UI
        Logger.debug(THIS_FILE, "New intent is launched");
        super.onNewIntent(intent);
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Logger.debug(THIS_FILE, "Configuration changed");
        if(cameraPreview != null && cameraPreview.getVisibility() == View.VISIBLE) {
            
            cameraPreview.setVisibility(View.GONE);
        }
        runOnUiThread(new UpdateUIFromCallRunnable());
    }

    private void applyTheme() {
        Theme t = Theme.getCurrentTheme(this);
        if (t != null) {
            // TODO ...
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case PICKUP_SIP_URI_XFER:
                if (resultCode == RESULT_OK && service != null) {
                    String callee = data.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                    int callId = data.getIntExtra(CALL_ID, -1);
                    if(callId != -1) {
                        try {
                            service.xfer((int) callId, callee);
                        } catch (RemoteException e) {
                            // TODO : toaster
                        }
                    }
                }
                return;
            case PICKUP_SIP_URI_NEW_CALL:
                if (resultCode == RESULT_OK && service != null) {
                    String callee = data.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                    long accountId = data.getLongExtra(SipProfile.FIELD_ID,
                            SipProfile.INVALID_ID);
                    if (accountId != SipProfile.INVALID_ID) {
                        try {
                            service.makeCall(callee, (int) accountId);
                        } catch (RemoteException e) {
                            // TODO : toaster
                        }
                    }
                }
                return;
            default:
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    private SipCallSessionImpl getActiveCallInfo() {
        SipCallSessionImpl currentCallInfo = null;
        if (callsInfo == null) {
            return null;
        }
        for (SipCallSessionImpl callInfo : callsInfo) {
           currentCallInfo = getPrioritaryCall(callInfo, currentCallInfo);
        }
        return currentCallInfo;
    }


    /**
     * Update the user interface from calls state.
     */
    private class UpdateUIFromCallRunnable implements Runnable {

        
        @Override
        public void run() {
           Logger.debug( THIS_FILE ,"in run of UpdateUIFromCallRunnable");
            // Current call is the call emphasis by the UI.
            SipCallSessionImpl mainCallInfo = null;
    
            int mainsCalls = 0;
            int heldsCalls = 0;
    
            synchronized (callMutex) {
                if (callsInfo != null) {
                    for (SipCallSessionImpl callInfo : callsInfo) {
                        Logger.debug(THIS_FILE,
                                "We have a call " + callInfo.getCallId() + " / " + callInfo.getCallState()
                                        + "/" + callInfo.getMediaStatus());
        
                        if (!callInfo.isAfterEnded()) {
                            if (callInfo.isLocalHeld()) {
                                heldsCalls++;
                            } else {
                                mainsCalls++;
                            }
                        }
                        mainCallInfo = getPrioritaryCall(callInfo, mainCallInfo);

                    }
                }
            }
            
            // Update call control visibility - must be done before call cards 
            // because badge avail size depends on that
            if ((mainsCalls + heldsCalls) >= 1) {
                // Update in call actions
                inCallControls.setCallState(mainCallInfo);
                inCallAnswerControls.setCallState(mainCallInfo);
            } else {
                inCallControls.setCallState(null);
                inCallAnswerControls.setCallState(null);
            }
            
            heldCallsGrid.setVisibility((heldsCalls > 0)? View.VISIBLE : View.GONE);
            
            activeCallsAdapter.notifyDataSetChanged();
            heldCallsAdapter.notifyDataSetChanged();
            
            //findViewById(R.id.inCallContainer).requestLayout();
            
            if (mainCallInfo != null) {
                Logger.debug(THIS_FILE, "Active call is " + mainCallInfo.getCallId());
                Logger.debug(THIS_FILE, "Update ui from call " + mainCallInfo.getCallId() + " state "
                        + CallsUtils.getStringCallState(mainCallInfo, InCallActivity.this));
                int state = mainCallInfo.getCallState();
    
                //int backgroundResId = R.drawable.bg_in_call_gradient_unidentified;
    
                // We manage wake lock
                switch (state) {
                    case SipCallSessionImpl.InvState.INCOMING:
                    case SipCallSessionImpl.InvState.EARLY:
                    case SipCallSessionImpl.InvState.CALLING:
                    case SipCallSessionImpl.InvState.CONNECTING:
    
                        Logger.debug(THIS_FILE, "Acquire wake up lock");
                        if (wakeLock != null && !wakeLock.isHeld()) {
                            wakeLock.acquire();
                        }
                        break;
                    case SipCallSessionImpl.InvState.CONFIRMED:
                        break;
                    case SipCallSessionImpl.InvState.NULL:
                    case SipCallSessionImpl.InvState.DISCONNECTED:
                        Logger.debug(THIS_FILE, "Active call session is disconnected or null wait for quit...");
                        // This will release locks
                       // onDisplayVideo(false);
                        delayedQuit();
                        return;
    
                }
                
                Logger.debug(THIS_FILE, "we leave the update ui function");
            }
            
            proximityManager.updateProximitySensorMode();
            
            if (heldsCalls + mainsCalls == 0) {
                delayedQuit();
            }
        }
    }

    /**
     * Get the call with the higher priority comparing two calls
     * @param call1 First call object to compare
     * @param call2 Second call object to compare
     * @return The call object with highest priority
     */
    private SipCallSessionImpl getPrioritaryCall(SipCallSessionImpl call1, SipCallSessionImpl call2) {
        // We prefer the not null
        if (call1 == null) {
            return call2;
        } else if (call2 == null) {
            return call1;
        }
        // We prefer the one not terminated
        if (call1.isAfterEnded()) {
            return call2;
        } else if (call2.isAfterEnded()) {
            return call1;
        }
        // We prefer the one not held
        if (call1.isLocalHeld()) {
            return call2;
        } else if (call2.isLocalHeld()) {
            return call1;
        }
        // We prefer the older call
        // to keep consistancy on what will be replied if new call arrives
        return (call1.getCallStart() > call2.getCallStart()) ? call2 : call1;
    }


    @Override
    public void onDisplayVideo(boolean show) {
        runOnUiThread(new UpdateVideoPreviewRunnable(show));
    }
    
    /**
     * Update ui from media state.
     */
    private class UpdateUIFromMediaRunnable implements Runnable {
        @Override
        public void run() {
            inCallControls.setMediaState(lastMediaState);
            proximityManager.updateProximitySensorMode();
        }
    }
    
    private class UpdateVideoPreviewRunnable implements Runnable {
        private final boolean show;
        UpdateVideoPreviewRunnable(boolean show){
            this.show = show;
        }
        @Override
        public void run() {
            // Update the camera preview visibility 
            if(cameraPreview != null) {
                cameraPreview.setVisibility(show ? View.VISIBLE : View.GONE);
                if(show) {
                    if(videoWakeLock != null) {
                        videoWakeLock.acquire();
                    }
                    SipService.setVideoWindow(SipCallSessionImpl.INVALID_CALL_ID, cameraPreview, true);
                }else {
                    if(videoWakeLock != null && videoWakeLock.isHeld()) {
                        videoWakeLock.release();
                    }
                    SipService.setVideoWindow(SipCallSessionImpl.INVALID_CALL_ID, null, true);
                }
            }else {
                Log.w(THIS_FILE, "No camera preview available to be shown");
            }
        }
    }
    

    /*
    private void setSubViewVisibilitySafely(int id, boolean visible) {
        View v = findViewById(id);
        if(v != null) {
            v.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
    private class UpdateDraggingRunnable implements Runnable {
        private DraggingInfo di;
        
        UpdateDraggingRunnable(DraggingInfo draggingInfo){
            di = draggingInfo;
        }
        
        public void run() {
            inCallControls.setVisibility(di.isDragging ? View.GONE : View.VISIBLE);
            findViewById(R.id.dropZones).setVisibility(di.isDragging ? View.VISIBLE : View.GONE);
            
            setSubViewVisibilitySafely(R.id.dropHangup, di.isDragging);
            setSubViewVisibilitySafely(R.id.dropHold, (di.isDragging && di.call.isActive() && !di.call.isBeforeConfirmed()));
            setSubViewVisibilitySafely(R.id.dropAnswer, (di.call.isActive() && di.call.isBeforeConfirmed()
                    && di.call.isIncoming() && di.isDragging));
            setSubViewVisibilitySafely(R.id.dropXfer, (!di.call.isBeforeConfirmed()
                    && !di.call.isAfterEnded() && di.isDragging));
            
        }
    }
    */
    
    private synchronized void delayedQuit() {

        if (wakeLock != null && wakeLock.isHeld()) {
            Logger.debug(THIS_FILE, "Releasing wake up lock");
            wakeLock.release();
        }
        
        proximityManager.release(0);
        
        activeCallsGrid.setVisibility(View.VISIBLE);
        inCallControls.setVisibility(View.GONE);

        Logger.debug(THIS_FILE, "Start quit timer");
        if (quitTimer != null) {
            quitTimer.schedule(new QuitTimerTask(), QUIT_DELAY);
        } else {
            finish();
        }
    }

    private class QuitTimerTask extends TimerTask {
        @Override
        public void run() {
            Logger.debug(THIS_FILE, "Run quit timer");
            finish();
        }
    };



    private void showDialpad(int callId) {
        DtmfDialogFragment newFragment = DtmfDialogFragment.newInstance(callId);
        newFragment.show(getSupportFragmentManager(), "dialog");
    }
    


    @Override
    public void OnDtmf(int callId, int keyCode, int dialTone) {
        proximityManager.restartTimer();

        if (service != null) {
            if (callId != SipCallSessionImpl.INVALID_CALL_ID) {
                try {
                    service.sendDtmf(callId, keyCode);
                    dialFeedback.giveFeedback(dialTone);
                } catch (RemoteException e) {
                    Logger.debug(THIS_FILE, "Was not able to send dtmf tone"+ e);
                }
            }
        }
        
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Logger.debug(THIS_FILE, "Key down : " + keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
                //
                // Volume has been adjusted by the user.
                //
                Logger.debug(THIS_FILE, "onKeyDown: Volume button pressed");
                int action = AudioManager.ADJUST_RAISE;
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    action = AudioManager.ADJUST_LOWER;
                }

                // Detect if ringing
                SipCallSessionImpl currentCallInfo = getActiveCallInfo();
                // If not any active call active
                if (currentCallInfo == null && serviceConnected) {
                    break;
                }

                if (service != null) {
                    try {
                        service.adjustVolume(currentCallInfo, action, AudioManager.FLAG_SHOW_UI);
                    } catch (RemoteException e) {
                        Logger.debug(THIS_FILE, "Can't adjust volume"+ e);
                    }
                }

                return true;
            case KeyEvent.KEYCODE_CALL:
            case KeyEvent.KEYCODE_ENDCALL:
                return inCallAnswerControls.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_SEARCH:
                // Prevent search
                return true;
            default:
                // Nothing to do
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Logger.debug(THIS_FILE, "Key up : " + keyCode);
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_CALL:
            case KeyEvent.KEYCODE_SEARCH:
                return true;
            case KeyEvent.KEYCODE_ENDCALL:
                return inCallAnswerControls.onKeyDown(keyCode, event);

        }
        return super.onKeyUp(keyCode, event);
    }


    private BroadcastReceiver callStateReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.debug(THIS_FILE, "in BroadcastReceiver callStateReceiver");
            String action = intent.getAction();

            if (action.equals(SipManager.ACTION_SIP_CALL_CHANGED)) {
                if (service != null) {
                    try {
                        synchronized (callMutex) {
                            callsInfo = service.getCalls();
                            runOnUiThread(new UpdateUIFromCallRunnable());
                        }
                    } catch (RemoteException e) {
                        Logger.debug(THIS_FILE, "Not able to retrieve calls");
                    }
                }
            } else if (action.equals(SipManager.ACTION_SIP_MEDIA_CHANGED)) {
                if (service != null) {
                    MediaState mediaState;
                    try {
                        mediaState = service.getCurrentMediaState();
                        Logger.debug(THIS_FILE, "Media update ...." + mediaState.isSpeakerphoneOn);
                        synchronized (callMutex) {
                            if (!mediaState.equals(lastMediaState)) {
                                lastMediaState = mediaState;
                                runOnUiThread(new UpdateUIFromMediaRunnable());
                            }   
                        }

                    } catch (RemoteException e) {
                        Logger.debug(THIS_FILE, "Can't get the media state "+ e);
                    }
                }
            }
        }
    };

    /**
     * Service binding
     */
    private boolean serviceConnected = false;
    private ISipService service;
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {

            Logger.debug(THIS_FILE, "onServiceConnected of IncallActivity ");


            service = ISipService.Stub.asInterface(arg1);
           // try {
                // Logger.debug(THIS_FILE,
                // "Service started get real call info "+callInfo.getCallId());
                Logger.debug(THIS_FILE, "onServiceConnected of service.getCalls() Incall Activity ");
               // callsInfo = service.getCalls();
                Logger.debug(THIS_FILE, "ce.getCalls() Incall Activity ");

                serviceConnected = true;

                runOnUiThread(new UpdateUIFromCallRunnable());
                runOnUiThread(new UpdateUIFromMediaRunnable());

          //  } catch (RemoteException e) {
            //    Logger.debug(THIS_FILE, "Can't get back the call"+ e);
           // }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            serviceConnected = false;
            callsInfo = null;
        }
    };
    private AlertDialog infoDialog;

    // private boolean showDetails = true;


    @Override
    public void onTrigger(int whichAction, final SipCallSessionImpl call) {

        // Sanity check for actions requiring valid call id
        if (whichAction == TAKE_CALL || whichAction == REJECT_CALL || whichAction == DONT_TAKE_CALL ||
            whichAction == TERMINATE_CALL || whichAction == DETAILED_DISPLAY || 
            whichAction == TOGGLE_HOLD || whichAction == START_RECORDING ||
            whichAction == STOP_RECORDING || whichAction == DTMF_DISPLAY ||
            whichAction == XFER_CALL || whichAction == TRANSFER_CALL ||
            whichAction == START_VIDEO || whichAction == STOP_VIDEO ) {
            // We check that current call is valid for any actions
            if (call == null) {
                Logger.debug(THIS_FILE, "Try to do an action on a null call !!!");
                return;
            }
            if (call.getCallId() == SipCallSessionImpl.INVALID_CALL_ID) {
                Logger.debug(THIS_FILE, "Try to do an action on an invalid call !!!");
                return;
            }
        }

        // Reset proximity sensor timer
        proximityManager.restartTimer();
        
        try {
            switch (whichAction) {
                case TAKE_CALL: {
                    if (service != null) {
                        Logger.debug(THIS_FILE, "Answer call " + call.getCallId());

                        boolean shouldHoldOthers = false;

                        // Well actually we should be always before confirmed
                        if (call.isBeforeConfirmed()) {
                            shouldHoldOthers = true;
                        }

                        service.answer(call.getCallId(), SipCallSessionImpl.StatusCode.OK);

                        // if it's a ringing call, we assume that user wants to
                        // hold other calls
                        if (shouldHoldOthers && callsInfo != null) {
                            for (SipCallSessionImpl callInfo : callsInfo) {
                                // For each active and running call
                                if (SipCallSessionImpl.InvState.CONFIRMED == callInfo.getCallState()
                                        && !callInfo.isLocalHeld()
                                        && callInfo.getCallId() != call.getCallId()) {

                                    Logger.debug(THIS_FILE, "Hold call " + callInfo.getCallId());
                                    service.hold(callInfo.getCallId());

                                }
                            }
                        }
                    }
                    break;
                }
                case DONT_TAKE_CALL: {
                    if (service != null) {
                        service.hangup(call.getCallId(), SipCallSessionImpl.StatusCode.BUSY_HERE);
                    }
                    break;
                }
                case REJECT_CALL:
                case TERMINATE_CALL: {
                    if (service != null) {
                        service.hangup(call.getCallId(), 0);
                    }
                    break;
                }
                case MUTE_ON:
                case MUTE_OFF: {
                    if (service != null) {
                        service.setMicrophoneMute((whichAction == MUTE_ON) ? true : false);
                    }
                    break;
                }
                case SPEAKER_ON:
                case SPEAKER_OFF: {
                    if (service != null) {
                        Logger.debug(THIS_FILE, "Manually switch to speaker");
                        useAutoDetectSpeaker = false;
                        service.setSpeakerphoneOn((whichAction == SPEAKER_ON) ? true : false);
                    }
                    break;
                }
                case BLUETOOTH_ON:
                case BLUETOOTH_OFF: {
                    if (service != null) {
                        service.setBluetoothOn((whichAction == BLUETOOTH_ON) ? true : false);
                    }
                    break;
                }
                case DTMF_DISPLAY: {
                    showDialpad(call.getCallId());
                    break;
                }
                case DETAILED_DISPLAY: {
                    if (service != null) {
                        if(infoDialog != null) {
                            infoDialog.dismiss();
                        }
                        String infos = service.showCallInfosDialog(call.getCallId());
                        String natType = service.getLocalNatType();
                        SpannableStringBuilder buf = new SpannableStringBuilder();
                        Builder builder = new AlertDialog.Builder(this);

                        buf.append(infos);
                        if(!TextUtils.isEmpty(natType)) {
                            buf.append("\r\nLocal NAT type detected : ");
                            buf.append(natType);
                        }
                        TextAppearanceSpan textSmallSpan = new TextAppearanceSpan(this,
                                android.R.style.TextAppearance_Small);
                        buf.setSpan(textSmallSpan, 0, buf.length(),
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                        infoDialog = builder.setIcon(android.R.drawable.ic_dialog_info)
                                .setMessage(buf)
                                .setNeutralButton(R.string.ok, null)
                                .create();
                        infoDialog.show();
                    }
                    break;
                }
                case TOGGLE_HOLD: {
                    if (service != null) {
                        // Logger.debug(THIS_FILE,
                        // "Current state is : "+callInfo.getCallState().name()+" / "+callInfo.getMediaStatus().name());
                        if (call.getMediaStatus() == SipCallSessionImpl.MediaState.LOCAL_HOLD ||
                                call.getMediaStatus() == SipCallSessionImpl.MediaState.NONE) {
                            service.reinvite(call.getCallId(), true);
                        } else {
                            service.hold(call.getCallId());
                        }
                    }
                    break;
                }
                case MEDIA_SETTINGS: {
                    startActivity(new Intent(this, InCallMediaControl.class));
                    break;
                }
                case XFER_CALL: {
                    Intent pickupIntent = new Intent(this, PickupSipUri.class);
                    pickupIntent.putExtra(CALL_ID, call.getCallId());
                    startActivityForResult(pickupIntent, PICKUP_SIP_URI_XFER);
                    break;
                }
                case TRANSFER_CALL: {
                    final ArrayList<SipCallSessionImpl> remoteCalls = new ArrayList<SipCallSessionImpl>();
                    if(callsInfo != null) {
                        for(SipCallSessionImpl remoteCall : callsInfo) {
                            // Verify not current call
                            if(remoteCall.getCallId() != call.getCallId() && remoteCall.isOngoing()) {
                                remoteCalls.add(remoteCall);
                            }
                        }
                    }

                    if(remoteCalls.size() > 0) {
                        Builder builder = new AlertDialog.Builder(this);
                        CharSequence[] simpleAdapter = new String[remoteCalls.size()];
                        for(int i = 0; i < remoteCalls.size(); i++) {
                            simpleAdapter[i] = remoteCalls.get(i).getRemoteContact();
                        }
                        builder.setSingleChoiceItems(simpleAdapter , -1, new Dialog.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (service != null) {
                                            try {
                                                // 1 = PJSUA_XFER_NO_REQUIRE_REPLACES
                                                service.xferReplace(call.getCallId(), remoteCalls.get(which).getCallId(), 1);
                                            } catch (RemoteException e) {
                                                Logger.debug(THIS_FILE, "Was not able to call service method"+e);
                                            }
                                        }
                                        dialog.dismiss();
                                    }
                                })
                                .setCancelable(true)
                                .setNeutralButton(R.string.cancel, new Dialog.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                })
                                .show();
                    }
                    
                    break;
                }
                case ADD_CALL: {
                    Intent pickupIntent = new Intent(this, PickupSipUri.class);
                    startActivityForResult(pickupIntent, PICKUP_SIP_URI_NEW_CALL);
                    break;
                }
                case START_RECORDING :{
                    if(service != null) {
                        // TODO : add a tweaky setting for two channel recording in different files.
                        // Would just result here in two calls to start recording with different bitmask
                        service.startRecording(call.getCallId(), SipManager.BITMASK_ALL);
                    }
                    break;
                }
                case STOP_RECORDING : {
                    if(service != null) {
                        service.stopRecording(call.getCallId());
                    }
                    break;
                }
                case START_VIDEO :
                case STOP_VIDEO : {
                    if(service != null) {
                        Bundle opts = new Bundle();
                        opts.putBoolean(SipCallSessionImpl.OPT_CALL_VIDEO, whichAction == START_VIDEO);
                        service.updateCallOptions(call.getCallId(), opts);
                    }
                    break;
                }
                case ZRTP_TRUST : {
                    if(service != null) {
                        service.zrtpSASVerified(call.getCallId());
                    }
                    break;
                }
                case ZRTP_REVOKE : {
                    if(service != null) {
                        service.zrtpSASRevoke(call.getCallId());
                    }
                    break;
                }
            }
        } catch (RemoteException e) {
            Logger.debug(THIS_FILE, "Was not able to call service method"+ e);
        }
    }
    


    @Override
    public void onLeftRightChoice(int whichHandle) {
        switch (whichHandle) {
            case LEFT_HANDLE:
                Logger.debug(THIS_FILE, "We unlock");
                proximityManager.release(0);
                proximityManager.restartTimer();
                break;
            case RIGHT_HANDLE:
                Logger.debug(THIS_FILE, "We clear the call");
                onTrigger(IOnCallActionTrigger.TERMINATE_CALL, getActiveCallInfo());
                proximityManager.release(0);
            default:
                break;
        }

    }

    
    private class ShowZRTPInfoRunnable implements Runnable, DialogInterface.OnClickListener {
        private String sasString;
        private SipCallSessionImpl callSession;

        public ShowZRTPInfoRunnable(SipCallSessionImpl call, String sas) {
            callSession = call;
            sasString = sas;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if(which == DialogInterface.BUTTON_POSITIVE) {
                Logger.debug(THIS_FILE, "ZRTP confirmed");
                if (service != null) {
                    try {
                        service.zrtpSASVerified(callSession.getCallId());
                    } catch (RemoteException e) {
                        Logger.debug(THIS_FILE, "Error while calling service"+e);
                    }
                    dialog.dismiss();
                }
            }else if(which == DialogInterface.BUTTON_NEGATIVE) {
                dialog.dismiss();
            }
        }
        @Override
        public void run() {
            AlertDialog.Builder builder = new AlertDialog.Builder(InCallActivity.this);
            Resources r = getResources();
            builder.setTitle("ZRTP supported by remote party");
            builder.setMessage("Do you confirm the SAS : " + sasString);
            builder.setPositiveButton(r.getString(R.string.yes), this);
            builder.setNegativeButton(r.getString(R.string.no), this);

            AlertDialog backupDialog = builder.create();
            backupDialog.show();
        }
    }
    

    
    @Override
    public boolean shouldActivateProximity() {

        // TODO : missing headset & keyboard open
        if(lastMediaState != null) {
            if(lastMediaState.isBluetoothScoOn) {
                return false;
            }
            if(lastMediaState.isSpeakerphoneOn && ! useAutoDetectSpeaker) {
                // Imediate reason to not enable proximity sensor
                return false;
            }
        }
        
        if (callsInfo == null) {
            return false;
        }

        boolean isValidCallState = true;
        int count = 0;
        for (SipCallSessionImpl callInfo : callsInfo) {
            if(callInfo.mediaHasVideo()) {
                return false;
            }
            if(!callInfo.isAfterEnded()) {
                int state = callInfo.getCallState();
                
                isValidCallState &= (
                        (state == SipCallSessionImpl.InvState.CONFIRMED) ||
                        (state == SipCallSessionImpl.InvState.CONNECTING) ||
                        (state == SipCallSessionImpl.InvState.CALLING) ||
                        (state == SipCallSessionImpl.InvState.EARLY && !callInfo.isIncoming())
                        );
                count ++;
            }
        }
        if(count == 0) {
            return false;
        }

        return isValidCallState;
    }

    @Override
    public void onProximityTrackingChanged(boolean acquired) {
        if(useAutoDetectSpeaker && service != null) {
            if(acquired) {
                if(lastMediaState == null || lastMediaState.isSpeakerphoneOn) {
                    try {
                        service.setSpeakerphoneOn(false);
                    } catch (RemoteException e) {
                        Logger.debug(THIS_FILE, "Can't run speaker change");
                    }
                }
            }else {
                if(lastMediaState == null || !lastMediaState.isSpeakerphoneOn) {
                    try {
                        service.setSpeakerphoneOn(true);
                    } catch (RemoteException e) {
                        Logger.debug(THIS_FILE, "Can't run speaker change");
                    }
                }
            }
        }
    }

    
    // Active call adapter
    private class CallsAdapter extends BaseAdapter {
        
        private boolean mActiveCalls;
        
        private SparseArray<Long> seenConnected = new SparseArray<Long>();
        
        public CallsAdapter(boolean notOnHold) {
            mActiveCalls = notOnHold;
        }

        private boolean isValidCallForAdapter(SipCallSessionImpl call) {
            boolean holdStateOk = false;
            if(mActiveCalls && !call.isLocalHeld()) {
                holdStateOk = true;
            }
            if(!mActiveCalls && call.isLocalHeld()) {
                holdStateOk = true;
            }
            if(holdStateOk) {
                long currentTime = System.currentTimeMillis();
                if(call.isAfterEnded()) {
                    // Only valid if we already seen this call in this adapter to be valid
                    if(hasNoMoreActiveCall() && seenConnected.get(call.getCallId(), currentTime + 2 * QUIT_DELAY) < currentTime + QUIT_DELAY) {
                        return true;
                    }else {
                        seenConnected.delete(call.getCallId());
                        return false;
                    }
                }else {
                    seenConnected.put(call.getCallId(), currentTime);
                    return true;
                }
            }
            return false;
        }
        
        private boolean hasNoMoreActiveCall() {
            synchronized (callMutex) {
                if(callsInfo == null) {
                    return true;
                }
                
                for(SipCallSessionImpl call : callsInfo) {
                    // As soon as we have one not after ended, we have at least active call
                    if(!call.isAfterEnded()) {
                        return false;
                    }
                }
                
            }
            return true;
        }
        
        @Override
        public int getCount() {
            int count = 0;
            synchronized (callMutex) {
                if(callsInfo == null) {
                    return 0;
                }
                
                for(SipCallSessionImpl call : callsInfo) {
                    if(isValidCallForAdapter(call)) {
                        count ++;
                    }
                }
            }
            return count;
        }

        @Override
        public Object getItem(int position) {
            synchronized (callMutex) {
                if(callsInfo == null) {
                    return null;
                }
                int count = 0;
                for(SipCallSessionImpl call : callsInfo) {
                    if(isValidCallForAdapter(call)) {
                        if(count == position) {
                            return call;
                        }
                        count ++;
                     }
                }
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            SipCallSessionImpl call = (SipCallSessionImpl) getItem(position);
            if(call != null) {
                return call.getCallId();
            }
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Logger.debug(THIS_FILE,"getView of in call activity");
            if(convertView == null) {
                convertView = new InCallCard(InCallActivity.this, null);
            }
            
            if(convertView instanceof InCallCard) {
                InCallCard vc = (InCallCard) convertView;
                vc.setOnTriggerListener(InCallActivity.this);
                // TODO ---
                //badge.setOnTouchListener(new OnBadgeTouchListener(badge, call));

                SipCallSessionImpl session = (SipCallSessionImpl) getItem(position);
                vc.setCallState(session);
            }

            return convertView;
        }
        
    }



}
