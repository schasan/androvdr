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

package de.androvdr;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Environment;
import android.preference.PreferenceManager;
import de.androvdr.devices.Devices;
import de.androvdr.devices.VdrDevice;

public class Preferences {
	private static final String CONFIG_ROOTDIR = "AndroVDR";
	private static final String CONFIG_LOGODIR = "logos";
	private static final String CONFIG_PLUGINDIR = "plugins";
	private static final String CONFIG_MACROFILE = "macros.xml";
	private static final String CONFIG_LOGFILE = "log.txt";
	private static final String CONFIG_GESTUREFILE = "gestures";
	private static final String CONFIG_SSH_KEY = "sshkey";
	private static final String CONFIG_SSH_KNOWN_HOSTS = "known_hosts";
	
	private static VdrDevice sCurrentVdr = null;
	private static long sCurrentVdrId = -1;
	
	public static SharedPreferences sharedPreferences;
	public static String sFilesDir;
	
	public static boolean blackOnWhite;
	public static boolean useLogos;
	public static int textSizeOffset;
	public static boolean deleteRecordingIds = false;
	public static int tabIndicatorColor;
	public static int logoBackgroundColor;
	public static boolean alternateLayout;
	public static boolean epgsearch_title;
	public static boolean epgsearch_subtitle;
	public static boolean epgsearch_description;
	public static int epgsearch_max;
	
	public static String dateformat = "dd.MM.";
	public static String dateformatLong = "dd.MM.yyyy HH:mm";
	public static String timeformat = "HH:mm";

	public static boolean useInternet = false; // legt fest, ob Portforwarding zum Einsatz kommt
	public static Boolean useInternetSync = false;
	public static boolean doRecordingIdCleanUp = true;
	
	public static String getExternalRootDirName() {
		return Environment.getExternalStorageDirectory() + "/" + CONFIG_ROOTDIR;
	}
	
	public static String getGestureFileName() {
		return getExternalRootDirName() + "/" + CONFIG_GESTUREFILE;
	}
	
	public static String getLogFileName() {
		return getExternalRootDirName() + "/" + CONFIG_LOGFILE;
	}
	
	public static String getLogoDirName() {
		return getExternalRootDirName() + "/" + CONFIG_LOGODIR;
	}
	
	public static String getMacroFileName() {
		return getPluginDirName() + "/" + CONFIG_MACROFILE;
	}
	
	public static String getPluginDirName() {
		return getExternalRootDirName() + "/" + CONFIG_PLUGINDIR;
	}
	
	public static String getSSHKeyFileName() {
		return sFilesDir + "/" + CONFIG_SSH_KEY;
	}
	
	public static String getSSHKnownHostsFileName() {
		return sFilesDir + "/" + CONFIG_SSH_KNOWN_HOSTS;
	}
	
	public static VdrDevice getVdr() {
		if (sCurrentVdr == null) {
			sCurrentVdr = Devices.getInstance().getVdr(sCurrentVdrId);
			if (sCurrentVdr == null)
				if (Devices.getInstance().getVdrs().size() == 1)
					if ((sCurrentVdr = Devices.getInstance().getFirstVdr()) != null) {
						sCurrentVdrId = sCurrentVdr.getId();
						SharedPreferences.Editor editor = sharedPreferences.edit();
						editor.putLong("currentVdrId", sCurrentVdrId);
						editor.commit();
					}
		}
		return sCurrentVdr;
	}
	
	public static void setVdr(long id) {
		if (id == sCurrentVdrId)
			return;
		Channels.clear();
		sCurrentVdrId = id;
		sCurrentVdr = null;
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.putLong("currentVdrId", id);
		editor.commit();
	}
	
	public static void init(Context context) {
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		sFilesDir = context.getFilesDir().getAbsolutePath();
		
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
			File appDir = new File(Environment.getExternalStorageDirectory(), CONFIG_ROOTDIR);
			if (!appDir.exists())
				appDir.mkdirs();
		}
		
		Logger logger = LoggerFactory.getLogger(Preferences.class);
		if (logger instanceof IFileLogger) {
			IFileLogger filelogger = (IFileLogger) logger;
			int logging = Integer.parseInt(sharedPreferences.getString("logLevel", "0"));
			if (logging == 0)
				filelogger.setLogLevel(0);
			else
				filelogger.setLogLevel(Integer.parseInt(sharedPreferences.getString("slf4jLevel", "5")));
		}

		blackOnWhite = sharedPreferences.getBoolean("blackOnWhite", false);
	    textSizeOffset = Integer.parseInt(sharedPreferences.getString("textSizeOffset", "0"));
	    useLogos = sharedPreferences.getBoolean("useLogos", false);
	    deleteRecordingIds = sharedPreferences.getBoolean("deleteRecordingIds", false);
	    sCurrentVdrId = sharedPreferences.getLong("currentVdrId", -1);
	    alternateLayout = sharedPreferences.getBoolean("alternateLayout", true);
	    epgsearch_title = sharedPreferences.getBoolean("epgsearch_title", true);
	    epgsearch_subtitle = sharedPreferences.getBoolean("epgsearch_subtitle", true);
	    epgsearch_description = sharedPreferences.getBoolean("epgsearch_description", false);
	    epgsearch_max = Integer.parseInt(sharedPreferences.getString("epgsearch_max", "30"));

	    String colorname = sharedPreferences.getString("tabIndicatorColor", "blue");
	    if (!colorname.equals("none"))
	    	tabIndicatorColor = Color.parseColor(colorname);
	    else
	    	tabIndicatorColor = 0;
	    colorname = sharedPreferences.getString("logoBackgroundColor", "none");
	    if (!colorname.equals("none"))
	    	logoBackgroundColor = Color.parseColor(colorname);
	    else
	    	logoBackgroundColor = 0;
	    
	    sCurrentVdr = null;
	}
	
	public static void store() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("deleteRecordingIds", deleteRecordingIds);
        editor.commit();		
	}
}
