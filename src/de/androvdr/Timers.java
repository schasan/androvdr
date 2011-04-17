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
	
	private ArrayList<Timer> mItems = new ArrayList<Timer>();
	
	public Timers() throws IOException {
		init();
	}

	public Timers(EpgSearch search) throws IOException {
		init(search);
	}
	
	public ArrayList<Timer> getItems() {
		return mItems;
	}
	
	private void init() throws IOException {
		int lastUpdate = (int) (new Date().getTime() / 60000);
		Connection connection = null;
		try {
			boolean isLastLine = false;
			
			connection = new Connection();
			connection.sendData("LSTT\n");
			do {
				String s = connection.readLine();
				
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
		} finally {
			if (connection != null)
				connection.closeDelayed();
		}
	}

	private void init(EpgSearch search) throws IOException {
		int lastUpdate = (int) (new Date().getTime() / 60000);
		Connection connection = null;
		try {
			boolean isLastLine = false;
			int marginStart = 0;
			int marginStop = 0;
			int count = 0;
			
			String command = "PLUG epgsearch FIND 0:"
					+ search.search
					+ ":0:::0::0:0:"
					+ (search.inTitle ? 1 : 0) + ":"
					+ (search.inSubtitle ? 1 : 0) + ":"
					+ (search.inDescription ? 1 : 0) + ":"
					+ "0:::0:0:0:0::::::0:0:0::0::1:1:1:0::::::0:::0::0:::::";
			
			connection = new Connection();
			connection.sendData(command + "\n");
			do {
				String s = connection.readLine();

				count += 1;
				if (count > Preferences.epgsearch_max) {
					connection.close();
					break;
				}
				
				if (s.length() < 4) {
					MyLog.v(TAG, "ERROR init(search): " + s);
					throw new IOException("invalid data received");
				}
				
				if (s.charAt(3) == ' ')
					isLastLine = true;
				
				try {
					int result = Integer.parseInt(s.substring(0, 3));
					
					if (result == 550)
						throw new IOException("550 epgsearch plugin not found");
					
					if (result == 900) {
						Timer timer = new Timer();
						timer.initFromEpgsearchResult(s);
						timer.lastUpdate = lastUpdate;
						mItems.add(timer);
					}
				} catch (ParseException e) {
					MyLog.v(TAG, "ERROR invalid timer format: " + e.toString());
					continue;
				}
				
			} while (! isLastLine);
			
			isLastLine = false;
			if (connection.isClosed)
				connection.open();
			connection.sendData("PLUG epgsearch SETP\n");
			do {
				String s = connection.readLine();
				if (s.length() < 4) {
					MyLog.v(TAG, "ERROR init(search): " + s);
					throw new IOException("invalid data received");
				}
				
				if (s.charAt(3) == ' ')
					isLastLine = true;
				
				try {
					String[] sa = s.substring(4).split(":");
					if (sa[0].equals("DefMarginStart"))
						marginStart = Integer.parseInt(sa[1].trim()) * 60;
					if (sa[0].equals("DefMarginStop"))
						marginStop = Integer.parseInt(sa[1].trim()) * 60;
				} catch (Exception e) {
					MyLog.v(TAG, "ERROR invalid epgsearch setp response");
					continue;
				}
			} while (! isLastLine);

			for (Timer timer : mItems) {
				timer.start += marginStart;
				timer.end -= marginStop;
			}
			
			Collections.sort(mItems, new TimerComparer());
		} catch (IOException e) {
			MyLog.v(TAG, "ERROR init(search): " + e.toString());
			throw e;
		} finally {
			if (connection != null)
				connection.closeDelayed();
		}
	}
	
	private class TimerComparer implements java.util.Comparator<Timer> {
		public int compare(Timer a, Timer b) {
			Long l = a.start;
			return l.compareTo(b.start);
		}
	}
}
