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

package com.csipsimple.pjsip.recorder.impl;

import static org.pjsip.pjsua2.pjmedia_type.PJMEDIA_TYPE_AUDIO;

import android.content.Intent;
import android.text.format.DateFormat;

import com.csipsimple.api.SipManager;
import com.csipsimple.pjsip.recorder.IRecorderHandler;
import com.csipsimple.api.SipCallSessionImpl;


import org.pjsip.pjsua2.AudioMedia;
import org.pjsip.pjsua2.AudioMediaRecorder;

import java.io.File;
import java.io.IOException;
import java.util.Date;

public class SimpleWavRecorderHandler implements IRecorderHandler {
    final int way;
    final SipCallSessionImpl callInfo;
    private final String recordingPath;

    private AudioMediaRecorder audioMediaRecorder;
    private AudioMedia audio_media;


    public SimpleWavRecorderHandler(SipCallSessionImpl callInfo, File recordFolder, int way)
            throws Exception {
        this.way = way;
        this.callInfo = callInfo;

        File targetFile = getRecordFile(recordFolder, callInfo.getRemoteContact(), way);
        if (targetFile == null) {
            throw new IOException("No target file possible");
        }
        recordingPath = targetFile.getAbsolutePath();
       // pj_str_t file = pjsua.pj_str_copy(recordingPath);
       // int[] rcId = new int[1];


        audioMediaRecorder = new AudioMediaRecorder();
        audioMediaRecorder.createRecorder(recordingPath);

      //  int status = pjsua.recorder_create(file, 0, (byte[]) null, 0, 0, rcId);

    }

    /**
     * Get the file to record to for a given remote contact. This will
     * implicitly get the current date in file name.
     * 
     * @param remoteContact The remote contact name
     * @return The file to store conversation
     */
    private File getRecordFile(File dir, String remoteContact, int way) {
        if (dir != null) {
            // The file name is only to have an unique identifier.
            // It should never be used to store datas as may change.
            // The app using the recording files should rely on the broadcast
            // and on callInfo instead that is reliable.
            String datePart = (String) DateFormat.format("yy-MM-dd_kkmmss", new Date());
            String remotePart = sanitizeForFile(remoteContact);
            String fileName = datePart + "_" + remotePart;
            if (way != (SipManager.BITMASK_ALL)) {
                fileName += ((way & SipManager.BITMASK_IN) == 0) ? "_out" : "_in";
            }
            File file = new File(dir.getAbsoluteFile() + File.separator
                    + fileName + ".wav");
            return file;
        }
        return null;
    }

    private String sanitizeForFile(String remoteContact) {
        String fileName = remoteContact;
        fileName = fileName.replaceAll("[\\.\\\\<>:; \"\'\\*]", "_");
        return fileName;
    }

    @Override
    public void startRecording() throws Exception {
        // TODO : treat connect errors ? is it useful? Should we fail gracefully
        for (int i=0; i<callInfo.getCallInfo().getMedia().size(); ++i) {
            if (callInfo.getCallInfo().getMedia().get(i).getType() == PJMEDIA_TYPE_AUDIO) {
              //  audio_media = (AudioMedia) callInfo.getMedia(i);
                break;
            }
        }

     //   audioMediaRecorder.startTransmit(audio_media);

    }

    @Override
    public void stopRecording() throws Exception {

        audioMediaRecorder.stopTransmit(audio_media);

    }

    @Override
    public void fillBroadcastWithInfo(Intent it) {
        it.putExtra(SipManager.EXTRA_FILE_PATH, recordingPath);
        it.putExtra(SipManager.EXTRA_SIP_CALL_CALL_WAY, way);
    }

}
