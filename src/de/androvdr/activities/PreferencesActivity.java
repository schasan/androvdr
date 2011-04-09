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

import java.util.List;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import de.androvdr.ListPreferenceValueHolder;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.devices.Devices;
import de.androvdr.devices.IActuator;

public class PreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {

	private void initVolumeDevicePref(final ListPreference pref) {
		DeviceHolder deviceHolder = new DeviceHolder();
		pref.setEntryValues(deviceHolder.getIds());
		pref.setEntries(deviceHolder.getNames());
	}

	public void initVolumeUpDownPref(final ListPreference pref, long deviceId) {
		DeviceVolumeHolder volumeHolder = new DeviceVolumeHolder(deviceId);
		pref.setEntries(volumeHolder.getIds());
		pref.setEntryValues(volumeHolder.getNames());
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (Preferences.blackOnWhite) {
			setTheme(R.style.Theme_Light);
			getListView().setCacheColorHint(Color.TRANSPARENT);
			getWindow().setBackgroundDrawable(getResources().getDrawable(android.R.drawable.screen_background_light));
		}
		
		addPreferencesFromResource(R.xml.preferences);

		Devices devices = Devices.getInstance(this);
		if (!devices.hasPlugins()) {
			PreferenceGroup volumeCategory = (PreferenceGroup) findPreference("category_volume");
			for (CharSequence prefName : Devices.volumePrefNames) {
				Preference pref = findPreference(prefName);
				volumeCategory.removePreference(pref);
			}
		} else {
			ListPreference volumeDeviceListPref = (ListPreference) findPreference("volumeDevice");
			initVolumeDevicePref(volumeDeviceListPref);
			initVolumeUpDownPref((ListPreference) findPreference("volumeUp"),
					Long.parseLong(volumeDeviceListPref.getValue()));
			initVolumeUpDownPref((ListPreference) findPreference("volumeDown"),
					Long.parseLong(volumeDeviceListPref.getValue()));
			SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
			pref.registerOnSharedPreferenceChangeListener(this);
			updateSummaries();
			updateVolumePreferences(!PreferenceManager.getDefaultSharedPreferences(this).getBoolean("volumeVDR", false));
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		Preferences.init(null);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sp,	String key) {
		if (key.equals("volumeVDR")) {
			updateVolumePreferences(!sp.getBoolean(key, false));
		} else if (key.equals("volumeDevice")) {
			SharedPreferences.Editor editor = sp.edit();
			editor.remove("volumeUp");
			editor.remove("volumeDown");
			editor.commit();

			long deviceId = Long.parseLong(sp.getString(key, ""));
			initVolumeUpDownPref((ListPreference) findPreference("volumeUp"),
					deviceId);
			initVolumeUpDownPref((ListPreference) findPreference("volumeDown"),
					deviceId);
		}
		updateSummaries();
	}

	public void updateSummaries() {
		for (CharSequence prefName : Devices.volumePrefNames) {
			ListPreference pref = (ListPreference) findPreference(prefName);
			pref.setSummary(pref.getEntry());
		}
		ListPreference pref = (ListPreference) findPreference("tabIndicatorColor");
		pref.setSummary(pref.getEntry());
		
		pref = (ListPreference) findPreference("logoBackgroundColor");
		pref.setSummary(pref.getEntry());
	}
	
	private void updateVolumePreferences(boolean state) {
		for (CharSequence prefName : Devices.volumePrefNames) {
			Preference pref = findPreference(prefName);
			pref.setEnabled(state);
		}
	}

	public class DeviceHolder extends ListPreferenceValueHolder {

		@Override
		protected void setValues(List<CharSequence> ids,
				List<CharSequence> names) {
			Devices devices = Devices.getInstance();
			ids.add("-1");
			names.add("None");
			for (IActuator device : devices.getDevices()) {
				ids.add(Long.toString(device.getId()));
				names.add(device.getName());
			}
		}
	}

	public class DeviceVolumeHolder extends ListPreferenceValueHolder {
		private long mDeviceId;

		public DeviceVolumeHolder(long deviceId) {
			mDeviceId = deviceId;
		}

		@Override
		protected void setValues(List<CharSequence> ids,
				List<CharSequence> names) {
			IActuator device = Devices.getInstance().getDevice(mDeviceId);
			if (device != null && device.getCommands() != null) {
				for (String command : device.getCommands()) {
					ids.add(command);
					names.add(command);
				}
			}
		}
	}
}
