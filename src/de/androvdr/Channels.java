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
import java.util.ListIterator;

public class Channels {
	private static final String TAG = "Channels";
	
	private static boolean mIsInitialized = false;
	private static String mDefaults;
	private static ArrayList<Channel> mItems = new ArrayList<Channel>();
	
	public Channels(String defaults) throws IOException {
		if(mDefaults != null && defaults.compareTo(mDefaults) != 0)
			mIsInitialized = false;
		mDefaults = defaults;
		if(! mIsInitialized)
			init();
	}
	
	public Channel addChannel(int kanal, Connection connection) throws IOException {
		Channel channel = null;
		
		if (kanal == -1)
			connection.sendData("CHAN\n");
		else
			connection.sendData("LSTC "+kanal+"\n");
		
		String s = connection.readLine().substring(4);
		try {
			if(s.contains("not defined")){
				MyLog.v(TAG, "Kanal "+kanal+" nicht gefunden");
				return null;
			}
			channel = new Channel(s);
			if (kanal == -1) {
				channel.isTemp = true;
				if (getChannel(channel.nr) == null)
					mItems.add(channel);
			} else {
				mItems.add(channel);
			}
		} catch (IOException e) {
			MyLog.v(TAG, "ungueltiger Kanaldatensatz",e);
			// faengt u A NumberformatExceptions ab
		}
		return channel;
	}

	public static void clear() {
		mIsInitialized = false;
		mItems.clear();
	}
	
	private void deleteTempChannels() {
		for(ListIterator<Channel> itr = mItems.listIterator(); itr.hasNext();) {
			Channel channel = itr.next();
			if (channel.isTemp)
				itr.remove();
		}
	}
	
	public ArrayList<Channel> getItems() {
		deleteTempChannels();
		return mItems;
	}
	
	public Channel getChannel(int channel) {
		for(int i = 0; i < mItems.size(); i++)
			if(mItems.get(i).nr == channel)
				return mItems.get(i);
		return null;
	}
	
	public String getName(int channel) {
		for(int i = 0; i < mItems.size(); i++)
			if(mItems.get(i).nr == channel)
				return mItems.get(i).name;
		return "";
	}

	public void init() throws IOException {
		String[] channelList = mDefaults.split(",");
		String[] bereich;
		
		mItems.clear();
		mIsInitialized = false;
		Connection connection = null;
		
		try {
			connection = new Connection();
			int i = 0;
			for (i = 0; i < channelList.length; i++) { // Bereiche oder einzelne Kanaele
				try {
					if (channelList[i].contains("-")) { // hier sind die Bereiche
						bereich = channelList[i].split("-");
						int from = Integer.valueOf(bereich[0]);
						int to = Integer.valueOf(bereich[1]);
						for (int x = from; x <= to; x++) {
							addChannel(x, connection);
						}
					} else { // einzelne Kanaele
						addChannel(Integer.valueOf(channelList[i]), connection);
					}
				} catch (IOException e) {
					throw e;
				} catch (Exception e) {
					MyLog.v(TAG, "ERROR invalid channellist: " + mDefaults);
					continue;
				}
			}
			mIsInitialized = true;
		} catch (IOException e) {
			MyLog.v(TAG, "Channels.init(): " + e);
			throw e;
		} finally {
			if (connection != null)
				connection.closeDelayed();
		}
	}
	
	public static boolean isInitialized() {
		return mIsInitialized;
	}
}
