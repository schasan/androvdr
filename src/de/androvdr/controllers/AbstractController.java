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

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import de.androvdr.Messages;

public abstract class AbstractController {
	protected Activity mActivity;
	protected Handler mHandler;

	public void onCreate(Activity activity, Handler handler) {
		mActivity = activity;
		mHandler = handler;
	}

	protected void sendMsg(Handler handler, int type, int stringId) {
		Message resultMessage = Messages.obtain(type, stringId);
		synchronized (handler) {
			handler.sendMessage(resultMessage);
		}
	}
	
	protected void sendMsg(Handler handler, int type, String msg) {
		if (handler != null) {
			Message resultMessage = Messages.obtain(type);
			if (msg != null) {
				Bundle resultBundle = new Bundle();
				resultBundle.putString(Messages.MSG_MESSAGE, msg);
				resultMessage.setData(resultBundle);
			}
			synchronized (handler) {
				handler.sendMessage(resultMessage);
			}
		}
	}
}
