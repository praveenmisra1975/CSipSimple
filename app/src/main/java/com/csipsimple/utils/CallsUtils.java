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

package com.csipsimple.utils;

import android.content.Context;

import com.csipsimple.R;
import com.csipsimple.api.SipCallSessionImpl;

public class CallsUtils {
	/**
	 * Get the corresponding string for a given state
	 * Can be used to translate or debug current state
	 * @return the string reprensenting this call info state
	 */
	public static final String getStringCallState(SipCallSessionImpl session, Context context) {

		int callState = session.getCallState();
		switch(callState) {
		case SipCallSessionImpl.InvState.CALLING:
			return context.getString(R.string.call_state_calling);
		case SipCallSessionImpl.InvState.CONFIRMED:
			return context.getString(R.string.call_state_confirmed);
		case SipCallSessionImpl.InvState.CONNECTING:
			return context.getString(R.string.call_state_connecting);
		case SipCallSessionImpl.InvState.DISCONNECTED:
			return context.getString(R.string.call_state_disconnected);
		case SipCallSessionImpl.InvState.EARLY:
			return context.getString(R.string.call_state_early);
		case SipCallSessionImpl.InvState.INCOMING:
			return context.getString(R.string.call_state_incoming);
		case SipCallSessionImpl.InvState.NULL:
			return context.getString(R.string.call_state_null);
		}
		
		return "";
	}
}
