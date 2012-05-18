package de.androvdr;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import de.androvdr.fragments.RemoteTabletFragment;
import de.androvdr.fragments.RemoteUserFragment;

public class ActionBarHelper {
	public static void initActionBar(FragmentActivity activity) {
    	ActionBar actionBar = activity.getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
	    
		// --- remote tab ---
		actionBar.addTab(actionBar
				.newTab()
				.setText("Remote")
				.setTabListener(
						new TabListener<RemoteTabletFragment>(activity,
								"remote", RemoteTabletFragment.class)));

		// --- user tab ---
		actionBar.addTab(actionBar
				.newTab()
				.setText("User")
				.setTabListener(
						new TabListener<RemoteUserFragment>(activity, "user",
								RemoteUserFragment.class)));
}

    public static class TabListener<T extends Fragment> implements ActionBar.TabListener {

        private Fragment mFragment;
        private final FragmentActivity mActivity;
        private final String mTag;
        private final Class<T> mClass;

        /** Constructor used each time a new tab is created.
          * @param activity  The host Activity, used to instantiate the fragment
          * @param tag  The identifier tag for the fragment
          * @param clz  The fragment's Class, used to instantiate the fragment
          */
        public TabListener(FragmentActivity activity, String tag, Class<T> clz) {
            mActivity = activity;
            mTag = tag;
            mClass = clz;
            
            // Reuse existent fragement
            mFragment = mActivity.getSupportFragmentManager().findFragmentByTag(mTag);
        }

		@Override
		public void onTabUnselected(Tab tab, FragmentTransaction unused) {
			FragmentManager fm = mFragment.getFragmentManager();
			android.support.v4.app.FragmentTransaction ft = fm.beginTransaction();
			ft.detach(mFragment);
			ft.commit();
		}

		@Override
		public void onTabSelected(Tab tab, FragmentTransaction unused) {
			FragmentManager fm = mActivity.getSupportFragmentManager();
			android.support.v4.app.FragmentTransaction ft = fm.beginTransaction();

			// Check if the fragment is already initialized
	        if (mFragment == null) {
	            // If not, instantiate and add it to the activity
	            mFragment = Fragment.instantiate(mActivity, mClass.getName());
		        ft.add(android.R.id.content, mFragment, mTag);
	        } else {
	        	ft.attach(mFragment);
	        }
	        ft.commit();
		}

		@Override
		public void onTabReselected(Tab tab, FragmentTransaction unused) {
		}
    }
}
