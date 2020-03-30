/**
 * File: ProcessServerMgmtMBean.java
 * Description: MBean interface for managing the process server.
 *
 * @author Jeff Fisher
 *
 * Create Date: 10/28/2011
 * Last Update: 2012/03/07
 *
 * History
 *    $Log: ProcessServerMgmtMBean.java,v $
 *    Revision 1.1  2012/03/07 16:13:00  jfisher
 *    Initial add
 *
 */
package com.emerywaterhouse.server;


public interface ProcessServerMgmtMBean
{  
   /**
    * Changes the maximum number of processes for a given process monitor
    *
    * @param monitorId The id number of the monitor to change values on.
    * @param newMax The maximum.
    *
    * @return The previous max if successful, otherwise -1.
    */
   public int changeMaxProcCount(int monitorId, int newMax);

   /**
    * Get the internal application ID number based on the name of the name of the application.
    * Allows calling programs to use the ID in other methods
    * 
    * @param name The name of the application
    * @return The internal ID number of the application.
    */
   public long getAppId(String name);
   
   public String getDescription();
   
   public String getName();

   /**
    * Interface for processing data for an application.  The application will handle any
    * data conversions required.  Note that most of the process data methods would probably take
    * a byte array.
    * 
    * @param appId The application ID number.
    * @param the data to be processed.  Should be URL Encoded and will be converted to a byte array.
    * 
    * @return The outcome of the process data call.  Application dependent return values.
    */
   public int processData(long appId, String data);
   
   /**
    * Starts a specific applications job.
    * 
    * @param appId - The numeric identifier of the application.
    * @param id - The numeric identifier of the job.
    * 
    * @return true if the job was started, false if not.
    */
   public boolean startJob(long appId, long jobId);
   
   /**
    * Stops the process server.
    */
   public void stopServer();
   
   /**
    * Stops an applications job based on the id
    * 
    * @param appId - The numeric identifier of the application.
    * @param jobId - The numeric identifier of the job.
    * 
    * @return true if the job was stopped, false if not.
    */
   public boolean stopJob(long appId, long jobId);
   
   /**
    * Stops a specific applications job based on the name.
    * 
    * @param appId - The numeric identifier of the application.
    * @param jobNname - The name of the job
    * 
    * @return true if the job was stopped, false if not.
    */
   public boolean stopJob(long appId, String jobNname);
   
   
   /**
    * @return Status information about the server which includes running processes, etc.
    */
   public String viewStatusInfo();

}
