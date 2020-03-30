/**
 *
 */
package com.emerywaterhouse.fax;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeoutException;

import javax.naming.NamingException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import com.emerywaterhouse.server.ProcessApplication;
import com.emerywaterhouse.server.ProcessServer;

public class FaxApp extends ProcessApplication 
{
   public static final String exchange = "b2bcomm";   
   public static final String faxConfRouteKey = "b2bcomm.conf.fax";
   public static final String faxQueue = "ha.faxreq";
   private ArrayList<FaxProcessor> m_FaxProcs;

   //
   // message broker vars
   private Channel m_Channel;      
   private Connection m_Cnx;
   
   /**
    * Default constructor/
    */
   public FaxApp() 
   {
      super();

      m_Name = "FaxApp";
      m_MaxProcCount = 1;
      m_FaxProcs = new ArrayList<FaxProcessor>();
   }

   /**
    * Clean up anything we created.
    * 
    * @throws Throwable
    */
   @Override
   public void finalize() throws Throwable 
   {
      m_Thread = null;
            
      if ( m_FaxProcs != null ) {
         m_FaxProcs.clear();
         m_FaxProcs = null;
      }

      super.finalize();
   }

   /**
    * Adds a FaxProcessor reference to the list of running fax requests.
    *
    * @param faxProc The running FaxProcessor instance.
    */
   public synchronized void addFaxProc(FaxProcessor faxProc) 
   {
      if ( faxProc != null ) {
         synchronized ( m_FaxProcs ) {
            m_FaxProcs.add(faxProc);
         }
      }
   }

   /**
    * Closes the connections to the RMQ message broker.
    */
   private void closeRMQConnection() 
   {
      try {
         m_Channel.close();
         m_Cnx.close();
      } 
      
      catch ( IOException | TimeoutException ex ) {
         ProcessServer.log.error("[FaxApp] Failed to close RMQ Connections.", ex);
      }

      m_Cnx = null;
   }

   /**
    * Connects to the RMQ message broker.
    *
    * @throws NamingException
    * @throws JMSException
    * @throws UnknownHostException
    */
   private void connectToRMQ() throws NamingException, UnknownHostException 
   {
      ConnectionFactory cnxFactory = null;
      String user = System.getProperty("msgbroker.user");
      String passwd = System.getProperty("msgbroker.passwd");
      String host = System.getProperty("msgbroker.host");
      int port = Integer.parseInt(System.getProperty("msgbroker.port"));
   
      if ( host != null && host.length() > 0 ) {
         //
         // see https://www.rabbitmq.com/api-guide.html#recovery
         // user, passwd, host, port are set on the factory, vhost is left as default
         try {
            cnxFactory = new ConnectionFactory();
            cnxFactory.setAutomaticRecoveryEnabled(true);
            
            cnxFactory.setUsername(user);
            cnxFactory.setPassword(passwd);
            cnxFactory.setHost(host);
            cnxFactory.setPort(port);
            
            m_Cnx = cnxFactory.newConnection("FaxApp");
            m_Channel = m_Cnx.createChannel();
                                    
            //
            // this sets the maximum number of unacknowledged messages to 10 across the entire channel
            // it will act as a speed bump if we're not doing our acknowledgments correctly
            m_Channel.basicQos(10);
            m_Channel.basicConsume(FaxApp.faxQueue, false, getClientId(), new FaxQueueListener(this, null, m_Channel));
         } 
         
         catch ( Exception ex ) {
            ProcessServer.log.error("[FaxApp]", ex);
         }
      }
      else {
         ProcessServer.log.fatal("[FaxApp] unable to connect to the message broker.  Host was missing");
         ProcessServer.getInstance().notifyMis("[FaxApp] Fatal error; unable to connect to the message broker.  Host was missing");
      }
   }
   
   /**
    * 
    * @return An instance of the FaxApp RMQ channel.
    */
   public synchronized final Channel getChannel()
   {
      return m_Channel;
   }
   
   /**
    * @return The id for the connection to the message broker. Based on host
    *         name and proc monitor name.
    *
    * @throws UnknownHostException
    */
   private String getClientId() throws UnknownHostException 
   {
      return String.format("%s.%s", getName(), InetAddress.getLocalHost().getHostName());
   }

   /**
    * Gets the status information from each running fax request.
    *
    * @return HTML data that represents the status information from the
    *         currently running reports.
    */
   @Override
   public synchronized String getProcessStatus() 
   {
      SimpleDateFormat df = new SimpleDateFormat("MM/dd' 'HH:mm");
      Date date = null;
      long hour = 0;
      long min = 0;
      long sec = 0;
      StringBuffer buf = new StringBuffer(1024);
      FaxStatus status = null;
      String tmp = null;

      //
      // Display the name of the application and the ID of the application.
      buf.append("<br>");
      buf.append("<table style=\"font-family: Arial, Helvetica, sans-serif; ");
      buf.append("font-size: 12px; border-width: 0; border-style: none; width: 300px\">\r\n");
      buf.append("<tr>\r\n");
      buf.append(String.format("<td width=\"200px\"><b>App Name: %s</b></td>\r\n", getName()));
      buf.append(String.format("<td width=\"100px\"><b>App ID: %d</b></td>\r\n", getId()));
      buf.append("</tr>\r\n");
      buf.append("</table>\r\n");

      //
      //
      buf.append("<table style=\"font-family: Arial, Helvetica, sans-serif; ");
      buf.append("font-size: 12px; border-width: 0; border-style: none; width: 850px\">\r\n");

      buf.append("<tr>\r\n");
      buf.append("<td width=\"50px\"><b>PID</b></td>\r\n");
      buf.append("<td width=\"100px\"><b>Start Time</b></td>\r\n");
      buf.append("<td width=\"100px\"><b>Running Time</b></td>\r\n");
      buf.append("<td width=\"100px\"><b>Max Run Time</b></td>\r\n");
      buf.append("<td width=\"300px\"><b>Current Action</b></td>\r\n");
      buf.append("</tr>\r\n");

      //
      // Format the data elements for display on the html page
      synchronized ( m_FaxProcs ) {
         for (int i = 0; i < m_FaxProcs.size(); i++) {
            status = m_FaxProcs.get(i).getFaxStatus();
            new Date(status.startTime);

            //
            // Convert to seconds and then break out the hours and minutes.
            // If this is not
            // done then the seconds will accumulate past 60. So will the
            // minutes.
            sec = status.runTime / ProcessServer.second;

            if (sec >= 3600) {
               hour = sec / 3600;
               sec = sec - hour * 3600;
            }

            if (sec >= 60) {
               min = sec / 60;
               sec = sec - min * 60;
            }

            tmp = String.format("%02d:%02d:%02d", hour, min, sec);
            // maxRun = Double.toString(status.maxRunTime /
            // ProcessServer.hour) + " HRS";

            buf.append("<tr>");
            buf.append(String.format("<td width=\"50px\">%d</td>\r\n", status.id));
            buf.append(String.format("<td width=\"100px\">%s</td>\r\n", df.format(date)));
            buf.append(String.format("<td width=\"100px\">%s</td>\r\n", tmp));
            buf.append(String.format("<td width=\"100px\">%d</td>\r\n", 0));
            buf.append(String.format("<td width=\"300px\">%s</td>\r\n", status.currentAction));
            buf.append("</tr>\r\n");

            hour = 0;
            min = 0;
            sec = 0;
         }
      }

      buf.append("</table>");
      buf.append("<br>");

      return buf.toString();
   }
   
   /**
    * Utility function for determining the time in hours, minutes or seconds.
    *
    * @param seconds The time in seconds.
    * @param timeType The type of time unit needed.
    *
    * @return The time in the units specified or seconds if the unit is not one
    *         that matches.
    */
   @SuppressWarnings("unused")
   private long getTime(long seconds, int timeType) 
   {
      long time = 0;

      switch ( timeType ) {
         case ProcessServer.hour:
            if ( seconds >= 3600 ) {
               time = seconds / 3600;
            }
            break;
   
         case ProcessServer.minute:
            if ( seconds >= 60 ) {
               time = seconds / 60;
            }
            break;
   
         default:
            time = seconds;
      }

      return time;
   }
      
   /**
    * Overrides the base class method to provide the number of currently
    * running processes.
    */
   @Override
   public synchronized int getProcCount() 
   {
      return m_FaxProcs.size();
   }

   /**
    * Removes a FaxProcessor object from the list of running reports.
    *
    * @param faxProc The faxProcessor to remove.
    */
   public synchronized void remFaxProc(FaxProcessor faxProc) 
   {
      if ( faxProc != null ) {
         synchronized ( m_FaxProcs ) {
            for ( int i = 0; i < m_FaxProcs.size(); i++ ) {
               if ( faxProc.getId() == m_FaxProcs.get(i).getId() ) {
                  m_FaxProcs.remove(i);
               }
            }
         }
      }
   }

   /**
    * Creates the internal thread and starts the monitor
    *
    * @throws Exception
    */
   @Override
   public void start() throws Exception 
   {
      m_Status = ProcessServer.init;
      connectToRMQ();
      super.start();
   }

   /**
    * Stops the processing and closes the queue connection.
    */
   @Override
   public void stop() 
   {
      int procCount = m_FaxProcs.size();

      //
      // Call this first so the status is set.
      super.stop();
      closeRMQConnection();

      //
      // We need to let the processes finish because they share some JMS
      // connection information with
      // this application
      while ( procCount > 0 ) {
         try {
            ProcessServer.log.info(String.format("waiting for %d fax processes to finish", procCount));
            Thread.sleep(1000);
            procCount = m_FaxProcs.size();
         }

         catch ( InterruptedException ex ) {
            ProcessServer.log.warn("[FaxApp] " + ex.getMessage());
         }
      }
   }
   
}
