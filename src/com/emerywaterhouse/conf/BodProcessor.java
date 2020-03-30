/**
 *
 */
package com.emerywaterhouse.conf;

import java.util.ArrayList;

import com.emerywaterhouse.esb.ESBConst;
import com.emerywaterhouse.server.ProcessServer;

public abstract class BodProcessor implements Runnable
{
   private static long startId = 0;
   
   protected ConfApp m_App;                     // Pointer to the process application instance.
   protected String m_Bod;                      // The internal BOD that was pulled from the message broker
   protected String m_Component;                // The component that sent the request.
   protected String m_DocName;                  // The name of the document being processed.
   protected String m_EmailAddr;                // Place holder for an email addressed used in the confirmation process.
   protected long m_Id;                         // An id for identifying an instance of a bod processor.
   protected String m_LogicalId;                // The logical ID from the bod.
   protected String m_Name;                     // The name of the processor.
   protected String m_System;                   // The system that sent the bod.
   protected String m_SenderComp;               // The component of the sender that called the web service
   protected String m_SenderTask;               // The task of the sender that called the web service.
   protected Thread m_Thread;                   // The thread that runs the processing.

   /**
    *
    */
   public BodProcessor()
   {
      m_Id = startId++;
      m_Name = String.format("BodProc-%d", m_Id);
      m_Thread = new Thread(this, m_Name);
      m_Thread.setDaemon(true);
      m_DocName = "unkown bod";
   }

   /**
    * Creates a BodProcessor object with a bod and an instance to the ConfApp class.
    *
    * @param app An instance of the ConfApp class.
    * @param bod The BOD that is to be processed.
    */
   public BodProcessor(ConfApp app, String bod)
   {
      this();

      try {
         setApplication(app);
         setBOD(bod);
      }

      catch ( Exception ex ) {
         ProcessServer.log.error(String.format("[%s]", m_Name), ex);
      }
   }

   /**
    * Return the id of the processor.
    *
    * @return The internal processor id used for identify this process.
    */
   public long getId()
   {
      return m_Id;
   }

   /**
    * The processor name
    * @return The name of the processor or the document.
    */
   public String getName()
   {
      return m_Name;
   }
   
   /**
    * Should be implemented by the descendant class.  Should return some sort of status.
    * @return The current status of the process.
    */
   public String getProcessStatus()
   {
      return "processing document";
   }
   
   /**
    * Gets a list of recipients.  This is the default method and will return a
    * list of recipients defined internally.  Descendant classes should override
    * this to return a different list.
    *
    * @return An ArrayList with the list of recipients.
    *
    * @throws Exception
    */
   public ArrayList<String> getRecipients() throws Exception
   {
      ArrayList<String> recips = ProcessServer.getRecipients(m_System, m_Component, ESBConst.info);

      //
      // If there's no routing for the system and component, then use the email address that came with the
      // document if it exists.
      if ( recips.size() == 0 ) {
         ProcessServer.log.warn(
            String.format("missing recipient list for %s, %s, using default %s", m_System, m_Component, m_EmailAddr != null ? m_EmailAddr:"")
         );

         if ( m_EmailAddr != null && m_EmailAddr.length() > 0 )
            recips.add(m_EmailAddr);
         else {
            throw new Exception(
                  String.format(
                        "missing recipient list; unable to send acknowledgement for %s, %s",
                        m_System,
                        m_Component)
            );
         }
      }

      return recips;
   }
   /**
    * Must be be overridden by the descendant classes.  This is called by the run method.
    */
   protected abstract void processBod();

   /**
    * @see java.lang.Runnable#run()
    */
   @Override
   public void run()
   {
      try {
         processBod();
      }

      finally {
         if ( m_App != null )
            m_App.remBodProc(this);
      }
   }

   /**
    * Sends an email acknowledgment
    *
    * @param msg The message to send.
    * @throws Exception
    */
   protected void sendAcknowledgement(String msg, String subj) throws Exception
   {
      m_App.getServer();
      ProcessServer.sendEmailNotification(getRecipients(), subj, msg);
   }
   
   /**
    * Sets the parent application var.
    *
    * @param app A reference to the parent application object.
    * @throws Exception when the monitor var is null.
    */
   public void setApplication(ConfApp app) throws Exception
   {
      if ( app != null ) {
         m_App = app;
         m_App.addBodProc(this);
      }
      else
         throw new Exception("[BodProcessor] parent application can't be set to null");
   }

   /**
    * Sets the internal BOD member.
    *
    * @param bod The bod.
    * @throws Exception when the bod var is null.
    */
   public void setBOD(String bod) throws Exception
   {
      if ( bod != null)
         m_Bod = bod;
      else
         throw new Exception("[BodProcessor] attempt to set bod to null");
   }

   /**
    * Starts the processing of the BOD by starting the thread or by calling the run method directly.
    * @param threaded flag for threaded or non threaded processing.
    */
   public void startProcessing(boolean threaded)
   {
      if ( threaded )
         m_Thread.start();
      else
         run();
   }
}
