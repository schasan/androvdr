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
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.LinearLayout;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.controllers.EpgdataController;

public class EpgdataActivity extends AbstractGestureActivity {
	private EpgdataController mController;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.epgdata);

		LinearLayout view = (LinearLayout) findViewById(R.id.pgi);
		
		/*
		 * setTheme doesn't change background color :(
		 */
		if (Preferences.blackOnWhite)
			view.setBackgroundColor(Color.WHITE);
		
		Bundle bundle = getIntent().getExtras();
		if (bundle != null) {
			int channelnumber = bundle.getInt("channelnumber");
			mController = new EpgdataController(this, handler, view, channelnumber);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.epg_menu, menu);
		menu.setHeaderTitle(mController.getTitle());
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.epg_record:
			mController.action(EpgdataController.EPGDATA_ACTION_RECORD);
			break;
		default:
			super.onContextItemSelected(item);
		}
		return true;
	}
}
