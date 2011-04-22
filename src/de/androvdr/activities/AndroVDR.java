/*
 * Copyright (c) 2009-2011 by androvdr <androvdr@googlemail.com>
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TabHost;
import android.widget.TabHost.TabContentFactory;
import android.widget.Toast;
import de.androvdr.ConfigurationManager;
import de.androvdr.GesturesFind;
import de.androvdr.MyLog;
import de.androvdr.OnLoadListener;
import de.androvdr.PortForwarding;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.UsertabParser;
import de.androvdr.WorkspaceView;
import de.androvdr.devices.Devices;
import de.androvdr.devices.IActuator;
import de.androvdr.devices.OnChangeListener;
import de.androvdr.devices.VdrDevice;
import de.androvdr.svdrp.VDRConnection;

public class AndroVDR extends AbstractActivity implements OnChangeListener, OnLoadListener, OnSharedPreferenceChangeListener {
    
	private static final String TAG = "AndroVDR";
	private static final int PREFERENCEACTIVITY_ID = 1;
	private static final int ACTIVITY_ID = 2;
	
	private final File usertabFile = new File(Preferences.getExternalRootDirName() + "/mytab");

	final private View.OnClickListener myButtonListener = new View.OnClickListener() {
        public void onClick(View x) {
      	  onButtonClick(x);
        }
    };

	public static PortForwarding portForwarding = null;

	private static final int SWITCH_DIALOG_ID = 0;
	
	private Devices mDevices;
	private CharSequence mTitle;
	private WorkspaceView mWorkspace;
	private boolean mLayoutChanged = false;
	
	public static int VDR = 4;
	public static int VDR_NUMERICS = 5;

	public static final String MSG_RESULT = "result";
	
	private Handler mResultHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Bundle bundle = msg.getData();
			if (bundle != null) {
				String result = bundle.getString(MSG_RESULT);
				if (result != "")
					Toast.makeText(AndroVDR.this, result, Toast.LENGTH_LONG).show();
			}
		}
	};
	
	private void addLongClickListener(View view) {
		if (view instanceof ViewGroup) {
			ViewGroup v = (ViewGroup) view;
			for (int j = 0; j < v.getChildCount(); j++)
				if (v.getChildAt(j).getClass() == Button.class) {
					Button button = (Button) v.getChildAt(j);
					button.setOnLongClickListener(getOnLongClickListener());
				} else if (v.getChildAt(j).getClass() == ImageButton.class) {
					ImageButton imageButton = (ImageButton) v.getChildAt(j);
					imageButton.setOnLongClickListener(getOnLongClickListener());
				} else {
					if (v.getChildAt(j) instanceof ViewGroup)
						addLongClickListener((ViewGroup) v.getChildAt(j));
				}
		}
	}
	
	private OnLongClickListener getOnLongClickListener() {
		return new OnLongClickListener() {

			@Override
			public boolean onLongClick(View v) {
				boolean result = false;
				if (v.getTag() instanceof String) {
					String sa[] = ((String) v.getTag()).split("\\|");
					if (sa.length > 1) {
						mConfigurationManager.vibrate();
						mDevices.send(sa[1]);
						result = true;
					}
				}
				return result;
			}
		};
	}

	public View getUserScreen() {
	    LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View root = inflater.inflate(R.layout.usertab, null);
		RelativeLayout v = (RelativeLayout)root.findViewById(R.id.root);
		if (!Preferences.alternateLayout)
			v.setBackgroundColor(Color.BLACK);
		UsertabParser p = new UsertabParser(usertabFile);
		ArrayList<UsertabParser.UserButton> bList = p.getButtons();
		LayoutParams l;
		Button b;
		ImageButton ib;
		for(int i = 0; i < bList.size();i++){
			UsertabParser.UserButton userButton = bList.get(i);
			l = new LayoutParams(userButton.width,userButton.height);
			l.setMargins(userButton.posX, userButton.posY, 0, 0);
			if(userButton.art == 1){ // normaler Button
				b = new Button(getApplicationContext());
				if (Preferences.alternateLayout) {
					b.setBackgroundResource(R.drawable.btn_remote);
					b.setTextColor(Color.WHITE);
					b.setTypeface(null, Typeface.BOLD);
				}
				b.setLayoutParams(l);
				b.setText(userButton.beschriftung);
				b.setTag("VDR." + userButton.action);
				b.setOnClickListener(myButtonListener);
				v.addView(b);
				continue;
			}
			if(userButton.art == 2){ // ImageButton
				ib = new ImageButton(getApplicationContext());
				if (Preferences.alternateLayout) {
					ib.setBackgroundResource(R.drawable.btn_remote);
				}
				ib.setLayoutParams(l);
				ib.setImageURI(Uri.parse(userButton.beschriftung));
				ib.setTag("VDR." + userButton.action);
				ib.setOnClickListener(myButtonListener);
				v.addView(ib);
				continue;
			}					
		}
		return v;
	}

	public void initWorkspaceView() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		if (Preferences.alternateLayout)
			setTheme(R.style.Theme);
		else
			setTheme(R.style.Theme_Original);
		
		MyLog.v(TAG, "Model: " + Build.MODEL);
		MyLog.v(TAG, "SDK Version: " + Build.VERSION.SDK_INT);

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		MyLog.v(TAG, "Width: " + metrics.widthPixels);
		MyLog.v(TAG, "Height: " + metrics.heightPixels);
		MyLog.v(TAG, "Density: " + metrics.densityDpi);
		
	    Configuration conf = getResources().getConfiguration();
	    boolean screenSmall = ((conf.screenLayout & Configuration.SCREENLAYOUT_SIZE_SMALL) == Configuration.SCREENLAYOUT_SIZE_SMALL);
	    boolean screenNormal = ((conf.screenLayout & Configuration.SCREENLAYOUT_SIZE_NORMAL) == Configuration.SCREENLAYOUT_SIZE_NORMAL);
	    boolean screenLong = ((conf.screenLayout & Configuration.SCREENLAYOUT_LONG_YES) == Configuration.SCREENLAYOUT_LONG_YES);
	    boolean screenLarge = ((conf.screenLayout & Configuration.SCREENLAYOUT_SIZE_LARGE) == Configuration.SCREENLAYOUT_SIZE_LARGE);
	    boolean screenXLarge = ((conf.screenLayout & Configuration.SCREENLAYOUT_SIZE_XLARGE) == Configuration.SCREENLAYOUT_SIZE_XLARGE);

	    MyLog.v(TAG, "Screen Small: " + screenSmall);
	    MyLog.v(TAG, "Screen Normal: " + screenNormal);
	    MyLog.v(TAG, "Screen Long: " + screenLong);
	    MyLog.v(TAG, "Screen Large: " + screenLarge);
	    MyLog.v(TAG, "Screen XLarge: " + screenXLarge);

	    /*
	     * device dependant layout initilization
	     */
		if (!usertabFile.exists() && Preferences.alternateLayout && (screenLarge || screenXLarge)) {
			
			/*
			 * tablets with large screen and no usertab
			 */
			
			setContentView(R.layout.remote_vdr_main);
			addLongClickListener(findViewById(R.id.remote_vdr_main_id));
			
		} else if (Build.VERSION.SDK_INT > 4) {

			/*
			 * devices with Android > 1.6
			 */
			
			mWorkspace = new WorkspaceView(this, null);
			mWorkspace.setTouchSlop(32);
			mWorkspace.setOnLoadListener(this);
			prefs.registerOnSharedPreferenceChangeListener(mWorkspace);

			LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

			int[] screens;
			if (Preferences.alternateLayout) {
				if (screenLarge || screenXLarge) {
					screens = new int[] { R.layout.remote_vdr_main };
				} else if (screenNormal)
					screens = new int[] { R.layout.remote_vdr_main,	R.layout.remote_vdr_numerics };
				else if (screenSmall)
					screens = new int[] { R.layout.remote_vdr_main,	R.layout.remote_vdr_numerics, R.layout.remote_vdr_play };
				else
					screens = new int[] { R.layout.remote_vdr_main,	R.layout.remote_vdr_numerics };
	
				mWorkspace.setDefaultScreen(0);
			} else {
				screens = new int[] { R.layout.tab1, R.layout.tab2,
						R.layout.tab3 };
				mWorkspace.setDefaultScreen(0);
			}

			for (int screen : screens) {
				View view = inflater.inflate(screen, null, false);
				addLongClickListener(view);
				mWorkspace.addView(view);
			}

			if (usertabFile.exists()) {
				MyLog.v(TAG, "Add user defined screen");
				mWorkspace.addView(getUserScreen());
			}

			setContentView(mWorkspace);
			
		} else {

			/*
			 * devices with Android 1.6
			 */
			
			setContentView(R.layout.main);
		    MyTabContentFactory tab = new MyTabContentFactory(this);
		    TabHost mTabHost = (TabHost) findViewById(R.id.tabhost);
		    mTabHost.setup();
		    
		    if (Preferences.alternateLayout) {
			    mTabHost.addTab(mTabHost.newTabSpec("tab_main").setIndicator(getString(R.string.tab_main_text)).setContent(tab));
		        mTabHost.addTab(mTabHost.newTabSpec("tab_numerics").setIndicator(getString(R.string.tab_numerics_text)).setContent(tab));
		        
				if (screenSmall || (screenNormal && !screenLong)) {
			        mTabHost.addTab(mTabHost.newTabSpec("tab_play").setIndicator(getString(R.string.tab_play_text)).setContent(tab));
				}
		    } else {
			    mTabHost.addTab(mTabHost.newTabSpec("tab_rc1").setIndicator(getString(R.string.tab1_text)).setContent(tab));
		        mTabHost.addTab(mTabHost.newTabSpec("tab_rc2").setIndicator(getString(R.string.tab2_text)).setContent(tab));
		        mTabHost.addTab(mTabHost.newTabSpec("tab_rc3").setIndicator(getString(R.string.tab3_text)).setContent(tab));
		    }
		    
	        if(usertabFile.exists()){
	        	MyLog.v("Test","fuege User-Tab hinzu");
	        	mTabHost.addTab(mTabHost.newTabSpec("tab_rc5").setIndicator("User").setContent(tab));
	        }

	        int tabheight = (mTabHost.getTabWidget().getChildAt(0).getLayoutParams().height / 3) * 2;
	        for ( int i = 0; i < mTabHost.getTabWidget().getChildCount(); i++)
		        mTabHost.getTabWidget().getChildAt(i).getLayoutParams().height = tabheight;
	        
	        mTabHost.setCurrentTab(0);        
		}
	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch (requestCode) {
    	case PREFERENCEACTIVITY_ID:
    		if (mLayoutChanged) {
    			Intent intent = new Intent(this, AndroVDR.class);
    			startActivity(intent);
    			finish();
    		}
    	}
    }
    
	public void onButtonClick(View v) {
		if (v.getTag() instanceof String) {
			mConfigurationManager.vibrate();
			String sa[] = ((String) v.getTag()).split("\\|");
			if (sa.length > 0)
				mDevices.send(sa[0]);
		}
	}

	@Override
	public void onChange() {
		removeDialog(SWITCH_DIALOG_ID);
	}

	/** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, false);

        Preferences.init(this);

	    mConfigurationManager = ConfigurationManager.getInstance(this);

        mTitle = getTitle();
        
		mDevices = Devices.getInstance(this);
		mDevices.setParentActivity(this);
		mDevices.setResultHandler(mResultHandler);
		mDevices.setOnDeviceConfigurationChangedListener(this);

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
	    sp.registerOnSharedPreferenceChangeListener(this);
	    sp.registerOnSharedPreferenceChangeListener(mDevices);

	    // initialize the svdrp connection
	    initializeSvdrp();
	    
		initWorkspaceView();
    }

    private void initializeSvdrp() {
        VdrDevice vdr = Preferences.getVdr();
        if (vdr == null) {
            Toast.makeText(this, "No VDR defined", Toast.LENGTH_LONG).show();
            return;
        }
        String hostname;
        int port,timeout;
        if(Preferences.useInternet == true){
            hostname = "localhost";
            port = vdr.remote_local_port;
            timeout = vdr.remote_timeout;
            MyLog.v(TAG,"Es wurden die Interneteinstellungen gewaehlt");
        }
        else{
            hostname = vdr.getIP();
            port = vdr.getPort();
            timeout = vdr.timeout;
            MyLog.v(TAG,"Es wurden lokale Netzwerkeinstellungen gewaehlt");
        }
        VDRConnection.host = hostname;
        VDRConnection.port = port;
        VDRConnection.timeout = timeout;
        VDRConnection.charset = "UTF-8";
    }

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		switch (id) {
		case SWITCH_DIALOG_ID:
			final ArrayList<VdrDevice> vdrs = mDevices.getVdrs();
			final ArrayList<String> items = new ArrayList<String>();
			
			Comparator<VdrDevice> comparator = new Comparator<VdrDevice>() {
				@Override
				public int compare(VdrDevice a, VdrDevice b) {
					return a.getName().compareTo(b.getName());
				}
			};
			Collections.sort(vdrs, comparator);
			
			long current = -1;
			for (int i = 0; i < vdrs.size(); i++) {
				VdrDevice vdr = vdrs.get(i);
				if ((Preferences.getVdr() != null)
						&& (vdr.getId() == Preferences.getVdr().getId()))
					current = i;
				items.add(vdr.getName());
			}
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.main_select_vdr)
			.setNegativeButton(R.string.cancel_action, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			})
			.setSingleChoiceItems(items.toArray(new CharSequence[items.size()]), (int) current, new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int item) {
			        IActuator ac = mDevices.getVdr(vdrs.get(item).getId());
		        	Preferences.setVdr(ac.getId());
			        dialog.dismiss();
			    }
			});
			dialog = builder.create();
			break;
		default:
			dialog = null;
		}
		return dialog;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_option_menu, menu);
		return true;
	}

    @Override
    public void onLoad() {
    	updateTitle(mWorkspace.getCurrentView());
    }
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.androvdr_exit:
		    try {
                VDRConnection.close();
            } catch (IOException e) {}
			android.os.Process.killProcess(android.os.Process.myPid());
			return true;
		case R.id.androvdr_about:
			startActivity(new Intent(this, AboutActivity.class));
			return true;
		case R.id.androvdr_switch_vdr:
			showDialog(SWITCH_DIALOG_ID);
			return true;
		case R.id.androvdr_settings:
			startActivityForResult(new Intent(this, PreferencesActivity.class), PREFERENCEACTIVITY_ID);
			return true;
		case R.id.androvdr_gestures:
			Intent intent = new Intent(this, GesturesFind.class);
			startActivityForResult(intent, ACTIVITY_ID);
			return true;
		case R.id.androvdr_internet:
			togglePortforwarding();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		if (Preferences.useInternet)
			menu.getItem(1).setTitle(R.string.main_forwarding_off);
		else
			menu.getItem(1).setTitle(R.string.main_forwarding_on);
		if (Integer.parseInt(Build.VERSION.SDK) < 4)
			menu.getItem(5).setEnabled(false);
		return true;
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals("alternateLayout"))
			mLayoutChanged = true;
		
		initializeSvdrp();
	}

    private Handler sshDialogHandler = new Handler() {
		@Override
        public void handleMessage(Message msg) {
			PortForwarding.sshDialogHandlerMessage(msg);
		}
	};
    
	private void togglePortforwarding() {
		if (Preferences.useInternet == false) {
			String connectionState = "";
			final ConnectivityManager cm = (ConnectivityManager) this
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo[] info = cm.getAllNetworkInfo();
			for (int i = 0; i < info.length; i++) {
				if (info[i].isConnected() == true) { // dann wird wohl hoffentlich eine Verbindung klappen
					AndroVDR.portForwarding = new PortForwarding(
							sshDialogHandler, this);
					return;
				} else { // sammel mer mal die Begruendung
					NetworkInfo.DetailedState state = info[i].getDetailedState();
					connectionState += info[i].getTypeName() + " State is "	+ state.name() + "\n";
				}
			}
			// kein Netzwerk vorhanden, also mach mer nix
			Toast.makeText(this, connectionState, Toast.LENGTH_LONG).show();
		} else {
			// Toast.makeText(Settings.this, "Nein", Toast.LENGTH_SHORT).show();
			if (AndroVDR.portForwarding != null) {
				AndroVDR.portForwarding.disconnect();
				AndroVDR.portForwarding = null;
			}
		}
	}

	public void updateTitle(View view) {
    	if ((view != null) && (view.getTag() instanceof CharSequence))
    		setTitle(mTitle + " (" + (CharSequence) view.getTag() + ")");
    	else
    		setTitle(mTitle);
    }
    
    public class MyTabContentFactory implements TabContentFactory {

    	Context context;
    	LayoutInflater inflater;
    	
    	public MyTabContentFactory(Context c){
    		this.context = c;
    		inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    	}
    	
    	
    	@Override
    	public View createTabContent(String tag) {
    		View root;
    		MyLog.v("MyTabContentFactory","Erstelle den Tab "+tag);
    		
    		if(tag.equals("tab_rc1")){
    			root = inflater.inflate(R.layout.tab1, null);
    			addLongClickListener(root);
    			return root.findViewById(R.id.tab1);
    		}
    		if(tag.equals("tab_rc2")){
    			root = inflater.inflate(R.layout.tab2, null);
    			addLongClickListener(root);
    			return root.findViewById(R.id.tab2);
    		}
    		if(tag.equals("tab_rc3")){
    			root = inflater.inflate(R.layout.tab3, null);
    			addLongClickListener(root);
    			return root.findViewById(R.id.tab3);
    		}
    		if(tag.equals("tab_rc5")){
    			return getUserScreen();
    		}
    		if(tag.equals("tab_main")){
    			root = inflater.inflate(R.layout.remote_vdr_main, null);
    			addLongClickListener(root);
    			return root.findViewById(R.id.remote_vdr_main_id);
    		}
    		if(tag.equals("tab_numerics")){
    			root = inflater.inflate(R.layout.remote_vdr_numerics, null);
    			addLongClickListener(root);
    			return root.findViewById(R.id.remote_vdr_numerics_id);
    		}
    		if(tag.equals("tab_play")){
    			root = inflater.inflate(R.layout.remote_vdr_play, null);
    			addLongClickListener(root);
    			return root.findViewById(R.id.remote_vdr_play_id);
    		}
    		return null;
    	}
      }
}