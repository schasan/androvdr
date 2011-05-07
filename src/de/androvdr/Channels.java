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
import java.util.List;
import java.util.ListIterator;

import org.hampelratte.svdrp.Response;
import org.hampelratte.svdrp.commands.CHAN;
import org.hampelratte.svdrp.commands.LSTC;
import org.hampelratte.svdrp.parsers.ChannelParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.androvdr.svdrp.VDRConnection;

public class Channels {
	private static transient Logger logger = LoggerFactory.getLogger(Channels.class);
	
	private static Boolean mIsInitialized = false;
	private static String mDefaults;
	private static ArrayList<Channel> mItems = new ArrayList<Channel>();
	
	public Channels(String defaults) throws IOException {
		if(mDefaults != null && defaults.compareTo(mDefaults) != 0)
			mIsInitialized = false;
		mDefaults = defaults;
		init();
	}
	
	public Channel addChannel(int kanal) throws IOException {
		Channel channel = null;
		boolean isTempChannel = (kanal == -1);
		
		Response response; 
		// determine the current channel
		if (kanal == -1) {
		    response = VDRConnection.send(new CHAN());
		    if(response.getCode() != 250) {
		        throw new IOException("Couldn't determine current channel number");
		    }
		    kanal = Integer.parseInt(response.getMessage().split(" ")[0]);
		}
	    response = VDRConnection.send(new LSTC(kanal));
		if(response.getCode() != 250) {
		    throw new IOException(response.getCode() + " - " + response.getMessage());
		}
		try {
		    List<org.hampelratte.svdrp.responses.highlevel.Channel> channels = ChannelParser.parse(response.getMessage(), true);
			channel = new Channel(channels.get(0));
			channel.isTemp = isTempChannel;
			if (getChannel(channel.nr) == null)
				mItems.add(channel);
		} catch (IOException e) {
			logger.error("ungueltiger Kanaldatensatz",e);
			// faengt u A NumberformatExceptions ab
		} catch(ParseException pe) {
		    logger.error("Couldn't parse channel details", pe);
		}
		return channel;
	}

	public static void clear() {
		mIsInitialized = false;
		mItems.clear();
	}
	
	public void deleteTempChannels() {
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

		synchronized (mIsInitialized) {
			if (mIsInitialized)
				return;
			
			String[] channelList = mDefaults.split(",");
			String[] bereich;
			
			mItems.clear();
			mIsInitialized = false;
			
			try {
				int i = 0;
				for (i = 0; i < channelList.length; i++) { // Bereiche oder einzelne Kanaele
					try {
						if (channelList[i].contains("-")) { // hier sind die Bereiche
							bereich = channelList[i].split("-");
							int from = Integer.valueOf(bereich[0]);
							int to = Integer.valueOf(bereich[1]);
							for (int x = from; x <= to; x++) {
								addChannel(x);
							}
						} else { // einzelne Kanaele
							addChannel(Integer.valueOf(channelList[i]));
						}
					} catch (IOException e) {
						throw e;
					} catch (Exception e) {
						logger.error("invalid channellist: {}", mDefaults);
						continue;
					}
				}
				Collections.sort(mItems);
				mIsInitialized = true;
			} catch (IOException e) {
				logger.error("Couldn't initialize Channels", e);
				throw e;
			}
		}
	}
	
	public static boolean isInitialized() {
		return mIsInitialized;
	}
}
