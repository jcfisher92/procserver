/**
 *
 */
package com.emerywaterhouse.server;

import java.util.HashMap;

public abstract class ProcessApplication implements Runnable 
{
   protected static int m_AppCount = 0; // Internal reference number of the application count.

   protected int m_AppId;               // The identifier of the application. Gets seeded from the static id.
   protected boolean m_AutoStart;       // Flag to determine whether the application should be started automatically.
   protected Thread m_Thread;
   protected int m_MaxProcCount;
   protected int m_SleepInterval;       // The amount of time to sleep during routine processing.
   protected int m_Status;
   protected String m_Name;             // The name of the process monitor.
   protected ProcessServer m_Server;    // Reference to the parent container
   
   protected HashMap<String, String> m_Props;

   /**
    * Default constructor
    */
   public ProcessApplication() 
   {
      super();
      m_Name = getDefaultName();
      m_AppId = ++m_AppCount;
      m_SleepInterval = 300;
   }

   /**
    * Place holder for the processing that each application controls. Descendant
    * classes need to override this if they have special processing needs. See
    * the run method.
    */
   protected void doProcessing() throws Exception 
   {

   }

   /**
    *
    * @return True if the app should be started automatically, false if not.
    */
   public boolean getAutoStart() 
   {
      return m_AutoStart;
   }

   /**
    * Creates the default name of the monitor.
    * 
    * @return The name of this monitor.
    */
   private String getDefaultName() 
   {
      return String.format("%s-%d", this.getClass().getSimpleName(), m_AppId);
   }

   /**
    * Gets the applications internal ID number. This is used to identify the app
    * in JMX calls, etc.
    * 
    * @return The internal ID number of the application.
    */
   public long getId() 
   {
      return m_AppId;
   }

   /**
    * @return The maximum number of concurrent threads.
    */
   public int getMaxProcCount() 
   {
      return m_MaxProcCount;
   }

   /**
    * @return The name of the process monitor.
    */
   public synchronized String getName() 
   {
      return m_Name;
   }

   /**
    * Returns the current process count. This should be overridden by any class
    * that needs this information.
    *
    * @return The count.
    */
   public synchronized int getProcCount() 
   {
      return 0;
   }

   /**
    * @return The status of the monitor and it's processes.
    */
   public synchronized String getProcessStatus() 
   {
      return "";
   }

   /**
    * Returns a property value set during application startup.
    * 
    * @param key The key of the property
    * @return The value of the property based on the key or an empty string if
    *         it wasn't found.
    */
   public synchronized String getProperty(String key) 
   {
      String value = "";

      if ( m_Props != null ) {
         if ( !m_Props.isEmpty() ) {
            if ( m_Props.containsKey(key) )
               value = m_Props.get(key);
         }
      }

      return value != null ? value : "";
   }

   /**
    * Getter for returning an instance of the proc server.
    * @return The proc server instance associated with this app.
    */
   public synchronized final ProcessServer getServer()
   {
      return m_Server;
   }
   
   /**
    * The reference implementation for the JMX call.  Decendant classes
    * should override this method if it's to be implemented.
    * 
    * @param data The data to process.
    * 
    * @return An application dependent result.
    */
   public synchronized int processData(byte[] data)
   {
      return 0;
   }
   
   /**
    * Runs in the background to allow processing and communications with other
    * processes. Descendant classes should override this method if special
    * processing is needed.
    *
    * @see java.lang.Runnable#run()
    */
   public void run() 
   {
      StringBuffer logMsg = new StringBuffer();

      if ( Thread.currentThread() == m_Thread ) {
         logMsg.append(String.format("[%s] started", m_Name));
         ProcessServer.log.info(logMsg.toString());

         //
         // Double while loop to handle exceptions and not kill the
         // processing. The inner loop is normal processing and the
         // outer loop re-establishes the processing after an exception.
         while ( m_Status != ProcessServer.stopped ) {
            try {
               if ( m_Status != ProcessServer.stopped ) {
                  m_Status = ProcessServer.running;

                  //
                  // Base class process handling call
                  // May not return until the application has actually
                  // shutdown.
                  doProcessing();

                  if ( m_Status != ProcessServer.stopped ) {
                     m_Status = ProcessServer.idle;
                     Thread.sleep(m_SleepInterval);
                  }
               }
            }

            catch ( Exception ex ) {
               if ( ex instanceof InterruptedException ) {
                  Thread.currentThread().interrupt();
                  break;
               } 
               else {
                  ProcessServer.log.error(String.format("[%s]", m_Name), ex);

                  logMsg.setLength(0);
                  logMsg.append(String.format("[%s\r\n]", m_Name));
                  logMsg.append("There was an exception during processing.  See ../log/server.log for the stacktrace\r\n");

                  logMsg.append("The exception was:\r\n");
                  logMsg.append(ex.getMessage());

                  ProcessServer.getInstance().notifyMis(logMsg.toString());
               }
            }

            //
            // If we've totally exploded and we're out of the loop above
            // because of an exception
            // sleep here for a while before proceeding again. This will
            // keep the flood of emails
            // down and let MIS try and deal with the problem.
            try {
               m_Status = ProcessServer.idle;
               Thread.sleep(m_SleepInterval * 3);
            }

            catch ( Exception ex ) {
               if (ex instanceof InterruptedException) {
                  Thread.currentThread().interrupt();
                  break;
               }

               ProcessServer.log.error(String.format("[%s]", m_Name), ex);
            }
         }
      } 
      else {
         logMsg.setLength(0);
         logMsg.append(String.format("[%s] should be started by calling the start method", m_Name));
         ProcessServer.log.error(logMsg.toString());
      }

      logMsg.setLength(0);
      logMsg.append(String.format("[%s] stopped", m_Name));
      ProcessServer.log.info(logMsg.toString());
      logMsg = null;
   }

   /**
    * Sets the auto start flag
    * 
    * @param start true = start, false = not
    */
   public void setAutoStart(boolean start)
   {
      m_AutoStart = start;
   }
   
   /**
    * Sets the number of concurrent threads that will be processing.
    * 
    * @param count
    */
   public void setMaxProcCount(int count) 
   {
      m_MaxProcCount = count;
   }

   /**
    * Sets the name of this processes. Names the thread as well.
    * 
    * @param name The name of the process and thread.
    */
   public void setName(String name) 
   {
      if ( name != null && name.length() > 0 )
         m_Name = name;
      else
         m_Name = getDefaultName();
   }

   /**
    * 
    * @param Server
    */
   public void setServer(ProcessServer server) 
   {
      m_Server = server;
   }
   
   /**
    * Sets the sleep interval for the application.
    * 
    * @param interval The length of time in milliseconds to sleep.
    */
   public void setSleepInterval(int interval) 
   {
      if ( interval > 0 )
         m_SleepInterval = interval;
   }

   /**
    * Place holder for descendant classes to implement. Sets a property value.
    *
    * @param name The name of the property to set.
    * @param val The value of the property.
    */
   public void setProperty(String name, String val) 
   {
      ;
   }

   /**
    * Place holder for descendant classes to implement. Set's all the property
    * values listed in the HashMap.
    *
    * @param props
    *           A list of unique property name/value pairs
    */
   @SuppressWarnings("unchecked")
   public void setProperties(HashMap<String, String> props) 
   {
      if ( props != null ) {
         m_Props = (HashMap<String, String>) props.clone();

         //
         // By default, set the maximum number of processes for the
         // application
         if ( !m_Props.isEmpty() ) {
            if ( m_Props.containsKey("maxProcs") )
               setMaxProcCount(Integer.parseInt(props.get("maxProcs")));

            if ( m_Props.containsKey("sleepInterval") )
               setSleepInterval(Integer.parseInt(props.get("sleepInterval")));
         }
      }
   }

   /**
    * Creates the internal thread and starts the monitor
    *
    * @throws Exception
    */
   public void start() throws Exception 
   {
      if ( m_Thread == null ) {
         m_Thread = new Thread(this, m_Name);
         m_Thread.setDaemon(true);
      }

      m_Status = ProcessServer.running;
      m_Thread.start();
   }

   /**
    * 
    * @param id
    * @return
    */
   public boolean startJob(long id) 
   {
      return true;
   }

   /**
    * 
    * @param name
    * @return
    */
   public boolean startJob(String name)
   {
      return true;
   }

   /**
    * Stops the monitor.
    */
   public void stop() 
   {
      ProcessServer.log.info(String.format("[%s] terminating", m_Name));
      m_Status = ProcessServer.stopped;
   }

   /**
    * Place holder for descendant classes to stop any jobs they have.
    * 
    * @param name
    *           The name of the job
    * 
    * @return true if the job was stopped, false if not.
    */
   public boolean stopJob(String name)
   {
      return true;
   }

   /**
    * Place holder for descendant classes to stop any jobs they have.
    * 
    * @param id
    *           The numeric id of the job
    *
    * @return true if the job was stopped, false if not.
    */
   public boolean stopJob(long id)
   {
      return true;
   }

   /**
    * returns whether the application has been stopped.
    * 
    * @return true if stopped, false if not.
    */
   public synchronized boolean stopped()
   {
      return m_Status == ProcessServer.stopped;
   }
}
