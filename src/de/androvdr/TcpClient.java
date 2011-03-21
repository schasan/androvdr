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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

import de.androvdr.devices.VdrDevice;

public class TcpClient {
	
	private static final String TAG = "TcpClient";
	
	private Socket socket = null;
	private BufferedWriter bw = null;
	private BufferedReader br = null;
    	
    public TcpClient() throws IOException {
    	VdrDevice vdr = Preferences.getVdr();
    	if (vdr == null) {
    		throw new IOException("No VDR defined");
    	}
    	String hostname;
    	int port,timeout;
    	if(Preferences.useInternet == true){
    		hostname = "localhost";
    		port = vdr.remote_local_port;
    		timeout = vdr.remote_timeout;
    		MyLog.v(TAG,"Es wurden die Interneteinstellungen gewaehlt");
    	}
    	else{
    		hostname = vdr.getIP();
    		port = vdr.getPort();
    		timeout = 7500;
    		MyLog.v(TAG,"Es wurden lokale Netzwerkeinstellungen gewaehlt");
    	}
    	connect(hostname, port, timeout);
    }  
    
    private void connect(String hostname, int port, int timeout) throws IOException {
     	socket = new Socket();
    	SocketAddress endpoint = new InetSocketAddress(hostname, port);
    	socket.connect(endpoint, 1000);
    	socket.setSoTimeout(timeout);
    	
    	VdrDevice vdr = Preferences.getVdr();
    	bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), vdr.characterset), 8192);
    	br = new BufferedReader(new InputStreamReader(socket.getInputStream(), vdr.characterset), 8192);
    	MyLog.v(TAG,"TcpClient instanziert,Socket geoeffnet");
    	receiveData();
    }

    public void sendData(String data)throws IOException {
    	bw.write(data);
    	bw.flush();
    	MyLog.v(TAG,"Schreibe Daten in Socket:"+data);
    }
    
    public String readLine() throws IOException, SocketTimeoutException {
    	String s = "";
		try {
			s = br.readLine();
			MyLog.v("TcpClient","Lese Daten aus Socket:"+s);
		} catch (SocketTimeoutException e) {
			// wenn keine Daten mehr anliegen kommt ein Timeout
			MyLog.v(TAG, "ERROR: read timeout");
			throw e;
		}
		return s;
	}
    
    
    public String receiveData() throws IOException, SocketTimeoutException {
		StringBuffer sb = new StringBuffer();
		try {
			//MyLog.v("TcpClient", "lese Daten aus Tcp-Verbindung");
			while (true) {
				String s = br.readLine();
				MyLog.v(TAG,"lese Daten aus socket:"+s);
				sb.append(s);
				sb.append("\n");
				// Abruch mit 250 Angeforderte Aktion okay, beendet oder mit 221
				// VDR-Service schlie�t Sende-Kanal nach QUIT Befehl ansonsten mit Timeout
				if (       (s.regionMatches(0, "250 ", 0, 4))
						|| (s.regionMatches(0, "221",  0, 3))
						|| (s.regionMatches(0, "220 ", 0, 4))
						|| (s.regionMatches(0, "215 ", 0, 4))
						|| (s.regionMatches(0, "501 ", 0, 4))
						|| (s.regionMatches(0, "550 ", 0, 4)))
					break;
			}
		} catch (SocketTimeoutException e) {
			// wenn keine Daten mehr anliegen kommt ein Timeout
			MyLog.v(TAG, "ERROR: read timeout");
			throw e;
		}
		return sb.toString();
	}
    
    
    public void close() throws IOException {
    	bw.close();
    	br.close();
    	socket.close();
    }
    
}
