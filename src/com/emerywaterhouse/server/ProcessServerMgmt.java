/**
 *
 */
package com.emerywaterhouse.server;

import java.net.URLDecoder;

public class ProcessServerMgmt implements ProcessServerMgmtMBean
{
   private ProcessServer m_Server;

   /**
    *
    */
   public ProcessServerMgmt()
   {
      super();
   }

   /**
    * Creates an instance of the FaxServerMgmt bean.
    *
    * @param server A reference to the FaxServer object.
    */
   public ProcessServerMgmt(ProcessServer server)
   {
      super();

      m_Server = server;
   }

   /**
    * Cleanup allocated resources.
    *
    * @see java.lang.Object#finalize()
    */
   @Override
   public void finalize() throws Throwable
   {
      m_Server = null;

      super.finalize();
   }
   
   /**
    * @see com.emerywaterhouse.server.ProcessServerMgmtMBean#changeMaxProcCount()
    */   
   public int changeMaxProcCount(int monitorId, int newMax)
   {
      // TODO find the correct monitor and call it's change method.
      return 0;
   }

   /**
    * @see com.emerywaterhouse.ProcessServerMgmtMBean.server.ProcessServerMgmtMBean#getAppId()
    */
   @Override
   public long getAppId(String name)
   {
      long result = 0;
      
      if ( m_Server != null )
         result = m_Server.getAppId(name);
      
      return result;
   }
   
   /**
    * @see com.emerywaterhouse.ProcessServerMgmtMBean.server.ProcessServer.ServerMgmtMBean#getDescription()
    */
   public String getDescription()
   {
      return "Process Server managment bean";
   }

   /**
    * @see com.emerywaterhouse.ProcessServerMgmtMBean.server.ProcessServerMgmtMBean#getName()
    */
   public String getName()
   {
      return getClass().getName();
   }
   
   /**
    * Implement the JMX method.  Decode the string and convert to a byte[] for
    * further processing by the application.
    * @see com.emerywaterhouse.server.ProcessServerMgmtMBean#processData()
    */
   @Override
   public int processData(long appId, String data)
   {
      int result = 0; 
      
      if ( m_Server != null )
         try {
            result = m_Server.processData(appId, URLDecoder.decode(data, "UTF-8").getBytes());
         }
      
         catch ( Exception ex ) {
            ProcessServer.log.error("[ProcessServerMgmt]", ex);
         }
      
      return result;
   }
   
   /**
    * @see com.emerywaterhouse.ProcessServerMgmtMBean.server.ProcessServerMgmtMBean#startJob()
    */
   public boolean startJob(long appId, long jobId)
   {
      boolean started = false;
      
      if ( m_Server != null )
         started = m_Server.startJob(appId, jobId);
      
      return started;
   }
   
   /**
    * @see com.emerywaterhouse.ProcessServerMgmtMBean.server.ProcessServerMgmtMBean#stopJob()
    */
   public boolean stopJob(long appId, long jobId)
   {
      boolean stopped = false;
      
      if ( m_Server != null )
         stopped = m_Server.stopJob(appId, jobId);
      
      return stopped;
   }
   
   /**
    * @see com.emerywaterhouse.ProcessServerMgmtMBean.server.ProcessServerMgmtMBean#stopJob()
    */
   public boolean stopJob(long appId, String jobName)
   {
      boolean stopped = false;
      
      if ( m_Server != null )
         stopped = m_Server.stopJob(appId, jobName);
      
      return stopped;
   }
   
   /**
    * @see com.emerywaterhouse.ProcessServerMgmtMBean.server.ProcessServerMgmtMBean#stopServer()
    */
   public void stopServer()
   {
      if ( m_Server != null )
         m_Server.shutdown(ProcessServer.stopped);
   }

   /**
    * Gets the status information from the server.
    *
    * @see com.emerywaterhouse.ProcessServerMgmtMBean.server.ProcessServerMgmtMBean#viewStatusInfo()
    */
   public String viewStatusInfo()
   {
      String info = null;

      if ( m_Server != null )
        info =  m_Server.getStatusInfo();
      else
         info = "no status information available";

      return info;
   }
}
