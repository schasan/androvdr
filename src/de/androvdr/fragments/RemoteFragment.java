package de.androvdr.fragments;

import java.io.File;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import de.androvdr.ConfigurationManager;
import de.androvdr.Dialogs;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.UsertabParser;
import de.androvdr.WorkspaceView;
import de.androvdr.devices.Devices;

public class RemoteFragment extends AbstractFragment {
	private static transient Logger logger = LoggerFactory.getLogger(RemoteFragment.class);

	protected final File mUsertabFile = new File(Preferences.getUsertabFileName());
	
	protected Activity mActivity;
	protected ConfigurationManager mConfigurationManager;
	protected Devices mDevices;

	protected void addClickListeners(View view) {
		if (view instanceof ViewGroup) {
			ViewGroup v = (ViewGroup) view;
			for (int j = 0; j < v.getChildCount(); j++)
				if (v.getChildAt(j) instanceof Button) {
					Button button = (Button) v.getChildAt(j);
					button.setOnClickListener(getOnClickListener());
					button.setOnLongClickListener(getOnLongClickListener());
				} else if (v.getChildAt(j) instanceof ImageButton) {
					ImageButton imageButton = (ImageButton) v.getChildAt(j);
					imageButton.setOnClickListener(getOnClickListener());
					imageButton.setOnLongClickListener(getOnLongClickListener());
				} else {
					if (v.getChildAt(j) instanceof ViewGroup)
						addClickListeners((ViewGroup) v.getChildAt(j));
				}
		}
	}

	protected OnClickListener getOnClickListener() {
		return new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (Preferences.getVdr() == null) {
					mActivity.showDialog(Dialogs.CONFIG_VDR);
					return;
				}

				if (v.getTag() instanceof String) {
					mConfigurationManager.vibrate();
					String sa[] = ((String) v.getTag()).split("\\|");
					if (sa.length > 0)
						mDevices.send(sa[0]);
				}
			}
		};
	}

	protected OnLongClickListener getOnLongClickListener() {
		return new OnLongClickListener() {

			@Override
			public boolean onLongClick(View v) {
				if (Preferences.getVdr() == null) {
					mActivity.showDialog(Dialogs.CONFIG_VDR);
					return false;
				}

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
	
	protected View getUserScreen() {
		LayoutInflater inflater = (LayoutInflater) mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View root = inflater.inflate(R.layout.usertab, null);
		RelativeLayout v = (RelativeLayout)root.findViewById(R.id.root);
		if (!Preferences.alternateLayout)
			v.setBackgroundColor(Color.BLACK);
		UsertabParser p = new UsertabParser(mUsertabFile);
		ArrayList<UsertabParser.UserButton> bList = p.getButtons();
		LayoutParams l;
		Button b;
		ImageButton ib;
		for(int i = 0; i < bList.size();i++){
			UsertabParser.UserButton userButton = bList.get(i);
			l = new LayoutParams(userButton.width,userButton.height);
			l.setMargins(userButton.posX, userButton.posY, 0, 0);
			if(userButton.art == 1){ // normaler Button
				b = new Button(mActivity.getApplicationContext());
				if (Preferences.alternateLayout) {
					b.setBackgroundResource(R.drawable.btn_remote);
					b.setTextColor(Color.WHITE);
					b.setTypeface(null, Typeface.BOLD);
				}
				b.setLayoutParams(l);
				b.setText(userButton.beschriftung);
				b.setTag("VDR." + userButton.action);
				v.addView(b);
				continue;
			}
			if(userButton.art == 2){ // ImageButton
				ib = new ImageButton(mActivity.getApplicationContext());
				if (Preferences.alternateLayout) {
					ib.setBackgroundResource(R.drawable.btn_remote);
				}
				ib.setLayoutParams(l);
				ib.setImageURI(Uri.parse(userButton.beschriftung));
				ib.setTag("VDR." + userButton.action);
				v.addView(ib);
				continue;
			}					
		}
		return v;
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mActivity = getActivity();
		mConfigurationManager = ConfigurationManager.getInstance(mActivity);
		mDevices = Devices.getInstance();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		WorkspaceView mWorkspace = new WorkspaceView(mActivity, null);
		mWorkspace.setTouchSlop(32);
		mWorkspace.setId(R.id.workspaceview);
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
		prefs.registerOnSharedPreferenceChangeListener(mWorkspace);

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
			addClickListeners(view);
			mWorkspace.addView(view);
		}

		if (mUsertabFile.exists()) {
			logger.trace("Add user defined screen");
			View view = getUserScreen();
			addClickListeners(view);
			mWorkspace.addView(view);
		}
		
		return mWorkspace;
	}
}
