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
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

public class VdrCommands {
	private static final String TAG = "VdrCommands";

	public static RecordingInfo getRecordingInfo(int number) throws IOException {
		RecordingInfo recordingInfo = new RecordingInfo();
		StringBuffer sb = new StringBuffer();
		
		Connection connection = null;
		String[] lines = null;
		try {
			connection = new Connection();
			connection.sendData("LSTR " + number + "\n");
			lines = connection.receiveData().split("\n");
			connection.closeDelayed();

			sb.setLength(0);
			for (int i = 0; i < lines.length; i++)
				sb.append(lines[i]);
			recordingInfo.id = MD5.calculate(sb.toString());
		} catch (IOException e) {
			MyLog.v(TAG, "ERROR getRecordingInfo: " + e.toString());
			throw e;
		} finally {
			if (connection != null)
				connection.closeDelayed();
		}
		
		try {
			for (int i = 0; i < lines.length; i++) {
				if (lines[i].charAt(3) == ' ')
					break;
				
				char type = lines[i].charAt(4);
				String[] sa;
				switch (type) {
				case 'C':
					sa = lines[i].split(" ");
					sb.setLength(0);
					if (sa.length > 2) {
						for (int j = 2; j < sa.length; j++)
							sb.append(sa[j] + " ");
						recordingInfo.channelName = sb.toString().trim();
					}
					break;
				case 'E':
					sa = lines[i].split(" ");
					if (sa.length > 2)
						recordingInfo.date = Long.valueOf(sa[2]);
					if (sa.length > 3)
						recordingInfo.duration = Long.valueOf(sa[3]);
					break;
				case 'T':
					recordingInfo.title = lines[i].substring(6);
					break;
				case 'S':
					recordingInfo.subtitle = lines[i].substring(6);
					break;
				case 'D':
					recordingInfo.description = lines[i].substring(6).replace(
							"|", "\n");
					break;
				case 'X':
					sa = lines[i].split(" ");
					if (sa.length > 1) {
						int kind = Integer.parseInt(sa[1]);
						StreamInfo si = new StreamInfo();
						if (sa.length >= 3)
							si.type = sa[2];
						if (sa.length >= 4)
							si.language = sa[3];
						if (sa.length >= 5) {
							sb.setLength(0);
							for (int j = 4; j < sa.length; j++) {
								sb.append(sa[j] + " ");
							}
							si.description = sb.toString().trim();
						}
						switch (kind) {
						case 1:
							recordingInfo.setVideoStream(si);
							break;
						case 2:
							recordingInfo.addAudioStream(si);
							break;
						case 4:
							recordingInfo.setAudioType(si);
							break;
						case 5:
							recordingInfo.setVideoType(si);
							break;
						}
					}
					break;
				case 'F':
					break;
				case 'P':
					recordingInfo.priority = Integer.valueOf(lines[i]
							.substring(6));
					break;
				case 'L':
					recordingInfo.lifetime = Integer.valueOf(lines[i]
							.substring(6));
					break;
				case '@':
					recordingInfo.remark = lines[i].substring(6);
					break;
				}
			}
		} catch (Exception e) {
			MyLog.v(TAG, "ERROR parsing recording info: " + e.toString());
		}
		return recordingInfo;
	}

	public static String setTimer(Epg epg) throws IOException {
		String result = "";

		if (epg == null)
			return result;

		SimpleDateFormat dateformatter = new SimpleDateFormat("yyyy-MM-dd");
		SimpleDateFormat timeformatter = new SimpleDateFormat("HHmm");

		GregorianCalendar calendar = new GregorianCalendar();
		calendar.setTimeInMillis(epg.startzeit * 1000);

		StringBuilder sb = new StringBuilder();
		int flag = 1;
		if (Preferences.getVdr().vps)
			flag = 5;
		
		sb.append("NEWT " + flag + ":" + epg.kanal + ":");
		sb.append(dateformatter.format(calendar.getTime()) + ":");

		calendar.setTimeInMillis(epg.startzeit * 1000
				- Preferences.getVdr().margin_start * 60 * 1000);
		sb.append(timeformatter.format(calendar.getTime()) + ":");

		calendar.setTimeInMillis(epg.startzeit * 1000 + epg.dauer * 1000
				+ Preferences.getVdr().margin_stop * 60 * 1000);
		sb.append(timeformatter.format(calendar.getTime()) + ":");

		sb.append("50:99:" + epg.titel.replace(':', '|') + ":\n");
		MyLog.v(TAG, "setTimer: " + sb.toString());

		Connection conn = null;
		try {
			conn = new Connection();
			result = conn.doThis(sb.toString());
		} catch (IOException e) {
			MyLog.v(TAG, "ERROR setTimer: " + e.toString());
			throw e;
		} finally {
			if (conn != null)
				conn.closeDelayed();
		}

		return result;
	}
}
