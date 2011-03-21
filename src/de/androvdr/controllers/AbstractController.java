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
import android.graphics.Color;
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.TextView;

public abstract class AbstractController {
	protected Activity mActivity;
	protected Handler mHandler;
	
	public void onCreate(Activity activity, Handler handler) {
		mActivity = activity;
		mHandler = handler;
	}
	
	protected void setTextColor(ViewGroup v) {
		for (int j = 0; j < v.getChildCount(); j++)
			if (v.getChildAt(j).getClass() == TextView.class) {
				TextView tv = (TextView) v.getChildAt(j);
				tv.setTextColor(Color.BLACK);
			}
			else {
				if (v.getChildAt(j) instanceof ViewGroup)
					setTextColor((ViewGroup) v.getChildAt(j));
			}
	}
}
