/**
 * File: FaxServer.java.
 * Description: The main class that creates the environment for running the message broker to
 *    send fax requests to the fax server.
 *
 * @author Jeff Fisher
 *
 * Create Data: 10/27/2011
 * Last Update: $Id: ProcessServer.java,v 1.2 2012/11/06 15:09:16 jfisher Exp $
 *
 * History
 *    $Log: ProcessServer.java,v $
 *    Revision 1.2  2012/11/06 15:09:16  jfisher
 *    Removed wasp references and changed some static references.
 *
 *    Revision 1.1  2012/03/07 16:13:00  jfisher
 *    Initial add
 *
 */
package com.emerywaterhouse.server;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.apache.log4j.AsyncAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import com.emerywaterhouse.esbrouting.RoutingInfo;
import com.emerywaterhouse.loader.EmLoader;
import com.emerywaterhouse.utils.DataSender;
import com.emerywaterhouse.utils.DbUtils;
import com.sun.jdmk.comm.HtmlAdaptorServer;
import com.ximpleware.AutoPilot;
import com.ximpleware.BookMark;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;

public class ProcessServer implements Runnable, ConfLoader 
{
   private static final String dataProcessing = "dataprocessing@emeryonline.com";
   private static final String callPhone = "2077492592@vzwpix.com";

   private static final String[] errorList = { dataProcessing, callPhone };

   private static final String[] testList = { "programming@emeryonline.com" };

   public final static short init = 0;
   public final static short running = 1;
   public final static short stopped = 2;
   public final static short abort = 3;
   public final static short fatal = 4;
   public final static short idle = 5;

   public static final int hour = 3600000;
   public static final int minute = 60000;
   public static final int second = 1000;

   private static String propFile = "server.properties";

   /**
    * Environment enumeration
    */
   public enum Environment {
      Test, Production
   };

   private HashMap<String, ProcessApplication> m_Apps; /** The list of running applications */
   private Environment m_Env;                           /** Environment variable used to determine test or production */
   private static String m_From;                        /** The email from property */
   private String m_JmxIPAddr;                          /** The IP address for the JMX console */
   private String m_JmxPort;                            /** The port the JMX console runs on */
   private String m_JmxURL;                             /** The url for the JMX conslole */
   private String m_Name;
   private int m_Status; // The current run status of the program
   private Thread m_Thread; // Internal thread.
   private MBeanServer m_MBServer; // MBean server for report mgmt mbean
   private HtmlAdaptorServer m_HtmlServer; // Suns html protocal adaptor for jmx.

   /** A list of configuration files used by the server. */
   private static ArrayList<ConfData> m_ConfFiles;

   //
   // Instance var for the process server.
   private static ProcessServer m_Instance;

   //
   // Custom class loader. Static so it's only instantiated once.
   private static EmLoader m_Loader;

   //
   // Log4j logger
   public static Logger log = Logger.getLogger(ProcessServer.class.getName());

   //
   // Static initialization. Forces log4j to be initialized before anything
   // else.
   {
      initLog();
   }

   /**
    * Default constructor
    */
   public ProcessServer() 
   {
      m_Name = this.getClass().getSimpleName();
      m_Status = init;
      m_Thread = new Thread(this, "ProcServer");
      m_Thread.setDaemon(true);
      m_Apps = new HashMap<String, ProcessApplication>();
      m_ConfFiles = new ArrayList<ConfData>();
      m_MBServer = MBeanServerFactory.createMBeanServer();
      m_HtmlServer = new HtmlAdaptorServer();

      m_From = System.getProperty("mail.from", "noreply@emeryonline.com");

      if ( System.getProperty("server.env", "test").equalsIgnoreCase("test") )
         m_Env = Environment.Test;
      else
         m_Env = Environment.Production;
   }

   /**
    * Clean up any allocated resources.
    *
    * @throws Throwable
    */
   @Override
   public void finalize() throws Throwable 
   {
      m_Thread = null;
      m_MBServer = null;
      m_HtmlServer = null;
      m_Name = null;
      m_Apps.clear();
      m_Apps = null;
      m_ConfFiles.clear();
      m_ConfFiles = null;

      super.finalize();
   }

   /**
    * Checks the configuration file for changes and adjusts the applications as
    * necessary.
    */
   private void checkConfs() 
   {
      String fileSep = System.getProperty("file.separator", "/");
      File file = new File(System.getProperty("user.dir") + fileSep + "conf");
      File files[] = file.listFiles();
      ConfData tmp = null;
      boolean found = false;

      try {
         if ( files != null ) {
            for ( int i = 0; i < files.length; i++ ) {
               if ( files[i].isFile() ) {
                  tmp = new ConfData();
                  tmp.fileName = files[i].getName();
                  tmp.lastModified = files[i].lastModified();

                  for ( int j = 0; j < m_ConfFiles.size(); j++ ) {
                     if ( m_ConfFiles.get(j).fileName.equals(tmp.fileName) ) {
                        if ( m_ConfFiles.get(j).lastModified != tmp.lastModified ) {
                           m_ConfFiles.get(j).lastModified = tmp.lastModified;

                           if ( m_ConfFiles.get(j).confObj != null )
                              m_ConfFiles.get(j).confObj.loadConf();
                        }

                        found = true;
                        break;
                     }
                  }

                  if ( !found )
                     m_ConfFiles.add(tmp);
               }
            }
         }
      }

      finally {
         fileSep = null;
         tmp = null;
         files = null;
      }
   }

   /**
    * Used by the Apache daemon process. Destroy any object created in init()
    */
   public void destroy() 
   {
      log.info("In destroy");
   }

   /**
    * Get the internal application id for the application specified.
    * 
    * @param name The name of the application.
    * 
    * @return The internal application id or -1 of the app is not found.
    */
   public synchronized long getAppId(String name)
   {
      long id = -1;
      Iterator<ProcessApplication> iter = m_Apps.values().iterator();
      ProcessApplication app = null;
            
      try {
         while ( iter.hasNext() ) {
            app = iter.next();

            if ( app.getName().equals(name) ) {
               id = app.getId();
               break;
            }                       
         }                     
      }

      finally {
         iter = null;
         app = null;
      }
      
      return id;
   }
   
   /**
    * @return The environment as a byte based on the oagis constants.
    */
   public synchronized Environment getEnv() 
   {
      return m_Env;
   }

   /**
    * @return The environment as a string.
    */
   public synchronized String getEnvStr() 
   {
      return m_Env == Environment.Production ? "Production" : "Test";
   }

   /**
    * @return The current JMX console IP address for the server it's running
    *         on.
    */
   public synchronized String getJmxIPAddr() 
   {
      if ( m_JmxIPAddr == null || m_JmxIPAddr.length() == 0 )
         m_JmxIPAddr = System.getProperty("jmx.ipaddr", "127.0.0.1");

      return m_JmxIPAddr;
   }

   /**
    * @return The current JMX console port
    */
   public synchronized String getJmxPort() 
   {
      if ( m_JmxPort == null || m_JmxPort.length() == 0 )
         m_JmxPort = System.getProperty("jmx.port", "9190");

      return m_JmxPort;
   }

   /**
    * @return The current JMX console URL
    */
   public synchronized String getJmxURL() 
   {
      if ( m_JmxURL == null || m_JmxURL.length() == 0 )
         m_JmxURL = System.getProperty("jmx.url", String.format("http://%s:%s", getJmxIPAddr(), getJmxPort()));

      return m_JmxURL;
   }

   /**
    * Get an instance of this process server.
    * 
    * @return This process server instance
    */
   public static synchronized ProcessServer getInstance() 
   {
      if ( m_Instance == null ) {
         m_Instance = new ProcessServer();
      }

      return m_Instance;
   }

   /**
    * Gets the custom class loader.
    *
    * @return An instance of the EmLoader.
    * @throws MalformedURLException
    */
   public static synchronized EmLoader getLoader() throws MalformedURLException 
   {
      if ( m_Loader == null ) {
         m_Loader = new EmLoader();
         m_Loader.addLocation("file:/");         
         m_Loader.addLocation("file:/usr/local/procserver/");         
         m_Loader.addLocation("file:/usr/local/procserver/com/emerywaterhouse/ace/");
         m_Loader.addLocation("file:/usr/local/procserver/com/emerywaterhouse/conf/");
         m_Loader.addLocation("file:/usr/local/procserver/com/emerywaterhouse/edoc/");
         m_Loader.addLocation("file:/usr/local/procserver/com/emerywaterhouse/email/");
         m_Loader.addLocation("file:/usr/local/procserver/com/emerywaterhouse/fas/");
         m_Loader.addLocation("file:/usr/local/procserver/com/emerywaterhouse/fax/");
         m_Loader.addLocation("file:/usr/local/procserver/com/emerywaterhouse/server/");         
      }

      return m_Loader;
   }

   /**
    * Creates and instantiates a ProcessApplication class.
    *
    * @param className
    *            The fully qualified class name.
    * @return An instantiated object of className or null if there was an
    *         error.
    */
   private ProcessApplication getProcessApp(String className) 
   {
      ProcessApplication app = null;
      Class<?> c = null;
      Object obj = null;

      try {
         if (className != null) {
            // c = getLoader().loadClass(className);
            c = Class.forName(className);

            if (c != null) {
               obj = c.newInstance();

               if (obj instanceof ProcessApplication)
                  app = (ProcessApplication) obj;
               else
                  log.error("[ProcessServer]" + className + " is not an instance of the class ProcessApplication");
            } 
            else
               log.error("[ProcessServer] unable to instantiate " + className);
         }
      }

      catch ( Exception ex ) {
         log.error("[ProcessServer] ", ex);
      }

      finally {
         obj = null;
         c = null;
         className = null;
      }

      return app;
   }
   
   /**
    * Gets the routing information if there is any and adds that to the list of recipients.
    * The default programming user id is skipped so we don't end up sending this to the
    * programming department.
    *
    * @param sys The system to get recipients for.
    * @param comp The component to get recipients for.
    * @param level The notification level.
    *
    * @return A list of recipients based on the system, component, and the notification level.
    *
    * @throws Exception on error
    */
   public static synchronized ArrayList<String> getRecipients(String sys, String comp, int level) throws Exception
   {
      ArrayList<String> recips = new ArrayList<String>();
      boolean allowProg = comp != null && comp.equals("TESTING");
      RoutingInfo ri = new RoutingInfo();
      Connection conn = null;
      String buf = null;
      int start = 0;
      int end = 0;
      int index = 0;
      int i;
      int j;

      try {
         if ( comp == null || sys == null )
            throw new Exception("missing system or component parameter");
        
         conn = ConnPool.getInstance().getEDBConn();
         buf = ri.getRoutingInfo(conn, sys, comp, level);
         start = buf.indexOf("<Recipient>", index);

         while ( start >= 0 ) {
            end = buf.indexOf("</Recipient>", index);
            i = buf.indexOf("<UserId>", start) + "<UserId>".length();
            j = buf.indexOf("</UserId>", start);

            //
            // We don't want this going to the default programming email address unless it's
            // being tested.  Add any other recipients to the list.
            if ( allowProg || !buf.substring(i, j).equalsIgnoreCase("programming") ) {
               i = buf.indexOf("<Address>", start) + "<Address>".length();
               j = buf.indexOf("</Address>", start);
               recips.add(buf.substring(i, j));
            }

            index = end + "</Recipient>".length();
            start = buf.indexOf("<Recipient>", index);
         }
      }

      finally {
         DbUtils.closeDbConn(conn, null, null);
         buf = null;
         ri = null;
         conn = null;
      }

      return recips;   
   }
   
   /**
    * Gets the status information from the server and running reports. Note -
    * Currently there is not a custom html adapter and the sun provided adapter
    * uses an html dtd that is a little old. Also there is no way to link in a
    * style sheet. This means we have to use some odd html.
    *
    * @return an html page containing the status data from the server, the list
    *         of currently running reports, and the report status.
    */
   public synchronized String getStatusInfo() 
   {
      StringBuffer html = new StringBuffer(1024);
      Iterator<ProcessApplication> iter = m_Apps.values().iterator();
      ProcessApplication app = null;

      try {
         //
         // Setup the banner
         html.append("<div style=\"height: 22px; margin: 0; padding-top: 6px;");
         html.append("padding-left: 8px; padding-bottom: 4px;");
         html.append("background-color: #4682b4;");
         html.append("vertical-align: middle; text-align: left;");
         html.append("font-family: Arial, Helvetica, sans-serif; font-weight: bold; font-size: 13px;");
         html.append("color: white\"> Emery &#124; Waterhouse &nbsp; &nbsp; Process Server Status Information</div>\r\n");

         //
         // Iterate through each application and get the status information
         // to display.
         while ( iter.hasNext() ) {
            app = iter.next();

            //
            // Setup the div for the body of text.
            html.append("<div style=\"font-family: Arial, Helvetica, sans-serif; font-size: 12px; text-align: left; vertical-align: top\">\r\n");
            html.append(app.getProcessStatus());
            html.append("</div>");
         }
      }

      finally {
         iter = null;
         app = null;
      }

      return html.toString();
   }

   /**
    * Called by the Apache daemon process. Initialize any application objects
    * needed.
    * 
    * @param arguments
    */
   public void init(String[] arguments) 
   {
      log.info("In init");
   }

   /**
    * Initialize the logger.
    */
   private void initLog() 
   {
      AsyncAppender asyncAppender = null;

      try {
         loadProperties();
         DOMConfigurator.configure("logcfg.xml");
         asyncAppender = (AsyncAppender) Logger.getRootLogger().getAppender("ASYNC");
         asyncAppender.setBufferSize(15);
         asyncAppender.setLocationInfo(true);
      }

      catch (Exception ex) {

      }
   }

   /**
    * Loads the applications and properties listed in the application conf
    * file. If the application is supposed to start automatically it's started.
    */
   public void loadApps() 
   {
      ProcessApplication app = null;
      String fileSep = null;
      String fileName = null;
      String appName = null;
      String appClass = null;
      boolean start = false;
      String propName = null;
      String propValue = null;
      VTDGen vg = new VTDGen();
      AutoPilot ap = new AutoPilot();
      BookMark bm = new BookMark();
      VTDNav vn = null;
      int i = -1;
      HashMap<String, String> props = new HashMap<String, String>();

      try {
         fileSep = System.getProperty("file.separator", "/");
         fileName = System.getProperty("user.dir") + fileSep + "conf" + fileSep + "app.xml";

         //
         // Load the applications found in the conf file.
         if ( vg.parseFile(fileName, true) ) {
            vn = vg.getNav();
            ap.bind(vn);
            bm.bind(vn);
            ap.selectXPath("/Applications/App");

            while ( ap.evalXPath() != -1 ) {
               bm.recordCursorPosition();

               i = vn.getAttrVal("name");
               if ( i != -1)
                  appName = vn.toNormalizedString(i);

               //
               // Get the class name attribute
               i = vn.getAttrVal("class");
               if ( i != -1)
                  appClass = vn.toNormalizedString(i);

               //
               // Get the class name attribute
               i = vn.getAttrVal("autoStart");
               if ( i != -1 )
                  start = Boolean.parseBoolean(vn.toNormalizedString(i));

               //
               // Iterate through the properties list and build
               // the map of name/value pairs.
               if ( vn.toElement(VTDNav.FIRST_CHILD, "Property") ) {
                  i = vn.getAttrVal("name");
                  if (i != -1 )
                     propName = vn.toNormalizedString(i);

                  i = vn.getAttrVal("value");
                  if ( i != -1 )
                     propValue = vn.toNormalizedString(i);

                  props.put(propName, propValue);

                  while (vn.toElement(VTDNav.NEXT_SIBLING, "Property")) {
                     i = vn.getAttrVal("name");
                     if ( i != -1 )
                        propName = vn.toNormalizedString(i);

                     i = vn.getAttrVal("value");                     
                     if ( i != -1 )
                        propValue = vn.toNormalizedString(i);

                     props.put(propName, propValue);
                  }
               }

               if ( !m_Apps.containsKey(appName) ) {
                  app = getProcessApp(appClass);

                  if ( app != null ) {
                     app.setName(appName);
                     app.setAutoStart(start);
                     app.setProperties(props);
                     app.setServer(this);
                     m_Apps.put(appName, app);

                     try {
                        if ( app.getAutoStart() ) {
                           log.info(String.format("[ProcessServer] starting %s", app.getName()));
                           app.start();
                        }
                     }

                     catch ( Exception ex ) {
                        log.error(String.format("[ProcessServer] unable to start %s", app.getName()));
                        log.error("[ProcessServer]", ex);
                     }
                  }
               }

               props.clear();
               bm.setCursorPosition();
            }
         }
      }

      catch ( Exception ex ) {
         log.error("[ProcessServer]", ex);
      }

      finally {
         vg = null;
         vn = null;
         ap = null;
         bm = null;
         props = null;
      }
   }

   /**
    * Implements the interface method. Wrapper around the application loading
    * process.
    */
   public void loadConf() 
   {
      loadApps();
   }

   /**
    * Loads the mbeans for the report server and sets any attributes on those
    * beans that need to be set. The mbeans are used for managing the report
    * server.
    *
    * @throws Exception if there are problems with the mbean loading
    */
   private void loadMBeans() throws Exception {
      ObjectName objName = null;
      ProcessServerMgmt mgtBean = new ProcessServerMgmt(this);

      //
      // Load up the server management class and set the server reference
      // so the bean can call server methods.
      objName = new ObjectName("ProcessServerMgmt:name=ServerMgmt");
      m_MBServer.registerMBean(mgtBean, objName);

      objName = new ObjectName("HtmlAdaptorServer:name=HtmlAdaptor");
      m_MBServer.registerMBean(m_HtmlServer, objName);
   }

   /**
    * Loads the system properties for the server.
    */
   private void loadProperties() 
   {
      String propFileName = null;
      FileInputStream propFile = null;
      Properties p = new Properties(System.getProperties());
      String sepChar = p.getProperty("file.separator");
      String appDir = p.getProperty("user.dir");

      try {
         //
         // Set the path to the properties file for the application.
         propFileName = appDir + sepChar + ProcessServer.propFile;
         propFile = new FileInputStream(propFileName);
         p.load(propFile);
         System.setProperties(p);
      }

      catch ( Exception ex ) {
         ;
      }

      finally {
         if ( propFile != null ) {
            try {
               propFile.close();
               propFile = null;
            }

            catch (Exception ex) {

            }
         }

         p = null;
         appDir = null;
         sepChar = null;
         propFileName = null;
      }
   }

   /**
    * @param args
    */
   public static void main(String[] args) 
   {
      ProcessServer server = ProcessServer.getInstance();

      try {
         server.start();
      }

      catch ( Exception ex ) {
         server.shutdown(ProcessServer.fatal);
      }
   }

   /**
    * Sends an email notification to the MIS department.
    *
    * @param msg - The email message to send.
    */
   public void notifyMis(String msg) 
   {
      String[] recips = null;
      String subj = "System Notification";

      if ( msg != null ) {
         switch ( m_Env ) {
            case Test: {
               subj = "[TEST] " + subj;
               recips = testList;
               break;
            }
   
            case Production: {
               recips = errorList;
               break;
            }
         }

         try {
            DataSender.smtp(m_From, recips, subj, String.format("Process Server \r\n %s", msg));
         }

         catch ( Exception ex ) {
            log.error("[ProcessServer]", ex);
         }

         finally {
            recips = null;
            subj = null;
         }
      }
   }

   /**
    * JMX implementation.  Pass through method to the application for further processing.
    * 
    * @param appId The internal ID number of the application to process the data.
    * @param data The data to be processed by the application
    * 
    * @return The outcome of processing the data.
    * 
    * @see com.emerywaterhouse.server.ProcessServerMgmtMBean#processData()
    */
   public synchronized int processData(long appId, byte[] data)
   {
      int result = 0;      
      Iterator<ProcessApplication> iter = m_Apps.values().iterator();
      ProcessApplication app = null;
            
      try {
         while ( iter.hasNext() ) {
            app = iter.next();

            if ( app.getId() == appId ) {               
               result = app.processData(data);
               break;
            }                       
         }                     
      }
      
      finally {
         iter = null;
         app = null;
      }
            
      return result;
   }
   
   /**
    * Registers a class that wants dynamic configuration with the server class.
    * Links the instance of the class with the configuration file name and adds
    * that class to the list of files and objects that need to be checked.
    *
    * By default all files in the conf directory are read, but not all will
    * have an associated object with them.
    *
    * @param obj A reference to a class that uses the ConfLoader interface.
    * @param confFile The name of the configuration file without the path.
    */
   public synchronized static void registerConfObj(ConfLoader obj, String confFile) 
   {
      boolean canAdd = true;
      ConfData tmp = null;

      if ( obj != null && (confFile != null && confFile.length() > 0) ) {
         synchronized (m_ConfFiles) {
            for ( int i = 0; i < m_ConfFiles.size(); i++ ) {
               tmp = m_ConfFiles.get(i);

               //
               // The config file may have already been loaded at startup
               // before the module
               // requiring dynamic conf file loading has registered with
               // the system.
               if ( tmp.fileName.equals(confFile) ) {
                  if ( tmp.confObj == null )
                     tmp.confObj = obj;

                  canAdd = false;
                  break;
               }
            }

            if ( canAdd ) {
               tmp = new ProcessServer.ConfData();
               tmp.confObj = obj;
               tmp.fileName = confFile;

               m_ConfFiles.add(tmp);
            }
         }
      }
   }

   /**
    * Implements the runnable interface. This allows the program to monitor
    * when to shut down independent of the other processes that are running.
    */
   public void run() 
   {
      try {
         log.info(String.format("[%s] server started", m_Name));

         //
         // Process incoming commands and send out status updates.
         while (m_Status == running) {
            checkConfs();
            Thread.sleep(second * 10);
         }
      }

      catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
      }

      catch (Exception ex) {
         log.fatal(String.format("[%s]", m_Name), ex);
         shutdown(fatal);
      }
   }

   /**
    * Wraps the sendMail method that accepts a String[]. Allows classes to use
    * the ArrayList and gets passed the runtime class cast exception.
    *
    * @param recips The recipient list
    * @param subj The subject of the email
    * @param msg The message.
    */
   public static synchronized void sendEmailNotification(ArrayList<String> recips, String subj, String msg) 
   {
      String[] tmp = null;
      Iterator<String> iter = null;
      int i = 0;

      if ( recips != null && recips.size() > 0 ) {
         iter = recips.iterator();
         tmp = new String[recips.size()];

         while (iter.hasNext()) {
            tmp[i] = iter.next();
            i++;
         }

         sendEmailNotification(tmp, subj, msg);
      }
   }

   /**
    * Sends an email notification to the recipients in the EMail input
    * parameter.
    * 
    * @param recips List of recipients
    * @param subj The email subject
    * @param msg The email message
    */
   public static synchronized void sendEmailNotification(String[] recips, String subj, String msg) 
   {
      if (msg != null && (recips != null && recips.length > 0)) {
         if (subj == null || subj.length() == 0)
            subj = "Notification";

         try {
            DataSender.smtp(m_From, recips, subj, msg);
         }

         catch (Exception ex) {
            log.error("[ProcessServer]", ex);
         }
      }
   }

   /**
    * Shuts down the report server and all sub processes.
    * 
    * @param mode The mode to shut down the server
    */
   public void shutdown(int mode) 
   {
      Iterator<ProcessApplication> iter = null;
      ProcessApplication proc = null;

      try {
         if ( m_Status == running ) {
            if ( mode < stopped ) {
               log.warn("[ProcessServer] invalid shutdown mode");
            } 
            else {
               if (m_Apps.size() > 0) {
                  iter = m_Apps.values().iterator();

                  while (iter.hasNext()) {
                     proc = iter.next();
                     log.info(String.format("[ProcessServer] stopping %s", proc.getName()));
                     proc.stop();
                  }
               }

               log.info("[ProcessServer] shutting down the server");
               m_Status = mode;
               m_Thread.interrupt();
               m_HtmlServer.stop();
            }
         }
      }

      finally {
         iter = null;
         proc = null;
      }
   }

   /**
    * Starts the server.
    *
    * @throws Exception
    */
   public void start() throws Exception 
   {
      int port = Integer.parseInt(System.getProperty("jmx.port", "8282"));

      log.info("[ProcessServer] starting");

      //
      // Start the application processes.
      try {
         //
         // JMX console
         log.info(String.format("[ProcessServer] starting the JMX HTML server on port: %d", port));
         loadMBeans();
         m_HtmlServer.setPort(port);
         m_HtmlServer.start();

         //
         // Reads the conf file and loads all the applications
         loadApps();
      }

      catch (Exception ex) {
         log.fatal("[ProcessServer]", ex);
         m_Status = abort;
      }

      if ( m_Status == fatal || m_Status == abort ) {
         log.fatal("[ProcessServer] aborting startup");
         shutdown(abort);
      } 
      else {
         registerConfObj(this, "app.xml");
         m_Status = running;
         m_Thread.start();
      }
   }

   /**
    * Starts a specific application's job or process.
    * 
    * @param appId The internal identifier of the application.
    * @param jobId The internal identifier of the job or process.
    * 
    * @return true if the job was started, false if not.
    */
   public boolean startJob(long appId, long jobId) 
   {
      boolean started = false;
      Iterator<ProcessApplication> iter = m_Apps.values().iterator();
      ProcessApplication app = null;

      try {
         while ( iter.hasNext() ) {
            app = iter.next();

            if (app.getId() == appId) {
               log.info(String.format("[ProcessServer] starting %s job ID: %d", app.getName(), jobId));
               started = app.startJob(jobId);
               break;
            }
         }
      }

      finally {
         iter = null;
      }

      return started;
   }

   /**
    * Called by the Apache daemon processor. Wrapper around the shutdown
    * method.
    */
   public void stop() 
   {
      log.info("In stop");
      shutdown(stopped);
   }

   /**
    * Stops a specific application's job or process.
    * 
    * @param appId The internal identifier of the application.
    * @param jobId The internal identifier of the job or process.
    * 
    * @return true if the job was stopped, false if not.
    */
   public boolean stopJob(long appId, long jobId) 
   {
      boolean stopped = false;
      Iterator<ProcessApplication> iter = m_Apps.values().iterator();
      ProcessApplication app = null;

      try {
         while ( iter.hasNext() ) {
            app = iter.next();

            if ( app.getId() == appId ) {
               log.info(String.format("[ProcessServer] stopping %s job ID: %d", app.getName(), jobId));
               stopped = app.stopJob(jobId);
               break;
            }
         }
      }

      finally {
         iter = null;
      }

      return stopped;
   }

   /**
    * Stops a specific application's job or process.
    * 
    * @param appId The internal identifier of the application.
    * @param jobName The name of the job or process.
    * 
    * @return true if the job was stopped, false if not.
    */
   public boolean stopJob(long appId, String jobName) 
   {
      boolean stopped = false;
      Iterator<ProcessApplication> iter = m_Apps.values().iterator();
      ProcessApplication app = null;

      try {
         while ( iter.hasNext() ) {
            app = iter.next();

            if ( app.getId() == appId ) {
               log.info(String.format("[ProcessServer] stopping app: %s; job name: %s", app.getName(), jobName));
               stopped = app.stopJob(jobName);
               break;
            }
         }
      }

      finally {
         iter = null;
      }

      return stopped;
   }

   /**
    * A helper class that's used when checking to see if a file has been
    * modified and for registering classes that want dynamic configuration file
    * loading.
    */
   private static class ConfData 
   {
      public String fileName;
      public long lastModified;
      public ConfLoader confObj;

      ConfData() {
         fileName = "";
         lastModified = 0;
         confObj = null;
      }
   }
}
