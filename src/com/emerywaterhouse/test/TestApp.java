/**
 * Unit test class.
 */
package com.emerywaterhouse.test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.jms.Connection;

import com.emerywaterhouse.server.ConfLoader;
import com.emerywaterhouse.server.ProcessApplication;
import com.emerywaterhouse.server.ProcessServer;

public class TestApp extends ProcessApplication implements ConfLoader
{
         
   public TestApp()
   {
      super();
      
      m_Name = "TestApp";
      m_MaxProcCount = 1;
   }
   
   /**
    * Clean up anything we created.
    * @throws Throwable
    */
   @Override
   public void finalize() throws Throwable
   {
      m_Thread = null;
      
      super.finalize();
   }
     
   
   /**
    * @return The id for the connection to the message broker.  Based on host name and proc monitor name.
    *
    * @throws UnknownHostException
    */
   private String getClientId() throws UnknownHostException
   {
      return String.format("%s.%s", getName(), InetAddress.getLocalHost().getHostName());
   }
      
   @Override
   public void loadConf()
   {
      System.out.println("TestProc.loadConf");
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
      super.start();
   }

   /**
    * Stops the processing and closes the queue connection.
    */
   @Override
   public void stop()
   {
      //
      // Call this first so the status is set.
      super.stop();
   }
}
