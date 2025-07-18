
package com.csipsimple.pjsip.player.impl;

import static org.pjsip.pjsua2.pjmedia_type.PJMEDIA_TYPE_AUDIO;

import com.csipsimple.pjsip.player.IPlayerHandler;
import com.csipsimple.api.SipCallSessionImpl;


import org.pjsip.pjsua2.AudioMedia;
import org.pjsip.pjsua2.AudioMediaPlayer;

public class SimpleWavPlayerHandler implements IPlayerHandler {

    private final SipCallSessionImpl callInfo;
    private AudioMediaPlayer  audioMediaPlayer;
    private AudioMedia audio_media;

    public SimpleWavPlayerHandler(SipCallSessionImpl callInfo, String filePath, int way) throws Exception {
        this.callInfo = callInfo;


        audioMediaPlayer =new AudioMediaPlayer();
        audioMediaPlayer.createPlayer(filePath);


    }

    @Override
    public void startPlaying() throws Exception {

        for (int i=0; i<callInfo.getCallInfo().getMedia().size(); ++i) {
            if (callInfo.getCallInfo().getMedia().get(i).getType() == PJMEDIA_TYPE_AUDIO) {
              //  audio_media = (AudioMedia) callInfo.sipCall.getMedia(i);
                break;
            }
        }
      //  audioMediaPlayer.startTransmit(audio_media);

    }

    @Override
    public void stopPlaying() throws Exception {
        audioMediaPlayer.stopTransmit(audio_media);

    }

}
