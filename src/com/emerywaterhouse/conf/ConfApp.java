/**
 *
 */
package com.emerywaterhouse.conf;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

import javax.jms.JMSException;
import javax.naming.NamingException;

import com.emerywaterhouse.server.ProcessApplication;
import com.emerywaterhouse.server.ProcessServer;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;

public class ConfApp extends ProcessApplication 
{
   public static final String exchange = "b2bcomm";   
   public static final String confQueue = "ha.conf.all";

   private ArrayList<BodProcessor> m_BodProcs;

   //
   // message broker vars
   private Channel m_Channel;      
   private Connection m_Cnx;

   /**
    * Default Constructor
    */
   public ConfApp() 
   {
      super();

      m_Name = "ConfApp";
      m_MaxProcCount = 10;
      m_BodProcs = new ArrayList<BodProcessor>();
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
      
      if ( m_BodProcs != null ) {
         m_BodProcs.clear();
         m_BodProcs = null;
      }

      super.finalize();
   }

   /**
    * Adds a BodProcessor reference to the list of running bod processors.
    *
    * @param bodProc  The running BodProcessor instance.
    */
   public synchronized void addBodProc(BodProcessor bodProc) 
   {
      if ( bodProc != null ) {
         synchronized ( m_BodProcs ) {
            m_BodProcs.add(bodProc);
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
         ProcessServer.log.error("[ConfApp] Failed to close RMQ Connections.", ex);
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
            
            m_Cnx = cnxFactory.newConnection(getClientId());
            m_Channel = m_Cnx.createChannel();
                                    
            //
            // this sets the maximum number of unacknowledged messages to 10 across the entire channel
            // it will act as a speed bump if we're not doing our acknowledgments correctly
            m_Channel.basicQos(10);
            m_Channel.basicConsume(ConfApp.confQueue, false, getClientId(), new ConfQueueConsumer(this, null, m_Channel));
         } 
         
         catch ( Exception ex ) {
            ProcessServer.log.error("[ConfApp]", ex);
         }
      }
      else {
         ProcessServer.log.fatal("[ConfApp] unable to connect to the message broker.  Host was missing");
         ProcessServer.getInstance().notifyMis("[ConfApp] Fatal error; unable to connect to the message broker.  Host was missing");
      }
   }
   
   /**
    * 
    * @return An instance of the ConfApp RMQ channel.
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
      return getName();
   }

   /**
    * Overrides the base class method to provide the number of currently
    * running processes.
    */
   @Override
   public synchronized int getProcCount() 
   {
      return m_BodProcs.size();
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
      StringBuffer buf = new StringBuffer(1024);
      BodProcessor proc = null;
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
      // Display all the currently running jobs.
      buf.append("<table style=\"font-family: Arial, Helvetica, sans-serif; ");
      buf.append("font-size: 12px; border-width: 0; border-style: none; width: 1500px\">\r\n");

      buf.append("<tr>\r\n");
      buf.append("<td width=\"200px\"><b>Process ID</b></td>\r\n");
      buf.append("<td width=\"200px\"><b>Proc Name</b></td>\r\n");
      buf.append("<td width=\"600px\"><b>Current Action</b></td>\r\n");
      buf.append("<td width=\"100px\"><b>Operations</b></td>\r\n");
      buf.append("</tr>\r\n");

      synchronized ( m_BodProcs ) {
         for ( int i = 0; i < m_BodProcs.size(); i++ ) {
            proc = m_BodProcs.get(i);
            
            buf.append("<tr>\r\n");
            buf.append(String.format("<td width=\"200px\">%s</td>\r\n", proc.getId()));
            buf.append(String.format("<td width=\"200px\">%s</td>\r\n", proc.getName()));
            buf.append(String.format("<td width=\"600px\">%s</td>\r\n", proc.getProcessStatus()));
            buf.append("<td width=\"100px\">");
            buf.append("<a href=\"");
            buf.append(ProcessServer.getInstance().getJmxURL());
            buf.append("/InvokeAction//ProcessServerMgmt%3Aname%3DServerMgmt/action=stopJob?action=stopJob");
            buf.append("&p1%2Blong=").append(getId()).append("&p2%2Blong=").append(proc.getId()).append("\">Stop Job</a>");
            buf.append("&nbsp;");
            buf.append("<a href=\"");
            buf.append(ProcessServer.getInstance().getJmxURL());
            buf.append("/InvokeAction//ProcessServerMgmt%3Aname%3DServerMgmt/action=startJob?action=startJob");
            buf.append("&p1%2Blong=").append(getId()).append("&p2%2Blong=").append(proc.getId())
            .append("\">Start Job</a>");
            buf.append("</td>\r\n");
            buf.append("</tr>\r\n");

            //
            // Reference:
            // JMX URLs for stopping and starting the jobs
            // http://localhost:9190/InvokeAction//ProcessServerMgmt%3Aname%3DServerMgmt/action=stopJob?action=stopJob&p1%2Blong=1&p2%2Blong=1
            // http://localhost:9190/InvokeAction//ProcessServerMgmt%3Aname%3DServerMgmt/action=startJob?action=startJob&p1%2Blong=1&p2%2Blong=1
         }
      }

      buf.append("</table>");
      buf.append("<br>");
      return buf.toString();
   }

   /**
    * Handles checking and processing each document pulled from the message
    * broker. The processing can be threaded at the process level or it can be
    * single threaded or a mixture.
    *
    * @param bod - The BOD from the broker to process.
    */
   public void processBod(String bod) 
   {
      BodProcessor proc = null;
      StringBuffer msg = new StringBuffer();
      String docName = "";
      VTDGen vg = new VTDGen();
      VTDNav vn = null;

      try {
         if (bod == null || bod.length() == 0)
            throw new Exception("missing or invalid bod, unable to process.");

         vg.setDoc(bod.getBytes());
         vg.parse(true);
         vn = vg.getNav();
         docName = vn.toString(vn.getCurrentIndex());

         proc = BodProcessorFactory.getInstance().getProcessor(docName);

         if ( proc != null ) {
            proc.setApplication(this);
            proc.setBOD(bod);

            //
            // If the max procs allowed is set to 0 then this will be run
            // in non threaded mode, otherwise multi-threaded up to max
            // count.
            proc.startProcessing(m_MaxProcCount > 0);
         } 
         else {
            throw new Exception(String.format("[%s] Unable to instantiate any processor for %s", m_Name, docName));
         }
      }

      catch ( Exception ex ) {
         ProcessServer.log.error(String.format("[%s]", m_Name), ex);

         msg.setLength(0);
         msg.append(String.format("[%s] The following exception occurred in the processBod method:\r\n", m_Name));
         msg.append(String.format("%s - %s\r\n", ex.getClass().getName(), ex.getMessage()));
         msg.append("See the log for the stack trace.  This error requires attention; the BOD is attached:\r\n\r\n");
         msg.append(bod);

         ProcessServer.getInstance().notifyMis(msg.toString());
      }

      finally {
         vg = null;
         vn = null;
         docName = null;
         proc = null;
         msg = null;
      }
   }

   /**
    * Removes a BodProcessor object from the list.
    *
    * @param bodProc The BodProcessor to remove.
    */
   public synchronized void remBodProc(BodProcessor bodProc) 
   {
      if ( bodProc != null ) {
         synchronized ( m_BodProcs ) {
            for ( int i = 0; i < m_BodProcs.size(); i++ ) {
               if ( bodProc.getId() == m_BodProcs.get(i).getId() ) {
                  m_BodProcs.remove(i);
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
      int procCount = m_BodProcs.size();

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
            ProcessServer.log.info(String.format("[%s] waiting for %d processes to finish", m_Name, procCount));
            Thread.sleep(1000);
            procCount = m_BodProcs.size();
         }

         catch (InterruptedException ex) {
            // TODO add some logging or just ignore.
         }
      }
   }
}
