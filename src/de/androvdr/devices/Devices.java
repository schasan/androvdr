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

package de.androvdr.devices;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import dalvik.system.DexClassLoader;
import de.androvdr.DBHelper;
import de.androvdr.DevicesTable;
import de.androvdr.Preferences;
import de.androvdr.Recordings;

public class Devices implements OnSharedPreferenceChangeListener {
	private static transient Logger logger = LoggerFactory.getLogger(Devices.class);
	
	private static Devices sInstance;
	
	public static final String VDR_CLASSNAME = "VDR";
	public static final CharSequence[] volumePrefNames = new CharSequence[] { "volumeDevice", "volumeUp", "volumeDown" };

	private ActivityDevice mActivity;
	private final DBHelper mDBHelper;
	private int mDatabaseIsExternalOpen = 0;
	private Hashtable<String, IActuator> mDevices = new Hashtable<String, IActuator>();
	private Hashtable<String, Macro> mMacros = new Hashtable<String, Macro>();
	private OnChangeListener mOnDeviceConfigurationChangedListener = null;
	private Hashtable<String, Class<?>> mPlugins = new Hashtable<String, Class<?>>();
	private Handler mResultHandler = null;
	private DeviceSendThread mSendThread = null;
	private String mVolumeDownCommand = "";
	private String mVolumeUpCommand = "";

	public static final String MSG_RESULT = "result";

	public static String macroConfig = Preferences.getMacroFileName();
	public static String pluginDir = Preferences.getPluginDirName();
	
	private Devices(Context context) {
		mDBHelper = new DBHelper(context);
		
		init();
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
		initVolumeCommands(sp);

		mSendThread = new DeviceSendThread();
		mSendThread.start();
	}

	public void addDevice(Cursor cursor) {
		if (cursor.getString(1).equals(VDR_CLASSNAME)) {
			VdrDevice vdr = new VdrDevice();
			vdr.setId(cursor.getLong(0));
			vdr.setName(cursor.getString(2));
			vdr.setIP(cursor.getString(3));
			vdr.setPort(cursor.getInt(4));
			vdr.macaddress = cursor.getString(7);
			vdr.remote_host = cursor.getString(8);
			vdr.remote_user = cursor.getString(9);
			vdr.remote_port = cursor.getInt(10);
			vdr.remote_local_port = cursor.getInt(11);
			vdr.remote_timeout = cursor.getInt(12);
			vdr.channellist = cursor.getString(13);
			vdr.epgmax = cursor.getInt(14);
			vdr.characterset = cursor.getString(15);
			vdr.margin_start = cursor.getInt(16);
			vdr.margin_stop = cursor.getInt(17);
			vdr.vps = cursor.getString(18).equals("true");
			vdr.timeout = cursor.getInt(19);
			vdr.sshkey = cursor.getString(20);
			mDevices.put(vdr.getName(), vdr);
		} else {
			try {
				if (mPlugins.containsKey(cursor.getString(1))) {
					IActuator actuator = (IActuator) mPlugins.get(cursor.getString(1)).newInstance();
					if (actuator != null) {
						actuator.setId(cursor.getLong(0));
						actuator.setName(cursor.getString(2));
						actuator.setIP(cursor.getString(3));
						actuator.setPort(cursor.getInt(4));
						actuator.setUser(cursor.getString(5));
						actuator.setPassword(cursor.getString(6));
						mDevices.put(actuator.getName(), actuator);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public void dbClose() {
		mDatabaseIsExternalOpen -= 1;
		if (mDatabaseIsExternalOpen == 0)
			mDBHelper.close();
	}
	
	public int dbDelete(long id) {
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		int result = db.delete(DevicesTable.TABLE_NAME, "_id = ?", new String[] { Long.toString(id) });
		if (mDatabaseIsExternalOpen == 0)
			db.close();
		Recordings.clearIds(mDBHelper, id);
		initDevices();
		return result;
	}
	
	public long dbStore(ContentValues values) {
		long result;
		SQLiteDatabase db = mDBHelper.getWritableDatabase();
		if (values.containsKey(DevicesTable.ID)) {
			result = db.update(DevicesTable.TABLE_NAME, values, DevicesTable.ID + "=?",
					new String[] { values.getAsString(DevicesTable.ID) });
		} else {
			result = db.insert(DevicesTable.TABLE_NAME, null, values);
		}
		if (mDatabaseIsExternalOpen == 0)
			db.close();
		return result;
	}

	public long dbUpdate(long id, ContentValues values) {
		ContentValues storevalues = new ContentValues(values);
		storevalues.put("_id", id);
		return dbStore(storevalues);
	}
	
//	public static Devices getInstance() {
//		return sDevices;
//	}
	
	public static Devices getInstance(Context context) {
		if (sInstance == null) {
			sInstance = new Devices(context);
		}
		return sInstance;
	}

	public IActuator get(String name) {
		return mDevices.get(name);
	}

	public Cursor getCursorForAllDevices() {
		final SQLiteDatabase db = mDBHelper.getReadableDatabase();
		mDatabaseIsExternalOpen += 1;
		return db.query(DevicesTable.TABLE_NAME, DevicesTable.ALL_COLUMNS,
				null, null, null, null, DevicesTable.NAME);
	}
	
	public Cursor getCursorForDevice(long id) {
		final SQLiteDatabase db = mDBHelper.getReadableDatabase();
		mDatabaseIsExternalOpen += 1;
		return db.query(DevicesTable.TABLE_NAME, DevicesTable.ALL_COLUMNS,
				"_id = ?", new String[] { Long.toString(id)}, null, null, null);
	}

	public IActuator getDevice(long id) {
		IActuator device = null;
		Enumeration<String> e = mDevices.keys();
		while (e.hasMoreElements()) {
			String key = e.nextElement();
			IDevice idev = (IDevice) mDevices.get(key);
			if ((idev instanceof IDevice) && (((IDevice) idev).getId() == id)) {
				device = (IActuator) idev;
				break;
			}
		}
		return device;
	}
	
	public ArrayList<IActuator> getDevices() {
		ArrayList<IActuator> actuators = new ArrayList<IActuator>();
		Enumeration<String> e = mDevices.keys();
		while (e.hasMoreElements()) {
			String key = e.nextElement();
			IActuator ac = mDevices.get(key);
			if ((ac instanceof IActuator) && !(ac instanceof VdrDevice)) {
				actuators.add(ac);
			}
		}
		return actuators;
	}
	
	public String[] getPluginNames() {
		List<CharSequence> list = new LinkedList<CharSequence>();
		Enumeration<String> e = mPlugins.keys();
		while (e.hasMoreElements()) {
			String key = e.nextElement();
			list.add(key); 
		}
		return list.toArray(new String[list.size()]);
	}

	public VdrDevice getFirstVdr() {
		VdrDevice vdr = null;
		Enumeration<String> e = mDevices.keys();
		while (e.hasMoreElements()) {
			String key = e.nextElement();
			IActuator ac = (IActuator) mDevices.get(key);
			if (ac instanceof VdrDevice) {
				vdr = (VdrDevice) ac;
				break;
			}
		}
		return vdr;
	}
	
	public VdrDevice getVdr(long id) {
		VdrDevice vdr = null;
		Enumeration<String> e = mDevices.keys();
		while (e.hasMoreElements()) {
			String key = e.nextElement();
			IActuator ac = (IActuator) mDevices.get(key);
			if ((ac instanceof VdrDevice) && (((VdrDevice) ac).getId() == id)) {
				vdr = (VdrDevice) ac;
				break;
			}
		}
		return vdr;
	}
	
	public ArrayList<VdrDevice> getVdrs() {
		ArrayList<VdrDevice> list = new ArrayList<VdrDevice>();
		Enumeration<String> e = mDevices.keys();
		while (e.hasMoreElements()) {
			String key = e.nextElement();
			IActuator ac = (IActuator) mDevices.get(key);
			if (ac instanceof VdrDevice) {
				list.add((VdrDevice) ac);
			}
		}
		return list;
	}
	
	public CharSequence[] getVdrNames() {
		List<CharSequence> list = new LinkedList<CharSequence>();
		Enumeration<String> e = mDevices.keys();
		while (e.hasMoreElements()) {
			String key = e.nextElement();
			IActuator ac = (IActuator) mDevices.get(key);
			if (ac instanceof VdrDevice) {
				list.add(ac.getName());
			}
		}
		return list.toArray(new CharSequence[list.size()]);
	}
	
	public boolean hasPlugins() {
		return (! mPlugins.isEmpty());
	}
	
	private void init() {
		mActivity = new ActivityDevice();

		if (new File(macroConfig).exists()) {
			MacroConfigParser parser = new MacroConfigParser(macroConfig);
			ArrayList<Macro> macros = parser.parse();
			if (macros == null) {
				logger.error("Couldn't parse macro configuration: {}", parser.lastError);
			} else {
				for (Macro macro : macros) {
					logger.debug("Macro: {}", macro.name);
					for (String command : macro.commands)
						logger.debug("  -> {}", command);
					mMacros.put(macro.name, macro);
				}
			}
		}
		
		loadPlugins();
		initDevices();
	}

	public void initDevices() {
		mDevices.clear();
		Cursor cursor = getCursorForAllDevices();
		while (cursor.moveToNext()) {
			addDevice(cursor);
		}
		cursor.close();
		dbClose();
		
		if (mOnDeviceConfigurationChangedListener != null)
			mOnDeviceConfigurationChangedListener.onChange();
	}
	
	private void initVolumeCommands(SharedPreferences sp) {
		if (sp.getBoolean("volumeVDR", false)) {
			mVolumeUpCommand = "VDR.vol_up";
			mVolumeDownCommand = "VDR.vol_down";
		} else {
			long deviceId = Long.parseLong(sp.getString("volumeDevice", "-1"));
			if (deviceId == -1) {
				mVolumeUpCommand = "";
				mVolumeDownCommand = "";
			} else {
				IDevice device = getDevice(deviceId);
				if (device == null) {
					mVolumeUpCommand = "";
					mVolumeDownCommand = "";
				} else {
					mVolumeUpCommand = device.getName() + "." + sp.getString("volumeUp", "");
					mVolumeDownCommand = device.getName() + "." + sp.getString("volumeDown", "");
				}
			}
		}
	}
	
	private void loadPlugins() {
		FilenameFilter filter = new FilenameFilter() {

			@Override
			public boolean accept(File dir, String filename) {
				return filename.endsWith(".jar");
			}
		};

		if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()))
			return;
		
		File filepath = new File(pluginDir);
		if (!filepath.exists())
			return;

		File dexpath = new File(pluginDir + File.separatorChar + "dex");
		if (!dexpath.exists())
			dexpath.mkdir();

		File files[] = filepath.listFiles(filter);
		if (files != null) {
			for (File file : files) {
				if (file.isFile()) {
					try {
						String className = null;
						JarFile jar = new JarFile(file);
						ZipEntry zipentry = jar
								.getEntry("META-INF/services/de.androvdr.devices.IActuator");
						if (zipentry != null) {
							InputStream is = jar.getInputStream(zipentry);
							BufferedReader br = new BufferedReader(
									new InputStreamReader(is), 8192);
							className = br.readLine();
							br.close();
						}
						ClassLoader loader = new DexClassLoader(file
								.getAbsolutePath(), dexpath.getAbsolutePath(),
								null, getClass().getClassLoader());
						Class<?> c = loader.loadClass(className);
						if (IActuator.class.isAssignableFrom(c)) {
							IActuator ac = (IActuator) c.newInstance();
							mPlugins.put(ac.getDisplayClassName(), c);
							logger.debug("Plugin: {}", 
									(ac.getDisplayClassName() + " (" + c.getName() + ")"));
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		boolean isVolumePref = false;
		for (CharSequence s : volumePrefNames)
			if (s.equals(key)) {
				isVolumePref = true;
				break;
			}
		if (key.equals("volumeVDR") || isVolumePref)
			initVolumeCommands(sharedPreferences);
	}

	public void onPause() {
		Enumeration<String> e = mDevices.keys();
		while (e.hasMoreElements()) {
			String key = e.nextElement();
			mDevices.get(key).disconnect();
		}
	}

	public void send(String command) {
		logger.debug("send: {}", command);
		String sa[] = command.split("\\.");
		if (sa.length > 1 && sa[0].equals("Macro")) {
			Macro macro = mMacros.get(sa[1]);
			if (macro != null)
				new MacroThread(macro);
			else {
				sendErrorMessage("Macro " + sa[1] + " not found");
			}
		} else if (sa.length > 1 && sa[0].equals("Activity")) {
			String result = null;
			if (mActivity != null) {
				StringBuilder sb = new StringBuilder();
				for (int i = 1; i < sa.length; i++) {
					if (i > 1)
						sb.append(".");
					sb.append(sa[i]);
				}
				if (!mActivity.write(sb.toString()))
					result = mActivity.getLastError();
				if (result != null)
					sendErrorMessage(result);
			}
		} else {
			mSendThread.send(command);
		}
	}

	private void sendErrorMessage(String msg) {
		if (mResultHandler != null) {
			Bundle resultBundle = new Bundle();
			resultBundle.putString(MSG_RESULT, msg);
			Message resultMessage = new Message();
			resultMessage.setData(resultBundle);
			mResultHandler.sendMessage(resultMessage);
		}
	}

	public void setOnDeviceConfigurationChangedListener(OnChangeListener listener) {
		mOnDeviceConfigurationChangedListener = listener;
		
	}
	public void setParentActivity(Activity activity) {
		mActivity.setParentActivity(activity);
	}

	public void setResultHandler(Handler resultHandler) {
		mResultHandler = resultHandler;
	}

	public boolean volumeControl() {
		return (mVolumeDownCommand.length() > 0) || (mVolumeUpCommand.length() > 0);
	}
	
	public void volumeDown() {
		if (mVolumeDownCommand.length() > 0)
			send(mVolumeDownCommand);
	}
	
	public void volumeUp() {
		if (mVolumeUpCommand.length() > 0)
			send(mVolumeUpCommand);
	}
	
	private class DeviceSendThread extends Thread {
		private Handler mHandler;

		public void run() {
			Looper.prepare();
			mHandler = new Handler() {
				@Override
				public void handleMessage(Message msg) {
					Bundle bundle = msg.getData();
					if (bundle != null) {
						String command = bundle.getString("command");
						String[] sa = command.split("\\.");
						IActuator ac = null;
						if (sa[0].equals(VDR_CLASSNAME))
							ac = Preferences.getVdr();
						else
							ac = mDevices.get(sa[0]);
						String result = null;
						if (ac != null && sa.length > 1) {
							if (!ac.write(sa[1])) {
								result = ac.getLastError();
							}
						} else {
							result = "Error in command: " + command;
						}
						if (result != null)
							sendErrorMessage(result);
					}
				}
			};
			Looper.loop();
		}

		public void send(String command) {
			Bundle bundle = new Bundle();
			bundle.putString("command", command);
			Message msg = new Message();
			msg.setData(bundle);
			mHandler.sendMessage(msg);
		}
	}

	private class MacroThread extends Thread {
		private final Macro mMacro;

		public MacroThread(Macro macro) {
			mMacro = macro;
			start();
		}

		public void run() {
			for (String command : mMacro.commands) {
				String[] sa = command.split("\\.");
				if (sa.length > 1 && sa[0].equalsIgnoreCase("Sleep")) {
					try {
						int microSecond = Integer.parseInt(sa[1]);
						Thread.sleep(microSecond);
					} catch (NumberFormatException e) {
						logger.error("Invalid sleep value");
					} catch (InterruptedException e) {
						logger.trace("MacroThread interrupted");
					}
				} else {
					send(command);
				}
			}
		}
	}
}
