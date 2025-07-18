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

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;


import android.widget.ActionMenuView;

import android.view.MenuInflater;
import android.view.MenuItem;
import com.csipsimple.R;
import com.csipsimple.api.MediaState;
import com.csipsimple.api.SipConfigManager;
import com.csipsimple.api.SipCallSessionImpl;
import com.csipsimple.sipservice.Logger;
import com.csipsimple.utils.Log;

/**
 * Manages in call controls not relative to a particular call such as media route
 */
public class InCallControls extends FrameLayout /*implements Callback*/ {

	private static final String THIS_FILE = "InCallControls";
	IOnCallActionTrigger onTriggerListener;
	
	private MediaState lastMediaState;
	private SipCallSessionImpl currentCall;

	private boolean supportMultipleCalls = false;


	public InCallControls(Context context) {
        this(context, null, 0);
    }
	
	public InCallControls(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	
    public InCallControls(Context context, AttributeSet attrs, int style) {
        super(context, attrs, style);
        
        if(!isInEditMode()) {
            supportMultipleCalls = SipConfigManager.getPreferenceBooleanValue(getContext(), SipConfigManager.SUPPORT_MULTIPLE_CALLS, false);
        }
        
        final FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                (int) getResources().getDimension(R.dimen.incall_bottom_bar_height));
        ActionMenuView actionMenuView = new ActionMenuView(getContext());
        actionMenuView.setBackgroundResource(R.drawable.btn_compound_background);


      /*    ActionMenuPresenter mActionMenuPresenter = new ActionMenuPresenter(getContext()) {
            public void bindItemView(MenuItemImpl item, MenuView.ItemView itemView) {
                super.bindItemView(item, itemView);
                View actionItemView = (View) itemView;
                actionItemView.setBackgroundResource(R.drawable.btn_compound_background);
            }
        };
        */

       // mActionMenuPresenter.setReserveOverflow(true);
        // Full width
      //  mActionMenuPresenter.setWidthLimit(
       //         getContext().getResources().getDisplayMetrics().widthPixels, true);
        // We use width limit, no need to limit items.
       // mActionMenuPresenter.setItemLimit(20);
       // btnMenuBuilder = new MenuBuilder(getContext());
       // btnMenuBuilder.setCallback(this);
        MenuInflater inflater = new MenuInflater(getContext());
        inflater.inflate(R.menu.in_call_controls_menu, actionMenuView.getMenu());
       // btnMenuBuilder.addMenuPresenter(mActionMenuPresenter);
      //  ActionMenuView menuView = (ActionMenuView) mActionMenuPresenter.getMenuView(this);
      //  actionMenuView.setBackgroundResource(R.drawable.abs__ab_bottom_transparent_dark_holo);
        
        this.addView(actionMenuView, layoutParams);
    }
    
	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		// Finalize object style
		setEnabledMediaButtons(false);
	}

	
	
	private boolean callOngoing = false;
	public void setEnabledMediaButtons(boolean isInCall) {
        callOngoing = isInCall;
        setMediaState(lastMediaState);
	}
	
	public void setCallState(SipCallSessionImpl callInfo) {
		currentCall = callInfo;
		
		if(currentCall == null) {
			setVisibility(GONE);
			return;
		}
		
		int state = currentCall.getCallState();
		Logger.debug(THIS_FILE, "Mode is : " + state);
		switch (state) {
		case SipCallSessionImpl.InvState.INCOMING:
		    setVisibility(GONE);
			break;
		case SipCallSessionImpl.InvState.CALLING:
		case SipCallSessionImpl.InvState.CONNECTING:
		    setVisibility(VISIBLE);
			setEnabledMediaButtons(true);
			break;
		case SipCallSessionImpl.InvState.CONFIRMED:
		    setVisibility(VISIBLE);
			setEnabledMediaButtons(true);
			break;
		case SipCallSessionImpl.InvState.NULL:
		case SipCallSessionImpl.InvState.DISCONNECTED:
		    setVisibility(GONE);
			break;
		case SipCallSessionImpl.InvState.EARLY:
		default:
			if (currentCall.isIncoming()) {
			    setVisibility(GONE);
			} else {
			    setVisibility(VISIBLE);
				setEnabledMediaButtons(true);
			}
			break;
		}
		
	}
	
	/**
	 * Registers a callback to be invoked when the user triggers an event.
	 * 
	 * @param listener
	 *            the OnTriggerListener to attach to this view
	 */
	public void setOnTriggerListener(IOnCallActionTrigger listener) {
		onTriggerListener = listener;
	}

	private void dispatchTriggerEvent(int whichHandle) {
		if (onTriggerListener != null) {
			onTriggerListener.onTrigger(whichHandle, currentCall);
		}
	}
	
	public void setMediaState(MediaState mediaState) {
		lastMediaState = mediaState;
        ActionMenuView actionMenuView = new ActionMenuView(getContext());

        // Update menu
		// BT
		boolean enabled, checked;
		if(lastMediaState == null) {
		    enabled = callOngoing;
		    checked = false;
		}else {
    		enabled = callOngoing && lastMediaState.canBluetoothSco;
    		checked = lastMediaState.isBluetoothScoOn;
		}
		if(actionMenuView.getMenu().findItem(R.id.bluetoothButton) !=null) {
			actionMenuView.getMenu().findItem(R.id.bluetoothButton).setVisible(enabled);
			actionMenuView.getMenu().findItem(R.id.bluetoothButton).setChecked(checked);
		}
        // Mic
        if(lastMediaState == null) {
            enabled = callOngoing;
            checked = false;
        }else {
            enabled = callOngoing && lastMediaState.canMicrophoneMute;
            checked = lastMediaState.isMicrophoneMute;
        }
		if(actionMenuView.getMenu().findItem(R.id.muteButton)!=null)
		{
			actionMenuView.getMenu().findItem(R.id.muteButton).setVisible(enabled);
			actionMenuView.getMenu().findItem(R.id.muteButton).setChecked(checked);
		}

        

        // Speaker
        Logger.debug(THIS_FILE, ">> Speaker " + lastMediaState);
        if(lastMediaState == null) {
            enabled = callOngoing;
            checked = false;
        }else {
            Logger.debug(THIS_FILE, ">> Speaker " + lastMediaState.isSpeakerphoneOn);
            enabled = callOngoing && lastMediaState.canSpeakerphoneOn;
            checked = lastMediaState.isSpeakerphoneOn;
        }
		if(actionMenuView.getMenu().findItem(R.id.speakerButton)!=null)
		{
			actionMenuView.getMenu().findItem(R.id.speakerButton).setVisible(enabled);
			actionMenuView.getMenu().findItem(R.id.speakerButton).setChecked(checked);

		}
        if(actionMenuView.getMenu().findItem(R.id.addCallButton)!=null)
		{
			actionMenuView.getMenu().findItem(R.id.addCallButton).setVisible(supportMultipleCalls && callOngoing);
		}


	}

    public boolean onMenuItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (item.isCheckable()) {
            item.setChecked(!item.isChecked());
        }
        if (id == R.id.bluetoothButton) {
            if (item.isChecked()) {
                dispatchTriggerEvent(IOnCallActionTrigger.BLUETOOTH_ON);
            } else {
                dispatchTriggerEvent(IOnCallActionTrigger.BLUETOOTH_OFF);
            }
            return true;
        } else if (id == R.id.speakerButton) {
            if (item.isChecked()) {
                dispatchTriggerEvent(IOnCallActionTrigger.SPEAKER_ON);
            } else {
                dispatchTriggerEvent(IOnCallActionTrigger.SPEAKER_OFF);
            }
            return true;
        } else if (id == R.id.muteButton) {
            if (item.isChecked()) {
                dispatchTriggerEvent(IOnCallActionTrigger.MUTE_ON);
            } else {
                dispatchTriggerEvent(IOnCallActionTrigger.MUTE_OFF);
            }
            return true;
        } else if (id == R.id.addCallButton) {
            dispatchTriggerEvent(IOnCallActionTrigger.ADD_CALL);
            return true;
        } else if (id == R.id.mediaSettingsButton) {
            dispatchTriggerEvent(IOnCallActionTrigger.MEDIA_SETTINGS);
            return true;
        }
        return false;
    }

    public void onMenuModeChange() {
        // Nothing to do.
    }

}
