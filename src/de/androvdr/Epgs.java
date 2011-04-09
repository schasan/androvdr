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
import java.util.ArrayList;

public class Epgs {
	public static final String TAG = "Epgs";

	private final int EPG_ALL = 0;
	private final int EPG_NOW = -1;
	private final int EPG_NEXT = -2;
	
	private final int mChannel;
	private Connection mConnection;
	
	public Epgs(int channel) throws IOException {
		mChannel = channel;
		mConnection = new Connection();
	}
	
	public ArrayList<Epg> getAll() throws IOException {
		return get(EPG_ALL);
	}
	
	public Epg getAt(long time) throws IOException {
		ArrayList<Epg> list = get(time);
		if(list.size() == 1)
			return list.get(0);
		else
			return new Epg(mChannel, true);
	}
	
	public Epg getNext() throws IOException {
		ArrayList<Epg> list = get(EPG_NEXT);
		if(list.size() == 1)
			return list.get(0);
		else
			return new Epg(mChannel, true);
	}
	
	public Epg getNow() throws IOException {
		ArrayList<Epg> list = get(EPG_NOW);
		if(list.size() == 1)
			return list.get(0);
		else
			return new Epg(mChannel, true);
	}
	
	public ArrayList<Epg> get(long count) throws IOException {
		ArrayList<Epg> result = new ArrayList<Epg>();
		String zusatz = "";
		String[] sArr;
		Epg epg = new Epg();
		int epgAnzahl = 0;

		if (count == EPG_NOW)
			zusatz = " now";
		else if (count == EPG_NEXT)
			zusatz = " next";
		else if (count > 1000)
			zusatz = " at " + count;

		mConnection.sendData("LSTE " + mChannel + zusatz + "\n");
		while (true) {
			String s = mConnection.readLine();
			try {
				if (s.charAt(3) == ' ') // Ende der VDR-Ausgaben erreicht
					break;
				if (count > 0) { // wenn 0, dann werden alle Epg-Daten ausgelesen
					if (epgAnzahl >= count) {
						mConnection.close();
						break;
					}
				}
				switch (s.charAt(4)) {
				case 'E':// Start Epg-Eintrag
					sArr = s.split(" ");
					epg = new Epg();
					epg.startzeit = Long.parseLong(sArr[2]);
					epg.dauer = Integer.parseInt(sArr[3]);
					break;
				case 'T':// Titel
					epg.titel = s.substring(6);
					break;
				case 'D':// Beschreibung
					epg.beschreibung = s.substring(6).replace('|', '\n');
					break;
				case 'S':// Kurztext
					epg.kurztext = s.substring(6);
					break;
				case 'V':// VPS
					sArr = s.split(" ");
					epg.vps = Long.parseLong(sArr[1]);
					break;
				case 'X':// Stream info
					sArr = s.split(" ");
					if (sArr.length > 1) {
						int kind = Integer.parseInt(sArr[1]);
						StreamInfo si = new StreamInfo();
						if (sArr.length >= 3)
							si.type = sArr[2];
						if (sArr.length >= 4)
							si.language = sArr[3];
						if (sArr.length >= 5) {
							StringBuilder sb = new StringBuilder();
							for (int i = 4; i < sArr.length; i++) {
								if (i > 4)
									sb.append(" ");
								sb.append(sArr[i]);
							}
							si.description = sb.toString();
						}
						switch (kind) {
						case 1:
							epg.setVideoStream(si);
							break;
						case 2:
							epg.addAudioStream(si);
							break;
						case 4:
							epg.setAudioType(si);
							break;
						case 5:
							epg.setVideoType(si);
							break;
						}
					}
					break;
				case 'e':// Ende epg-Eintrag
					epgAnzahl++;
					epg.kanal = mChannel;
					if (epg != null)
						result.add(epg);
					break;
				}
			} catch (Exception e) {
				MyLog.v(TAG, "ERROR parsing epg data: " + e.toString());
			}
		}
		mConnection.closeDelayed();
		for (int i = 0; i < result.size(); i++)
			result.get(i).calculatePercentDone();
		return result;
	}
}
