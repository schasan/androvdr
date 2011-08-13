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

package de.androvdr.controllers;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Formatter;
import java.util.GregorianCalendar;

import org.hampelratte.svdrp.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.os.Handler;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import de.androvdr.Channel;
import de.androvdr.Channels;
import de.androvdr.Epg;
import de.androvdr.Messages;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.StreamInfo;
import de.androvdr.VdrCommands;

public class EpgdataController extends AbstractController {
	private static transient Logger logger = LoggerFactory.getLogger(EpgdataController.class);
	
	private static final int pgi_titelSize = 20,
							 pgi_shorttextSize = 16,
							 pgi_defaultSize = 15;
	
	public static final int EPGDATA_ACTION_RECORD = 1;
	
	public String lastError;
	
	private final LinearLayout mView;
	private final int mChannelNumber;
	private Channel mChannel;
	
	public EpgdataController(Activity activity, Handler handler,
			LinearLayout view, int channelNumber) {
		super.onCreate(activity, handler);
		mView = view;
		
		mChannelNumber = channelNumber;
		mActivity.registerForContextMenu(mView.findViewById(R.id.pgi_layout_content));
		try {
			mChannel = new Channels(Preferences.getVdr().channellist).getChannel(mChannelNumber);
			if (mChannel == null)
				throw new IOException("Couldn't get channel");
			
			showData();
		} catch (IOException e) {
			logger.error("Couldn't load channels", e);
			sendMsg(mHandler, Messages.MSG_ERROR, e.getMessage());
		}
	}

	public void action(int action) {
		if (mChannel.viewEpg == null)
			return;
		
		switch (action) {
		case EPGDATA_ACTION_RECORD:
			Response response = VdrCommands.setTimer(mChannel.viewEpg);
			if (response.getCode() != 250)
				logger.error("Couldn't set timer: {}", response.getMessage());
			Toast.makeText(mActivity, response.getCode() + " - " + response.getMessage().replaceAll("\n$", ""), 
					Toast.LENGTH_LONG).show();
			break;
		}
	}
	
	public String getTitle() {
		if (mChannel.viewEpg != null)
			return mChannel.viewEpg.titel;
		else
			return "";
	}
	
	private void showData() {
		Epg epg = mChannel.viewEpg;
		
		if (epg != null) {
			TextView tv = (TextView) mView.findViewById(R.id.pgi_header);
			if (tv != null){
				tv.setText(epg.titel);
				tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,pgi_defaultSize + Preferences.textSizeOffset);
			}
			tv = (TextView) mView.findViewById(R.id.pgi_title);
			if (tv != null){
				tv.setText(epg.titel);
				tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,pgi_titelSize + Preferences.textSizeOffset);
			}
			tv = (TextView) mView.findViewById(R.id.pgi_shorttext);
			if (tv != null){
				tv.setText(epg.kurztext);
				tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,pgi_shorttextSize + Preferences.textSizeOffset);
			}
			tv = (TextView) mView.findViewById(R.id.pgi_channel);
			if (tv != null){
				tv.setText(mChannel.name);
				tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,pgi_defaultSize + Preferences.textSizeOffset);
			}
			tv = (TextView) mView.findViewById(R.id.pgi_start);
			if (tv != null) {
				SimpleDateFormat dateformatter = new SimpleDateFormat(
						Preferences.dateformat);
				SimpleDateFormat timeformatter = new SimpleDateFormat(
						Preferences.timeformat);
				String[] weekdays = mActivity.getResources().getStringArray(
						R.array.weekday);
				GregorianCalendar calendar = new GregorianCalendar();
				StringBuilder sb = new StringBuilder();

				calendar.setTimeInMillis(epg.startzeit * 1000);
				sb.append(weekdays[calendar.get(Calendar.DAY_OF_WEEK) - 1]
						+ " " + dateformatter.format(calendar.getTime()) + " ");
				sb.append(timeformatter.format(calendar.getTime()));
				sb.append(" - ");
				calendar.setTimeInMillis(epg.startzeit * 1000 + epg.dauer
						* 1000);
				sb.append(timeformatter.format(calendar.getTime()));
				tv.setText(sb.toString());
				tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,pgi_defaultSize + Preferences.textSizeOffset);
			}
			tv = (TextView) mView.findViewById(R.id.pgi_durationtext);
			if (tv != null) {
				tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,pgi_defaultSize + Preferences.textSizeOffset);
			}
			tv = (TextView) mView.findViewById(R.id.pgi_duration);
			if (tv != null) {
				StringBuilder sb = new StringBuilder();
				new Formatter(sb).format("%02d:%02d", epg.dauer / 3600,
						(epg.dauer % 3600) / 60);
				tv.setText(sb.toString());
				tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,pgi_defaultSize + Preferences.textSizeOffset);
			}
			tv = (TextView) mView.findViewById(R.id.pgi_description);
			if (tv != null){
				tv.setText(epg.beschreibung);
				tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,pgi_defaultSize + Preferences.textSizeOffset);
			}

			TableLayout tb = (TableLayout) mView
					.findViewById(R.id.pgi_infotable);
			if (tb != null) {
				StreamInfo si = epg.getVideoStream();
				if (si != null)
					tb.addView(tableRow(mActivity.getString(R.string.videoformat), si));

				ArrayList<StreamInfo> asi = epg.getAudioStreams();
				if (asi != null)
					for (int i = 0; i < asi.size(); i++) {
						if (i == 0)
							tb.addView(tableRow(mActivity.getString(R.string.audiostreams), asi.get(i)));
						else
							tb.addView(tableRow("", asi.get(i)));
					}

				si = epg.getVideoType();
				if (si != null)
					tb.addView(tableRow(mActivity.getString(R.string.videoformat), si));

				si = epg.getAudioType();
				if (si != null)
					tb.addView(tableRow(mActivity.getString(R.string.audioformat), si));
			}
		}
	}

	private TableRow tableRow(String title, StreamInfo streaminfo) {
		TableRow tr = new TableRow(mActivity);
		int px = (int) mActivity.getResources().getDisplayMetrics().density * 6;
		
		TextView tc = new TextView(mActivity);
		tc.setText(title);
		tc.setTextSize(TypedValue.COMPLEX_UNIT_DIP,pgi_defaultSize + Preferences.textSizeOffset);
		tr.addView(tc);
		
		tc = new TextView(mActivity);
		tc.setText(streaminfo.description);
		tc.setTextSize(TypedValue.COMPLEX_UNIT_DIP,pgi_defaultSize + Preferences.textSizeOffset);
		tc.setPadding(px, 0, 0, 0);
		tr.addView(tc);
		
/*
		tc = new TextView(mActivity);
		tc.setText(streaminfo.language);
		tc.setPadding(px, 0, 0, 0);
		tr.addView(tc);
*/
		
		return tr;
	}
}
