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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.DateFormat;
import java.util.Date;

import android.os.Environment;
import android.util.Log;

public class MyLog {

	private static final File logFile = new File(Preferences.getExternalRootDirName() + "/log.txt");

	// die Loglevel sind in der Resourcendatei @values/arrays festgelegt
	private static int logLevel = 0;

	public static void v(String tag, String msg) {
		v(tag, msg, null);
	}

	public static void v(String tag, String msg, Throwable tr) {
		switch (logLevel) {
		case 0:// kein Loggen
			return;
		case 1:// Systemlogging wird benutzt
			if (tr != null)
				Log.v(tag, msg, tr);
			else
				Log.v(tag, msg);
			break;

		case 2:// Loggen auf SDCARD
		case 3:
			writeToSD(tag, msg, tr);
			break;
		}
	}

	public static void setLogLevel(int l) {
		logLevel = l;
		if (logLevel >= 2) // zeichnet auf SDCARD auf
			clearFile();
	}

	public static int getLogLevel() {
		return logLevel;
	}

	public static void clearFile() {
		try {
			boolean mode;
			if (logLevel == 2)
				mode = false; // Datei wird neu angelegt
			else
				mode = true; // Neue Eintrage werden angehaengt
			FileWriter fstream = new FileWriter(logFile, mode);
			BufferedWriter out = new BufferedWriter(fstream, 1024);
			DateFormat df = DateFormat.getDateInstance();
			out.write("AndroVDR - starte Logging am:" + df.format(new Date())
					+ "\n");
			out.close();
		} catch (Exception e) {// Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}
	}

	private static void writeToSD(String tag, String msg, Throwable tr) {
		if (tr != null)
			tr.printStackTrace();
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
			try {
				FileWriter fstream = new FileWriter(logFile, true);
				BufferedWriter out = new BufferedWriter(fstream, 1024);
				out.write(tag + " - " + msg + "\n");
				// Log.v("###",tag + " - "+ msg + "\n");
				out.close();
			} catch (Exception e) {// Catch exception if any
				System.err.println("Error: " + e.getMessage());
			}
		}
	}
}
