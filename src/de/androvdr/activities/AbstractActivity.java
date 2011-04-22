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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import de.androvdr.ConfigurationManager;
import de.androvdr.Messages;
import de.androvdr.MyLog;
import de.androvdr.Preferences;
import de.androvdr.R;

public class AbstractActivity extends Activity {
	private static final String TAG = "AbstractActivity";

	protected ConfigurationManager mConfigurationManager;
	
	protected Handler handler = new Handler () {
		private ProgressDialog pd = null;
		
		protected void dismiss() {
			if (pd != null) {
				try {
					pd.dismiss();
				} catch (IllegalArgumentException e) { }
				pd = null;
			}
		}

		@Override
		public void handleMessage(Message msg) {
			MyLog.v(TAG, "handleMessage: arg1 = " + msg.arg1);
			
			switch (msg.arg1) {
			case Messages.MSG_PROGRESS_SHOW:
				dismiss();
				if (pd == null) {
					pd = new ProgressDialog(AbstractActivity.this);
					pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
					pd.setMessage(AbstractActivity.this.getString(msg.arg2));
					pd.show();
				}
				break;
			case Messages.MSG_PROGRESS_DISMISS:
				dismiss();
				break;
			case Messages.MSG_VDR_ERROR:
				dismiss();
				showError(AbstractActivity.this.getString(R.string.connect_err_vdr));
				break;
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (Preferences.blackOnWhite)
			setTheme(R.style.Theme_Light);
		
		mConfigurationManager = ConfigurationManager.getInstance(this);
		Preferences.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
	}
	
	protected void onCreate(Bundle savedInstanceState, boolean initConfigurationManager) {
		super.onCreate(savedInstanceState);
		if (initConfigurationManager)
			mConfigurationManager = ConfigurationManager.getInstance(this);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return mConfigurationManager.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		mConfigurationManager.onPause();
		handler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_DISMISS));
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		mConfigurationManager.onResume();
	}

	protected void showError(String message) {
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle(this.getText(R.string.error));
		alert.setMessage(message);
		alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				finish();
			}
		});
		if (! isFinishing())
			alert.show();
	}
}
