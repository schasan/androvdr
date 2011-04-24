package de.androvdr.svdrp;


import java.io.IOException;
import java.util.TimerTask;

import org.hampelratte.svdrp.Command;
import org.hampelratte.svdrp.Connection;
import org.hampelratte.svdrp.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class VDRConnection {
    
    private static transient Logger logger = LoggerFactory.getLogger(VDRConnection.class.getName());

    private static Connection connection;
    private static Integer syncer = 0;
    
    public static String host;

    public static int port;

    public static int timeout = 500;
    
    public static String charset;
    
    /**
     * If set, the connection will be kept open for some time,
     * so that consecutive request will be much faster
     */
    public static boolean persistentConnection = true;
    
    private static java.util.Timer timer;
    
    private static long lastTransmissionTime = 0;
    
    /**
     * The time in ms, the connection will be kept alive after
     * the last request. {@link #persistentConnection} has to be
     * set to true.
     */
    private static final int CONNECTION_KEEP_ALIVE = 10000;
    
    /**
     * Sends a SVDRP command to VDR and returns a response object, which represents the vdr response
     * @param cmd The SVDRP command to send
     * @return The SVDRP response or null, if the Command couldn't be sent
     */
	public synchronized static Response send(final Command cmd) {
		Response res = null;
		try {
			
			/*
			 *  prevent ConnectionCloser to close the connection
			 */
			synchronized (syncer) {
				if (connection == null) {
					logger.trace("New connection");
					connection = new Connection(host, port, timeout, charset);
				} else {
					logger.trace("old connection");
					lastTransmissionTime = System.currentTimeMillis();
				}
				logger.debug("--> {}", cmd.getCommand());
			}

			res = connection.send(cmd);
			lastTransmissionTime = System.currentTimeMillis();
			if (!persistentConnection) {
				connection.close();
				connection = null;
			} else {
				if (timer == null) {
					logger.debug("Starting connection closer");
					timer = new java.util.Timer("SVDRP connection closer");
					timer.schedule(new ConnectionCloser(), 0, 1000);
				}
			}
			logger.debug("<-- {}", res.getMessage());
		} catch (Exception e1) {
			res = new ConnectionProblem();
			logger.error(res.getMessage(), e1);
		}

		return res;
	}
    
    static class ConnectionCloser extends TimerTask {
        @Override
        public void run() {
        	synchronized (syncer) {
                if (connection != null && (System.currentTimeMillis() - lastTransmissionTime) > CONNECTION_KEEP_ALIVE) {
                    logger.debug("Closing connection");
                    try {
                        close();
                    } catch (IOException e) {
                        logger.error("Couldn't close connection", e);
                    }
                }
			}
        }
    }
    
    public static void close() throws IOException {
        if(connection != null) {
            connection.close();
            connection = null;
        }
        if(timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}