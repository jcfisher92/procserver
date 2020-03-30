/**
 * File: EDBApp.java
 * Description: The process application that handles updating information related to the EDB database.
 * 
 * Author: Jeff Fisher
 * 
 * Create Date: 05/05/2017
 * Last Update: 05/08/2017
 * 
 * History:
 */
package com.emerywaterhouse.edb;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.emerywaterhouse.server.ConfLoader;
import com.emerywaterhouse.server.ProcessApplication;
import com.emerywaterhouse.server.ProcessServer;

public class EDBApp extends ProcessApplication implements ConfLoader 
{
   private static final String confFileName = "edbproc.xml";
   private ArrayList<DbJobProc> m_JobProcs;

   /**
    * 
    */
   public EDBApp() 
   {
      m_Name = "EDBApp";
      m_MaxProcCount = 1;

      m_JobProcs = new ArrayList<DbJobProc>();
      loadConf();
      ProcessServer.registerConfObj(this, confFileName);
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

      if (m_JobProcs != null) {
         m_JobProcs.clear();
         m_JobProcs = null;
      }

      super.finalize();
   }

   /**
    * Adds a DbJobProc reference to the list of running processors.
    *
    * @param bodProc
    *            The running BodProcessor instance.
    */
   public synchronized void addJobProc(DbJobProc jobProc) 
   {
      if (jobProc != null) {
         synchronized (m_JobProcs) {
            m_JobProcs.add(jobProc);
         }
      }
   }

   /**
    * override the base class method.
    * 
    * @see ProcessApplication#run()
    *
    *      The base class handles the outer loop. It will call this again if
    *      there is an exception and the processing ends. Otherwise the
    *      processing stays in this method until the application is stopped.
    *
    *      Pulls messages from the topic and creates processing objects to deal
    *      with them.
    */
   @Override
   protected void doProcessing() throws Exception 
   {
      boolean shouldSleep = false;

      while (m_Status != ProcessServer.stopped) {
         m_Status = ProcessServer.running;

         //
         // Wait to see if there are more messages
         if (shouldSleep) {
            try {
               m_Status = ProcessServer.idle;
               Thread.sleep(m_SleepInterval);
               shouldSleep = false;
            }

            catch (InterruptedException ex) {
               Thread.currentThread().interrupt();
               break;
            }
         }
      }
   }

   /**
    * Overrides the base class method to provide the number of currently
    * running processes.
    */
   @Override
   public synchronized int getProcCount() 
   {
      return m_JobProcs.size();
   }

   /**
    * Creates a DbJobProc instance based on the type of document.
    * 
    * @param jobName
    *
    * @return
    */
   public DbJobProc getProcessor(String className) 
   {
      DbJobProc proc = null;
      Class<?> c = null;
      Object obj = null;

      if (className != null && className.length() > 0) {
         try {
            c = ProcessServer.getLoader().loadClass(className);

            if (c != null) {
               obj = c.newInstance();

               if (obj instanceof DbJobProc)
                  proc = (DbJobProc) obj;
               else
                  ProcessServer.log.error("[EDBApp]" + className + " is not an instance of the class DbJobProc");
            } else
               ProcessServer.log.error("[EDBApp] unable to instantiate " + className);
         }

         catch (Exception ex) {
            ProcessServer.log.error("[EDBApp]", ex);
         }

         finally {
            obj = null;
            c = null;
         }
      }

      return proc;
   }

   /**
    * Gets the status information from each running request.
    *
    * @return HTML data that represents the status information from the
    *         currently running reports.
    */
   @Override
   public synchronized String getProcessStatus() 
   {
      StringBuffer html = new StringBuffer(1024);
      String runTime;
      String lastStartTime;
      long hour = 0;
      long min = 0;
      long sec = 0;
      DbJobProc proc = null;

      //
      // Display the name of the application and the ID of the application.
      html.append("<br>");
      html.append("<table style=\"font-family: Arial, Helvetica, sans-serif; ");
      html.append("font-size: 12px; border-width: 0; border-style: none; width: 300px\">\r\n");
      html.append("<tr>\r\n");
      html.append(String.format("<td width=\"200px\"><b>App Name: %s</b></td>\r\n", getName()));
      html.append(String.format("<td width=\"100px\"><b>App ID: %d</b></td>\r\n", getId()));
      html.append("</tr>\r\n");
      html.append("</table>\r\n");

      //
      // Display all the currently running jobs.
      html.append("<table style=\"font-family: Arial, Helvetica, sans-serif; ");
      html.append("font-size: 12px; border-width: 0; border-style: none; width: 1150px\">\r\n");

      html.append("<tr>\r\n");
      html.append("<td width=\"250px\"><b>Job Name</b></td>\r\n");
      html.append("<td width=\"200px\"><b>Interval</b></td>\r\n");
      html.append("<td width=\"200px\"><b>Last Start Time</b></td>\r\n");
      html.append("<td width=\"200px\"><b>Running Time</b></td>\r\n");
      html.append("<td width=\"200px\"><b>Max Time</b></td>\r\n");
      html.append("<td width=\"400px\"><b>Current Action</b></td>\r\n");
      /*
		html.append("<td width=\"100px\"><b>Operations</b></td>\r\n");
       */
      html.append("</tr>\r\n");

      synchronized (m_JobProcs) {
         for (int i = 0; i < m_JobProcs.size(); i++) {
            proc = m_JobProcs.get(i);

            sec = proc.getRunTime();

            //
            // Convert to seconds and then break out the hours and minutes.
            // If this is not
            // done then the seconds will accumulate past 60. So will the
            // minutes.
            if (sec >= 3600) {
               hour = sec / 3600;
               sec = sec - hour * 3600;
            }

            if (sec >= 60) {
               min = sec / 60;
               sec = sec - min * 60;
            }

            runTime = String.format("%02d:%02d:%02d", new Long(hour), new Long(min), new Long(sec)); // Running time

            sec = proc.getLastStartTime();

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            Date lastDate = new Date(proc.getLastStartTime());
            lastStartTime = sdf.format(lastDate); // Last start time

            html.append("<tr>\r\n");
            html.append(String.format("<td width=\"250px\">%s</td>\r\n", proc.getName()));
            html.append(String.format("<td width=\"200px\">%d minutes</td>\r\n", proc.getInterval()));
            html.append(String.format("<td width=\"200px\">%s</td>\r\n", lastStartTime));
            html.append(String.format("<td width=\"200px\">%s</td>\r\n", runTime));
            html.append(String.format("<td width=\"200px\">%d minutes</td>\r\n", proc.getMaxRunTime()));
            html.append(String.format("<td width=\"600px\">%s</td>\r\n", proc.getProcessStatus()));
            /*
				html.append("<td width=\"200px\">");
				html.append("<a href=\"");
				html.append(ProcessServer.getInstance().getJmxURL());
				html.append("/InvokeAction//ProcessServerMgmt%3Aname%3DServerMgmt/action=stopJob?action=stopJob");
				html.append("&p1%2Blong=").append(getId()).append("&p2%2Blong=").append(proc.getId())
						.append("\">Stop Job</a>");
				html.append("&nbsp;");
				html.append("<a href=\"");
				html.append(ProcessServer.getInstance().getJmxURL());
				html.append("/InvokeAction//ProcessServerMgmt%3Aname%3DServerMgmt/action=startJob?action=startJob");
				html.append("&p1%2Blong=").append(getId()).append("&p2%2Blong=").append(proc.getId())
						.append("\">Start Job</a>");
				html.append("</td>\r\n");
             */
            html.append("</tr>\r\n");
         }
      }

      html.append("</table>");
      html.append("<br>");

      return html.toString();
   }

   /**
    * Removes a Processor object from the list.
    *
    * @param bodProc
    *            The BodProcessor to remove.
    */
   public synchronized void remJobProc(DbJobProc jobProc) 
   {
      if (jobProc != null) {
         synchronized (m_JobProcs) {
            for (int i = 0; i < m_JobProcs.size(); i++) {
               if (jobProc.getId() == m_JobProcs.get(i).getId()) {
                  m_JobProcs.remove(i);
               }
            }
         }
      }
   }

   /**
    * Implements the method for the interface ConfLoader. Loads the
    * configuration file data and fills the hash map with the classes that need
    * to be instantiated based on the system component.
    */
   @Override
   public void loadConf() 
   {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      DocumentBuilder db = null;
      Document doc = null;
      NodeList objList = null;
      NodeList tmpList = null;
      Node objNode = null;
      Element objElmnt = null;
      Element tmpElmnt = null;
      String fileSep = null;
      File file = null;
      String jobName = null;
      String jobClass = null;
      boolean autoStart = false;
      int interval = 10;
      int maxRunTime = 60;
      boolean found = false;
      DbJobProc job = null;

      try {
         fileSep = System.getProperty("file.separator", "/");
         file = new File(System.getProperty("user.dir") + fileSep + "conf" + fileSep + confFileName);
         db = dbf.newDocumentBuilder();
         doc = db.parse(file);
         doc.getDocumentElement().normalize();
         objList = doc.getElementsByTagName("jobProc");

         synchronized (m_JobProcs) {
            for (int i = 0; i < objList.getLength(); i++) {
               objNode = objList.item(i);

               if (objNode.getNodeType() == Node.ELEMENT_NODE) {
                  objElmnt = (Element) objNode;

                  //
                  // Get the system node list
                  tmpList = objElmnt.getElementsByTagName("job");
                  tmpElmnt = (Element) tmpList.item(0);
                  tmpList = tmpElmnt.getChildNodes();
                  jobName = (tmpList.item(0)).getNodeValue();

                  tmpList = objElmnt.getElementsByTagName("class");
                  tmpElmnt = (Element) tmpList.item(0);
                  tmpList = tmpElmnt.getChildNodes();
                  jobClass = (tmpList.item(0)).getNodeValue();

                  tmpList = objElmnt.getElementsByTagName("autoStart");
                  tmpElmnt = (Element) tmpList.item(0);
                  tmpList = tmpElmnt.getChildNodes();
                  autoStart = Integer.parseInt((tmpList.item(0)).getNodeValue()) == 1;

                  tmpList = objElmnt.getElementsByTagName("interval");
                  tmpElmnt = (Element) tmpList.item(0);
                  tmpList = tmpElmnt.getChildNodes();
                  interval = Integer.parseInt((tmpList.item(0)).getNodeValue());

                  tmpList = objElmnt.getElementsByTagName("maxRunTime");
                  tmpElmnt = (Element) tmpList.item(0);
                  tmpList = tmpElmnt.getChildNodes();
                  maxRunTime = Integer.parseInt((tmpList.item(0)).getNodeValue());

                  //
                  // Check to see if this job is already in the list of
                  // jobs.
                  // This conf file can be updated at any time so we don't
                  // want to have
                  // duplicates if it changes. We also don't want to clear
                  // everything out.
                  for (int j = 0; i < m_JobProcs.size(); j++) {
                     found = m_JobProcs.get(j).getName().equals(jobName);

                     if (found)
                        break;
                  }

                  if (!found) {
                     job = getProcessor(jobClass);
                     job.setName(jobName);
                     job.setInterval(interval);
                     job.setMaxRunTime(maxRunTime);
                     job.setApplication(this);

                     if (autoStart)
                        job.startProcessing(true);
                  }
               }
            }
         }
      }

      catch (Exception ex) {
         ProcessServer.log.error("[BodProcessorFactory", ex);
      }

      finally {
         fileSep = null;
         jobClass = null;
         tmpList = null;
         tmpElmnt = null;
         objElmnt = null;
         objNode = null;
         objList = null;
      }
   }

   /**
    * Creates the internal thread and initializes the internal db jobs Reads
    * the applications properties and check for a list of jobs that should be
    * run.
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
    * Starts a job that has been stopped via the stopIdle command. Restarts the
    * the thread processing.
    * 
    * @param id
    *            The job's internal ID number.
    * 
    * @return true if the job was started, false if not.
    */
   public boolean startJob(long id) 
   {
      boolean started = false;
      Iterator<DbJobProc> iter = m_JobProcs.iterator();
      DbJobProc job = null;

      try {
         while (iter.hasNext()) {
            job = iter.next();
            if (job.getId() == id) {
               if (job.getStatus() == ProcessServer.idle) {
                  job.startProcessing(true);
                  started = true;
                  break;
               }
            }
         }
      }

      finally {
         iter = null;
      }

      return started;
   }

   /**
    * Stops the processing and closes the queue connection.
    */
   @Override
   public void stop() 
   {
      int procCount = m_JobProcs.size();
      Iterator<DbJobProc> iter = m_JobProcs.iterator();
      DbJobProc job = null;
      //
      // Call this first so the status is set.
      super.stop();

      while (iter.hasNext()) {
         job = iter.next();
         job.stop();
      }

      //
      // We need to let the processes finish because they may be in the middle
      // of a db transaction
      while (procCount > 0) {
         try {
            ProcessServer.log.info(String.format("[%s] waiting for %d processes to finish", m_Name, procCount));
            Thread.sleep(1000);
            procCount = m_JobProcs.size();
         }

         catch (InterruptedException ex) {
            ProcessServer.log.warn("[EDBApp] " + ex.getMessage());
         }
      }
   }

   /**
    * Stops the job identified by id.
    * 
    */
   @Override
   public boolean stopJob(long id) 
   {
      boolean stopped = false;
      Iterator<DbJobProc> iter = m_JobProcs.iterator();
      DbJobProc job = null;
      try {
         while (iter.hasNext()) {
            job = iter.next();
            
            if (job.getId() == id) {
               job.stopIdle();
               stopped = true;
               break;
            }
         }
      }

      finally {
         iter = null;
      }

      return stopped;
   }
}
