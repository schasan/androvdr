/*
 *      Copyright (C) 2005-2009 Team XBMC
 *      http://xbmc.org
 *
 *  This Program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2, or (at your option)
 *  any later version.
 *
 *  This Program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with XBMC Remote; see the file license.  If not, write to
 *  the Free Software Foundation, 675 Mass Ave, Cambridge, MA 02139, USA.
 *  http://www.gnu.org/copyleft/gpl.html
 *
 */

package de.androvdr;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.widget.Toast;

public class Wol {
	
	private final transient Logger logger = LoggerFactory.getLogger(Wol.class);
	
	private Context context;
	
	public String lastError;
	
	public Wol(Context c){
		context = c;
	}
	
	public boolean sendMacigPaket(String ipAddr, String macStr,	boolean sendBroadcast) {
		lastError = "";

		final int PORT = 9;
		try {
			byte[] macBytes = getMacBytes(macStr);

			byte[] bytes = new byte[6 + 16 * macBytes.length];
			for (int i = 0; i < 6; i++) {
				bytes[i] = (byte) 0xff;
			}
			for (int i = 6; i < bytes.length; i += macBytes.length) {
				System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
			}

			InetAddress address;
			address = InetAddress.getByName(ipAddr);
			logger.debug("Address: {}", address.getHostAddress());
			if (sendBroadcast == true) {
				// Umwandeln in IP-Broadcast-Adresse
				byte[] adr = address.getAddress();
				if (adr.length != 4) {
					throw new IllegalArgumentException("Invalid IP4 address.");
				}
				adr[3] = -1; // Broadcast
				address = InetAddress.getByAddress(adr);
				logger.debug("Broadcastaddress: {}", address.getHostAddress());
			}
			DatagramPacket packet = new DatagramPacket(bytes, bytes.length,	address, PORT);
			DatagramSocket socket = new DatagramSocket();
			socket.setBroadcast(true);
			socket.send(packet);
			socket.close();
			return true;
		} catch (Exception e) {
			if (context != null)
				Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show();
			lastError = e.toString();
			logger.error("Couldn't send WOL packet", e.toString());
			return false;
		}
	}    
    
    private static byte[] getMacBytes(String macStr) throws IllegalArgumentException {
        byte[] bytes = new byte[6];
        String[] hex = macStr.split("(\\:|\\-)");
        if (hex.length != 6) {
            throw new IllegalArgumentException("Invalid MAC address.");
        }
        try {
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) Integer.parseInt(hex[i], 16);
            }
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex digit in MAC address.");
        }
        return bytes;
    }

}
