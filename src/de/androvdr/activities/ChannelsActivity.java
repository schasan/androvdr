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

package de.androvdr.activities;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.AdapterView.AdapterContextMenuInfo;
import de.androvdr.Messages;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.controllers.ChannelController;

public class ChannelsActivity extends AbstractListActivity {
	private static transient Logger logger = LoggerFactory.getLogger(ChannelsActivity.class);
	private static final int HEADER_TEXT_SIZE = 15;
	private static final int DIALOG_WHATS_ON = 1;
	
	public static final String SEARCHTIME = "searchtime";
	
	private ChannelController mController;
	private ListView mListView;
	private long mSearchTime;
	
	private Handler mHandler = new Handler () {

		@Override
		public void handleMessage(Message msg) {
			logger.trace("handleMessage: arg1 = {}", msg.arg1);
			
			switch (msg.arg1) {
			case Messages.MSG_DATA_UPDATE_DONE:
				handler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_DISMISS));
				mController.notifyDataSetChanged();
				break;
			default:
				Message forward = new Message();
				forward.copyFrom(msg);
				handler.sendMessage(forward);
				break;
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.extendedchannels);

		TextView tv = (TextView) findViewById(R.id.channels_header);
		tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,	HEADER_TEXT_SIZE + Preferences.textSizeOffset);
	    
		mListView = (ListView) findViewById(android.R.id.list);

		/*
		 * setTheme doesn't change background color :(
		 */
		if (Preferences.blackOnWhite)
			mListView.setBackgroundColor(Color.WHITE);
		
		mSearchTime = getIntent().getLongExtra(SEARCHTIME, 0);
	    mController = new ChannelController(this, mHandler, mListView, mSearchTime);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.channels_menu, menu);
		AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
		menu.setHeaderTitle(mController.getChannelName(mi.position));
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		if (!sp.getBoolean("livetv_enabled", false)) {
			menu.removeItem(R.id.cm_livetv);
		} else if (Preferences.useInternet) {
			MenuItem menuitem = menu.findItem(R.id.cm_livetv);
			if (menuitem != null)
				menuitem.setEnabled(false);
		}
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_WHATS_ON:
			final Dialog dialog = new Dialog(this);
			dialog.setContentView(R.layout.extendedchannels_whats_on);
			dialog.setTitle(R.string.channels_whats_on);
			
			final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
			final DatePicker dp = (DatePicker) dialog.findViewById(R.id.channels_datePicker);
			final TimePicker tp = (TimePicker) dialog.findViewById(R.id.channels_timePicker);
			tp.setIs24HourView(DateFormat.is24HourFormat(getApplicationContext()));
			if (sp.contains("whats_on_hour")) {
				tp.setCurrentHour(sp.getInt("whats_on_hour", 0));
				tp.setCurrentMinute(sp.getInt("whats_on_minute", 0));
			}
			
			Button button = (Button) dialog.findViewById(R.id.channels_cancel);
			button.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					dialog.dismiss();
				}
			});

			button = (Button) dialog.findViewById(R.id.channels_search);
			button.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Editor editor = sp.edit();
					editor.putInt("whats_on_hour", tp.getCurrentHour());
					editor.putInt("whats_on_minute", tp.getCurrentMinute());
					editor.commit();
					
					SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy hh:mm");
					try {
						long time = df.parse(
								dp.getDayOfMonth() + "." + (dp.getMonth() + 1) + "." + dp.getYear() + " " +
								tp.getCurrentHour() + ":" + tp.getCurrentMinute()).getTime() / 1000;
						mController.whatsOn(time);
					} catch (ParseException e) {
						logger.error("Couldn't get date from pickers", e);
					}
					dialog.dismiss();
				}
			});
			return dialog;
		default:
			return super.onCreateDialog(id);
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.channels_option_menu, menu);
		return (mSearchTime == 0);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.cm_switch:
			mController.action(ChannelController.CHANNEL_ACTION_SWITCH, info.position);
			break;
		case R.id.cm_overview:
			mController.action(ChannelController.CHANNEL_ACTION_PROGRAMINFOS, info.position);
			break;
		case R.id.cm_overviewfull:
			mController.action(ChannelController.CHANNEL_ACTION_PROGRAMINFOS_ALL, info.position);
			break;
		case R.id.cm_remote:
			mController.action(ChannelController.CHANNEL_ACTION_REMOTECONTROL, info.position);
			break;
		case R.id.cm_record:
			mController.action(ChannelController.CHANNEL_ACTION_RECORD, info.position);
			break;
		case R.id.cm_livetv:
			mController.action(ChannelController.CHANNEL_ACTION_LIVETV, info.position);
			break;
		default:
			super.onContextItemSelected(item);
		}
		return true;
	}

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.cm_search:
			onSearchRequested();
			break;
		case R.id.cm_whats_on:
			showDialog(DIALOG_WHATS_ON);
			break;
		default:
			super.onContextItemSelected(item);
		}
		return true;
	}
    
	@Override
	public void onPause() {
		super.onPause();
		mController.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		mController.onResume();
	}
}
