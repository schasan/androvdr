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

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import de.androvdr.DevicesTable;
import de.androvdr.ListPreferenceValueHolder;
import de.androvdr.MyLog;
import de.androvdr.R;
import de.androvdr.devices.Devices;
import de.androvdr.devices.IDevice;

public class DevicePreferencesActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private CursorPreferenceHack pref = null;
	private long mId;
	private boolean mIsVDR = false;
	
	@Override
	public SharedPreferences getSharedPreferences(String name, int mode) {
		return this.pref;
	}

	private void initCharsetPref(final ListPreference charsetPref) {
		CharsetHolder charsetHolder = new CharsetHolder();
		charsetPref.setEntryValues(charsetHolder.getIds());
		charsetPref.setEntries(charsetHolder.getNames());
	}

	private void initClassPref(final ListPreference classPref) {
		ClassHolder classHolder = new ClassHolder();
		classPref.setEntryValues(classHolder.getIds());
		classPref.setEntries(classHolder.getNames());
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mId = getIntent().getExtras().getInt("deviceid", -1);
		
		pref = new CursorPreferenceHack(mId);
		pref.registerOnSharedPreferenceChangeListener(this);

		IDevice device = Devices.getInstance().getDevice(mId);
		if (device instanceof OnSharedPreferenceChangeListener)
			pref.registerOnSharedPreferenceChangeListener((OnSharedPreferenceChangeListener) device);
		
		if (mId == -1)
			mIsVDR = true;
		else if (mId == -2)
			mIsVDR = false;
		else
			mIsVDR = pref.getString(DevicesTable.CLASS, "").equals(Devices.VDR_CLASSNAME);

		if (mIsVDR) {
			addPreferencesFromResource(R.xml.devicepreferences_vdr);

			// Populate the character set encoding list with all available
			final ListPreference charsetPref = (ListPreference) findPreference(DevicesTable.CHARACTERSET);

			CharsetHolder charsetHolder = new CharsetHolder();
			if (charsetHolder.isInitialized()) {
				initCharsetPref(charsetPref);
			} else {
				String[] currentCharsetPref = new String[1];
				currentCharsetPref[0] = charsetPref.getValue();
				charsetPref.setEntryValues(currentCharsetPref);
				charsetPref.setEntries(currentCharsetPref);

				new Thread(new Runnable() {
					public void run() {
						initCharsetPref(charsetPref);
					}
				}).start();
			}
		} else {
			addPreferencesFromResource(R.xml.devicepreferences);

			final ListPreference classPref = (ListPreference) findPreference(DevicesTable.CLASS);
			initClassPref(classPref);
		}

		updateSummaries();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		updateSummaries();
	}
		
	private void updateSummaries() {
		// for all text preferences, set hint as current database value
		for (String key : this.pref.values.keySet()) {
			Preference pref = this.findPreference(key);
			if (pref != null
					&& (!mIsVDR || ((pref.getKey().equals("name")
							|| pref.getKey().equals("margin_start")
							|| pref.getKey().equals("margin_stop")
							|| pref.getKey().equals("remote_user") || pref
							.getKey().equals("remote_timeout"))))) {

				CharSequence value = this.pref.getString(key, "");

				if (pref instanceof ListPreference) {
					ListPreference listPref = (ListPreference) pref;
					int entryIndex = listPref.findIndexOfValue((String) value);
					if (entryIndex >= 0)
						value = listPref.getEntries()[entryIndex];
				}

				pref.setSummary(value);
			}
		}
	}

	public class CursorPreferenceHack implements SharedPreferences {
		protected long id;

		protected Map<String, String> values = new HashMap<String, String>();

		// protected Map<String, String> pubkeys = new HashMap<String,
		// String>();

		public CursorPreferenceHack(long id) {
			this.id = id;

			cacheValues();
		}

		protected final void cacheValues() {
			// fill a cursor and cache the values locally
			// this makes sure we dont have any floating cursor to dispose later

			Devices devices = Devices.getInstance();
			Cursor cursor = devices.getCursorForDevice(id);
			if (cursor.moveToFirst()) {
				for (int i = 0; i < cursor.getColumnCount(); i++) {
					String key = cursor.getColumnName(i);
					if (key.equals(DevicesTable.ID))
						continue;
					String value = cursor.getString(i);
					values.put(key, value);
				}
			}
			cursor.close();
			devices.dbClose();
		}

		public boolean contains(String key) {
			return values.containsKey(key);
		}

		public class Editor implements SharedPreferences.Editor {

			private ContentValues update = new ContentValues();

			public SharedPreferences.Editor clear() {
				MyLog.v(this.getClass().toString(), "clear()");
				update = new ContentValues();
				return this;
			}

			public boolean commit() {
				// Log.d(this.getClass().toString(),
				// "commit() changes back to database");
				// Devices.getInstance().storeConfig(update);
				Devices devices = Devices.getInstance();
				if (id < 0) {
					id = devices.dbStore(update);
					setResult((int) id);
				} else {
					devices.dbUpdate(id, update);
				}
				
				// make sure we refresh the parent cached values
				cacheValues();

				// and update any listeners
				for (OnSharedPreferenceChangeListener listener : listeners) {
					listener.onSharedPreferenceChanged(
							CursorPreferenceHack.this, null);
				}

				return true;
			}

			public android.content.SharedPreferences.Editor putBoolean(
					String key, boolean value) {
				return this.putString(key, Boolean.toString(value));
			}

			public android.content.SharedPreferences.Editor putFloat(
					String key, float value) {
				return this.putString(key, Float.toString(value));
			}

			public android.content.SharedPreferences.Editor putInt(String key,
					int value) {
				return this.putString(key, Integer.toString(value));
			}

			public android.content.SharedPreferences.Editor putLong(String key,
					long value) {
				return this.putString(key, Long.toString(value));
			}

			public android.content.SharedPreferences.Editor putString(
					String key, String value) {
				// Log.d(this.getClass().toString(),
				// String.format("Editor.putString(key=%s, value=%s)", key,
				// value));
				update.put(key, value);
				return this;
			}

			public android.content.SharedPreferences.Editor remove(String key) {
				// Log.d(this.getClass().toString(),
				// String.format("Editor.remove(key=%s)", key));
				update.remove(key);
				return this;
			}

			@Override
			public void apply() {
				commit();
			}

			@Override
			public android.content.SharedPreferences.Editor putStringSet(
					String arg0, Set<String> arg1) {
				// TODO Auto-generated method stub
				return null;
			}

		}

		public Editor edit() {
			// Log.d(this.getClass().toString(), "edit()");
			return new Editor();
		}

		public Map<String, ?> getAll() {
			return values;
		}

		public boolean getBoolean(String key, boolean defValue) {
			return Boolean.valueOf(this.getString(key, Boolean
					.toString(defValue)));
		}

		public float getFloat(String key, float defValue) {
			return Float.valueOf(this.getString(key, Float.toString(defValue)));
		}

		public int getInt(String key, int defValue) {
			return Integer.valueOf(this.getString(key, Integer
					.toString(defValue)));
		}

		public long getLong(String key, long defValue) {
			return Long.valueOf(this.getString(key, Long.toString(defValue)));
		}

		public String getString(String key, String defValue) {
			// Log.d(this.getClass().toString(),
			// String.format("getString(key=%s, defValue=%s)", key, defValue));

			if (!values.containsKey(key))
				return defValue;
			return values.get(key);
		}

		protected List<OnSharedPreferenceChangeListener> listeners = new LinkedList<OnSharedPreferenceChangeListener>();

		public void registerOnSharedPreferenceChangeListener(
				OnSharedPreferenceChangeListener listener) {
			listeners.add(listener);
		}

		public void unregisterOnSharedPreferenceChangeListener(
				OnSharedPreferenceChangeListener listener) {
			listeners.remove(listener);
		}

		@Override
		public Set<String> getStringSet(String arg0, Set<String> arg1) {
			// TODO Auto-generated method stub
			return null;
		}
	}


	public static class CharsetHolder extends ListPreferenceValueHolder {

		@Override
		protected void setValues(List<CharSequence> ids,
				List<CharSequence> names) {
			for (Entry<String, Charset> entry : Charset.availableCharsets().entrySet()) {
				Charset c = entry.getValue();
				if (c.canEncode() && c.isRegistered()) {
					ids.add(c.displayName());
					names.add(c.displayName());
				}
			}
		}
	}

	public static class ClassHolder extends ListPreferenceValueHolder {

		@Override
		protected void setValues(List<CharSequence> ids,
				List<CharSequence> names) {
			Devices devices = Devices.getInstance();
			for (String name : devices.getPluginNames()) {
				ids.add(name);
				names.add(name);
			}
		}
	}
}
