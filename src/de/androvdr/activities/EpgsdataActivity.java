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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.graphics.Color;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.LinearLayout;
import android.widget.AdapterView.AdapterContextMenuInfo;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.controllers.EpgsdataController;

public class EpgsdataActivity extends AbstractListActivity {
	private static transient Logger logger = LoggerFactory.getLogger(EpgsdataActivity.class);
	
	private int mChannelNumber;
	private EpgsdataController mController;
	private LinearLayout mView;
	private int mMaxItems;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.epgsdata);

		Bundle bundle = getIntent().getExtras();
		if(bundle != null){
			mChannelNumber = bundle.getInt("channelnumber");
			mMaxItems = bundle.getInt("maxitems");
		}
		else {
			mChannelNumber = 0;
			mMaxItems = Preferences.getVdr().epgmax;
		}
		logger.trace("onCreate");
	    mView = (LinearLayout) findViewById(R.id.epgsdata_main);

		/*
		 * setTheme doesn't change background color :(
		 */
		if (Preferences.blackOnWhite)
			mView.setBackgroundColor(Color.WHITE);
		
		mController = new EpgsdataController(this, handler, mView, mChannelNumber, mMaxItems);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.epgs_menu, menu);
		AdapterContextMenuInfo mi = (AdapterContextMenuInfo) menuInfo;
		menu.setHeaderTitle(mController.getTitle(mi.position));
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.epgs_option_menu, menu);
		return true;
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		case R.id.epgs_record:
			mController.action(EpgsdataController.EPGSDATA_ACTION_RECORD, info.position);
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.epgs_search:
			onSearchRequested();
			break;
		default:
			super.onContextItemSelected(item);
		}
		return true;
	}
}
