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
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.Window;
import de.androvdr.ConfigurationManager;
import de.androvdr.Messages;
import de.androvdr.MyLog;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.SimpleGestureFilter;
import de.androvdr.SimpleGestureFilter.SimpleGestureListener;

public class AbstractListActivity extends ListActivity implements SimpleGestureListener {
	private static final String TAG = "AbstractListActivity";
	
	protected ConfigurationManager mConfigurationManager;
	protected SimpleGestureFilter mDetector;
	
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
					pd = new ProgressDialog(AbstractListActivity.this);
					pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
					pd.setMessage(AbstractListActivity.this.getString(msg.arg2));
					pd.show();
				}
				break;
			case Messages.MSG_PROGRESS_UPDATE:
				if (pd != null && pd.isShowing()) {
					pd.setMessage(AbstractListActivity.this.getString(msg.arg2));
				}
				break;
			case Messages.MSG_TITLEBAR_PROGRESS_SHOW:
				setProgressBarIndeterminateVisibility(true);
				break;
			case Messages.MSG_TITLEBAR_PROGRESS_DISMISS:
				setProgressBarIndeterminateVisibility(false);
				break;
			case Messages.MSG_PROGRESS_DISMISS:
				dismiss();
				break;
			case Messages.MSG_VDR_ERROR:
				dismiss();
				showError(AbstractListActivity.this.getString(R.string.connect_err_vdr));
				break;
			}
		}
	};
	
	@Override
	public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
		mDetector.onTouchEvent(ev);
		return super.dispatchTouchEvent(ev);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (Preferences.blackOnWhite)
			setTheme(R.style.Theme_Light);
		
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		mDetector = new SimpleGestureFilter(this, this);
		mDetector.setMode(SimpleGestureFilter.MODE_TRANSPARENT);
		mConfigurationManager = ConfigurationManager.getInstance(this);
	}

	@Override
	public void onDoubleTap() {
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
		handler.sendMessage(Messages.obtain(Messages.MSG_TITLEBAR_PROGRESS_DISMISS));
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		mConfigurationManager.onResume();
	}
	
	@Override
	public void onSwipe(int direction) {
		if (mConfigurationManager.doSwipe(direction))
			finish();
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
		alert.show();
	}
}
