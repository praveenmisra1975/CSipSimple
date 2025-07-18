
package com.csipsimple.pjsip;

import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;

import com.csipsimple.service.SipService.SameThreadException;
import com.csipsimple.api.SipCallSessionImpl;
import com.csipsimple.sipservice.Logger;
import com.csipsimple.sipservice.SipCall;
import com.csipsimple.utils.Log;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.Surface;

import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallMediaInfoVector;
import org.pjsip.pjsua2.TimeVal;
import org.pjsip.pjsua2.pj_constants_;
import org.pjsip.pjsua2.pjsip_inv_state;

/**
 * Singleton class to manage pjsip calls. It allows to convert retrieve pjsip
 * calls information and convert that into objects that can be easily managed on
 * Android side
 */
public final class PjSipCalls {

    private PjSipCalls() {
    }

    private static final String THIS_FILE = "PjSipCalls";



}
