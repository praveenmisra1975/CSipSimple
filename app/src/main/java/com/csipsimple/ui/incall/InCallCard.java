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

package com.csipsimple.ui.incall;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import android.widget.ActionMenuView;
import android.support.v7.view.menu.MenuBuilder;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.csipsimple.R;
import com.csipsimple.api.SipConfigManager;
import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipProfile;
import com.csipsimple.api.SipUri;
import com.csipsimple.api.SipUri.ParsedSipContactInfos;
import com.csipsimple.models.CallerInfo;
import com.csipsimple.service.SipService;
import com.csipsimple.api.SipCallSessionImpl;
import com.csipsimple.sipservice.Logger;
import com.csipsimple.utils.ContactsAsyncHelper;
import com.csipsimple.utils.CustomDistribution;
import com.csipsimple.utils.ExtraPlugins;
import com.csipsimple.utils.ExtraPlugins.DynActivityPlugin;
import com.csipsimple.utils.Log;
import com.csipsimple.utils.PreferencesProviderWrapper;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InCallCard extends FrameLayout implements  OnClickListener{

    private static final String THIS_FILE = "InCallCard";
    
    private SipCallSessionImpl callInfo;
    private String cachedRemoteUri = "";
    private int cachedInvState = SipCallSessionImpl.InvState.INVALID;
    private int cachedMediaState = SipCallSessionImpl.MediaState.ERROR;
    private boolean cachedCanRecord = false;
    private boolean cachedIsRecording = false;
    private boolean cachedIsHold = false;
    private boolean cachedVideo = false;
    private ImageView photo;
    private TextView remoteName, remoteSipAddress, callStatusText, callSecureText;
    private ViewGroup callSecureBar;
    private Chronometer elapsedTime;
    private SurfaceView renderView;
    private PreferencesProviderWrapper prefs;
    private ViewGroup endCallBar;

    private boolean hasVideo = false;
    private boolean canVideo = false;
    private boolean cachedZrtpVerified;
    private boolean cachedZrtpActive;



    private Map<String, DynActivityPlugin> incallPlugins;


    public InCallCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.in_call_card, this, true);

        prefs = new PreferencesProviderWrapper(context);
        canVideo = prefs.getPreferenceBooleanValue(SipConfigManager.USE_VIDEO);
        initControllerView();
        
        incallPlugins = ExtraPlugins.getDynActivityPlugins(context, SipManager.ACTION_INCALL_PLUGIN);
    }

    @SuppressLint("RestrictedApi")
    private void initControllerView() {
        photo = (ImageView) findViewById(R.id.contact_photo);
        remoteName = (TextView) findViewById(R.id.contact_name_display_name);
        remoteSipAddress = (TextView) findViewById(R.id.contact_name_sip_address);
        elapsedTime = (Chronometer) findViewById(R.id.elapsedTime);
        callStatusText = (TextView) findViewById(R.id.call_status_text);
        callSecureBar = (ViewGroup) findViewById(R.id.call_secure_bar);
        callSecureText = (TextView) findViewById(R.id.call_secure_text);
        endCallBar = (ViewGroup) findViewById(R.id.end_call_bar);


        View btn;
        btn = findViewById(R.id.endButton);
        btn.setOnClickListener(this);

      //  ActionMenuView actionMenuView = new ActionMenuView(getContext());

      //  MenuInflater inflater = new MenuInflater(getContext());
      //  inflater.inflate(R.menu.in_call_card_menu, actionMenuView.getMenu());


        
        updateMenuView();
    }
    
    private boolean added = false;
    private void updateMenuView() {
        int w = getWidth();
        if(w <= 0) {
            w = getResources().getDisplayMetrics().widthPixels;
        }
        w -= 100;
        if(!added) {

            final FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            ActionMenuView actionMenuView = new ActionMenuView(getContext());
            actionMenuView.setBackgroundResource(R.drawable.btn_compound_background);

            MenuInflater inflater = new MenuInflater(getContext());
            inflater.inflate(R.menu.in_call_card_menu, actionMenuView.getMenu());
            ViewGroup menuViewWrapper = (ViewGroup) findViewById(R.id.call_action_bar);
            menuViewWrapper.addView(actionMenuView, layoutParams);

            //  final RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
          //          FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);


            // Use width limit (this means we don't care item limits

         //   ActionMenuView menuView = (ActionMenuView) menuViewWrapper;
            //UtilityWrapper.getInstance().setBackgroundDrawable(menuView, null);
            //change tqc
            //menuView.setBackgroundDrawable(null);
         //   menuViewWrapper.addView(menuView, layoutParams);
         //   menuViewWrapper.setLayoutParams(layoutParams);
            added = true;

        }else {
           // mActionMenuPresenter.setWidthLimit(w, true);
           // mActionMenuPresenter.updateMenuView(true);
        }
    }

    public synchronized void setCallState(SipCallSessionImpl aCallInfo) {
        Logger.debug(THIS_FILE,"setCallState of incardview");
        callInfo = aCallInfo;
        if (callInfo == null) {
            updateElapsedTimer();
            cachedInvState = SipCallSessionImpl.InvState.INVALID;
            cachedMediaState = SipCallSessionImpl.MediaState.ERROR;
            cachedCanRecord = false;
            cachedIsRecording = false;
            cachedIsHold = false;
            cachedVideo = false;
            cachedZrtpActive = false;
            cachedZrtpVerified = false;
            return;
        }

        Logger.debug(THIS_FILE, "Set call state incallcard : " + callInfo.getCallState());
        
        updateRemoteName();
        updateCallStateBar();
        updateQuickActions();
        updateElapsedTimer();

        cachedInvState = callInfo.getCallState();
        cachedMediaState = callInfo.getMediaStatus();
        cachedCanRecord = callInfo.canRecord();
        cachedIsRecording = callInfo.isRecording();
        cachedIsHold = callInfo.isLocalHeld();
        cachedVideo = callInfo.mediaHasVideo();
        cachedZrtpActive = callInfo.getHasZrtp();
        cachedZrtpVerified = callInfo.isZrtpSASVerified();
        
        // VIDEO STUFF -- EXPERIMENTAL
       /* if(canVideo) {
            if (callInfo.getCallId() >= 0 && cachedVideo) {
                if (renderView == null) {
                    renderView = ViERenderer.CreateRenderer(getContext(), true);
                    photo.setVisibility(View.GONE);
                    RelativeLayout container = (RelativeLayout) findViewById(R.id.call_card_container);
                    RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.MATCH_PARENT,
                            RelativeLayout.LayoutParams.MATCH_PARENT);
                    lp.addRule(RelativeLayout.ALIGN_LEFT, RelativeLayout.TRUE);
                    lp.addRule(RelativeLayout.ALIGN_RIGHT, RelativeLayout.TRUE);
                    lp.addRule(RelativeLayout.ALIGN_TOP, RelativeLayout.TRUE);
                    lp.addRule(RelativeLayout.ABOVE, R.id.call_action_bar);
                    renderView.setLayoutParams(lp);
                    container.addView(renderView, 0);

                    Log.d(THIS_FILE, "Render window added");
                    SipService.setVideoWindow(callInfo.getCallId(), renderView, false);
                    
                    View v = findViewById(R.id.end_call_bar);
                    ViewGroup.LayoutParams lp2 = v.getLayoutParams();
                    lp2.height = ViewGroup.LayoutParams.WRAP_CONTENT; 
                    v.setLayoutParams(lp2);
                }
                hasVideo = true;
            }else {
                if(renderView != null) {
                    renderView.setVisibility(View.GONE);
                    photo.setVisibility(View.VISIBLE);
                }
                hasVideo = false;
            }
        }*/
      //  if (onTriggerListener != null) {
        //    onTriggerListener.onDisplayVideo(hasVideo && canVideo);
       // }
        // End of video stuff
        
        //requestLayout();
        /*
        if(dragListener != null) {
            dragListener.setCallState(callInfo);
        }
        */
    }
    
    /* We accept height twice than width */
    private static float minRatio = 0.5f;
    /* We accept width 1/4 bigger than height */ 
    private static float maxRatio = 1.25f;
    
    private static float minButtonRation = 0.75f;
    

    private final Handler handler = new Handler();
    private final Runnable postLayout = new Runnable() {
        @Override
        public void run() {
            float w = getWidth();
            float h = getHeight();
            if(w > 0 && h > 0) {
                float currentRatio = w/h;
                float newWidth = w;
                float newHeight = h;
                Log.d(THIS_FILE, "Current ratio is " + currentRatio);
                if(currentRatio < minRatio) {
                    newHeight = w / minRatio;
                    int padding = (int) (float)Math.floor((h - newHeight) /2);
                    setPadding(0, padding, 0, padding);
                }else if(currentRatio > maxRatio) {
                    newWidth = h * maxRatio;
                    int padding = (int) (float)Math.floor((w - newWidth) /2);
                    setPadding(padding, 0, padding, 0);
                }else {
                    setPadding(0, 0, 0, 0);
                }

                View v = findViewById(R.id.end_call_bar);
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                if(currentRatio < minButtonRation && !hasVideo) {
                    lp.height = (int) ((1.0f - minButtonRation) * newHeight);
                }else {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                v.setLayoutParams(lp);
                updateMenuView();

            }
        }
    };

    
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if(changed) {
            handler.postDelayed(postLayout, 100);
        }
        
        super.onLayout(changed, left, top, right, bottom);
    }

    private void updateQuickActions() {
        
        // Useless to process that
        if (cachedInvState == callInfo.getCallState() &&
                cachedMediaState == callInfo.getMediaStatus() &&
                cachedIsRecording == callInfo.isRecording() &&
                cachedCanRecord == callInfo.canRecord() &&
                cachedIsHold == callInfo.isLocalHeld() &&
                cachedVideo  == callInfo.mediaHasVideo() &&
                cachedZrtpActive == callInfo.getHasZrtp() &&
                cachedZrtpVerified == callInfo.isZrtpSASVerified()
                ) {
            Log.d(THIS_FILE, "Nothing changed, ignore this update");
            return;
        }
        
        boolean active = callInfo.isBeforeConfirmed() && callInfo.isIncoming();

        ActionMenuView actionMenuView = new ActionMenuView(getContext());

if(actionMenuView.getMenu().findItem(R.id.takeCallButton)!=null)
{ actionMenuView.getMenu().findItem(R.id.takeCallButton).setVisible(active);}
if(actionMenuView.getMenu().findItem(R.id.dontTakeCallButton)!=null)
{   actionMenuView.getMenu().findItem(R.id.dontTakeCallButton).setVisible(active);}
if(actionMenuView.getMenu().findItem(R.id.declineCallButton)!=null)
{   actionMenuView.getMenu().findItem(R.id.declineCallButton).setVisible(active);}
        
        active = !callInfo.isAfterEnded()
                && (!callInfo.isBeforeConfirmed() || (!callInfo.isIncoming() && callInfo
                        .isBeforeConfirmed()));
            if(actionMenuView.getMenu().findItem(R.id.terminateCallButton)!=null)
            {
                actionMenuView.getMenu().findItem(R.id.terminateCallButton).setVisible(active);
            }

        
        active = (!callInfo.isAfterEnded() && !callInfo.isBeforeConfirmed());
       if( actionMenuView.getMenu().findItem(R.id.xferCallButton)!=null)
       {
           actionMenuView.getMenu().findItem(R.id.xferCallButton).setVisible(active);
       }

       if( actionMenuView.getMenu().findItem(R.id.transferCallButton)!=null)
       {
           actionMenuView.getMenu().findItem(R.id.transferCallButton).setVisible(active);
       }


       if(actionMenuView.getMenu().findItem(R.id.holdCallButton)!=null)
       {
           actionMenuView.getMenu().findItem(R.id.holdCallButton).setVisible(active);
           actionMenuView.getMenu().findItem(R.id.holdCallButton).setTitle(callInfo.isLocalHeld() ? R.string.resume_call : R.string.hold_call);
       }

       if(actionMenuView.getMenu().findItem(R.id.videoCallButton)!=null)
       {
           actionMenuView.getMenu().findItem(R.id.videoCallButton).setVisible(active && canVideo && !callInfo.mediaHasVideo());
       }


        // DTMF
        active = callInfo.isActive() ;
        active &= ( (callInfo.getMediaStatus() == SipCallSessionImpl.MediaState.ACTIVE) || (callInfo.getMediaStatus() == SipCallSessionImpl.MediaState.REMOTE_HOLD));
       if(actionMenuView.getMenu().findItem(R.id.dtmfCallButton)!=null)
       {
           actionMenuView.getMenu().findItem(R.id.dtmfCallButton).setVisible(active);
       }

        
        // Info
        active = !callInfo.isAfterEnded();
       if(actionMenuView.getMenu().findItem(R.id.detailedDisplayCallButton)!=null)
       {
           actionMenuView.getMenu().findItem(R.id.detailedDisplayCallButton).setVisible(active);
       }

        
        // Record
        active = CustomDistribution.supportCallRecord();
        if(!callInfo.isRecording() && !callInfo.canRecord()) {
            active = false;
        }
        if(callInfo.isAfterEnded()) {
            active = false;
        }
        if(actionMenuView.getMenu().findItem(R.id.recordCallButton)!=null)
        {
            actionMenuView.getMenu().findItem(R.id.recordCallButton).setVisible(active);
            actionMenuView.getMenu().findItem(R.id.recordCallButton).setTitle(
                    callInfo.isRecording() ? R.string.stop_recording : R.string.record);
        }

        
        // ZRTP
        active = callInfo.getHasZrtp() && !callInfo.isAfterEnded();
        if(actionMenuView.getMenu().findItem(R.id.zrtpAcceptance)!=null)
        {
            actionMenuView.getMenu().findItem(R.id.zrtpAcceptance).setVisible(active);
            actionMenuView.getMenu().findItem(R.id.zrtpAcceptance).setTitle(
                    callInfo.isZrtpSASVerified() ? R.string.zrtp_revoke_trusted_remote : R.string.zrtp_trust_remote);

        }

        
        
        // Expand plugins
        actionMenuView.getMenu().removeGroup(R.id.controls);
        for(DynActivityPlugin callPlugin : incallPlugins.values()) {
            int minState = callPlugin.getMetaDataInt(SipManager.EXTRA_SIP_CALL_MIN_STATE, SipCallSessionImpl.InvState.EARLY);
            int maxState = callPlugin.getMetaDataInt(SipManager.EXTRA_SIP_CALL_MAX_STATE, SipCallSessionImpl.InvState.CONFIRMED);
            int way = callPlugin.getMetaDataInt(SipManager.EXTRA_SIP_CALL_CALL_WAY, (1 << 0 | 1 << 1));
            Log.d(THIS_FILE, "Can add plugin ? " + minState + ", " + maxState + ", "+ way);
            if(callInfo.getCallState() < minState) {
                continue;
            }
            if(callInfo.getCallState() > maxState) {
                continue;
            }
            if(callInfo.isIncoming() && ((way & (1 << 0)) == 0) ) {
                continue;
            }
            if(!callInfo.isIncoming() && ((way & (1 << 1)) == 0) ) {
                continue;
            }
            MenuItem pluginMenu = actionMenuView.getMenu().add(R.id.controls, MenuBuilder.NONE, MenuBuilder.NONE, callPlugin.getName());
            Intent it = callPlugin.getIntent();
            Bundle bundle = new Bundle();
            bundle.putParcelable(SipManager.EXTRA_CALL_INFO, new SipCallSessionImpl(callInfo));
            it.putExtra("EXTRA_CALL_INFO",bundle);
            pluginMenu.setIntent(it);
        }
        
        
    }

    /**
     * Bind the main visible view with data from call info
     */
    private void updateCallStateBar() {
        
        int stateText = -1; 
        //int stateIcon = R.drawable.ic_incall_ongoing;
        if (callInfo.isAfterEnded()) {
            //stateIcon = R.drawable.ic_incall_end;
            stateText = R.string.call_state_disconnected;
        } else if (callInfo.isLocalHeld() || callInfo.isRemoteHeld()) {
            //stateIcon = R.drawable.ic_incall_onhold;
            stateText = R.string.on_hold;
        } else if (callInfo.isBeforeConfirmed()) {
            if (callInfo.isIncoming()) {
                //stateIcon = R.drawable.ic_call_log_header_incoming_call;
                stateText = R.string.call_state_incoming;
            } else {
                //stateIcon = R.drawable.ic_call_log_header_outgoing_call;
                stateText = R.string.call_state_calling;
            }
        }
        if( (callInfo.isBeforeConfirmed() && callInfo.isIncoming()) /* Before call is established we have the slider */ ||
                callInfo.isAfterEnded() /*Once ended, just wait for the call finalization*/) {
            endCallBar.setVisibility(GONE);
        }else {
            endCallBar.setVisibility(VISIBLE);
        }
        
        if(stateText != -1) {
            callStatusText.setText(stateText);
            setVisibleWithFade(callStatusText, true);
        } else {
            setVisibleWithFade(callStatusText, false);
        }
        //callIcon.setContentDescription(CallsUtils.getStringCallState(callInfo, getContext()));

    }

    private void updateRemoteName() {

        final String aRemoteUri = callInfo.getRemoteContact();

        // If not already set with the same value, just ignore it
        if (aRemoteUri != null && !aRemoteUri.equalsIgnoreCase(cachedRemoteUri)) {
            cachedRemoteUri = aRemoteUri;
            ParsedSipContactInfos uriInfos = SipUri.parseSipContact(cachedRemoteUri);
            String text = SipUri.getDisplayedSimpleContact(aRemoteUri);

            StringBuffer statusTextBuffer = new StringBuffer();

            remoteName.setText(text);
            if (callInfo.getAccId() != SipProfile.INVALID_ID) {
                SipProfile acc = SipProfile.getProfileFromDbId(getContext(), callInfo.getAccId(),
                        new String[] {
                                SipProfile.FIELD_ID, SipProfile.FIELD_DISPLAY_NAME
                        });
                if (acc != null && acc.display_name != null) {
                    statusTextBuffer.append("SIP/" + acc.display_name + " : ");
                }
            } else {
                statusTextBuffer.append("SIP : ");
            }

            statusTextBuffer.append(uriInfos.userName);
            remoteSipAddress.setText(statusTextBuffer.toString());

            Thread t = new Thread() {
                public void run() {
                    // Looks like a phone number so search the contact throw
                    // contacts
                    CallerInfo callerInfo = CallerInfo.getCallerInfoFromSipUri(getContext(),
                            cachedRemoteUri);
                    if (callerInfo != null && callerInfo.contactExists) {
                        LoadCallerInfoMessage lci = new LoadCallerInfoMessage(InCallCard.this, callerInfo);
                        userHandler.sendMessage(userHandler.obtainMessage(LOAD_CALLER_INFO,
                                lci));
                    }
                };
            };
            t.start();

        }
        
        // Useless to process that
        if (cachedInvState == callInfo.getCallState() &&
                cachedMediaState == callInfo.getMediaStatus()) {
            return;
        }
    }

    private void updateElapsedTimer() {

        if (callInfo == null) {
            elapsedTime.stop();
            elapsedTime.setVisibility(VISIBLE);
            return;
        }

        elapsedTime.setBase(callInfo.getConnectStart());
        
        int sigSecureLevel = callInfo.getTransportSecureLevel();
        boolean isSecure = (callInfo.isMediaSecure() || sigSecureLevel > 0); 
        setVisibleWithFade(callSecureBar, isSecure);
        String secureMsg = "";
        if (isSecure) {
            List<String> secureTxtList = new ArrayList<String>();
            if(sigSecureLevel == SipCallSessionImpl.TRANSPORT_SECURE_TO_SERVER) {
                secureTxtList.add(getContext().getString(R.string.transport_secure_to_server));
            }else if(sigSecureLevel == SipCallSessionImpl.TRANSPORT_SECURE_FULL) {
                secureTxtList.add(getContext().getString(R.string.transport_secure_full));
            }
            if(callInfo.isMediaSecure()) {
                secureTxtList.add(callInfo.getMediaSecureInfo());
            }
            secureMsg = TextUtils.join("\r\n", secureTxtList);
        }
        callSecureText.setText(secureMsg);
        
        int state = callInfo.getCallState();
        switch (state) {
            case SipCallSessionImpl.InvState.INCOMING:
            case SipCallSessionImpl.InvState.CALLING:
            case SipCallSessionImpl.InvState.EARLY:
            case SipCallSessionImpl.InvState.CONNECTING:
                elapsedTime.setVisibility(GONE);
                break;
            case SipCallSessionImpl.InvState.CONFIRMED:
                Log.v(THIS_FILE, "we start the timer now ");
                if(callInfo.isLocalHeld()) {
                    elapsedTime.stop();
                    elapsedTime.setVisibility(View.GONE);
                }else {
                    elapsedTime.start();
                    elapsedTime.setVisibility(View.VISIBLE);
                }
                break;
            case SipCallSessionImpl.InvState.NULL:
            case SipCallSessionImpl.InvState.DISCONNECTED:
                elapsedTime.stop();
                elapsedTime.setVisibility(VISIBLE);
                break;
            default:
                break;
        }

    }

    private static final int LOAD_CALLER_INFO = 0;
    private class LoadCallerInfoMessage {
        LoadCallerInfoMessage(InCallCard callCard, CallerInfo ci){
            callerInfo = ci;
            target = callCard;
        }
        CallerInfo callerInfo;
        InCallCard target;
    }

    private final static Handler userHandler = new ContactLoadedHandler();
    
    private static class ContactLoadedHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            if (msg.arg1 == LOAD_CALLER_INFO) {
                LoadCallerInfoMessage lci = (LoadCallerInfoMessage) msg.obj;
                if(lci.callerInfo != null && lci.callerInfo.contactContentUri != null) {
                    // Flag we'd like high res loading
                    lci.callerInfo.contactContentUri = lci.callerInfo.contactContentUri.buildUpon().appendQueryParameter(ContactsAsyncHelper.HIGH_RES_URI_PARAM, "1").build();
                }
                ContactsAsyncHelper.updateImageViewWithContactPhotoAsync(
                        lci.target.getContext(),
                        lci.target.photo,
                        lci.callerInfo,
                        R.drawable.ic_contact_picture_180_holo_light);
                lci.target.remoteName.setText(lci.callerInfo.name);
                lci.target.photo.setContentDescription(lci.callerInfo.name);
            }

        }
    };

    /*
    private OnBadgeTouchListener dragListener;
    public void setOnTouchListener(OnBadgeTouchListener l) {
        dragListener = l;
        super.setOnTouchListener(l);
    }
    */
    
    private IOnCallActionTrigger onTriggerListener;

    /*
     * Registers a callback to be invoked when the user triggers an event.
     * @param listener the OnTriggerListener to attach to this view
     */
    public void setOnTriggerListener(IOnCallActionTrigger listener) {
        onTriggerListener = listener;
    }

    
    private void dispatchTriggerEvent(int whichHandle) {
        if (onTriggerListener != null) {
            onTriggerListener.onTrigger(whichHandle, callInfo);
        }
    }
    


    public void terminate() {
        if(callInfo != null && renderView != null) {
            SipService.setVideoWindow(callInfo.getCallId(), null, false);
        }
    }
    
    
    private void setVisibleWithFade(View v, boolean in) {
        if(v.getVisibility() == View.VISIBLE && in) {
            // Already visible and ask to show, ignore
            return;
        }
        if(v.getVisibility() == View.GONE && !in) {
            // Already gone and ask to hide, ignore
            return;
        }
        
        Animation anim = AnimationUtils.loadAnimation(getContext(), in ? android.R.anim.fade_in : android.R.anim.fade_out);
        anim.setDuration(1000);
        v.startAnimation(anim);
        v.setVisibility(in ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if(id == R.id.endButton) {
            if (callInfo.isBeforeConfirmed() && callInfo.isIncoming()) {
                dispatchTriggerEvent(IOnCallActionTrigger.REJECT_CALL);
            }else if (!callInfo.isAfterEnded()) {
                dispatchTriggerEvent(IOnCallActionTrigger.TERMINATE_CALL);
            }
        }
    }
    public boolean onMenuItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if(itemId == R.id.takeCallButton) {
            dispatchTriggerEvent(IOnCallActionTrigger.TAKE_CALL);
            return true;
        }else if(itemId == R.id.terminateCallButton) {
            dispatchTriggerEvent(IOnCallActionTrigger.TERMINATE_CALL);
            return true;
        }else if(itemId ==  R.id.dontTakeCallButton) {
            dispatchTriggerEvent(IOnCallActionTrigger.DONT_TAKE_CALL);
            return true;
        }else if(itemId ==  R.id.declineCallButton) {
            dispatchTriggerEvent(IOnCallActionTrigger.REJECT_CALL);
            return true;
        }else if(itemId == R.id.detailedDisplayCallButton) {
            dispatchTriggerEvent(IOnCallActionTrigger.DETAILED_DISPLAY);
            return true;
        }else if(itemId == R.id.holdCallButton) {
            dispatchTriggerEvent(IOnCallActionTrigger.TOGGLE_HOLD);
            return true;
        }else if(itemId == R.id.recordCallButton) {
            dispatchTriggerEvent(callInfo.isRecording() ? IOnCallActionTrigger.STOP_RECORDING : IOnCallActionTrigger.START_RECORDING);
            return true;
        }else if(itemId == R.id.dtmfCallButton) {
            dispatchTriggerEvent(IOnCallActionTrigger.DTMF_DISPLAY);
            return true;
        }else if(itemId == R.id.videoCallButton) {
            dispatchTriggerEvent(callInfo.mediaHasVideo() ? IOnCallActionTrigger.STOP_VIDEO : IOnCallActionTrigger.START_VIDEO);
            return true;
        }else if(itemId == R.id.xferCallButton) {
            dispatchTriggerEvent(IOnCallActionTrigger.XFER_CALL);
            return true;
        }else if(itemId == R.id.transferCallButton) {
            dispatchTriggerEvent(IOnCallActionTrigger.TRANSFER_CALL);
            return true;
        }else if(itemId == R.id.zrtpAcceptance) {
            dispatchTriggerEvent(callInfo.isZrtpSASVerified()? IOnCallActionTrigger.ZRTP_REVOKE : IOnCallActionTrigger.ZRTP_TRUST);
            return true;
        }
        return false;
    }

    public void onMenuModeChange() {
        // Nothing to do.
    }

}
