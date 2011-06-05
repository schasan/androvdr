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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.controllers.RecordingController;

public class RecordingsActivity extends AbstractListActivity {
	private static final int HEADER_TEXT_SIZE = 15;
	
	private RecordingController mController;
	private ListView mListView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.recordings);

		if (! Preferences.showDiskStatus) {
			LinearLayout lay = (LinearLayout) findViewById(R.id.recdiskstatus);
			lay.setVisibility(View.GONE);
		}
		
		TextView tv = (TextView) findViewById(R.id.recheader);
		tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,	HEADER_TEXT_SIZE + Preferences.textSizeOffset);
		tv = (TextView) findViewById(R.id.recdiskstatus_values);
		if (tv != null)
			tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,	HEADER_TEXT_SIZE + Preferences.textSizeOffset);
			
		mListView = (ListView) findViewById(android.R.id.list);

		/*
		 * setTheme doesn't change background color :(
		 */
		if (Preferences.blackOnWhite)
			mListView.setBackgroundColor(Color.WHITE);
		
		Bundle bundle = getIntent().getExtras();
		if (bundle != null)
			mController = new RecordingController(this, handler, mListView, bundle.getParcelableArray("recordings"));
		else
			mController = new RecordingController(this, handler, mListView, null);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.rec_play:
			mController.action(RecordingController.RECORDING_ACTION_PLAY, info.position);
			return true;
		case R.id.rec_play_start:
			mController.action(RecordingController.RECORDING_ACTION_PLAY_START, info.position);
			return true;
		case R.id.rec_remote:
			mController.action(RecordingController.RECORDING_ACTION_REMOTE);
			return true;
		case R.id.rec_delete:
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage(R.string.rec_delete_recording)
			       .setCancelable(false)
			       .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			        	   dialog.dismiss();
			        	   mController.action(RecordingController.RECORDING_ACTION_DELETE, info.position);
			           }
			       })
			       .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
			           public void onClick(DialogInterface dialog, int id) {
			                dialog.cancel();
			           }
			       });
			AlertDialog alert = builder.create();
			alert.show();
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
		if (! mController.isFolder(mi.position)) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.recordings_menu, menu);
			menu.setHeaderTitle(mController.getTitle(mi.position));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.recordings_option_menu, menu);
		return true;
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            mController.action(RecordingController.RECORDING_ACTION_KEY_BACK);
            return true;
        }
        else
        	return super.onKeyDown(keyCode, event);
    }	
    
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.recm_sort_date:
			mController.action(RecordingController.RECORDING_ACTION_SORT_DATE);
			break;
		case R.id.recm_sort_name:
			mController.action(RecordingController.RECORDING_ACTION_SORT_NAME);
			break;
		default:
			super.onContextItemSelected(item);
		}
		return true;
	}
    
    @Override
    protected void onPause() {
    	mController.onPause();
    	super.onPause();
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	mController.onResume();
    }
    
    @Override
    public void onSwipe(int direction) {
    	if (mConfigurationManager.doSwipe(direction))
    		mController.action(RecordingController.RECORDING_ACTION_KEY_BACK);
    }
}
