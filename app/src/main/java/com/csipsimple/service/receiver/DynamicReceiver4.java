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

package com.csipsimple.service.receiver;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import com.csipsimple.api.SipConfigManager;
import com.csipsimple.api.SipManager;
import com.csipsimple.api.SipProfile;
import com.csipsimple.service.SipService;
import com.csipsimple.service.SipService.SameThreadException;
import com.csipsimple.service.SipService.SipRunnable;
import com.csipsimple.utils.CallHandlerPlugin;
import com.csipsimple.utils.ExtraPlugins;
import com.csipsimple.utils.Log;
import com.csipsimple.utils.PhoneCapabilityTester;
import com.csipsimple.utils.PreferencesProviderWrapper;
import com.csipsimple.utils.RewriterPlugin;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class DynamicReceiver4 extends BroadcastReceiver {

    private static final String THIS_FILE = "DynamicReceiver";
    

    // Comes from android.net.vpn.VpnManager.java
    // Action for broadcasting a connectivity state.
    public static final String ACTION_VPN_CONNECTIVITY = "vpn.connectivity";
    /** Key to the connectivity state of a connectivity broadcast event. */
    public static final String BROADCAST_CONNECTION_STATE = "connection_state";
    
    private SipService service;
    
    
    // Store current state
    private String mNetworkType;
    private boolean mConnected = false;
    private String mRoutes = "";
    
    private boolean hasStartedWifi = false;



    
    /**
     * Check if the intent received is a sticky broadcast one 
     * A compat way
     * @param it intent received
     * @return true if it's an initial sticky broadcast
     */
    public boolean compatIsInitialStickyBroadcast(Intent it) {
        if(ConnectivityManager.CONNECTIVITY_ACTION.equals(it.getAction())) {
            if(!hasStartedWifi) {
                hasStartedWifi = true;
                return true;
            }
        }
        return false;
    }
    public DynamicReceiver4() {
    }
    
    public DynamicReceiver4(SipService aService) {
        service = aService;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {

        Log.d(THIS_FILE, "onReceive Dynamic receiver " +intent.getAction());

        ////////////////////////
        //praveen
        PreferencesProviderWrapper prefWrapper = new PreferencesProviderWrapper(context);
        String intentAction = intent.getAction();


        if (intentAction.equals(ConnectivityManager.CONNECTIVITY_ACTION) ||
                        intentAction.equals(Intent.ACTION_BOOT_COMPLETED)) {

            if (prefWrapper.isValidConnectionForIncoming() && !prefWrapper.getPreferenceBooleanValue(PreferencesProviderWrapper.HAS_BEEN_QUIT)) {
                Log.d(THIS_FILE, "Try to start service if not already started");
                Intent sip_service_intent = new Intent(context, SipService.class);
                context.startService(sip_service_intent);
            }

        } else if (intentAction.equals(SipManager.INTENT_SIP_ACCOUNT_ACTIVATE)) {
            context.enforceCallingOrSelfPermission(SipManager.PERMISSION_CONFIGURE_SIP, null);

            long accId;
            accId = intent.getLongExtra(SipProfile.FIELD_ID, SipProfile.INVALID_ID);

            if (accId == SipProfile.INVALID_ID) {
                // allow remote side to send us integers.
                // previous call will warn, but that's fine, no worries
                accId = intent.getIntExtra(SipProfile.FIELD_ID, (int) SipProfile.INVALID_ID);
            }

            if (accId != SipProfile.INVALID_ID) {
                boolean active = intent.getBooleanExtra(SipProfile.FIELD_ACTIVE, true);
                ContentValues cv = new ContentValues();
                cv.put(SipProfile.FIELD_ACTIVE, active);
                int done = context.getContentResolver().update(
                        ContentUris.withAppendedId(SipProfile.ACCOUNT_ID_URI_BASE, accId), cv,
                        null, null);
                if (done > 0) {
                    if (prefWrapper.isValidConnectionForIncoming()) {
                        Intent sipServiceIntent = new Intent(context, SipService.class);
                        context.startService(sipServiceIntent);
                    }
                }
            }
        }



    /// /////////////////////////////////

        // Run the handler in SipServiceExecutor to be protected by wake lock
        service.getExecutor().execute(new SipRunnable()  {
            public void doRun() throws SameThreadException {
                Thread.currentThread().setName("Myreceiveinterval");
                onReceiveInternal(context, intent, compatIsInitialStickyBroadcast(intent));
            }
        });
    }
    
    

    /**
     * Internal receiver that will run on sip executor thread
     * @param context Application context
     * @param intent Intent received
     * @throws SameThreadException
     */
    private void onReceiveInternal(Context context, Intent intent, boolean isSticky) throws SameThreadException {
        String action = intent.getAction();
        Log.d(THIS_FILE, "Internal receive " + action);
        if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
            ConnectivityManager cm =
                    (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            onConnectivityChanged(activeNetwork, isSticky);
        } else if (action.equals(SipManager.ACTION_SIP_ACCOUNT_CHANGED)) {
            final long accountId = intent.getLongExtra(SipProfile.FIELD_ID, SipProfile.INVALID_ID);
            // Should that be threaded?
            if (accountId != SipProfile.INVALID_ID) {
                final SipProfile account = service.getAccount(accountId);
                if (account != null) {
                    Log.d(THIS_FILE, "Enqueue set account registration");
                    service.setAccountRegistration(account, account.active ? 1 : 0, true);
                }
            }
        } else if (action.equals(SipManager.ACTION_SIP_ACCOUNT_DELETED)){
            final long accountId = intent.getLongExtra(SipProfile.FIELD_ID, SipProfile.INVALID_ID);
            if(accountId != SipProfile.INVALID_ID) {
                final SipProfile fakeProfile = new SipProfile();
                fakeProfile.id = accountId;
                service.setAccountRegistration(fakeProfile, 0, true);
            }
        } else if (action.equals(SipManager.ACTION_SIP_CAN_BE_STOPPED)) {
            service.cleanStop();
        } else if (action.equals(SipManager.ACTION_SIP_REQUEST_RESTART)){
            service.restartSipStack();
        } else if(action.equals(ACTION_VPN_CONNECTIVITY)) {
            onConnectivityChanged(null, isSticky);
        }
    }
    

  /*  private static final String PROC_NET_ROUTE = "/proc/net/route";
    private String dumpRoutes() {
        String routes = "";
        FileReader fr = null;
        try {
            fr = new FileReader(PROC_NET_ROUTE);
            if(fr != null) {
                StringBuffer contentBuf = new StringBuffer();
                BufferedReader buf = new BufferedReader(fr);
                String line;
                while ((line = buf.readLine()) != null) {
                    contentBuf.append(line+"\n");
                }
                routes = contentBuf.toString();
                buf.close();
            }
        } catch (FileNotFoundException e) {
            Log.e(THIS_FILE, "No route file found routes", e);
        } catch (IOException e) {
            Log.e(THIS_FILE, "Unable to read route file", e);
        }finally {
            try {
                fr.close();
            } catch (IOException e) {
                Log.e(THIS_FILE, "Unable to close route file", e);
            }
        }
        
        // Clean routes that point unique host 
        // this aims to workaround the fact android 4.x wakeup 3G layer when position is retrieve to resolve over 3g position
        String finalRoutes = routes;
        if(!TextUtils.isEmpty(routes)) {
            String[] items = routes.split("\n");
            List<String> finalItems = new ArrayList<String>();
            int line = 0;
            for(String item : items) {
                boolean addItem = true;
                if(line > 0){
                    String[] ent = item.split("\t");
                    if(ent.length > 8) {
                        String maskStr = ent[7];
                        if(maskStr.matches("^[0-9A-F]{8}$")) {
                            int lastMaskPart = Integer.parseInt(maskStr.substring(0, 2), 16);
                            if(lastMaskPart > 192) {
                                // if more than 255.255.255.192 : ignore this line
                                addItem = false;
                            }
                        }else {
                            Log.w(THIS_FILE, "The route mask does not looks like a mask" + maskStr);
                        }
                    }
                }
                
                if(addItem) {
                    finalItems.add(item);
                }
                line ++;
            }
            finalRoutes = TextUtils.join("\n", finalItems); 
        }
        
        return finalRoutes;
    }*/

    

    private void onConnectivityChanged(NetworkInfo info, boolean isSticky) throws SameThreadException {
        // We only care about the default network, and getActiveNetworkInfo()
        // is the only way to distinguish them. However, as broadcasts are
        // delivered asynchronously, we might miss DISCONNECTED events from
        // getActiveNetworkInfo(), which is critical to our SIP stack. To
        // solve this, if it is a DISCONNECTED event to our current network,
        // respect it. Otherwise get a new one from getActiveNetworkInfo().
        if (info == null || info.isConnected() ||
                !info.getTypeName().equals(mNetworkType)) {
            ConnectivityManager cm = (ConnectivityManager) service.getSystemService(Context.CONNECTIVITY_SERVICE);
            info = cm.getActiveNetworkInfo();
        }

        boolean connected = (info != null && info.isConnected() && service.isConnectivityValid());
        String networkType = connected ? info.getTypeName() : "null";
      //  String currentRoutes = dumpRoutes();
        String oldRoutes;
       // synchronized (mRoutes) {
        //    oldRoutes = mRoutes;
       // }

        // Ignore the event if the current active network is not changed.
        if (connected == mConnected && networkType.equals(mNetworkType) /*&& currentRoutes.equals(oldRoutes)*/) {
            return;
        }
        if(Log.getLogLevel() >= 4) {
            if(!networkType.equals(mNetworkType)) {
                Log.d(THIS_FILE, "onConnectivityChanged(): " + mNetworkType +
                            " -> " + networkType);
            }
        }
        // Now process the event
      //  synchronized (mRoutes) {
       //     mRoutes = currentRoutes;
      //  }
        mConnected = connected;
        mNetworkType = networkType;

        if(!isSticky) {
            if (connected) {
                service.restartSipStack();
            } else {
                Log.d(THIS_FILE, "We are not connected, stop");
                if(service.stopSipStack()) {
                    service.stopSelf();
                }
            }
        }
    }
    
    
    

}
