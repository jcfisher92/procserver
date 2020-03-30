package com.emerywaterhouse.test;

import java.io.FileNotFoundException;
import java.io.PrintWriter;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import com.emerywaterhouse.server.ProcessServer;

public class QueueListener implements MessageListener, ExceptionListener
{
   protected static int startId = 0;
   protected TestApp m_App;
   protected String m_Name;               // The name of the listener.  Should equate to a topic.
   protected int m_Id;                    // The listener id number.
   protected PrintWriter m_Writer;
   
   public QueueListener() 
   {
      this(null, null);
   }
   
   public QueueListener(TestApp app, String name)
   {
      super();
      m_App = app;
      setName(name);
      m_Id = startId++;

      try {
         m_Writer = new PrintWriter("ace-ny01.xml");
      } 
      
      catch (FileNotFoundException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();         
      }
      
      ProcessServer.log.info("[QueueListener] starting listener: name = " + m_Name + " id = " + m_Id);
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
      m_Writer.close();
      m_Writer = null;

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
   public TestApp getApp()
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
   public void onMessage(Message message)
   {      
      String msg = null;      
      
      if ( !m_App.stopped() && message != null ) {
         if ( message instanceof TextMessage ) {
            try {
               msg = ((TextMessage)message).getText();

               if ( msg != null && msg.length() > 0 ) {
                  if ( msg.contains("NY01") ) {
                     m_Writer.println(msg);
                  }
               }
               else {
                  try {
                     //
                     // Wait around and try again.
                     Thread.sleep(500);
                  }

                  catch ( InterruptedException ex ) {
                     ;
                  }
               }
            }

            catch ( JMSException ex ) {
               ProcessServer.log.error("[QueueListener] ", ex);
            }
         }
         else
            ProcessServer.log.warn("[QueueListener] Message of wrong type: " + message.getClass().getName());
      }
   }
   
   /**
    * @param monitor The monitor to set.
    */
   public void setApp(TestApp app)
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
         m_Name = String.format("queuelsnr-%d", m_Id);
   }

   /**
    * Handles exceptions when processing the message.  This should allow us to resend the original message
    * back to the queue for re-processing.
    */
   @Override
   public void onException(JMSException ex)
   {
      System.out.println(ex.getErrorCode());
      System.out.println(ex.getMessage());
   }
}
