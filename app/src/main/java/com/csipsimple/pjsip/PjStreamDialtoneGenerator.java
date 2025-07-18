
package com.csipsimple.pjsip;

import com.csipsimple.utils.Log;

import org.pjsip.pjsua2.ToneDesc;
import org.pjsip.pjsua2.ToneDescVector;
import org.pjsip.pjsua2.ToneDigit;
import org.pjsip.pjsua2.ToneDigitMapDigit;
import org.pjsip.pjsua2.ToneDigitMapVector;
import org.pjsip.pjsua2.ToneDigitVector;
import org.pjsip.pjsua2.ToneGenerator;
import org.pjsip.pjsua2.pj_constants_;
import org.pjsip.pjsua2.pjmedia_tone_digit;

/**
 * DTMF In band tone generator for a given call object
 * It creates it's own pool, media port, and can stream in. 
 *
 */
public class PjStreamDialtoneGenerator {

    
    private static final String THIS_FILE = "PjStreamDialtoneGenerator";
    private static String SUPPORTED_DTMF = "0123456789abcd*#";
    private final int callId;


	ToneGenerator toneGenerator;
	
	public PjStreamDialtoneGenerator(int aCallId) {
        this(aCallId, true);
    }
	
    public PjStreamDialtoneGenerator(int aCallId, boolean onMicro) {
        callId = aCallId;

    }
	
	/**
	 * Start the tone generate.
	 * This is automatically done by the send dtmf
	 * @return the pjsip error code for creation
	 */
	private synchronized int startDialtoneGenerator() {

		try {

			toneGenerator = new ToneGenerator();

			int status;

			long clockRate = 8000;
			long channelCount = 1;

			toneGenerator.createToneGenerator(clockRate, channelCount);
			return pj_constants_.PJ_SUCCESS;

		} catch (Exception e) {
			e.printStackTrace();
			stopDialtoneGenerator();
			return pj_constants_.PJ_FALSE;
		}

	}

	/**
	 * Stop the dialtone generator.
	 * This has to be called manually when no more DTMF codes are to be send for the associated call
	 */
	public synchronized void stopDialtoneGenerator()  {
	    stopSending();

	}
	
	/**
	 * Send multiple tones.
	 * @param dtmfChars tones list to send. 
	 * @return the pjsip status
	 */
	public synchronized int sendPjMediaDialTone(String dtmfChars) throws Exception {
	    int status = ensureDialtoneGen();
	    if(status != pj_constants_.PJ_SUCCESS) {
	        return status;
	    }
		stopSending();
		
		for(int i = 0 ; i < dtmfChars.length(); i++ ) {
		    char d = dtmfChars.charAt(i);
		    if(SUPPORTED_DTMF.indexOf(d) == -1) {
		        Log.w(THIS_FILE, "Unsupported DTMF char " + d);
		    } else {
				try {
					// Found dtmf char, use digit api
					ToneDigit toneDigit = new ToneDigit();
					toneDigit.setVolume((short) 0);
					toneDigit.setOn_msec((short) 100);
					toneDigit.setOff_msec((short) 200);
					toneDigit.setDigit(d);

					ToneDigitVector toneDigitVector = new ToneDigitVector();
					toneDigitVector.add(toneDigit);
					toneGenerator.playDigits(toneDigitVector, true);
					return pj_constants_.PJ_TRUE;
				}catch (Exception e) {
					e.printStackTrace();
					stopDialtoneGenerator();
					return pj_constants_.PJ_FALSE;
				}


		    }
		}

		return status;
	}
	/**
	 * Start playback of a waiting tone.
	 * This will create a thread looping until {@link #stopDialtoneGenerator()} called
	 *  
	 * @return #PJ_SUCCESS if start done correctly
	 */
    public synchronized int startPjMediaWaitingTone() {
        int status = ensureDialtoneGen();
        if (status != pj_constants_.PJ_SUCCESS) {
            return status;
        }
        stopSending();
        // Found dtmf char, use digit api
		try {
		ToneDesc  toneDesc=new ToneDesc();

		toneDesc.setVolume((short) 0);  // 0 means default
		toneDesc.setOn_msec((short) 100);
		toneDesc.setOff_msec((short) 1500);
		toneDesc.setFreq1((short)440);
		toneDesc.setFreq2((short)350); // Not sure about this one
		ToneDescVector  toneDescVector =new ToneDescVector();
		toneDescVector.add(toneDesc);
		toneGenerator.play(toneDescVector,true);

        return pj_constants_.PJ_TRUE;
	}catch (Exception e) {
		e.printStackTrace();
		stopDialtoneGenerator();
		return pj_constants_.PJ_FALSE;
	}
    }
	
	private int ensureDialtoneGen()  {

            int status = startDialtoneGenerator();
            if (status != pj_constants_.PJ_SUCCESS) {
                return -1;
            }

        return pj_constants_.PJ_SUCCESS;
	}
	
	private void stopSending() {
		try {
			toneGenerator.stop();
		}catch(Exception e)
		{}

	}

}
