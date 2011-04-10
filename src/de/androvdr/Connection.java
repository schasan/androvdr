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
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Connection {
	private static final String TAG = "Connection";
	
	private static final int SEMAPHORE_MAX_WAIT = 5 * 1000;
	
	private static TcpClient mTcpClient = null;
	private final Semaphore sem = new Semaphore(1, true);
	public boolean isClosed = false;
	
	// dient dazu, das Socket nach einer bestimmten Zeit zu schliessen, um die
	// TCP-Verbindung fuer andere Client's frei zu machen
	private static final long waitTimeKillConnection = 10000; // Wartezeit, bis TCP-Verbindung geschlossen wird
	private static Timer t = new Timer(); // der Timer fuer die Task killConnection()
	private static TimerTask task;
	
	public Connection() throws IOException {
	    try {
			sem.tryAcquire(SEMAPHORE_MAX_WAIT, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			MyLog.v(TAG, "ERROR: " + e.toString());
			throw new IOException(e.toString());
		}
	}
	
	public void close() {
		if (isClosed)
			return;
		
		if(task != null)
			removeTask();

		if(mTcpClient != null) {
			try {
				MyLog.v(TAG,"Breche Tcp-Verbindung in closeConnection() ab") ;
				mTcpClient.sendData("QUIT\n");
				mTcpClient.close();
			} catch (IOException e) {
				MyLog.v(TAG,"Fehler aufgetreten in closeConnection beim Beenden der TCP-Verbindung",e) ;
			} finally {
				sem.release();
				isClosed = true;
				mTcpClient = null;
			}
		}
	}
	
	public void closeDelayed() {
		if (isClosed)
			return;
		
		sem.release();
		isClosed = true;

		if(task == null) {
			MyLog.v(TAG, "Starte task in closeDelayed()");
			t.schedule(killConnection(), waitTimeKillConnection);
		}
	}
	
	public String doThis(String doit) throws IOException {
		// sende das gewuenschte Kommando an den VDR
		String message = "";
		try {
			getTcpClient().sendData(doit);
			message = getTcpClient().receiveData();
		} catch (IOException e) {
			MyLog.v(TAG, "Fehler beim Senden des Kommandos:" + e.toString());
			close();
			throw e;
		}
		closeDelayed();
		return message;
	}
	
	private TcpClient getTcpClient() throws IOException {
		if (isClosed)
			throw new IOException("Connection is closed");

		if (task != null)
			removeTask();
		
		if (mTcpClient == null)
			try {
				mTcpClient = new TcpClient();
			} catch (IOException e) {
				MyLog.v(TAG, "ERROR connection(): " + e.toString());
				close();
				throw e;
			}
		return mTcpClient;
	}
	
	public void kill() {
		close();
	}
	
	// installiert einen TimerTask, der eine bestehende Verbindung zum VDR beendet
	private static TimerTask killConnection() {
		removeTask();
		task = new TimerTask() {
			public void run() {
				if(mTcpClient == null)
					return;
				try {
					MyLog.v(TAG,"Beende Tcp-Verbindung in killConnection()") ;
					mTcpClient.sendData("QUIT\n");
					mTcpClient.close();
				} catch (IOException e){
					MyLog.v(TAG,"Fehler aufgetreten in killConnection()",e) ;
				} finally {
					mTcpClient = null;
				}
			}
		};
		return task;
	}	

	public void open() throws IOException {
		if (isClosed) {
		    try {
				sem.tryAcquire(SEMAPHORE_MAX_WAIT, TimeUnit.MILLISECONDS);
				isClosed = false;
			} catch (InterruptedException e) {
				MyLog.v(TAG, "ERROR: " + e.toString());
				throw new IOException(e.toString());
			}
		}
	}
	
	public String readLine() throws IOException {
		try {
			return getTcpClient().readLine();
		} catch (IOException e) {
			close();
			throw e;
		}
	}
	
	// cancelt die installierte TimerTask, damit lange Datentransfer mit dem VDR nicht unterbrochen werden
	public static void removeTask() {
		if (task != null) {
			task.cancel();
			task = null;
			MyLog.v(TAG, "Remove Task killConnection() from timer");
		}
	}
	
	public String receiveData() throws IOException {
		try {
			return getTcpClient().receiveData();
		} catch (IOException e) {
			close();
			throw e;
		}
	}
	
	public void sendData(String data) throws IOException {
		try {
			getTcpClient().sendData(data);
		} catch (IOException e) {
			close();
			throw e;
		}
	}
}
