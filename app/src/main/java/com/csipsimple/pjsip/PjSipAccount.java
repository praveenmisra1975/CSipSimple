
package com.csipsimple.pjsip;

import static android.net.wifi.WifiEnterpriseConfig.Eap.TLS;
import static com.csipsimple.api.SipProfile.CRED_SCHEME_DIGEST;
import static com.csipsimple.sipservice.SipAccountTransport.TCP;
import static com.csipsimple.sipservice.SipAccountTransport.UDP;
import static com.csipsimple.sipservice.SipServiceConstants.DEFAULT_SIP_PORT;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

import android.content.Context;
import android.text.TextUtils;

import com.csipsimple.api.SipConfigManager;
import com.csipsimple.api.SipProfile;
import com.csipsimple.api.SipUri;
import com.csipsimple.api.SipUri.ParsedSipContactInfos;
import com.csipsimple.service.SipService;
import com.csipsimple.sipservice.SipAccountTransport;
import com.csipsimple.sipservice.SipServiceConstants;
import com.csipsimple.utils.Log;
import com.csipsimple.utils.PreferencesProviderWrapper;

import org.pjsip.pjsua2.AccountConfig;
import org.pjsip.pjsua2.AuthCredInfo;
import org.pjsip.pjsua2.AuthCredInfoVector;
import org.pjsip.pjsua2.CallInfo;
import org.pjsip.pjsua2.CallOpParam;
import org.pjsip.pjsua2.EpConfig;
import org.pjsip.pjsua2.OnIncomingCallParam;
import org.pjsip.pjsua2.OnRegStateParam;
import org.pjsip.pjsua2.StringVector;
import org.pjsip.pjsua2.TransportConfig;
import org.pjsip.pjsua2.pj_constants_;
import org.pjsip.pjsua2.pj_qos_params;
import org.pjsip.pjsua2.pj_qos_type;
import org.pjsip.pjsua2.pjmedia_srtp_use;
import org.pjsip.pjsua2.pjsip_status_code;
import org.pjsip.pjsua2.pjsua_stun_use;

import java.util.HashMap;
import java.util.Set;

public class PjSipAccount {
	
	//private static final String THIS_FILE = "PjSipAcc";

	private String displayName;
	// For now everything is public, easiest to manage
	public String wizard;
	public boolean active;



	public Integer transport = 0;

	public void setRealm(String realm) {
		this.realm = realm;
	}

	public String realm;

	public Long id;

	private int profile_vid_auto_show = -1;
	private int profile_vid_auto_transmit = -1;
    private int profile_enable_qos;
    private int profile_qos_dscp;
    private boolean profile_default_rtp_port = true;

	public AccountConfig getAccountConfig() {
		return accountConfig;
	}

	public void setAccountConfig(AccountConfig accountConfig) {
		this.accountConfig = accountConfig;
	}

	public AccountConfig accountConfig;

	public SipProfile pjprofile;
	
	//private boolean hasZrtpValue = false;

	public PjSipAccount() {

	}

	/**
	 * Initialize from a SipProfile (public api) object
	 * @param profile the sip profile to use
	 */
	public PjSipAccount(SipProfile profile) {
		this();
		pjprofile=profile;
		try {


			if (profile.id != SipProfile.INVALID_ID) {
				id = profile.id;
			}

			displayName = profile.display_name;
			wizard = profile.wizard;
			transport = profile.transport;
			active = profile.active;
			transport = profile.transport;
			realm = profile.realm;

			accountConfig = new AccountConfig();
			accountConfig.setPriority(profile.priority);
			accountConfig.getRegConfig().setRegisterOnAdd(true);


			if (profile.acc_id != null) {
				accountConfig.setIdUri(profile.acc_id);
			}
			if (profile.reg_uri != null) {
				accountConfig.getRegConfig().setRegistrarUri(profile.reg_uri);
			}

			if (profile.publish_enabled) {
				accountConfig.getPresConfig().setPublishEnabled(profile.publish_enabled);
			}
			if (profile.reg_timeout != -1) {
				accountConfig.getRegConfig().setTimeoutSec(profile.reg_timeout);
			}
			if (profile.reg_delay_before_refresh != -1) {
				accountConfig.getRegConfig().setDelayBeforeRefreshSec(profile.reg_delay_before_refresh);
			}
			if (profile.ka_interval != -1) {
				//ll see
				accountConfig.getRegConfig().setRetryIntervalSec(profile.ka_interval);
			}
			if (profile.pidf_tuple_id != null) {
				accountConfig.getPresConfig().setPidfTupleId(profile.pidf_tuple_id);
			}
			if (profile.force_contact != null) {
				//ll see
				//	accountConfig.setForce_contact(pjsua.pj_str_copy(profile.force_contact));
			}

			if (profile.use_srtp != -1) {
				accountConfig.getMediaConfig().setSrtpUse(profile.use_srtp);
				accountConfig.getMediaConfig().setSrtpSecureSignaling(0);
			}


			if (profile.proxies != null) {

				StringVector v = new StringVector(profile.proxies);
				accountConfig.getSipConfig().setProxies(v);


			} else {
				accountConfig.getSipConfig().setProxies(null);
			}
			accountConfig.getRegConfig().setProxyUse(profile.reg_use_proxy);
			if (profile.username != null || profile.data != null) {
				AuthCredInfoVector authCredInfoVector = new AuthCredInfoVector();
				AuthCredInfo authCredInfo = new AuthCredInfo();

				authCredInfo.setScheme(CRED_SCHEME_DIGEST);

				if (profile.realm != null) {
					authCredInfo.setRealm(profile.realm);
				}
				if (profile.username != null) {
					authCredInfo.setUsername(profile.username);
				}
				if (profile.datatype != -1) {
					authCredInfo.setDataType(profile.datatype);
				}
				if (profile.data != null) {
					authCredInfo.setData(profile.data);
				}
				authCredInfoVector.add(authCredInfo);
				accountConfig.getSipConfig().setAuthCreds(authCredInfoVector);
			}

			//accountConfig.getMwiConfig().setEnabled(profile.mwi_enabled);
			accountConfig.getMediaConfig().setIpv6Use(profile.ipv6_media_use);

			// RFC5626
			if (profile.use_rfc5626) {
				accountConfig.getNatConfig().setSipOutboundUse(1);
				if (!TextUtils.isEmpty(profile.rfc5626_instance_id)) {
					accountConfig.getNatConfig().setSipOutboundInstanceId(profile.rfc5626_instance_id);
				}
				if (!TextUtils.isEmpty(profile.rfc5626_reg_id)) {
					accountConfig.getNatConfig().setSipOutboundRegId(profile.rfc5626_reg_id);
				}

			}
			// Video
			if (profile.vid_in_auto_show != -1)
				accountConfig.getVideoConfig().setAutoShowIncoming(true);
			if (profile.vid_out_auto_transmit != -1)
				accountConfig.getVideoConfig().setAutoTransmitOutgoing(true);


			// Rtp cfg
			TransportConfig transportConfig = accountConfig.getMediaConfig().getTransportConfig();
			if (profile.rtp_port >= 0) {
				transportConfig.setPort(profile.rtp_port);
				profile_default_rtp_port = false;
			}
			if (!TextUtils.isEmpty(profile.rtp_public_addr)) {
				transportConfig.setPublicAddress(profile.rtp_public_addr);
			}
			if (!TextUtils.isEmpty(profile.rtp_bound_addr)) {
				transportConfig.setBoundAddress(profile.rtp_bound_addr);
			}

			profile_enable_qos = profile.rtp_enable_qos;
			profile_qos_dscp = profile.rtp_qos_dscp;
			accountConfig.getNatConfig().setSipStunUse(profile.sip_stun_use);
			accountConfig.getNatConfig().setMediaStunUse(profile.media_stun_use);
			if (profile.ice_cfg_use == 1) {
				accountConfig.getNatConfig().setIceEnabled((profile.ice_cfg_enable == 1) ? true : FALSE);
			} else {
				accountConfig.getNatConfig().setIceEnabled(false);
			}
			if (profile.turn_cfg_use == 1) {
				accountConfig.getNatConfig().setTurnEnabled((profile.turn_cfg_enable == 1) ? TRUE : FALSE);
				accountConfig.getNatConfig().setTurnServer(profile.turn_cfg_server);
				accountConfig.getNatConfig().setTurnUserName(profile.turn_cfg_user);
				accountConfig.getNatConfig().setTurnPassword(profile.turn_cfg_password);

			} else {
				accountConfig.getNatConfig().setTurnEnabled(false);

			}
			accountConfig.getMediaConfig().getTransportConfig().setQosType(pj_qos_type.PJ_QOS_TYPE_VOICE);
			accountConfig.getMediaConfig().setSrtpUse(srtpUse);
			accountConfig.getMediaConfig().setSrtpSecureSignaling(srtpSecureSignalling);


			accountConfig.getNatConfig().setSdpNatRewriteUse(pj_constants_.PJ_TRUE);
			accountConfig.getNatConfig().setViaRewriteUse(pj_constants_.PJ_TRUE);

		}catch(Exception e)
		{
			e.printStackTrace();
            e.fillInStackTrace();
		}
	}

	/**
	 * Automatically apply csipsimple specific parameters to the account
	 * @param ctxt
	 */
	public void applyExtraParams(Context ctxt) {
		
		// Transport
		String regUri = "";
		String argument = "";
		switch (transport) {
		case SipProfile.TRANSPORT_UDP:
			argument = ";transport=udp;lr";
			break;
		case SipProfile.TRANSPORT_TCP:
			argument = ";transport=tcp;lr";
			break;
		case SipProfile.TRANSPORT_TLS:
			//TODO : differentiate ssl/tls ?
			argument = ";transport=tls;lr";
			break;
		default:
			break;
		}
		
		if (!TextUtils.isEmpty(argument)) {
			regUri = accountConfig.getRegConfig().getRegistrarUri();
			if(!TextUtils.isEmpty(regUri)) {
				 StringVector proxies=accountConfig.getSipConfig().getProxies();
				//TODO : remove lr and transport from uri
				String firstProxy = proxies.get(0);
				if (proxies.size() == 0 || TextUtils.isEmpty(firstProxy)) {
					accountConfig.getRegConfig().setRegistrarUri(regUri + argument);

				} else {
					proxies.set(0,firstProxy + argument);
					StringVector vproxies= new StringVector(proxies);
					accountConfig.getSipConfig().setProxies(vproxies);
				}
			}
		}
		
		//Caller id
		PreferencesProviderWrapper prefs = new PreferencesProviderWrapper(ctxt);
		String defaultCallerid = prefs.getPreferenceStringValue(SipConfigManager.DEFAULT_CALLER_ID);
		// If one default caller is set 
		if (!TextUtils.isEmpty(defaultCallerid)) {
			String accId = accountConfig.getRegConfig().getCallID();
			ParsedSipContactInfos parsedInfos = SipUri.parseSipContact(accId);
			if (TextUtils.isEmpty(parsedInfos.displayName)) {
				// Apply new display name
				parsedInfos.displayName = defaultCallerid;
				accountConfig.getRegConfig().setCallID(parsedInfos.toString());
			}
		}
		
		// Keep alive
		accountConfig.getNatConfig().setUdpKaIntervalSec(prefs.getUdpKeepAliveInterval());

		// Video

		if(profile_vid_auto_show >= 0) {
			accountConfig.getVideoConfig().setAutoShowIncoming((profile_vid_auto_show == 1) ? true : false);
		}else {
			accountConfig.getVideoConfig().setAutoShowIncoming(true);

		}
		if(profile_vid_auto_transmit >= 0) {
			accountConfig.getVideoConfig().setAutoTransmitOutgoing((profile_vid_auto_transmit == 1) ? true : false);
        }else {
			accountConfig.getVideoConfig().setAutoTransmitOutgoing(true);

        }
		
		
		// RTP cfg
		TransportConfig transportConfig=accountConfig.getMediaConfig().getTransportConfig();
		if(profile_default_rtp_port) {
			transportConfig.setPort(prefs.getRTPPort());
			profile_default_rtp_port = false;
		}

		boolean hasQos = prefs.getPreferenceBooleanValue(SipConfigManager.ENABLE_QOS);
        if(profile_enable_qos >= 0) {
            hasQos = (profile_enable_qos == 1);
        }
        if(hasQos) {
            // TODO - video?

			transportConfig.setQosType(pj_qos_type.PJ_QOS_TYPE_VOICE);
			pj_qos_params qosParam =transportConfig.getQosParams();
            // Default for RTP layer is different than default for SIP layer.
            short dscpVal = (short) prefs.getPreferenceIntegerValue(SipConfigManager.DSCP_RTP_VAL);
            if(profile_qos_dscp >= 0) {
                // If not set, we don't need to change dscp value
                dscpVal = (short) profile_qos_dscp;
				qosParam.setDscp_val(dscpVal);
				qosParam.setFlags((short) 1);// DSCP
            }
        }
	}
	
	/**
	 * @return the displayName
	 */
	public String getDisplayName() {
		return displayName;
	}
	@Override
	public boolean equals(Object o) {
		if(o != null && o.getClass() == PjSipAccount.class) {
			PjSipAccount oAccount = (PjSipAccount) o;
			return oAccount.id == id;
		}
		return super.equals(o);
	}

	public AccountConfig getGuestAccountConfig() {
		AccountConfig accountConfig = new AccountConfig();
		accountConfig.getMediaConfig().getTransportConfig().setQosType(pj_qos_type.PJ_QOS_TYPE_VIDEO);
		String idUri = getGuestDisplayName().isEmpty()
				? getIdUri()
				: "\""+getGuestDisplayName()+"\" <"+getIdUri()+">";
		accountConfig.setIdUri(idUri);
		accountConfig.getSipConfig().getProxies().add(getProxyUri());
		accountConfig.getRegConfig().setRegisterOnAdd(false);
		accountConfig.getCallConfig().setTimerSessExpiresSec(sessionTimerExpireSec);
		setVideoConfig(accountConfig);
		return accountConfig;
	}

	private void setVideoConfig(AccountConfig accountConfig) {
		accountConfig.getVideoConfig().setAutoTransmitOutgoing(false);
		accountConfig.getVideoConfig().setAutoShowIncoming(true);
		accountConfig.getVideoConfig().setDefaultCaptureDevice(SipServiceConstants.FRONT_CAMERA_CAPTURE_DEVICE);
		accountConfig.getVideoConfig().setDefaultRenderDevice(SipServiceConstants.DEFAULT_RENDER_DEVICE);
	}

	public String getGuestDisplayName() {
		return this.guestDisplayName;
	}

	public PjSipAccount setGuestDisplayName(String guestDisplayName) {
		this.guestDisplayName = guestDisplayName;
		return this;
	}
	private String guestDisplayName = "";
	private int srtpUse = pjmedia_srtp_use.PJMEDIA_SRTP_OPTIONAL;
	private int srtpSecureSignalling = 0; // not required

	public static final String AUTH_TYPE_DIGEST = "digest";
	public static final String AUTH_TYPE_PLAIN = "plain";
	private final int sessionTimerExpireSec = 600;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	private String username;

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	private String password;

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	private String host;

	public long getPort() {
		return port;
	}

	public void setPort(long port) {
		this.port = port;
	}

	private long port = DEFAULT_SIP_PORT;
	private boolean tcpTransport = false;
	private String authenticationType = AUTH_TYPE_DIGEST;
	private String contactUriParams = "";
	private int regExpirationTimeout = 300;     // 300s

	private String callId = "";

	public SipAccountTransport getMtransport() {
		return mtransport;
	}

	public void setMtransport(SipAccountTransport mtransport) {
		this.mtransport = mtransport;
	}

	private SipAccountTransport mtransport = SipAccountTransport.UDP;

	public String getIdUri() {
		if ("*".equals(realm))
			return "sip:" + username;

		return "sip:" + username + "@" + realm;
	}

	String getProxyUri() {
		return "sip:" + host + ":" + port + getTransportString();
	}

	String getRegistrarUri() {
		return "sip:" + host + ":" + port;
	}


	public boolean isValid() {
		return ((username != null) && !username.isEmpty()
				&& (password != null) && !password.isEmpty()
				&& (host != null) && !host.isEmpty()
				&& (realm != null) && !realm.isEmpty());
	}

	String getTransportString() {
		switch (mtransport) {
			case TCP: return ";transport=tcp";
			case TLS: return ";transport=tls";
			case UDP:
			default: {
				// backward compatibility
				if (tcpTransport) return ";transport=tcp";
				else return "";
			}
		}
	}

	public String getRealm() {
		return realm;
	}


}