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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

public class Channel implements Comparable<Channel> {
	public static final String TAG = "Channel";
	
	private static final long EPG_UPDATE_PERIOD = 1;
	
	public static final String logoDir = Preferences.getLogoDirName();
	
	private long mLastEpgUpdate = 0;
	private Epg mNext = null;
	private Epg mNow = null;

	public String name;
	public String zusatz;
	public int nr;
	public final Bitmap logo;
	public boolean isTemp = false;

	public Epg viewEpg = null;		// used by EpdataController
	
	public Channel(String vdrchannelinfo) throws IOException {
		parse(vdrchannelinfo);
		mNow = new Epg(nr, true);
		mNext = new Epg(nr, true);
		logo = initLogo();
	}

	public void cleanupEpg() {
		if(mNow != null && (System.currentTimeMillis() / 1000) > (mNow.startzeit + mNow.dauer)) {
			mNow = new Epg(nr, true);
			mNext = new Epg(nr, true);
		}
	}

	@Override
	public int compareTo(Channel another) {
		return ((Integer) nr).compareTo(another.nr);
	}
	
	public ArrayList<Epg> get(int count) throws IOException {
		return new Epgs(nr).get(count);
	}
	
	public ArrayList<Epg> getAll() throws IOException {
		return new Epgs(nr).getAll();
	}
	
	public Epg getAt(long time) throws IOException {
		return new Epgs(nr).getAt(time);
	}
	
	public long getMillisToNextUpdate() {
		if (mLastEpgUpdate == 0)
			return 0;
		else
			return ((mLastEpgUpdate + 1) * 60 * 1000) - System.currentTimeMillis() + 5000; 
	}
	
	public Epg getNext() {
		return mNext;
	}

	public Epg getNow() {
		return mNow;
	}

	public boolean hasLogo() {
		return (logo != null);
	}
	
	private Bitmap initLogo() {
		if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
			return null;
		
		String filename = logoDir + "/" + name + ".png";
		Bitmap image = null;
		File imagefile = new File(filename);
		if (imagefile.exists()) {
			image = BitmapFactory.decodeFile(filename);
		}
		return image;
	}
	
	private void parse(String vdrchannelinfo) throws IOException {
		int idx1 = vdrchannelinfo.indexOf(' ');
		String snr;
		if (idx1 == -1) {
			snr = vdrchannelinfo;
			name = "";
			zusatz = "";
		} else {
			snr = vdrchannelinfo.substring(0, idx1);
			String[] ss = vdrchannelinfo.substring(idx1 + 1).split(":");
			if (ss.length > 0) {
				String eintrag = ss[0].split(",")[0];
				String[] sss = eintrag.split(";");
				if (sss.length > 1) {
					name = sss[0];
					zusatz = sss[1];
				} else {
					name = eintrag;
					zusatz = "";
				}
			}
		}
		try {
			nr = Integer.valueOf(snr);
		} catch (NumberFormatException e) {
			MyLog.v(TAG, "ERROR parsing channelinfo: " + e.toString());
			throw new IOException("invalid channelinfo");
		}
	}
	
	public void updateEpg(boolean next) throws IOException {
		long systemTime = (long) System.currentTimeMillis() / 60000;
		if ((mLastEpgUpdate == 0)
				|| (systemTime - mLastEpgUpdate >= EPG_UPDATE_PERIOD)) {
			if (mNow.isEmpty
					|| ((System.currentTimeMillis() / 1000) >= (mNow.startzeit + mNow.dauer - 60))) {
				mNow = new Epgs(nr).getNow();
				if (next)
					mNext = new Epgs(nr).getNext();
			}
		}
		if (next && mNext.isEmpty) {
			mNext = new Epgs(nr).getNext();
		}
		mLastEpgUpdate = systemTime;
		mNow.calculatePercentDone();
	}
}