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
import java.util.GregorianCalendar;

import org.hampelratte.svdrp.Response;
import org.hampelratte.svdrp.commands.CHAN;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import de.androvdr.Channel;
import de.androvdr.Channels;
import de.androvdr.Messages;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.VdrCommands;
import de.androvdr.activities.EpgdataActivity;
import de.androvdr.activities.EpgsdataActivity;
import de.androvdr.devices.VdrDevice;
import de.androvdr.svdrp.VDRConnection;

public class ChannelController extends AbstractController implements Runnable {
	public static transient Logger logger = LoggerFactory.getLogger(ChannelController.class);

	public static final int CHANNEL_ACTION_PROGRAMINFO = 1;
	public static final int CHANNEL_ACTION_PROGRAMINFOS = 2;
	public static final int CHANNEL_ACTION_PROGRAMINFOS_ALL = 3;
	public static final int CHANNEL_ACTION_SWITCH = 4;
	public static final int CHANNEL_ACTION_REMOTECONTROL = 5;
	public static final int CHANNEL_ACTION_RECORD = 6;

	private Channels mChannels = null;
	private final ListView mListView;
	private ChannelAdapter mChannelAdapter;
	private UpdateThread mUpdateThread;
	
	// --- needed by each row ---
	private final SimpleDateFormat timeformatter;
	private final GregorianCalendar calendar;

	private Handler mThreadHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.arg1) {
			case Messages.MSG_DONE:
				try {
					mChannels = new Channels(Preferences.getVdr().channellist);
					setChannelAdapter(new ChannelAdapter(mActivity,	mChannels.getItems()),	mListView);
					mHandler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_DISMISS));
				} catch (IOException e) {
					sendMsg(mHandler, Messages.MSG_ERROR, e.getMessage());
				}
				break;
			default:
				Message newMsg = new Message();
				newMsg.copyFrom(msg);
				mHandler.sendMessage(newMsg);
				break;
			}
		}
	};

	public ChannelController(Activity activity, Handler handler,
			ListView listView) {
		super.onCreate(activity, handler);
		
		calendar = new GregorianCalendar();
		timeformatter = new SimpleDateFormat(Preferences.timeformat);
		
		mListView = listView;

		if (!Channels.isInitialized()) {
			Message msg = Messages.obtain(Messages.MSG_PROGRESS_SHOW);
			msg.arg2 = R.string.loading_channels;
			mHandler.sendMessage(msg);
		}

		Thread thread = new Thread(this);
		thread.start();
	}

	public void action(int action, int position) {
		Intent intent;
		Channel channel = mChannelAdapter.getItem(position);
		switch (action) {
		case CHANNEL_ACTION_PROGRAMINFO:
			intent = new Intent(mActivity, EpgdataActivity.class);
			intent.putExtra("channelnumber", channel.nr);
			channel.viewEpg = channel.getNow();
			mActivity.startActivityForResult(intent, 1);
			break;
		case CHANNEL_ACTION_PROGRAMINFOS:
		case CHANNEL_ACTION_PROGRAMINFOS_ALL:
			intent = new Intent(mActivity, EpgsdataActivity.class);
			intent.putExtra("channelnumber", channel.nr);
			if (action == CHANNEL_ACTION_PROGRAMINFOS)
				intent.putExtra("maxitems", Preferences.getVdr().epgmax);
			else
				intent.putExtra("maxitems", EpgsdataController.EPG_ALL);
			mActivity.startActivityForResult(intent, 1);
			break;
		case CHANNEL_ACTION_SWITCH:
		    Response resp = VDRConnection.send(new CHAN(Integer.toString(channel.nr)));
		    if(resp.getCode() != 250) {
		        logger.error("Couldn't switch channel: {}", resp.getCode() + " - " + resp.getMessage());
		        Toast.makeText(mActivity, resp.getCode() + " - " + resp.getMessage(), Toast.LENGTH_LONG).show();
		    }
			mActivity.finish();
			break;
		case CHANNEL_ACTION_REMOTECONTROL:
			mActivity.finish();
			break;
		case CHANNEL_ACTION_RECORD:
			Response response = VdrCommands.setTimer(channel.getNow());
			if (response.getCode() != 250)
				logger.error("Couldn't set timer: {}", response.getCode());
			Toast.makeText(mActivity, response.getCode() + " - " + response.getMessage(), 
					Toast.LENGTH_LONG).show();
			break;
		}
	}

	public String getChannelName(int position) {
		Channel channel = mChannelAdapter.getItem(position);
		return channel.name;
	}

	private OnItemClickListener getOnItemClickListener() {
		return new OnItemClickListener() {
			public void onItemClick(AdapterView<?> listView, View v,
					int position, long ID) {
				action(CHANNEL_ACTION_PROGRAMINFO, position);
			}
		};
	}

	public void notifyDataSetChanged() {
		mChannelAdapter.notifyDataSetChanged();
	}

	public void onPause() {
		logger.trace("onPause");
		if (mUpdateThread != null)
			mUpdateThread.interrupt();
	}
	
	public void onResume() {
		logger.trace("onResume");
		if (mChannels != null)
			mChannels.deleteTempChannels();
		if (mUpdateThread != null)
			mUpdateThread = new UpdateThread(mThreadHandler);
	}
	
	public void run() {
		try {
			VdrDevice vdr = Preferences.getVdr();
			if (vdr == null)
				throw new IOException("No VDR defined");
			new Channels(Preferences.getVdr().channellist).getItems();
			mThreadHandler.sendMessage(Messages.obtain(Messages.MSG_DONE));
		} catch (IOException e) {
			logger.error("Couldn't load channels", e);
			sendMsg(mThreadHandler, Messages.MSG_ERROR, e.getMessage());
		}
	}

	private void setChannelAdapter(ChannelAdapter adapter, ListView listView) {
		mChannelAdapter = adapter;
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(getOnItemClickListener());
		listView.setSelected(true);
		listView.setSelection(0);
		mActivity.registerForContextMenu(mListView);
		if (! Preferences.useInternet && (mChannelAdapter.getCount() > 0))
			mUpdateThread = new UpdateThread(mThreadHandler);
	}

	private class ChannelAdapter extends ArrayAdapter<Channel> {
		private final Activity mActivity;
		
		private final static int channelnumberSize = 20,
								 channeltextSize = 20,
								 channelnowplayingSize = 15;
		
		private class ViewHolder {
			private LinearLayout logoHolder;
			private ImageView logo;
			private TextView number;
			private TextView text;
			private ProgressBar progress;
			private TableLayout program;
			private TextView nowPlaying;
			private TextView nowPlayingTime;
			private TextView nextPlaying;
			private TableRow nextPlayingRow;
			private TextView nextPlayingTime;
		}
		
		public ChannelAdapter(Activity activity, ArrayList<Channel> channels) {
			super(activity, R.layout.extendedchannels_item, channels);
			mActivity = activity;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View row;
			if (convertView == null) {
				LayoutInflater inflater = mActivity.getLayoutInflater();
				row = inflater.inflate(R.layout.extendedchannels_item, null);

				ViewHolder vh = new ViewHolder();
				vh.logoHolder = (LinearLayout) row.findViewById(R.id.channellogoholder);
				vh.logo = (ImageView) row.findViewById(R.id.channellogo);
				vh.number = (TextView) row.findViewById(R.id.channelnumber);
				vh.text = (TextView) row.findViewById(R.id.channeltext);
				vh.progress = (ProgressBar) row.findViewById(R.id.channelprogress);
				vh.program = (TableLayout) row.findViewById(R.id.channelprogram);
				vh.nowPlaying = (TextView) row.findViewById(R.id.channelnowplaying);
				vh.nowPlayingTime = (TextView) row.findViewById(R.id.channelnowplayingtime);
				vh.nextPlaying = (TextView) row.findViewById(R.id.channelnextplaying);
				vh.nextPlayingRow = (TableRow) row.findViewById(R.id.channelnextplayingrow);
				vh.nextPlayingTime = (TextView) row.findViewById(R.id.channelnextplayingtime);

				vh.number.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						channelnumberSize + Preferences.textSizeOffset);
				vh.text.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						channeltextSize + Preferences.textSizeOffset);
				vh.nowPlayingTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						channelnowplayingSize + Preferences.textSizeOffset);
				vh.nowPlaying.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						channelnowplayingSize + Preferences.textSizeOffset);
				vh.nextPlayingTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						channelnowplayingSize + Preferences.textSizeOffset);
				vh.nextPlaying.setTextSize(TypedValue.COMPLEX_UNIT_DIP,
						channelnowplayingSize + Preferences.textSizeOffset);
			
				row.setTag(vh);				
			} else {
				row = convertView;
			}

			Channel item = this.getItem(position);
			ViewHolder vh = (ViewHolder) row.getTag();
			
			if (Preferences.logoBackgroundColor != 0)
				vh.logoHolder.setBackgroundColor(Preferences.logoBackgroundColor);
			
			if (Preferences.useLogos) {
					vh.logo.setVisibility(View.VISIBLE);
					vh.number.setVisibility(View.GONE);

				if (item.hasLogo())
					vh.logo.setImageBitmap(item.logo);
				else
					vh.logo.setImageBitmap(null);
			} else {
				vh.logoHolder.setVisibility(View.GONE);
				vh.number.setVisibility(View.VISIBLE);
				
				vh.number.setText(String.valueOf(item.nr));
			}
			
			vh.text.setText(item.name);

			vh.progress.setProgress(item.getNow().getActualPercentDone());

			if (item.getNow().isEmpty) {
				vh.progress.setVisibility(View.GONE);
				vh.program.setVisibility(View.GONE);
			} else {
				vh.progress.setVisibility(View.VISIBLE);
				vh.program.setVisibility(View.VISIBLE);

				calendar.setTimeInMillis(item.getNow().startzeit * 1000);
				vh.nowPlayingTime.setText(timeformatter.format(calendar.getTime()));
				vh.nowPlaying.setText(item.getNow().titel);
			}
				
			if (item.getNext().isEmpty) {
				vh.nextPlayingRow.setVisibility(View.GONE);
			} else {
				vh.nextPlayingRow.setVisibility(View.VISIBLE);

				calendar.setTimeInMillis(item.getNext().startzeit * 1000);
				vh.nextPlayingTime.setText(timeformatter.format(calendar.getTime()));

				vh.nextPlaying.setText(item.getNext().titel);
			}

			return row;
		}
	}
	
	private class UpdateThread extends Thread {
		private final Handler mHandler;
		
		public UpdateThread(Handler handler) {
			mHandler = handler;
			start();
		}
		
		@Override
		public void run() {
			logger.trace("UpdateThread started");
			try {
				while (! isInterrupted()) {
					try {
						if (mChannelAdapter.getCount() > 0) {
							long l = mChannelAdapter.getItem(0).getMillisToNextUpdate();
							if (l > 0) {
								sleep(l);
							} else if (l != 0) {
								for (int i = 0; i < mChannelAdapter.getCount(); i++) 
									mChannelAdapter.getItem(i).cleanupEpg();
								mHandler.sendMessage(Messages.obtain(Messages.MSG_DATA_UPDATE_DONE));
							}
						}
						
						logger.trace("epg update started");
						mHandler.sendMessage(Messages.obtain(Messages.MSG_TITLEBAR_PROGRESS_SHOW));
						for (int i = 0; i < mChannelAdapter.getCount(); i++) {
							mChannelAdapter.getItem(i).updateEpg(true);
							if (isInterrupted())
								throw new InterruptedException();;
						}
						logger.trace("epg update finished");
					} catch (InterruptedException e) {
						logger.trace("UpdateThread interrupted");
						break;
					} catch (Exception e) {
						logger.error("Couldn't update epg data", e);
					} finally {
						mHandler.sendMessage(Messages.obtain(Messages.MSG_DATA_UPDATE_DONE));
						mHandler.sendMessage(Messages.obtain(Messages.MSG_TITLEBAR_PROGRESS_DISMISS));
					}
				}
				logger.trace("UpdateThread finished");
			} finally {
				mHandler.sendMessage(Messages.obtain(Messages.MSG_TITLEBAR_PROGRESS_DISMISS));
			}
		}
	}
}
