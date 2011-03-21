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

package de.androvdr.controllers;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Stack;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import de.androvdr.Connection;
import de.androvdr.DBHelper;
import de.androvdr.Messages;
import de.androvdr.MyLog;
import de.androvdr.Preferences;
import de.androvdr.R;
import de.androvdr.Recording;
import de.androvdr.RecordingInfo;
import de.androvdr.RecordingViewItem;
import de.androvdr.Recordings;
import de.androvdr.VdrCommands;
import de.androvdr.activities.AbstractListActivity;
import de.androvdr.activities.RecordingInfoActivity;

public class RecordingController extends AbstractController implements Runnable {
	public static final int RECORDING_ACTION_INFO = 1;
	public static final int RECORDING_ACTION_SORT_NAME = 2;
	public static final int RECORDING_ACTION_SORT_DATE = 3;
	public static final int RECORDING_ACTION_PLAY = 4;
	public static final int RECORDING_ACTION_PLAY_START = 5;
	public static final int RECORDING_ACTION_DELETE = 6;
	public static final int RECORDING_ACTION_REMOTE = 7;
	public static final int RECORDING_ACTION_KEY_BACK = 8;
	
	private static final String TAG = "RecordingController";
	
	private RecordingViewItemComparer mComparer;
	private final ListView mListView;
	private RecordingViewItemList mRecordingViewItems = new RecordingViewItemList();
	private RecordingAdapter mRecordingAdapter;
	private Stack<RecordingViewItemList> mRecordingsStack = new Stack<RecordingViewItemList>();
	private RecordingIdUpdateThread mUpdateThread = null;
	private DBHelper db;

	// --- AsyncTasks ---
	RecordingInfoTask mRecordingTask = null;
	
	// --- needed by each row ---
	private final SimpleDateFormat datetimeformatter;
	private final GregorianCalendar calendar;

	public String lastError;
	
	private Handler mThreadHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.arg1) {
			case Messages.MSG_DONE:
				// --- set adapter ---
				mRecordingAdapter = new RecordingAdapter(mActivity, mRecordingViewItems);
				setRecordingAdapter(mRecordingAdapter, mListView);
				mHandler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_DISMISS));
				break;
			case Messages.MSG_TITLEBAR_PROGRESS_SHOW:
				mHandler.sendMessage(Messages.obtain(Messages.MSG_TITLEBAR_PROGRESS_SHOW));
				break;
			case Messages.MSG_TITLEBAR_PROGRESS_DISMISS:
				mHandler.sendMessage(Messages.obtain(Messages.MSG_TITLEBAR_PROGRESS_DISMISS));
				break;
			case Messages.MSG_VDR_ERROR:
				mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
				break;
			}
		}
	};

	public RecordingController(AbstractListActivity activity, Handler handler, ListView listView, Parcelable[] recordings) {
		super.onCreate(activity, handler);
		mListView = listView;
		if (Preferences.blackOnWhite) {
			mListView.setBackgroundColor(Color.WHITE);
			mListView.setCacheColorHint(Color.WHITE);
		}
		datetimeformatter = new SimpleDateFormat(Preferences.dateformatLong);
		calendar = new GregorianCalendar();
		db = new DBHelper(mActivity);
		
		if (recordings != null && recordings.length > 0) {
			mRecordingViewItems = new RecordingViewItemList();
			for (int i = 0; i < recordings.length; i++) {
				mRecordingViewItems.add((RecordingViewItem) recordings[i]);
				mRecordingViewItems.get(i).recording.db = db;
			}
			mRecordingAdapter = new RecordingAdapter(mActivity, mRecordingViewItems);
			setRecordingAdapter(mRecordingAdapter, mListView);
		}
		else {
			Message msg = Messages.obtain(Messages.MSG_PROGRESS_SHOW);
			msg.arg2 = R.string.loading;
			mHandler.sendMessage(msg);
			Thread thread = new Thread(this);
			thread.start();
		}
	}
	
	public void action(int action) {
		if (mUpdateThread != null)
			mUpdateThread.interrupt();

		switch (action) {
		case RECORDING_ACTION_KEY_BACK:
			if (mRecordingsStack.empty())
				mActivity.finish();
			else {
				// --- restore adapter state ---
				RecordingViewItemList list = mRecordingsStack.pop();
				mRecordingAdapter.clear();
				if (list.size() > 0) {
					for (int i = 0; i < list.size(); i++)
						mRecordingAdapter.add(list.get(i));
					mRecordingAdapter.sort(mComparer);
				}
				else if (! mRecordingsStack.empty())
					action(RECORDING_ACTION_KEY_BACK);
			}
			break;
		case RECORDING_ACTION_REMOTE:
			mActivity.finish();
			break;
		case RECORDING_ACTION_SORT_DATE:
			if (mComparer == null || mComparer.compareBy != RECORDING_ACTION_SORT_DATE)
				mComparer =  new RecordingViewItemComparer(RECORDING_ACTION_SORT_DATE);
			else
				mComparer.ascending = ! mComparer.ascending;
			mRecordingAdapter.sort(mComparer);
			break;
		case RECORDING_ACTION_SORT_NAME:
			if (mComparer == null || mComparer.compareBy != RECORDING_ACTION_SORT_NAME)
				mComparer =  new RecordingViewItemComparer(RECORDING_ACTION_SORT_NAME);
			else
				mComparer.ascending = ! mComparer.ascending;
			mRecordingAdapter.sort(mComparer);
			break;
		}
	}
	
	public void action(final int action, int position) {
		if (mUpdateThread != null)
			mUpdateThread.interrupt();

		final RecordingViewItem item = mRecordingAdapter.getItem(position);
		
		switch (action) {
		case RECORDING_ACTION_DELETE:
			mRecordingTask = new RecordingDeleteTask();
			mRecordingTask.execute(item);
			break;
		case RECORDING_ACTION_INFO:
			if (item.isFolder) {
				// --- save current items ---
				RecordingViewItemList list = new RecordingViewItemList();
				for (int i = 0; i < mRecordingAdapter.getCount(); i++)
					list.add(mRecordingAdapter.getItem(i));
				mRecordingsStack.add(list);

				// --- fill adapter ---
				mRecordingAdapter.clear();
				for (int i = 0; i < item.folderItems.size(); i++)
					mRecordingAdapter.add(item.folderItems.get(i));
				
				// --- apply last sort criterion ---
				mRecordingAdapter.sort(mComparer);
			}
			else {
				mRecordingTask = new RecordingInfoTask();
				mRecordingTask.execute(item);
			}
			break;
			
		case RECORDING_ACTION_PLAY:
		case RECORDING_ACTION_PLAY_START:
			mRecordingTask = new RecordingPlayTask(action == RECORDING_ACTION_PLAY_START);
			mRecordingTask.execute(item);
			break;
		}
	}
	
	public String getTitle(int position) {
		RecordingViewItem item = mRecordingAdapter.getItem(position);
		return item.title;
	}

	private OnItemClickListener getOnItemClickListener() {
		return new OnItemClickListener() {
			public void onItemClick(AdapterView<?> listView, View v,
					int position, long ID) {
				action(RECORDING_ACTION_INFO, position);
			}
		};
	}

	public boolean isFolder(int position) {
		RecordingViewItem item = mRecordingAdapter.getItem(position);
		return item.isFolder;
	}

	public void onPause() {
		MyLog.v(TAG, "onPause");
		if (mUpdateThread != null) {
			mUpdateThread.interrupt();
			db.close();
		}
		if (mRecordingTask != null) {
			mRecordingTask.cancel(true);
			mRecordingTask = null;
		}
	}
	
	public void onResume() {
		MyLog.v(TAG, "onResume");
		if (mUpdateThread != null)
			mUpdateThread = new RecordingIdUpdateThread(mThreadHandler);
	}
	
	@Override
	public void run() {
		try {
			Recordings recordings = new Recordings(db);
			for(RecordingViewItem recordingViewItem: recordings.getItems()) {
				mRecordingViewItems.add(recordingViewItem);
			}
			mThreadHandler.sendMessage(Messages.obtain(Messages.MSG_DONE));
		} catch (IOException e) {
			MyLog.v(TAG, "ERROR new Recordings(): " + e.toString());
			lastError = e.toString();
			mThreadHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
		}
	}
	
	private void setRecordingAdapter(RecordingAdapter adapter, ListView listView) {
		action(RECORDING_ACTION_SORT_NAME);
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(getOnItemClickListener());
		listView.setSelected(true);
		listView.setSelection(0);
		mActivity.registerForContextMenu(listView);
		mUpdateThread = new RecordingIdUpdateThread(mThreadHandler);
	}

	private class RecordingAdapter extends ArrayAdapter<RecordingViewItem> {
		private final Activity mActivity;
		
		private final static int recordingtitelSize = 20,
								 recordingdefaultSize = 15;
		
		private class ViewHolder {
			private LinearLayout folder;
			private RelativeLayout recording;
			private TextView foldertitle;
			private TextView date;
			private ImageView state;
			private TextView title;
		}
		
		public RecordingAdapter(Activity activity, ArrayList<RecordingViewItem> recording) {
			super(activity, R.layout.recordings_item, recording);
			mActivity = activity;
		}
		
		public View getView(int position, View convertView, ViewGroup parent) {
			View row;
			if (convertView == null) {
				LayoutInflater inflater = mActivity.getLayoutInflater();
				row = inflater.inflate(R.layout.recordings_item, null);
				
				ViewHolder vh = new ViewHolder();
				vh.folder = (LinearLayout) row.findViewById(R.id.recording_folder);
				vh.recording = (RelativeLayout) row.findViewById(R.id.recording_recording);
				vh.foldertitle = (TextView) row.findViewById(R.id.recording_foldertitle);
				vh.date = (TextView) row.findViewById(R.id.recording_date);
				vh.state = (ImageView) row.findViewById(R.id.recording_stateimage);
				vh.title = (TextView) row.findViewById(R.id.recording_title);
				
				vh.foldertitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP,recordingtitelSize + Preferences.textSizeOffset);
				vh.date.setTextSize(TypedValue.COMPLEX_UNIT_DIP,recordingdefaultSize + Preferences.textSizeOffset);
				vh.title.setTextSize(TypedValue.COMPLEX_UNIT_DIP,recordingtitelSize + Preferences.textSizeOffset);
				
				row.setTag(vh);
				
				if (Preferences.blackOnWhite && row instanceof ViewGroup)
					setTextColor((ViewGroup)row);
			} else {
				row = convertView;
			}
			
			RecordingViewItem item = this.getItem(position);
			ViewHolder vh = (ViewHolder) row.getTag();
			
			if (item.isFolder) {
				vh.folder.setVisibility(View.VISIBLE);
				vh.recording.setVisibility(View.GONE);

				vh.foldertitle.setText(item.title);
			}
			else {
				vh.folder.setVisibility(View.GONE);
				vh.recording.setVisibility(View.VISIBLE);

				calendar.setTimeInMillis(item.recording.date * 1000);
				vh.date.setText(datetimeformatter.format(calendar.getTime()));

				if (! item.recording.isNew)
					vh.state.setImageResource(R.drawable.presence_online);
				else
					vh.state.setImageDrawable(null);
				
				vh.title.setText(item.recording.title);
			}
			return row;
		}
	}
	
	private class RecordingDeleteTask extends RecordingInfoTask {
		
		@Override
		protected String doIt() {
			try {
				String s = mConnection.doThis("DELR " + mRecording.number + "\n");
				
				if (s != null && s.regionMatches(0, "250 ", 0, 4)) {
					return "";
				}
				else {
					return s.replace("\n", "");
				}
			} catch (IOException e) {
				MyLog.v(TAG, "ERROR RecordingDeleteTask: " + e.toString());
				return null;
			}
		}
		
		@Override
		protected void onPostExecute(String result) {
			if (result != null && result == "") {
				mRecordingAdapter.remove(mRecordingViewItem);
				if (! mRecordingsStack.empty()) {
					RecordingViewItemList list = mRecordingsStack.lastElement();
					for (int i = 0; i < list.size(); i++)
						if (list.get(i).isFolder) {
							list.get(i).folderItems.remove(mRecordingViewItem);
						}
				}
			}
			super.onPostExecute(result);
		}
	}
	
	private class RecordingIdUpdateThread extends Thread {
		private final Handler mHandler;
		
		public RecordingIdUpdateThread(Handler handler) {
			mHandler = handler;
			start();
		}
		
		@Override
		public void run() {
			MyLog.v(TAG, "UpdateThread started");
			mHandler.sendMessage(Messages.obtain(Messages.MSG_TITLEBAR_PROGRESS_SHOW));
			
			if (Preferences.deleteRecordingIds && ! Preferences.useInternet) {
				Recordings.clearIds(db);
				for(Recording recording: mRecordingViewItems.getAllRecordings()) {
					recording.setInfoId(null);
				}
				Preferences.deleteRecordingIds = false;
				Preferences.store();
			}
			
			Connection connection = null;
			try {
				connection = new Connection();

				for (Recording recording: mRecordingViewItems.getAllRecordings()) {
					if (isInterrupted()) {
						MyLog.v(TAG, "UpdateThread interrupted");
						return;
					}
					if (recording.getInfoId() == null) {
						RecordingInfo info = VdrCommands.getRecordingInfo(recording.number);
						if (isInterrupted()) {
							MyLog.v(TAG, "UpdateThread interrupted");
							return;
						}
						recording.setInfoId(info.id);
						MyLog.v(TAG, recording.id + " --> " + info.id);
					}
				}
				
				if (Preferences.doRecordingIdCleanUp) {
					Recordings.deleteUnusedIds(db, mRecordingViewItems.getAllRecordings());
					Preferences.doRecordingIdCleanUp = false;
				}
			} catch (IOException e) {
				MyLog.v(TAG, e.toString());
			} finally {
				if (connection != null)
					connection.closeDelayed();
				mHandler.sendMessage(Messages.obtain(Messages.MSG_TITLEBAR_PROGRESS_DISMISS));
			}
			MyLog.v(TAG, "UpdateThread finished");
		}
	}

	private class RecordingInfoTask extends AsyncTask<RecordingViewItem, Void, String> {
		protected Connection mConnection;
		protected Recording mRecording;
		protected RecordingViewItem mRecordingViewItem;
		protected RecordingInfo mInfo;
		
		protected void onPreExecute() {
			mHandler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_SHOW, R.string.searching));
		}

		@Override
		protected String doInBackground(RecordingViewItem... params) {
			mRecordingViewItem = params[0];
			mRecording = mRecordingViewItem.recording;
			try {
				mConnection = new Connection();
				mInfo = VdrCommands.getRecordingInfo(mRecording.number);
				MyLog.v(TAG, "MD5: " + mRecording.getInfoId() + " --- " + mInfo.id);
				
				if (mRecording.getInfoId() != null && mRecording.getInfoId().compareTo(mInfo.id) == 0) {
					if (mRecording.number < 0)
						return mActivity.getString(R.string.rec_not_found);
					else
						return doIt();
				} else {
					onProgressUpdate();
					mRecordingViewItems.update(mConnection);
					ArrayList<Recording> allRecordings = mRecordingViewItems.getAllRecordings();
					int index = Collections.binarySearch(allRecordings, mRecording);
					if (index >= 0) {
						Recording foundRecording = allRecordings.get(index);
						if (foundRecording.number < 0)
							return mActivity.getString(R.string.rec_not_found);
						else
							return doIt(foundRecording);
					} else {
						return mActivity.getString(R.string.rec_not_found);
					}
				} 
			} catch (IOException e) {
				MyLog.v(TAG, "ERROR RecordingInfoTask: " + e.toString());
				return null;
			} finally {
				if (mConnection != null)
					mConnection.closeDelayed();
			}
		}
		
		protected String doIt() {
			Intent intent = new Intent(mActivity, RecordingInfoActivity.class);
			intent.putExtra("recordingnumber", mRecording.number);
			mActivity.startActivityForResult(intent, 1);
			return "";
		}

		protected String doIt(Recording recording) throws IOException {
			mInfo = VdrCommands.getRecordingInfo(recording.number);
			recording.setInfoId(mInfo.id);
			mRecording = recording;
			return doIt();
		}
		
		protected void onProgressUpdate(Void... values) {
			mHandler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_UPDATE, R.string.updating));
		}
		
		protected void onPostExecute(String result) {
			mHandler.sendMessage(Messages.obtain(Messages.MSG_PROGRESS_DISMISS));
			if (result != null) {
				mRecordingAdapter.notifyDataSetChanged();
				if (result != "")
					Toast.makeText(mActivity, result, Toast.LENGTH_LONG).show();
			} else {
				mHandler.sendMessage(Messages.obtain(Messages.MSG_VDR_ERROR));
			}
		}
	}
	
	private class RecordingPlayTask extends RecordingInfoTask {
		private boolean mFromBeginning = false;
		
		public RecordingPlayTask(boolean fromBeginning) {
			mFromBeginning = fromBeginning;
		}
		
		protected String doIt() {
			String command;
			
			if (mFromBeginning)
				command = "PLAY " + mRecording.number + " begin\n";
			else
				command = "PLAY " + mRecording.number + "\n";
		
			try {
				mConnection.doThis(command);
				mConnection.close();
				mActivity.finish();
				return "";
			} catch (IOException e) {
				return null;
			}
		}
	}
	
	private class RecordingViewItemList extends ArrayList<RecordingViewItem> {
		private static final long serialVersionUID = -4365980877168388915L;
		
		private ArrayList<Recording> mRecordings = new ArrayList<Recording>();
		private boolean mIsSorted = false;
		
		@Override
		public boolean add(RecordingViewItem recordingViewItem) {
			boolean result = super.add(recordingViewItem);
			if (result && recordingViewItem.isFolder) 
				initRecordings(recordingViewItem.folderItems);
			else {
				if (mRecordings.indexOf(recordingViewItem.recording) < 0)
					mRecordings.add(recordingViewItem.recording);
			}
			mIsSorted = false;
			return result;
		}
		
		public ArrayList<Recording> getAllRecordings() {
			if (! mIsSorted) {
				Collections.sort(mRecordings);
				mIsSorted = true;
			}
			return mRecordings;
		}
		
		private void initRecordings(ArrayList<RecordingViewItem> list) {
			for (int i = 0; i < list.size(); i++) {
				RecordingViewItem item = list.get(i);
				if (item.isFolder)
					initRecordings(item.folderItems);
				else {
					if (mRecordings.indexOf(item.recording) < 0)
						mRecordings.add(item.recording);
				}
			}
		}
		
		public void update(Connection connection) throws IOException {
			MyLog.v(TAG, "updateRecordings started");
			// --- get recordings from vdr ---
			Recordings recordings = new Recordings(connection, db);
			RecordingViewItemList recordingViewItems = new RecordingViewItemList();
			for(RecordingViewItem recordingViewItem: recordings.getItems())
				recordingViewItems.add(recordingViewItem);
			ArrayList<Recording> allRecordings = recordingViewItems.getAllRecordings();
			// --- update recordings ---
			for (int i = 0; i < mRecordings.size(); i++) {
				Recording dst = mRecordings.get(i);
				int index = Collections.binarySearch(allRecordings, dst);
				if (index >= 0) {
					Recording src = allRecordings.get(index);
					if (dst.number != src.number) {
						MyLog.v(TAG, dst.fullTitle + " " + dst.number + " -> " + src.number);
					}
					dst.number = src.number;
					dst.isNew = src.isNew;
				}
				else
					dst.number = -1;
			}
			MyLog.v(TAG, "updateRecordings finished");
		}
	}

	private class RecordingViewItemComparer implements java.util.Comparator<RecordingViewItem> {
		public boolean ascending = true;
		public final int compareBy;
		
		public RecordingViewItemComparer(int compareBy) {
			this.compareBy = compareBy;
		}
		
		public int compare(RecordingViewItem a, RecordingViewItem b) {
			switch (compareBy) {
			case RECORDING_ACTION_SORT_DATE:
				return compareByDate(a, b);
			case RECORDING_ACTION_SORT_NAME:
				return compareByName(a, b);
			}
			return 0;
		}

		public int compareByDate(RecordingViewItem a, RecordingViewItem b) {
			if (a.isFolder || b.isFolder) {
				if (a.isFolder && ! b.isFolder)
					return -1;
				else if (! a.isFolder && b.isFolder)
					return 1;
				else
					return a.title.compareToIgnoreCase(b.title);
			}
			else {
				int result;
				Long l = a.recording.date;
				result = l.compareTo(b.recording.date);
				if (!ascending && result != 0) {
					result = result - result * 2;
				}
				return result;
			}
		}
		
		public int compareByName(RecordingViewItem a, RecordingViewItem b) {
			if (a.isFolder || b.isFolder) {
				if (a.isFolder && ! b.isFolder)
					return -1;
				else if (! a.isFolder && b.isFolder)
					return 1;
				else
					return a.title.compareToIgnoreCase(b.title);
			}
			else {
				int result = a.title.compareToIgnoreCase(b.title);
				if (result == 0) {
					Long l = a.recording.date;
					result = l.compareTo(b.recording.date);
				}
				if (!ascending && result != 0) {
					result = result - result * 2;
				}
				return result;
			}
		}
	}
}