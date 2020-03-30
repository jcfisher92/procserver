/**
 * File: ConfFaxConsumer.java
 * Description: Listens for incoming messages on a queue.  Pulls the BODs off
 *    of the queue and sends them to the a processor to be parsed and run.  This is the
 *    generic listener and does not listen for any special bod.
 *
 * @author Jeff Fisher
 *
 * Create Data: 10/27/2011
 * Last Update: $Id: ConfFaxConsumer.java,v 1.1 2012/03/07 16:13:00 jfisher Exp $
 *
 * History
 *    $Log: ConfFaxConsumer.java,v $
 *    Revision 1.1  2012/03/07 16:13:00  jfisher
 *    Initial add
 *
 */
package com.emerywaterhouse.fax;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import java.io.IOException;

import com.emerywaterhouse.server.ProcessServer;

public class FaxQueueListener extends DefaultConsumer
{
   protected static int startId = 0;

   protected FaxApp m_App;
   protected String m_Name;               // The name of the listener.  Should equate to a topic.
   protected int m_Id;                    // The listener id number.

   /**
    *
    */
   public FaxQueueListener()
   {
      this(null, null, null);
   }

   /**
    * Creates a listener with a monitor reference and a name.
    *
    * @param app A reference to the fax application class
    * @param name The name of the to be given to the instance of the listener.
    */
   public FaxQueueListener(FaxApp app, String name, Channel channel)
   {
      super(channel);
      m_App = app;
      setName(name);
      m_Id = startId++;

      ProcessServer.log.info("[FaxQueueLsnr] starting listener: name = " + m_Name + " id = " + m_Id);
   }

   /**
    * Clean up anything we created.
    * @throws Throwable
    */
   @Override
   public void finalize() throws Throwable
   {
      m_App = null;
      m_Name = null;

      super.finalize();
   }

   /**
    * @return Returns the id.
    */
   public int getId()
   {
      return m_Id;
   }

   /**
    * @return Returns the app instance.
    */
   public FaxApp getApp()
   {
      return m_App;
   }

   /**
    * @return Returns the name.
    */
   public String getName()
   {
      return m_Name;
   }

   /**
    * Handles accepting messages on the fax request queue.  Checks to see if the limit
    * on concurrent processing has been met.  If so then it holds starting the next process until
    * that limit is below the max.
    * @see javax.jms.MessageListener#onMessage(javax.jms.Message)
    */
   @Override
   public void handleDelivery(String consumerTag, Envelope env, BasicProperties props, byte[] body) throws IOException 
   {
      FaxProcessor processor;
      String bod = null;
      boolean error = false;
      long deliveryTag = env.getDeliveryTag();
      
      if ( !m_App.stopped() && body != null ) {         
         try {
            while ( bod == null && !error ) {
               if ( m_App.getProcCount() < m_App.getMaxProcCount() ) {
                  bod = new String(body);

                  if ( bod != null && bod.length() > 0 ) {
                     processor = new FaxProcessor(m_App, bod);
                     processor.processBOD();
                     getChannel().basicAck(deliveryTag, false);
                  }
                  else {
                     error = true;
                     ProcessServer.log.warn("[FaxQueueLsnr] message did not contain any data");
                  }
               }
               else {
                  try {
                     //
                     // Wait around and try again.
                     Thread.sleep(500);
                  }

                  catch ( InterruptedException ex ) {
                     break;
                  }
               }
            }
         }

         catch ( Exception ex ) {
            getChannel().basicNack(deliveryTag, false, true);
            ProcessServer.log.error("[FaxQueueLsnr] ", ex);               
         }
      }
   }
   
   /**
    * @param monitor The monitor to set.
    */
   public void setApp(FaxApp app)
   {
      m_App = app;
   }

   /**
    * Sets the name of the listener.  This should be the topmost topic that the
    * listener listens for.
    *
    * @param name  The name to set.
    */
   public void setName(String name)
   {
      if ( name != null && name.length() > 0 )
         m_Name = name;
      else
         m_Name = String.format("FaxQueueLsnr-%d", m_Id);
   }

   
}
