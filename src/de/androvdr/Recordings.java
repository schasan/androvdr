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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

public class Recordings {
	private static final String TAG = "Recordings";
	
	private ArrayList<RecordingViewItem> mItems = new ArrayList<RecordingViewItem>();
	
	public Recordings(DBHelper db) throws IOException {
		Connection connection = new Connection();
		init(connection, db);
		connection.close();
	}
	
	public Recordings(Connection connection, DBHelper db) throws IOException {
		init(connection, db);
	}
	
	public static void clearIds(DBHelper db) {
		clearIds(db, Preferences.getVdr().getId());
	}
	
	public static void clearIds(DBHelper db, long vdrid) {
		SQLiteDatabase database = db.getWritableDatabase();
		synchronized (db) {
			int i = database.delete(RecordingIdsTable.TABLE_NAME,
					RecordingIdsTable.VDR_ID + "=?", 
					new String[] { Long.toString(vdrid) });
			i++;
		}
	}
	
	public ArrayList<RecordingViewItem> getItems() {
		return mItems;
	}
	
	private RecordingViewItem getFolder(String name) {
		RecordingViewItem result = null;
		for (int i = 0; i < mItems.size(); i++) {
			RecordingViewItem item = mItems.get(i);
			if (item.isFolder && item.folderName.equals(name)) {
				result = mItems.get(i);
				break;
			}
		}
		return result;
	}
	
	private void init(Connection connection, DBHelper db) throws IOException {
		try {
			boolean isLastLine = false;
			
			connection.sendData("LSTR\n");
			do {
				String s = connection.readLine();
				
				if (s.charAt(3) == ' ')
					isLastLine = true;
				
				try {
					Recording recording = new Recording(s.substring(4), db);
					RecordingViewItem item;
					if (recording.inFolder()) {
						if ((item = getFolder(recording.folders.get(0))) == null) {
							item = new RecordingViewItem(recording.folders.get(0));
							mItems.add(item);
						}
						recording.folders.remove(0);
						item.add(recording);
					}
					else {
						item = new RecordingViewItem(recording);
						mItems.add(item);
					}
				} catch (Exception e) {
					MyLog.v(TAG, "ERROR invalid recording format: " + e.toString());
					continue;
				}
				
			} while (! isLastLine);
		} catch (IOException e) {
			if (connection != null)
				connection.close();
			MyLog.v(TAG, "ERROR init(): " + e.toString());
			throw e;
		}
	}
	
	public static void deleteUnusedIds(DBHelper db, ArrayList<Recording> currentRecordings) {
		SQLiteDatabase database = db.getWritableDatabase();
		Cursor cursor = null;
		Recording searchRecording = new Recording();
		Collections.sort(currentRecordings);
		synchronized (db) {
			try {
				SQLiteStatement deleteStmt = database
						.compileStatement("DELETE FROM " + RecordingIdsTable.TABLE_NAME
								+ " WHERE " + RecordingIdsTable.ID + " = ? AND " + RecordingIdsTable.VDR_ID + " = ?");
				
				cursor = database.query(RecordingIdsTable.TABLE_NAME,
						new String[] { RecordingIdsTable.ID },
						RecordingIdsTable.VDR_ID + " = ?",
						new String[] { Long.toString(Preferences.getVdr().getId()) }, null,
						null, null);
				while (cursor.moveToNext()) {
					searchRecording.id = cursor.getString(0);
					if (Collections.binarySearch(currentRecordings, searchRecording) < 0) {
						deleteStmt.bindString(1, searchRecording.id);
						deleteStmt.execute();
					}
				}
			} finally {
				if (cursor != null)
					cursor.close();
			}
		}
	}
}
