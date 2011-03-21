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
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

public class Timers {
	private static final String TAG = "Timers";
	
	final private Connection mConnection;
	private ArrayList<Timer> mItems = new ArrayList<Timer>();
	
	public Timers() throws IOException {
		mConnection = new Connection();
		init();
		mConnection.closeDelayed();
	}
	
	public Timers(Connection connection) throws IOException {
		mConnection = connection;
		init();
	}
	
	public ArrayList<Timer> getItems() {
		return mItems;
	}
	
	private void init() throws IOException {
		int lastUpdate = (int) (new Date().getTime() / 60000);
		try {
			boolean isLastLine = false;
			
			mConnection.sendData("LSTT\n");
			do {
				String s = mConnection.readLine();
				
				if (s.charAt(3) == ' ')
					isLastLine = true;
				
				try {
					Timer timer = new Timer(s.substring(4));
					timer.lastUpdate = lastUpdate;
					mItems.add(timer);
				} catch (ParseException e) {
					MyLog.v(TAG, "ERROR invalid timer format: " + e.toString());
					continue;
				}
				
			} while (! isLastLine);
			Collections.sort(mItems, new TimerComparer());
		} catch (IOException e) {
			MyLog.v(TAG, "ERROR init(): " + e.toString());
			throw e;
		}
	}
	
	private class TimerComparer implements java.util.Comparator<Timer> {
		public int compare(Timer a, Timer b) {
			Long l = a.start;
			return l.compareTo(b.start);
		}
	}
}
