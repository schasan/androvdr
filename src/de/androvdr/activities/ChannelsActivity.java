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

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import de.androvdr.Messages;
import de.androvdr.MyLog;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.controllers.ChannelController;

public class ChannelsActivity extends AbstractListActivity {
	private static final String TAG = "ChannelsActivity";
	private static final int HEADER_TEXT_SIZE = 15;
	
	private ChannelController mController;
	private ListView mListView;

	private Handler mHandler = new Handler () {

		@Override
		public void handleMessage(Message msg) {
			MyLog.v(TAG, "handleMessage: arg1 = " + msg.arg1);
			
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
		
	    mController = new ChannelController(this, mHandler, mListView);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.channels_menu, menu);
		AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
		menu.setHeaderTitle(mController.getChannelName(mi.position));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.channels_option_menu, menu);
		return true;
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
