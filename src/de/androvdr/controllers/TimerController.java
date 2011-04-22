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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;

import org.hampelratte.svdrp.Response;
import org.hampelratte.svdrp.commands.CHAN;
import org.hampelratte.svdrp.commands.DELT;
import org.hampelratte.svdrp.commands.MODT;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import de.androvdr.Channel;
import de.androvdr.Channels;
import de.androvdr.Epg;
import de.androvdr.EpgSearch;
import de.androvdr.Messages;
import de.androvdr.MyLog;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.Timer;
import de.androvdr.Timers;
import de.androvdr.VdrCommands;
import de.androvdr.activities.EpgdataActivity;
import de.androvdr.activities.EpgsdataActivity;
import de.androvdr.devices.VdrDevice;
import de.androvdr.svdrp.VDRConnection;

public class TimerController extends AbstractController implements Runnable {
	private static final String TAG = "TimerController";
	
	private static final int timer_titleSize = 20,
							 timer_defaultSize = 15;
	
	private TimerAdapter mAdapter;
	private Channels mChannels;
	private final ListView mListView;
	private final EpgSearch mSearchFor;
	private ArrayList<Timer> mTimer;
	
	// --- needed by each row ---
	private final SimpleDateFormat dateformatter;
	private final SimpleDateFormat timeformatter;
	private final String[] weekdays;
	private final GregorianCalendar calendar;
	
	public final static int TIMER_ACTION_DELETE = 1;
	public final static int TIMER_ACTION_TOGGLE = 2;
	public final static int TIMER_ACTION_SHOW_EPG = 3;
	public final static int TIMER_ACTION_RECORD = 4;
	public final static int TIMER_ACTION_PROGRAMINFOS = 5;
	public final static int TIMER_ACTION_PROGRAMINFOS_ALL = 6;
	public final static int TIMER_ACTION_SWITCH_CAHNNEL = 7;

	public String lastError;
	
	private Handler mThreadHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.arg1) {
			case Messages.MSG_DONE:
					mAdapter = new TimerAdapter(mActivity, mTimer);
					setTimerAdapter(mAdapter, mListView);
					mHandler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_DISMISS));
				break;
			case Messages.MSG_VDR_ERROR:
				mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
				break;
			case Messages.MSG_EPGSEARCH_NOT_FOUND:
				mHandler.sendMessage(Messages.obtain(Messages.MSG_EPGSEARCH_NOT_FOUND));
				break;
			}
		}
	};

	public TimerController(Activity activity, Handler handler, ListView listView, EpgSearch epgSearch) {
		super.onCreate(activity, handler);
		mListView = listView;
		mSearchFor = epgSearch;
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
		final Timer item = mAdapter.getItem(position);
		try {
			Handler handler;
			int lastUpdate = (int) (new Date().getTime() / 60000);
			
			switch (action) {
			case TIMER_ACTION_DELETE:
				handler = new Handler() {
					@Override
					public void handleMessage(Message msg) {
						switch (msg.arg1) {
						case Messages.MSG_DATA_UPDATE_DONE:
							mHandler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_DISMISS));
							mAdapter.notifyDataSetChanged();
							
							Response response = VDRConnection.send(new DELT(item.number));
							if(response.getCode() == 250) {
							    mAdapter.remove(item);
							    for (int i = 0; i < mTimer.size(); i++)
							        mTimer.get(i).lastUpdate = 0;
							} else {
							    Toast.makeText(mActivity, response.getMessage().replace("\n", ""), Toast.LENGTH_LONG).show();
							}
							break;
						case Messages.MSG_VDR_ERROR:
							mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
							break;
						}
					}
				};
				
				if (item.lastUpdate < lastUpdate) {
					Message msg = Messages.obtain(Messages.MSG_PROGRESS_SHOW);
					msg.arg2 = R.string.updating;
					mHandler.sendMessage(msg);
					new Thread(new TimerUpdater(handler)).start();
				}
				else
					handler.sendMessage(Messages.obtain(Messages.MSG_DATA_UPDATE_DONE));
				break;
			case TIMER_ACTION_PROGRAMINFOS:
			case TIMER_ACTION_PROGRAMINFOS_ALL:
				Intent intent = new Intent(mActivity, EpgsdataActivity.class);
				intent.putExtra("channelnumber", item.channel);
				if (action == TIMER_ACTION_PROGRAMINFOS)
					intent.putExtra("maxitems", Preferences.getVdr().epgmax);
				else
					intent.putExtra("maxitems", EpgsdataController.EPG_ALL);
				mActivity.startActivityForResult(intent, 1);
				break;
			case TIMER_ACTION_RECORD:
				Epg epg = new Epg();
				epg.kanal = item.channel;
				epg.startzeit = item.start;
				Long l = (item.end - item.start);
				epg.dauer = l.intValue();
				epg.titel = item.title;
				VdrCommands.setTimer(epg);
				break;
			case TIMER_ACTION_SHOW_EPG:
				new GetEpgTask().execute(item);
				break;
			case TIMER_ACTION_SWITCH_CAHNNEL:
			    VDRConnection.send(new CHAN(item.channel));
				break;
			case TIMER_ACTION_TOGGLE:
				handler = new Handler() {
					@Override
					public void handleMessage(Message msg) {
						switch (msg.arg1) {
						case Messages.MSG_DATA_UPDATE_DONE:
							mHandler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_DISMISS));
							mAdapter.notifyDataSetChanged();
							if (item.lastUpdate > 0) {
								String s;
								if (item.isActive())
									s = " OFF";
								else
									s = " ON";
								MODT modt = new MODT(item.number, s);
								Response response = VDRConnection.send(modt);
								if(response.getCode() == 250) {
									update();
								}
								else
									Toast.makeText(mActivity, response.getMessage().replace("\n", ""), Toast.LENGTH_LONG).show();
							}
							else
								Toast.makeText(mActivity, R.string.timer_not_found, Toast.LENGTH_LONG).show();
							break;
						case Messages.MSG_VDR_ERROR:
							mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
							break;
						}
					}
				};

				if (item.lastUpdate < lastUpdate) {
					Message msg = Messages.obtain(Messages.MSG_PROGRESS_SHOW);
					msg.arg2 = R.string.updating;
					mHandler.sendMessage(msg);
					new Thread(new TimerUpdater(handler)).start();
				}
				else
					handler.sendMessage(Messages.obtain(Messages.MSG_DATA_UPDATE_DONE));
				break;
			}
		} catch (IOException e) {
			MyLog.v(TAG, "ERROR action: " + e.toString());
			lastError = e.toString();
			mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
		}
	}
	
	private OnItemClickListener getOnItemClickListener() {
		return new OnItemClickListener() {
			public void onItemClick(AdapterView<?> listView, View v,
					int position, long ID) {
				action(TIMER_ACTION_SHOW_EPG, position);
			}
		};
	}

	public CharSequence getTitle(int position) {
		return mAdapter.getItem(position).title;
	}
	
	@Override
	public void run() {
		try {
			if (mSearchFor == null)
				mTimer = new Timers().getItems();
			else {
			    mTimer = new Timers(mSearchFor).getItems();
			}
			mChannels = new Channels(Preferences.getVdr().channellist);
			mThreadHandler.sendMessage(Messages.obtain(Messages.MSG_DONE));
		} catch (IOException e) {
			MyLog.v(TAG, "ERROR new Timers(): " + e.toString());
			lastError = e.toString();
			if (lastError.contains("550"))
				mThreadHandler.sendMessage(Messages.obtain(Messages.MSG_EPGSEARCH_NOT_FOUND));
			else
				mThreadHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
		}
	}

	private void setTimerAdapter(TimerAdapter adapter, ListView listView) {
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(getOnItemClickListener());
		listView.setSelected(true);
		listView.setSelection(0);
		mActivity.registerForContextMenu(listView);
	}
	
	private void update() {
		Handler threadHandler = new Handler() {
			@Override
			public void dispatchMessage(Message msg) {
				switch (msg.arg1) {
				case Messages.MSG_DATA_UPDATE_DONE:
					mHandler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_DISMISS));
					mAdapter.notifyDataSetChanged();
					break;
				case Messages.MSG_VDR_ERROR:
					mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
					break;
				}
			}
		};
		
		Message msg = Messages.obtain(Messages.MSG_PROGRESS_SHOW);
		msg.arg2 = R.string.updating;
		mHandler.sendMessage(msg);
		new Thread(new TimerUpdater(threadHandler)).start();
	}
	
	private class GetEpgTask extends AsyncTask<Timer, Void, String> {

		@Override
		protected String doInBackground(Timer... params) {
			Timer timer = params[0];
			try {
				VdrDevice vdr = Preferences.getVdr();
				Channels channels = new Channels(vdr.channellist);
				Channel channel = channels.getChannel(timer.channel);
				if (channel == null) {
					channel = channels.addChannel(timer.channel);
					channel.isTemp = true;
				}
				channel.viewEpg = channel.getAt(timer.start + ((vdr.margin_start + 1) * 60));
				Intent intent = new Intent(mActivity, EpgdataActivity.class);
				intent.putExtra("channelnumber", channel.nr);
				mActivity.startActivityForResult(intent, 1);
				return "";
			} catch (IOException e) {
				MyLog.v(TAG, "ERROR GetEpgTask: " + e.toString());
				return null;
			}
		}

		@Override
		protected void onPostExecute(String result) {
			mHandler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_DISMISS));
			if (result != null) {
				if (result != "")
					Toast.makeText(mActivity, result, Toast.LENGTH_LONG).show();
			} else {
				mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
			}
		}

		@Override
		protected void onPreExecute() {
			mHandler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_SHOW, R.string.searching));
		}
		
	}
	
	private class TimerAdapter extends ArrayAdapter<Timer> {
		private final Activity mActivity;
		
		private class ViewHolder {
			private TextView date;
			private TextView channel;
			private TextView time;
			private TextView status;
			private ImageView folderimage;
			private TextView foldername;
			private TextView title;
		}
		
		public TimerAdapter(Activity activity, ArrayList<Timer> timer) {
			super(activity, R.layout.timers_item, timer);
			mActivity = activity;
		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
			View row;
			if (convertView == null) {
				LayoutInflater inflater = mActivity.getLayoutInflater();
				row = inflater.inflate(R.layout.timers_item, null);
				
				ViewHolder vh = new ViewHolder();
				vh.date = (TextView) row.findViewById(R.id.timer_date);
				vh.channel = (TextView) row.findViewById(R.id.timer_channel);
				vh.time = (TextView) row.findViewById(R.id.timer_time);
				vh.status = (TextView) row.findViewById(R.id.timer_status);
				vh.folderimage = (ImageView) row.findViewById(R.id.timer_folderimage);
				vh.foldername = (TextView) row.findViewById(R.id.timer_folder);
				vh.title = (TextView) row.findViewById(R.id.timer_title);
				
				vh.date.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						timer_defaultSize + Preferences.textSizeOffset);
				vh.channel.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						timer_defaultSize + Preferences.textSizeOffset);
				vh.time.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						timer_defaultSize + Preferences.textSizeOffset);
				vh.status.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						timer_defaultSize + Preferences.textSizeOffset);
				vh.foldername.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						timer_defaultSize + Preferences.textSizeOffset);
				vh.title.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						timer_titleSize + Preferences.textSizeOffset);
				row.setTag(vh);
			} else {
				row = convertView;
			}
			
			Timer item = this.getItem(position);
			ViewHolder vh = (ViewHolder) row.getTag();
			
			calendar.setTimeInMillis(item.start * 1000);
			if (item.noDate != "")
				vh.date.setText(item.noDate);
			else
				vh.date.setText(weekdays[calendar.get(Calendar.DAY_OF_WEEK) - 1]
							+ " " + dateformatter.format(calendar.getTime()));
			
			if (mChannels.getName(item.channel).equals(""))
				vh.channel.setText("(" + item.channel + ")");
			else
				vh.channel.setText(mChannels.getName(item.channel));

			StringBuilder sb = new StringBuilder();
			sb.append(timeformatter.format(calendar.getTime()));
			sb.append(" - ");
			calendar.setTimeInMillis(item.end * 1000);
			sb.append(timeformatter.format(calendar.getTime()));
			vh.time.setText(sb.toString());
			
			if (mSearchFor == null) {
				switch (item.getStatus()) {
				case Timer.TIMER_INACTIVE: 
					vh.status.setText("Inactive");
					break;
				case Timer.TIMER_ACTIVE:
					vh.status.setText("Active");
					break;
				case Timer.TIMER_VPS:
					vh.status.setText("VPS");
					break;
				case Timer.TIMER_RECORDING:
					vh.status.setText("Rec");
					break;
				default:
					vh.status.setText("");
					break;
				}
			} else {
				vh.status.setVisibility(View.GONE);
			}

			if (item.inFolder()) {
				vh.foldername.setText(item.folder());
				vh.foldername.setVisibility(View.VISIBLE);
				vh.folderimage.setVisibility(View.VISIBLE);
			}
			else {
				vh.foldername.setText("");
				vh.foldername.setVisibility(View.GONE);
				vh.folderimage.setVisibility(View.GONE);
			}

			vh.title.setText(item.title);

			return row;
		}
	}
	
	private class TimerComparer implements Comparator<Timer> {

		@Override
		public int compare(Timer a, Timer b) {
			Long l;
			Integer i;
			int result = a.title.compareTo(b.title);
			if (result == 0) {
				l = a.start;
				result = l.compareTo(b.start);
			}
			if (result == 0) {
				l = a.end;
				result = l.compareTo(b.end);
			}
			if (result == 0) {
				i = a.channel;
				result = i.compareTo(b.channel);
			}
			if (result == 0) {
				i = a.lifetime;
				result = i.compareTo(b.lifetime);
			}
			if (result == 0) {
				i = a.priority;
				result = i.compareTo(b.priority);
			}
			if (result == 0) {
				result = a.noDate.compareTo(b.noDate);
			}
			return result;
		}
		
	}
	
	private class TimerUpdater implements Runnable {
		private final Handler mHandler;
		
		public TimerUpdater(Handler handler) {
			mHandler = handler;
		}
		
		@Override
		public void run() {
			MyLog.v(TAG, "update start");
			
			int lastUpdate = (int) (new Date().getTime() / 60000);
			ArrayList<Timer> timers = null;
			
			// --- get timers from vdr ---
			try {
				timers = new Timers().getItems();
				TimerComparer comparator = new TimerComparer();
				Collections.sort(timers, comparator);
				// --- update timers ---
				for (int i = 0; i < mTimer.size(); i++) {
					Timer dst = mTimer.get(i);
					int index = Collections.binarySearch(timers, dst, comparator);
					if (index >= 0) {
						Timer src = timers.get(index);
						if (dst.number != src.number) {
							MyLog.v(TAG, dst.title + " " + dst.number + " -> " + src.number);
						}
						dst.number = src.number;
						dst.status = src.status;
						dst.lastUpdate = lastUpdate;
					}
					else
						dst.lastUpdate = -1;
				}
				MyLog.v(TAG, "update done");
				mHandler.sendMessage(Messages.obtain(Messages.MSG_DATA_UPDATE_DONE));
			} catch (IOException e) {
				MyLog.v(TAG, "ERROR TimerUpdater: " + e.toString());
				mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
			}
		}
		
	}
}
