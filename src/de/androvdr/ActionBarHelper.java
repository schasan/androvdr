package de.androvdr;

import android.app.Activity;
import android.os.Build;

public class ActionBarHelper {

	public static void setHomeButtonEnabled(Activity activity, boolean enabled) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) 
			CompatActionBar.setHomeButtonEnabled(activity, enabled);
	}
}
