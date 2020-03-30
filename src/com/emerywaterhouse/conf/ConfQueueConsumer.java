/**
 * File: ConfQueueConsumer.java
 * Description: Very light weight consumer on the ha.conf queue.  Grabs a document off of the queue and
 *   gives it to the app for processing.
 *
 * @author Jeff Fisher
 *
 * Create Data: 04/27/2018
 * Last Update: 
 *
 * History
 *     
 */    
package com.emerywaterhouse.conf;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import java.io.IOException;

import com.emerywaterhouse.server.ProcessServer;

public class ConfQueueConsumer extends DefaultConsumer
{
   protected ConfApp m_App;
   protected String m_Name;               // The name of the listener.  Should equate to a topic.
   
   /**
    *
    */
   public ConfQueueConsumer()
   {
      this(null, null, null);
   }

   /**
    * Creates a listener with a monitor reference and a name.
    *
    * @param app A reference to the confirmation application class
    * @param name The name of the to be given to the instance of the listener.
    */
   public ConfQueueConsumer(ConfApp app, String name, Channel channel)
   {
      super(channel);
      m_App = app;
      setName(name);

      ProcessServer.log.info("[ConfQueueConsumer] starting listener");
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
    * @return Returns the app instance.
    */
   public ConfApp getApp()
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
    * Handles accepting messages on the confirm bod queue.  Checks to see if the limit
    * on concurrent processing has been met.  If so then it holds starting the next process until
    * that limit is below the max.
    */
   @Override
   public void handleDelivery(String consumerTag, Envelope env, BasicProperties props, byte[] body) throws IOException 
   {      
      String bod = null;
      boolean error = false;
      long deliveryTag = env.getDeliveryTag();
      
      if ( !m_App.stopped() && body != null ) {         
         try {
            while ( bod == null && !error ) {
               if ( m_App.getProcCount() < m_App.getMaxProcCount() ) {
                  bod = new String(body);

                  if ( bod != null && bod.length() > 0 ) {
                     m_App.processBod(bod);
                     getChannel().basicAck(deliveryTag, false);
                  }
                  else {
                     error = true;
                     ProcessServer.log.warn("[ConfQueueConsumer] message did not contain any data");
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
            ProcessServer.log.error("[ConfQueueConsumer] ", ex);               
         }
      }
   }
   
   /**
    * @param monitor The monitor to set.
    */
   public void setApp(ConfApp app)
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
         m_Name = "ConfBodQueueLsnr";
   }

   
}
