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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;

import org.hampelratte.svdrp.Response;
import org.hampelratte.svdrp.commands.LSTC;
import org.hampelratte.svdrp.parsers.ChannelParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
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
import android.view.ViewGroup;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TabHost.TabContentFactory;
import de.androvdr.Channel;
import de.androvdr.ConfigurationManager;
import de.androvdr.GesturesFind;
import de.androvdr.IFileLogger;
import de.androvdr.OnLoadListener;
import de.androvdr.PortForwarding;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.UsertabParser;
import de.androvdr.WorkspaceView;
import de.androvdr.devices.Devices;
import de.androvdr.devices.IActuator;
import de.androvdr.devices.OnChangeListener;
import de.androvdr.devices.OnSensorChangeListener;
import de.androvdr.devices.VdrDevice;
import de.androvdr.svdrp.VDRConnection;

public class AndroVDR extends AbstractActivity implements OnChangeListener, OnLoadListener, 
		OnSharedPreferenceChangeListener {
    
	private static final int PREFERENCEACTIVITY_ID = 1;
	private static final int ACTIVITY_ID = 2;
	private static transient Logger logger = LoggerFactory.getLogger(AndroVDR.class);
	
	private static final int CLOSE_CONNECTION = 0;
	private static final int CLOSE_CONNECTION_PORTFORWARDING = 1;
	private static final int CLOSE_CONNECTION_TERMINATE = 2;
	
	private static final int SENSOR_DISKSTATUS = 1;
	private static final int SENSOR_CHANNEL = 2;
	
	private final File usertabFile = new File(Preferences.getExternalRootDirName() + "/mytab");

	final private View.OnClickListener myButtonListener = new View.OnClickListener() {
        public void onClick(View x) {
      	  onButtonClick(x);
        }
    };

	public static PortForwarding portForwarding = null;
	
	private static final int SWITCH_DIALOG_ID = 0;
	
	private Devices mDevices;
	private String mTitle;
	private String mTitleChannelName;
	private WatchPortForwadingThread mWatchPortForwardingThread;
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
	
	private Handler mSensorHandler = new Handler() {
		public void handleMessage(Message msg) {
			int type = msg.what;
			String result = msg.getData().getString(MSG_RESULT);
			TextView tv;
			
			switch (type) {
			case SENSOR_DISKSTATUS:
				tv = (TextView) findViewById(R.id.remote_diskstatus_values);
				if (tv != null && ! result.equals("N/A")) {
					try {
						String[] sa = result.split(" ");
						Integer total = Integer.parseInt(sa[0].replaceAll("MB$", "")) / 1024;
						int free = Integer.parseInt(sa[1].replaceAll("MB$", "")) / 1024;
						Integer used = total - free;

						tv.setText(used.toString() + " GB / " + total.toString() + " GB");

						ProgressBar pg = (ProgressBar) findViewById(R.id.remote_diskstatus_progressbar);
						pg.setMax(total);
						pg.setProgress(used);
					} catch (Exception e) {
						logger.error("Couldn't parse disk status: {}", e);
						tv.setText("N/A");
					}
				}
				break;
			case SENSOR_CHANNEL:
				new ChannelViewUpdater().execute(result);
				break;
			}
		};
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

	private void initLogging(SharedPreferences sp) {
		if (logger instanceof IFileLogger) {
			IFileLogger filelogger = (IFileLogger) logger;
	    	int loglevel = Integer.parseInt(sp.getString("logLevel", "0"));
	    	if (loglevel < 2)
	    		filelogger.initLogFile(null, false);
	    	else
	    		filelogger.initLogFile(Preferences.getLogFileName(), (loglevel == 3));
	    }
	}

	public void initWorkspaceView() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
		if (Preferences.alternateLayout)
			setTheme(R.style.Theme);
		else
			setTheme(R.style.Theme_Original);
		
		logger.debug("Model: {}", Build.MODEL);
		logger.debug("SDK Version: {}", Build.VERSION.SDK_INT);

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		logger.debug("Width: {}", metrics.widthPixels);
		logger.debug("Height: {}", metrics.heightPixels);
		logger.debug("Density: {}", metrics.densityDpi);
		
	    Configuration conf = getResources().getConfiguration();
	    boolean screenSmall = ((conf.screenLayout & Configuration.SCREENLAYOUT_SIZE_SMALL) == Configuration.SCREENLAYOUT_SIZE_SMALL);
	    boolean screenNormal = ((conf.screenLayout & Configuration.SCREENLAYOUT_SIZE_NORMAL) == Configuration.SCREENLAYOUT_SIZE_NORMAL);
	    boolean screenLong = ((conf.screenLayout & Configuration.SCREENLAYOUT_LONG_YES) == Configuration.SCREENLAYOUT_LONG_YES);
	    boolean screenLarge = ((conf.screenLayout & Configuration.SCREENLAYOUT_SIZE_LARGE) == Configuration.SCREENLAYOUT_SIZE_LARGE);
	    boolean screenXLarge = ((conf.screenLayout & Configuration.SCREENLAYOUT_SIZE_XLARGE) == Configuration.SCREENLAYOUT_SIZE_XLARGE);

	    logger.debug("Screen Small: {}", screenSmall);
	    logger.debug("Screen Normal: {}", screenNormal);
	    logger.debug("Screen Long: {}", screenLong);
	    logger.debug("Screen Large: {}", screenLarge);
	    logger.debug("Screen XLarge: {}", screenXLarge);

	    if (screenSmall)
	    	Preferences.screenSize = Preferences.SCREENSIZE_SMALL;
	    if (screenNormal)
	    	Preferences.screenSize = Preferences.SCREENSIZE_NORMAL;
	    if (screenLong)
	    	Preferences.screenSize = Preferences.SCREENSIZE_LONG;
	    if (screenLarge)
	    	Preferences.screenSize = Preferences.SCREENSIZE_LARGE;
	    if (screenXLarge)
	    	Preferences.screenSize = Preferences.SCREENSIZE_XLARGE;
	    logger.trace("Screen size: {}", Preferences.screenSize);
	    
	    /*
	     * device dependant layout initilization
	     */
		if (!usertabFile.exists() 
				&& Preferences.alternateLayout 
				&& Preferences.screenSize >= Preferences.SCREENSIZE_LARGE) {
			
			/*
			 * tablets with large screen and no usertab
			 */
			
			setContentView(R.layout.remote_vdr_main);
			addLongClickListener(findViewById(R.id.remote_vdr_main_id));

		} else if (Build.VERSION.SDK_INT > 4) {

			/*
			 * devices with Android > 1.6
			 */
			
			logger.trace("setting SCREEN_ORIENTATION_PORTRAIT");
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

			mWorkspace = new WorkspaceView(this, null);
			mWorkspace.setTouchSlop(32);
			mWorkspace.setOnLoadListener(this);
			prefs.registerOnSharedPreferenceChangeListener(mWorkspace);

			LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

			int[] screens;
			if (Preferences.alternateLayout) {
				switch (Preferences.screenSize) {
				case Preferences.SCREENSIZE_SMALL:
					screens = new int[] { R.layout.remote_vdr_main,	R.layout.remote_vdr_numerics, 
							R.layout.remote_vdr_play };
					break;
				case Preferences.SCREENSIZE_NORMAL:
				case Preferences.SCREENSIZE_LONG:
					screens = new int[] { R.layout.remote_vdr_main,	R.layout.remote_vdr_numerics };
					break;
				case Preferences.SCREENSIZE_LARGE:
				case Preferences.SCREENSIZE_XLARGE:
					screens = new int[] { R.layout.remote_vdr_main };
					break;
				default:
					screens = new int[] { R.layout.remote_vdr_main,	R.layout.remote_vdr_numerics };
				}
	
				mWorkspace.setDefaultScreen(0);
			} else {
				screens = new int[] { R.layout.tab1, R.layout.tab2,	R.layout.tab3 };
				mWorkspace.setDefaultScreen(0);
			}

			for (int screen : screens) {
				View view = inflater.inflate(screen, null, false);
				addLongClickListener(view);
				mWorkspace.addView(view);
			}

			if (usertabFile.exists()) {
				logger.trace("Add user defined screen");
				mWorkspace.addView(getUserScreen());
			}

			setContentView(mWorkspace);
			
		} else {

			/*
			 * devices with Android 1.6
			 */
			
			logger.trace("setting SCREEN_ORIENTATION_PORTRAIT");
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

			setContentView(R.layout.main);
		    MyTabContentFactory tab = new MyTabContentFactory(this);
		    TabHost mTabHost = (TabHost) findViewById(R.id.tabhost);
		    mTabHost.setup();
		    
		    if (Preferences.alternateLayout) {
			    mTabHost.addTab(mTabHost.newTabSpec("tab_main").setIndicator(getString(R.string.tab_main_text)).setContent(tab));
		        mTabHost.addTab(mTabHost.newTabSpec("tab_numerics").setIndicator(getString(R.string.tab_numerics_text)).setContent(tab));

		        switch (Preferences.screenSize) {
		        case Preferences.SCREENSIZE_SMALL:
		        case Preferences.SCREENSIZE_NORMAL:
			        mTabHost.addTab(mTabHost.newTabSpec("tab_play").setIndicator(getString(R.string.tab_play_text)).setContent(tab));
		        	break;
		        }
		    } else {
			    mTabHost.addTab(mTabHost.newTabSpec("tab_rc1").setIndicator(getString(R.string.tab1_text)).setContent(tab));
		        mTabHost.addTab(mTabHost.newTabSpec("tab_rc2").setIndicator(getString(R.string.tab2_text)).setContent(tab));
		        mTabHost.addTab(mTabHost.newTabSpec("tab_rc3").setIndicator(getString(R.string.tab3_text)).setContent(tab));
		    }
		    
	        if(usertabFile.exists()){
	        	logger.trace("fuege User-Tab hinzu");
	        	mTabHost.addTab(mTabHost.newTabSpec("tab_rc5").setIndicator("User").setContent(tab));
	        }

	        int tabheight = (mTabHost.getTabWidget().getChildAt(0).getLayoutParams().height / 3) * 2;
	        for ( int i = 0; i < mTabHost.getTabWidget().getChildCount(); i++)
		        mTabHost.getTabWidget().getChildAt(i).getLayoutParams().height = tabheight;
	        
	        mTabHost.setCurrentTab(0);        
		}

		if (Preferences.screenSize >= Preferences.SCREENSIZE_XLARGE) {
			mDevices.addOnSensorChangeListener("VDR.disk", 5,
					new OnSensorChangeListener() {
						@Override
						public void onChange(String result) {
							logger.trace("DiskStatus: {}", result);
							Message msg = Message.obtain(mSensorHandler,
									SENSOR_DISKSTATUS);
							Bundle bundle = new Bundle();
							bundle.putString(MSG_RESULT, result);
							msg.setData(bundle);
							msg.sendToTarget();
						}
					});
		}
		mDevices.addOnSensorChangeListener("VDR.channel", 1,
				new OnSensorChangeListener() {
					@Override
					public void onChange(String result) {
						logger.trace("Channel: {}", result);
						Message msg = Message.obtain(mSensorHandler,
								SENSOR_CHANNEL);
						Bundle bundle = new Bundle();
						bundle.putString(MSG_RESULT, result);
						msg.setData(bundle);
						msg.sendToTarget();
					}
				});
		mDevices.startSensorUpdater(0);
	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch (requestCode) {
    	case PREFERENCEACTIVITY_ID:
    		if (mLayoutChanged) {
    			initWorkspaceView();
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

	/** Called when the activity is first created. **/
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, false);

        Preferences.init(false);
		mDevices = Devices.getInstance();

		mConfigurationManager = ConfigurationManager.getInstance(this);

        mTitle = getTitle().toString();
        mTitleChannelName = "";
        
		mDevices.setParentActivity(this);
		mDevices.setResultHandler(mResultHandler);
		mDevices.setOnDeviceConfigurationChangedListener(this);
		
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
	    sp.registerOnSharedPreferenceChangeListener(this);
	    sp.registerOnSharedPreferenceChangeListener(mDevices);

	    initLogging(sp);
		initWorkspaceView();
		
		mWatchPortForwardingThread = new WatchPortForwadingThread();
		mWatchPortForwardingThread.start();
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
	protected void onDestroy() {
		super.onDestroy();
		mDevices.clearOnSensorChangeListeners();
		new CloseConnectionTask().execute(CLOSE_CONNECTION_PORTFORWARDING);
	}
	
    @Override
    public void onLoad() {
    	updateTitle(mWorkspace.getCurrentView());
    }
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.androvdr_exit:
			new CloseConnectionTask().execute(CLOSE_CONNECTION_TERMINATE);
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
		MenuItem mi = menu.findItem(R.id.androvdr_internet);
		if (Preferences.useInternet)
			mi.setTitle(R.string.main_forwarding_off);
		else
			mi.setTitle(R.string.main_forwarding_on);
		
		return true;
	}
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (key.equals("alternateLayout"))
			mLayoutChanged = true;
		if (key.equals("currentVdrId")) {
			new CloseConnectionTask().execute(CLOSE_CONNECTION_PORTFORWARDING);
		}
		if (key.equals("logLevel"))
			initLogging(sharedPreferences);
	}

	@Override
	protected void onStart() {
		super.onStart();
		mDevices.startSensorUpdater(1);
	}

	@Override
	protected void onStop() {
		super.onStop();
		mDevices.stopSensorUpdater();
	}
	
    private Handler sshDialogHandler = new Handler() {
		@Override
        public void handleMessage(Message msg) {
			PortForwarding.sshDialogHandlerMessage(msg);
		}
	};
    
	private void togglePortforwarding() {
		new CloseConnectionTask().execute(CLOSE_CONNECTION);

		if (Preferences.useInternet == false) {
			String connectionState = "";
			final ConnectivityManager cm = (ConnectivityManager) this
					.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo[] info = cm.getAllNetworkInfo();
			for (int i = 0; i < info.length; i++) {
				if (info[i].isConnected() == true) { // dann wird wohl hoffentlich eine Verbindung klappen
					portForwarding = new PortForwarding(sshDialogHandler, this);
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
			if (portForwarding != null) {
				portForwarding.disconnect();
				portForwarding = null;
			}
		}
	}

	public void updateTitle(View view) {
    	if ((view != null) && (view.getTag() instanceof CharSequence))
    		setTitle(mTitle + " (" + view.getTag() + ")");
    	else
    		setTitle(mTitle + mTitleChannelName);
    }
    
	private class ChannelViewUpdater extends AsyncTask<String, Void, Channel> {

		@Override
		protected Channel doInBackground(String... params) {
			LinearLayout channelInfo = (LinearLayout) findViewById(R.id.remote_channel_info);
			int channelNumber;
			StringBuilder channelName = new StringBuilder();
			
			if (params[0].equalsIgnoreCase("N/A"))
				return null;
			
			try {
				String[] sa = params[0].split(" ");
				channelNumber = Integer.parseInt(sa[0]);
				for (int i = 1; i < sa.length; i++)
					channelName.append(sa[i] + " ");
			} catch (Exception e) {
				logger.error("Couldn't parse channel: {}", e);
				return null;
			}

			// --- only channel name is needed ---
			if (channelInfo == null) {
				return new Channel(channelNumber, channelName.toString().trim(), "");
			}
			
		    Response response = VDRConnection.send(new LSTC(channelNumber));
			if(response.getCode() != 250) {
				logger.error("Couldn't get channel: {} {}", response.getCode(), response.getMessage());
				return null;
			}
			
			Channel channel;
			try {
				List<org.hampelratte.svdrp.responses.highlevel.Channel> channels = ChannelParser
						.parse(response.getMessage(), true);
				if (channels.size() > 0) {
					channel = new Channel(channels.get(0));
					channel.updateEpg(true);
					return channel;
				} else {
					logger.error("No channel found");
					return null;
				}
			} catch(ParseException pe) {
			    logger.error("Couldn't parse channel details", pe);
			    return null;
			} catch(IOException e) {
				logger.error("Couldn't get channel", e);
				return null;
			}
			
		}
		
		@Override
		protected void onPostExecute(Channel channel) {
			LinearLayout channelInfo = (LinearLayout) findViewById(R.id.remote_channel_info);

			if (channelInfo == null) {
				if (channel == null)
					mTitleChannelName = "";
				else
					mTitleChannelName = " - " + channel.name;

				if (mWorkspace != null)
					updateTitle(mWorkspace.getCurrentView());
				else
					updateTitle(null);
			} else {
				if (channel == null) {
					channelInfo.setVisibility(View.GONE);
				} else {
					channelInfo.setVisibility(View.VISIBLE);
					final SimpleDateFormat timeformatter = new SimpleDateFormat(
							Preferences.timeformat);
					final GregorianCalendar calendar = new GregorianCalendar();

					TextView tv = (TextView) findViewById(R.id.channelnumber);
					ImageView iv = (ImageView) findViewById(R.id.channellogo);

					if (Preferences.useLogos) {
						tv.setVisibility(View.GONE);
						iv.setVisibility(View.VISIBLE);
						iv.setImageBitmap(channel.logo);
					} else {
						tv.setVisibility(View.VISIBLE);
						iv.setVisibility(View.GONE);
						tv.setText(String.valueOf(channel.nr));
					}

					tv = (TextView) findViewById(R.id.channeltext);
					tv.setText(channel.name);

					ProgressBar pb = (ProgressBar) findViewById(R.id.channelprogress);
					if (channel.getNow().isEmpty) {
						pb.setProgress(0);
						tv = (TextView) findViewById(R.id.channelnowplayingtime);
						tv.setText("");
						tv = (TextView) findViewById(R.id.channelnowplaying);
						tv.setText("");
					} else {
						calendar.setTimeInMillis(channel.getNow().startzeit * 1000);
						pb.setProgress(channel.getNow().getActualPercentDone());
						tv = (TextView) findViewById(R.id.channelnowplayingtime);
						tv.setText(timeformatter.format(calendar.getTime()));
						tv = (TextView) findViewById(R.id.channelnowplaying);
						tv.setText(channel.getNow().titel);
					}

					if (channel.getNext().isEmpty) {
						tv = (TextView) findViewById(R.id.channelnextplayingtime);
						tv.setText("");
						tv = (TextView) findViewById(R.id.channelnextplaying);
						tv.setText("");
					} else {
						calendar.setTimeInMillis(channel.getNext().startzeit * 1000);
						tv = (TextView) findViewById(R.id.channelnextplayingtime);
						tv.setText(timeformatter.format(calendar.getTime()));
						tv = (TextView) findViewById(R.id.channelnextplaying);
						tv.setText(channel.getNext().titel);
					}
				}
			}
		}
	}
	
	private class CloseConnectionTask extends AsyncTask<Integer, Void, Void> {

		@Override
		protected Void doInBackground(Integer... params) {
			logger.trace("CloseConnection: {}", params[0]);
			logger.trace("  --> Closing");
			VDRConnection.close();

			if (params[0] >= CLOSE_CONNECTION_PORTFORWARDING) {
				logger.trace("  --> Disconnecting PortForward");
				if (portForwarding != null)
					portForwarding.disconnect();
			}
			
			if (params[0] >= CLOSE_CONNECTION_TERMINATE) {
				logger.trace("  --> Kill Process");
				android.os.Process.killProcess(android.os.Process.myPid());
			}
			return null;
		}
		
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
    		logger.trace("Erstelle den Tab {}", tag);
    		
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
    
    public class WatchPortForwadingThread extends Thread {
    	
    	public void run() {
			NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

			while (!isInterrupted()) {
    			synchronized (Preferences.useInternetSync) {
    				try {
						if (Preferences.useInternet) {
							int icon = R.drawable.stat_ic_menu_login;
							CharSequence tickerText = getString(R.string.notification_connected_ticker);
							long when = System.currentTimeMillis();
							Context context = getApplicationContext();
							CharSequence contentTitle = getString(R.string.app_name);
							CharSequence contentText = getString(R.string.notification_connected); 
							VdrDevice vdr = Preferences.getVdr();
							if (vdr != null)
								contentText = contentText + " " + vdr.remote_host; 
							Intent notificationIntent = new Intent(AndroVDR.this, AndroVDR.class);
							PendingIntent contentIntent = PendingIntent.getActivity(AndroVDR.this, 0, notificationIntent, 0);

							Notification notification = new Notification(icon, tickerText, when);
							notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
							notification.flags = Notification.FLAG_NO_CLEAR;
							
							notificationManager.notify(1, notification);
						} else {
							notificationManager.cancel(1);
						}
						Preferences.useInternetSync.wait();
					} catch (InterruptedException e) {
						logger.debug("WatchPortForwardingThread interrupted");
					}
				}
    		}
    	}
    }
}