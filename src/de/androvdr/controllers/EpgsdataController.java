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
import java.util.GregorianCalendar;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import de.androvdr.Channel;
import de.androvdr.Channels;
import de.androvdr.Epg;
import de.androvdr.Messages;
import de.androvdr.MyLog;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.VdrCommands;
import de.androvdr.activities.EpgdataActivity;

public class EpgsdataController extends AbstractController implements Runnable {
	private static final String TAG = "EpgsController";
	
	private static final int epgtitelSize = 20,
							 epgdefaultSize = 15;

	public static final int EPG_ALL = -1;
	public static final int EPG_NOW = -1;
	
	public static final int EPGSDATA_ACTION_RECORD = 1;
	
	private int mChannelNumber;
	private EpgdataAdapter mEpgdataAdapter;
	private ArrayList<Epg> mEpgdata;
	private final boolean mIsMultiChannelView;
	private final ListView mListView;
	private final LinearLayout mMainView;
	private final int mMaxEpgdata;
	
	// --- needed by each row ---
	private final SimpleDateFormat dateformatter;
	private final SimpleDateFormat timeformatter;
	private final String[] weekdays;
	private final GregorianCalendar calendar;
	
	public String lastError = "";
	
	private Handler mThreadHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.arg1) {
			case Messages.MSG_DONE:
				try {
					setEpgAdapter(new EpgdataAdapter(mActivity, mEpgdata),
							mListView);
					mHandler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_DISMISS));
				} catch (IOException e) {
					MyLog.v(TAG, "ERROR setEpgAdapter:" + e);
					lastError = e.toString();
					mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
				}
				break;
			case Messages.MSG_VDR_ERROR:
				mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
				break;
			}
		}
	};
	
	public EpgsdataController(Activity activity, Handler handler,
			LinearLayout view, int channelNumber, int max) {
		super.onCreate(activity, handler);
		
		mMainView = view;
		mListView = (ListView) view.findViewById(android.R.id.list);
		
		TextView tv = (TextView) view.findViewById(R.id.epgsheader);
		tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,	epgdefaultSize + Preferences.textSizeOffset);
		
		mChannelNumber = channelNumber;
		mMaxEpgdata = max;
		mIsMultiChannelView = (channelNumber == EPG_NOW);
		
		dateformatter = new SimpleDateFormat(Preferences.dateformat);
		timeformatter = new SimpleDateFormat(Preferences.timeformat);
		weekdays = mActivity.getResources().getStringArray(R.array.weekday);
		calendar = new GregorianCalendar();
		
		Message msg = Messages.obtain(Messages.MSG_PROGRESS_SHOW);
		msg.arg2 = R.string.loading;
		mHandler.sendMessage(msg);
		
		Thread thread = new Thread(this);
		thread.start();
	}
	
	public void action(int action, int position) {
		Epg epg = mEpgdataAdapter.getItem(position);
		
		switch (action) {
		case EPGSDATA_ACTION_RECORD:
			try {
				VdrCommands.setTimer(epg);
			} catch (IOException e) {
				MyLog.v(TAG, "ERROR action: " + e.toString());
				lastError = e.toString();
				mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
			}
			break;
		}
	}
	
	private OnItemClickListener getOnItemClickListener() {
		return new OnItemClickListener() {
			public void onItemClick(AdapterView<?> listView, View v,
					int position, long ID) {
				Epg item = (Epg) listView.getAdapter()
						.getItem(position);
				try {
					Channel channel = new Channels(Preferences.getVdr().channellist).getChannel(item.kanal);
					channel.viewEpg = item;
					
					Intent intent = new Intent(v.getContext(), EpgdataActivity.class);
					intent.putExtra("channelnumber", channel.nr);
					mActivity.startActivityForResult(intent, 1);
					
				} catch (IOException e) {
					MyLog.v(TAG, "ERROR getOnItemClickListener: " + e.toString());
					lastError = e.toString();
					mThreadHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
				}
			}
		};
	}

	public String getTitle(int position) {
		Epg epg = mEpgdataAdapter.getItem(position);
		return epg.titel;
	}
	
	public void run() {
		try {
			Channels channels = new Channels(Preferences.getVdr().channellist);
			if (mChannelNumber == EPG_NOW) {
				mEpgdata = new ArrayList<Epg>();
				for (int i = 0; i < channels.getItems().size(); i++) {
					Channel channel = channels.getItems().get(i);
					channel.updateEpg(true);
					mEpgdata.add(channel.getNow());
					mEpgdata.add(channel.getNext());
				}
			} else {
				if (mChannelNumber == 0) {
					Channel c = channels.addChannel(-1);
					mChannelNumber = c.nr;
				}

				if (channels.getChannel(mChannelNumber) == null) {
					Channel c = channels.addChannel(mChannelNumber);
					c.isTemp = true;
				}
				
				if (mMaxEpgdata == EPG_ALL)
					mEpgdata = channels.getChannel(mChannelNumber).getAll();
				else
					mEpgdata = channels.getChannel(mChannelNumber).get(mMaxEpgdata);
			}
			mThreadHandler.sendMessage(Messages.obtain(Messages.MSG_DONE));
		} catch (IOException e) {
			MyLog.v(TAG, "ERROR: new Channels() " + e.toString());
			lastError = e.toString();
			mThreadHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
		}
	}

	private void setEpgAdapter(EpgdataAdapter adapter, ListView listView) throws IOException {
		mEpgdataAdapter = adapter;

		TextView tv = (TextView) mMainView.findViewById(R.id.epgsheader);
		if (! mIsMultiChannelView) {
			tv.setText(new Channels(Preferences.getVdr().channellist).getName(mChannelNumber));
		} else {
			tv.setVisibility(View.GONE);
		}

		listView.setAdapter(adapter);
		listView.setOnItemClickListener(getOnItemClickListener());
		listView.setSelected(true);
		listView.setSelection(0);
		mActivity.registerForContextMenu(listView);
	}

	private class EpgdataAdapter extends ArrayAdapter<Epg> {
		private final Activity mActivity;
		
		private class ViewHolder {
			private TextView date;
			private TextView channel;
			private ProgressBar progress;
			private TextView title;
			private TextView shorttext;
		}
		public EpgdataAdapter(Activity activity, ArrayList<Epg> epgdata) {
			super(activity, R.layout.epgsdata_item, epgdata);
			mActivity = activity;
		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
			View row;
			if (convertView == null) {
				LayoutInflater inflater = mActivity.getLayoutInflater();
				row = inflater.inflate(R.layout.epgsdata_item, null);
				
				ViewHolder vh = new ViewHolder();
				vh.date = (TextView) row.findViewById(R.id.epgdate);
				vh.channel = (TextView) row.findViewById(R.id.epgchannel);
				vh.title = (TextView) row.findViewById(R.id.epgtitle);
				vh.shorttext = (TextView) row.findViewById(R.id.epgshorttext);

				vh.date.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						epgdefaultSize + Preferences.textSizeOffset);
				vh.channel.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						epgdefaultSize + Preferences.textSizeOffset);
	        	vh.title.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
	        			epgtitelSize + Preferences.textSizeOffset);
	       		vh.shorttext.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
	       				epgdefaultSize + Preferences.textSizeOffset);

				row.setTag(vh);
			} else {
				row = convertView;
			}

			Epg item = this.getItem(position);
			ViewHolder vh = (ViewHolder) row.getTag();
			
			calendar.setTimeInMillis(item.startzeit * 1000);
			vh.date.setText(weekdays[calendar.get(Calendar.DAY_OF_WEEK) - 1]
					+ " " + dateformatter.format(calendar.getTime())
					+ " " + timeformatter.format(calendar.getTime()));

			if (mIsMultiChannelView) {
				String text;
				try {
					text = String.valueOf(new Channels(Preferences.getVdr().channellist).getName(item.kanal));
				} catch (IOException e) {
					text = "";
				}
				vh.channel.setText(text);
				vh.channel.setVisibility(View.VISIBLE);

				vh.progress.setProgress(item.getActualPercentDone());
				vh.progress.setVisibility(View.VISIBLE);
			}
			
        	String text = item.titel;
        	vh.title.setText(text);

        	if(item.kurztext != null) {
            	vh.shorttext.setText(item.kurztext);                            
        	}
       		else {
       			vh.shorttext.setText("");
       		}

			return row;
		}
	}
}
