/*
 * Copyright (c) 2010-2011 by androvdr <androvdr@googlemail.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * For more information on the GPL, please go to:
 * http://www.gnu.org/copyleft/gpl.html
 */

package de.androvdr;

import java.io.IOException;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.StringTokenizer;

import org.hampelratte.svdrp.Response;
import org.hampelratte.svdrp.commands.LSTR;
import org.hampelratte.svdrp.commands.NEWT;
import org.hampelratte.svdrp.responses.highlevel.EPGEntry;
import org.hampelratte.svdrp.responses.highlevel.Stream;
import org.hampelratte.svdrp.responses.highlevel.VDRTimer;
import org.hampelratte.svdrp.util.EPGParser;

import de.androvdr.svdrp.VDRConnection;

public class VdrCommands {
	private static final String TAG = "VdrCommands";

	public static RecordingInfo getRecordingInfo(int number) throws IOException {
	    Response response = VDRConnection.send(new LSTR(number));
	    if (response != null && response.getCode() == 215) {

            // workaround for the epg parser, because LSTR does not send an 'e' as entry terminator
            StringTokenizer st = new StringTokenizer(response.getMessage(), "\n");
            StringBuilder mesg = new StringBuilder();
            while (st.hasMoreElements()) {
                String line = st.nextToken();
                if (!st.hasMoreElements()) {
                    mesg.append('e').append('\n');
                }
                mesg.append(line).append('\n');
            }

            // parse epg information
            List<EPGEntry> epg = EPGParser.parse(mesg.toString());
            if (epg.size() > 0) {
                EPGEntry entry = epg.get(0);
                RecordingInfo recordingInfo = new RecordingInfo();
                recordingInfo.id = MD5.calculate(response.getMessage());

                recordingInfo.channelName = entry.getChannelName();
                recordingInfo.date = entry.getStartTime().getTimeInMillis() / 1000;
                recordingInfo.description = entry.getDescription();
                long end = entry.getEndTime().getTimeInMillis() / 1000;
                recordingInfo.duration = end - recordingInfo.date;
                recordingInfo.title = entry.getTitle();
                recordingInfo.priority = entry.getPriority();
                recordingInfo.lifetime = entry.getLifetime();
                
                // add information about the muxed streams
                for (Stream stream : entry.getStreams()) {
                    StreamInfo si = new StreamInfo();
                    // stream type
                    si.type = Integer.toString(stream.getType(), 16);
                    
                    // stream language
                    si.language = stream.getLanguage();
                    
                    // stream description
                    si.description = stream.getDescription();
                    
                    // stream kind
                    switch(stream.getContent()) {
                    case MP2V:
                    case H264:
                        si.kind = 1;
                        recordingInfo.setVideoStream(si);
                        break;
                    case MP2A:
                    case AC3:
                    case HEAAC:
                        si.kind = 2;
                        recordingInfo.addAudioStream(si);
                        break;
                    }
                }
                
                // TODO add more information (prio, lifetime, etc?)
                
                return recordingInfo;
            } else {
                throw new IOException("Couldn't retrieve recording details: " + response.getCode() + " " + response.getMessage());
            }
        } else {
            throw new IOException("Couldn't retrieve recording details");
        }
	}

	public static String setTimer(Epg epg) throws IOException {
		
	    String result = "";

		if (epg == null)
			return result;

		GregorianCalendar startTime = new GregorianCalendar();
		startTime.setTimeInMillis(epg.startzeit * 1000 - Preferences.getVdr().margin_start * 60 * 1000);

		GregorianCalendar endTime = new GregorianCalendar();
		endTime.setTimeInMillis(epg.startzeit * 1000 + epg.dauer * 1000 + Preferences.getVdr().margin_stop * 60 * 1000);

		VDRTimer timer = new VDRTimer();
		timer.setChannelNumber(epg.kanal);
		timer.setStartTime(startTime);
		timer.setEndTime(endTime);
		timer.setPriority(50);
		timer.setLifetime(99);
		timer.setTitle(epg.titel);
		timer.setDescription(epg.beschreibung);
		timer.changeStateTo(VDRTimer.VPS, Preferences.getVdr().vps);

		NEWT newt = new NEWT(timer.toNEWT());
		Response response = VDRConnection.send(newt);
		if(response.getCode() == 250) {
		    result = response.getMessage();
		} else {
		    MyLog.v(TAG, "ERROR setTimer: " + response.getMessage());
		}
		return result;
	}
}
