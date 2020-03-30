/**
 * File: EmailConfFactory.java
 * Description: Factory class that can be used to instantiate EmailConf objects based on the system and 
 *    component.
 *
 * @author Jeff Fisher
 *
 * Create Date: 07/16/2009
 * Last Update: $Id: EmailConfFactory.java,v 1.2 2009/07/29 14:21:46 jfisher Exp $
 *
 * History:
 *    $Log: EmailConfFactory.java,v $
 *    Revision 1.2  2009/07/29 14:21:46  jfisher
 *    removed the missing class name log message.
 *
 *    Revision 1.1  2009/07/17 15:56:28  jfisher
 *    Initial add
 *
 */
package com.emerywaterhouse.edoc;

import java.io.File;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.emerywaterhouse.server.ConfLoader;
import com.emerywaterhouse.server.ProcessServer;

public class EmailConfFactory implements ConfLoader
{
   private static String confFileName = "emailconf.xml";
   private static EmailConfFactory m_Instance;
   private HashMap<String, String> m_EmailConfs;
   
   //
   // Log4j logger
   private static Logger log = Logger.getLogger("com.emerywaterhouse.edoc.EmailConfFactory");
   
   /**
    * Initialize the class.
    */
   public EmailConfFactory()
   {
      super();
            
      m_EmailConfs = new HashMap<String, String>();
      loadConf();
      ProcessServer.registerConfObj(this, confFileName);
   }

   /**
    * Clean up anything we created.
    * 
    * @throws Throwable
    */
   public void finalize() throws Throwable
   {      
      super.finalize();
   }
   
   /**
    * Creates an instance of the EmailConf factory.
    * 
    * @return The object reference to the EmailConf factory class.
    */
   public static EmailConfFactory getInstance()
   {
      if ( m_Instance == null ) {
         m_Instance = new EmailConfFactory();
      }
      
      return m_Instance;
   }
   
   /**
    * Convenience method for calling getEmailConf without an id.
    * 
    * @param system The system that generated the request for the conf. ie EIS
    * @param component The component the generated the request for a conf. ie VNDPO
    *    
    * @return An instance of an EmailConf object or null if no class can be instantiated.
    * 
    * @throws Exception
    */
   public EmailConf getEmailConf(String system, String component) throws Exception
   {
      return getEmailConf(system, component, null);
   }
   
   /**
    * Creates and email confirmation object based on the system, component and an optional
    * id.  The key will be the concatenation of the the three parameters.
    * 
    * @param system The system that generated the request for the conf. ie EIS
    * @param component The component the generated the request for a conf. ie VNDPO
    * @param id An optional id field if more granularity is needed than can be achieved with the
    *    system and component.
    *    
    * @return An instance of an EmailConf object or null if no class can be instantiated.
    * 
    * @throws Exception
    */
   public EmailConf getEmailConf(String system, String component, String id) throws Exception
   {
      String key = null;
      Class<?> c = null;
      Object obj = null;
      EmailConf conf = null;
      String className = null;
      
      if ( system == null || system.length() == 0 )
         throw new Exception("[EmailConfFactory] missing or invalid system");
      
      if ( component == null || component.length() == 0 )
         throw new Exception("[EmailConfFactory] missing or invalid system");
      
      try {
         key = system + component;
         
         if ( id != null )
            key += id;
         
         synchronized(m_EmailConfs) {
            className = m_EmailConfs.get(key);
         }
         
         if ( className != null && className.length() > 0 ) {
            c = ProcessServer.getLoader().loadClass(className);
            
            if ( c != null ) {
               obj = c.newInstance();
                              
               if ( obj instanceof EmailConf ) {
                  conf = (EmailConf)obj;
                  conf.setComponent(component);
                  conf.setSystem(system);
               }
               else
                  log.error("[EmailConfFactory] " + className + " is not an instance of the class EmailConf");
            }
            else
               log.error("[EmailConfFactory] unable to instantiate " + className);
         }         
      }
      
      catch ( Exception ex ) {
         log.error("exception: ", ex);
      }
      
      finally {
         obj = null;
         c = null;
         key = null;
         className = null;
      }
      
      return conf;      
   }
   
   /**
    *  Implements the method for the interface ConfLoader.  Loads the configuration file data
    *  and fills the hash map with the classes that need to be instantiated based on the system
    *  component. 
    */
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
      String sys = null;
      String comp = null;
      String confClass = null;
      
      try {
         fileSep = System.getProperty("file.separator", "/");
         file = new File(System.getProperty("user.dir") + fileSep + "conf" + fileSep + confFileName);
         db = dbf.newDocumentBuilder();
         doc = db.parse(file);
         doc.getDocumentElement().normalize();
         objList = doc.getElementsByTagName("confObject");
         
         synchronized( m_EmailConfs ) {
            m_EmailConfs.clear();
            
            for ( int i = 0; i < objList.getLength(); i++ ) {
               objNode = objList.item(i);
               
               if ( objNode.getNodeType() == Node.ELEMENT_NODE ) {
                  objElmnt = (Element)objNode;
                 
                  //
                  // Get the system node list
                  tmpList = objElmnt.getElementsByTagName("system");
                  tmpElmnt = (Element)tmpList.item(0);
                  tmpList = tmpElmnt.getChildNodes();
                  sys = (tmpList.item(0)).getNodeValue();
                  
                  tmpList = objElmnt.getElementsByTagName("component");
                  tmpElmnt = (Element)tmpList.item(0);
                  tmpList = tmpElmnt.getChildNodes();
                  comp = (tmpList.item(0)).getNodeValue();
                  
                  tmpList = objElmnt.getElementsByTagName("class");
                  tmpElmnt = (Element)tmpList.item(0);
                  tmpList = tmpElmnt.getChildNodes();
                  confClass = (tmpList.item(0)).getNodeValue();
                 
                  m_EmailConfs.put(sys + comp, confClass);
               }            
            }
         }
      }
      
      catch ( Exception ex ) {         
         log.error("exception", ex);
      }
      
      finally {
         fileSep = null;
         confClass = null;
         sys = null;
         comp = null;
         tmpList = null;
         tmpElmnt = null;
         objElmnt = null;
         objNode = null;
         objList = null;
     }
   }   
}
