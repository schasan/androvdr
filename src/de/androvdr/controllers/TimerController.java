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

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import de.androvdr.Channel;
import de.androvdr.Channels;
import de.androvdr.Connection;
import de.androvdr.Messages;
import de.androvdr.MyLog;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.Timer;
import de.androvdr.Timers;
import de.androvdr.activities.EpgdataActivity;
import de.androvdr.devices.VdrDevice;

public class TimerController extends AbstractController implements Runnable {
	private static final String TAG = "TimerController";
	
	private static final int timer_titleSize = 20,
							 timer_defaultSize = 15;
	
	private TimerAdapter mAdapter;
	private Channels mChannels;
	private final ListView mListView;
	private ArrayList<Timer> mTimer;
	
	// --- needed by each row ---
	private final SimpleDateFormat dateformatter;
	private final SimpleDateFormat timeformatter;
	private final String[] weekdays;
	private final GregorianCalendar calendar;
	
	public final static int TIMER_ACTION_DELETE = 1;
	public final static int TIMER_ACTION_TOGGLE = 2;
	public final static int TIMER_ACTION_SHOW_EPG = 3;

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
			}
		}
	};

	public TimerController(Activity activity, Handler handler, ListView listView) {
		super.onCreate(activity, handler);
		mListView = listView;
		if (Preferences.blackOnWhite) {
			mListView.setBackgroundColor(Color.WHITE);
			mListView.setCacheColorHint(Color.WHITE);
		}
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
			final Connection connection = new Connection();
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
							if (item.lastUpdate > 0) {
								try {
									String result = connection.doThis("DELT " + item.number + "\n");
									if (result != null && result.regionMatches(0, "250 ", 0, 4)) {
										mAdapter.remove(item);
										for (int i = 0; i < mTimer.size(); i++)
											mTimer.get(i).lastUpdate = 0;
									}
									else
										Toast.makeText(mActivity, result.replace("\n", ""), Toast.LENGTH_LONG).show();
									break;
								} catch (IOException e) {
									MyLog.v(TAG, "ERROR: " + e.toString());
									mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
								}
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
					new Thread(new TimerUpdater(handler, connection)).start();
				}
				else
					handler.sendMessage(Messages.obtain(Messages.MSG_DATA_UPDATE_DONE));
				break;
			case TIMER_ACTION_SHOW_EPG:
				new GetEpgTask().execute(item);
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
								try {
									String s;
									if (item.isActive())
										s = " OFF";
									else
										s = " ON";
									String result = connection.doThis("MODT " + item.number + s + "\n");
									if (result != null && result.regionMatches(0, "250 ", 0, 4)) {
										update();
									}
									else
										Toast.makeText(mActivity, result.replace("\n", ""), Toast.LENGTH_LONG).show();
								} catch (IOException e) {
									MyLog.v(TAG, "ERROR: " + e.toString());
									mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
								}
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
					new Thread(new TimerUpdater(handler, connection)).start();
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
			mTimer = new Timers().getItems();
			mChannels = new Channels(Preferences.getVdr().channellist);
			mThreadHandler.sendMessage(Messages.obtain(Messages.MSG_DONE));
		} catch (IOException e) {
			MyLog.v(TAG, "ERROR new Timers(): " + e.toString());
			lastError = e.toString();
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
		
		try {
			Connection connection = new Connection();
			Message msg = Messages.obtain(Messages.MSG_PROGRESS_SHOW);
			msg.arg2 = R.string.updating;
			mHandler.sendMessage(msg);
			new Thread(new TimerUpdater(threadHandler, connection)).start();
		} catch (IOException e) {
			mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
		}
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
					Connection connection = new Connection();
					try {
						channel = channels.addChannel(timer.channel, connection);
						channel.isTemp = true;
					} finally {
						connection.closeDelayed();
					}
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
		private final Connection mConnection;
		private final Handler mHandler;
		
		public TimerUpdater(Handler handler, Connection connection) {
			mConnection = connection;
			mHandler = handler;
		}
		
		@Override
		public void run() {
			MyLog.v(TAG, "update start");
			
			int lastUpdate = (int) (new Date().getTime() / 60000);
			ArrayList<Timer> timers = null;
			
			// --- get timers from vdr ---
			try {
				timers = new Timers(mConnection).getItems();
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
