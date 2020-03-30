/**
 * File: BodProcessorFactory.java
 *
 * Description: Factory class for generating bod processor objects.  Call the getInstance method
 *    to get an instance of the factory class.
 *
 * @author Jeff Fisher
 *
 * Create Date: 04/27/2018
 * Last Update: 
 *
 * History:
 *
 */
package com.emerywaterhouse.conf;

import java.io.File;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.emerywaterhouse.server.ConfLoader;
import com.emerywaterhouse.server.ProcessServer;

public class BodProcessorFactory implements ConfLoader
{
   private static final String confFileName = "confbodprocs.xml";
   private static BodProcessorFactory m_Instance;
   private HashMap<String, String> m_Processors;


   /**
    * Constructor is private so it can't be called except by this class.
    */
   private BodProcessorFactory()
   {
      super();

      m_Processors = new HashMap<String, String>();
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
      m_Processors.clear();
      m_Processors = null;

      super.finalize();
   }

   /**
    * Creates an instance of the translator factory.
    *
    * @return The object reference to the translator factory class.
    */
   public static BodProcessorFactory getInstance()
   {
      if ( m_Instance == null )
         m_Instance = new BodProcessorFactory();

      return m_Instance;
   }

   /**
    * Creates an BOD Processor instance based on the type of document.
    * @param docType The type of document that needs to be translated.  This must match an existing
    *    document type that the translator factory knows about.
    *
    * @return A reference to a newly created translator or null if the translator couldn't
    *    be instantiated.
    * @throws Exception 
    */
   public BodProcessor getProcessor(String docType) throws Exception
   {
      BodProcessor proc = null;
      String className = null;
      Class<?> c = null;
      Object obj = null;

      if ( docType != null && docType.length() > 0 ) {
         synchronized(m_Processors) {
            className = m_Processors.get(docType);            
         }

         if ( className != null ) {            
            try {
               c = ProcessServer.getLoader().loadClass(className);
   
               if ( c != null ) {
                  obj = c.newInstance();
   
                  if ( obj instanceof BodProcessor )
                     proc = (BodProcessor)obj;
                  else
                     ProcessServer.log.error("[BodProcessorFactory]" + className + " is not an instance of the class BodProcessor");
               }
               else
                  ProcessServer.log.error("[BodProcessorFactory] unable to instantiate " + className);
            }
   
            catch ( Exception ex ) {
               ProcessServer.log.error("[BodProcessorFactory]", ex);
            }
   
            finally {
               obj = null;
               c = null;
               className = null;
            }
         }
         else
            throw new Exception(String.format("Unable to load class for doc type: %s", docType));
      }

      return proc;
   }

   /**
    *  Implements the method for the interface ConfLoader.  Loads the configuration file data
    *  and fills the hash map with the classes that need to be instantiated based on the system
    *  component.
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
      String bod = null;;
      String confClass = null;

      try {
         fileSep = System.getProperty("file.separator", "/");
         file = new File(System.getProperty("user.dir") + fileSep + "conf" + fileSep + confFileName);
         db = dbf.newDocumentBuilder();
         doc = db.parse(file);
         doc.getDocumentElement().normalize();
         objList = doc.getElementsByTagName("bodProc");

         synchronized( m_Processors ) {
            m_Processors.clear();

            for ( int i = 0; i < objList.getLength(); i++ ) {
               objNode = objList.item(i);

               if ( objNode.getNodeType() == Node.ELEMENT_NODE ) {
                  objElmnt = (Element)objNode;

                  //
                  // Get the system node list
                  tmpList = objElmnt.getElementsByTagName("bod");
                  tmpElmnt = (Element)tmpList.item(0);
                  tmpList = tmpElmnt.getChildNodes();
                  bod = (tmpList.item(0)).getNodeValue();

                  tmpList = objElmnt.getElementsByTagName("class");
                  tmpElmnt = (Element)tmpList.item(0);
                  tmpList = tmpElmnt.getChildNodes();
                  confClass = (tmpList.item(0)).getNodeValue();

                  m_Processors.put(bod, confClass);
               }
            }
         }
      }

      catch ( Exception ex ) {
         ProcessServer.log.error("[BodProcessorFactory", ex);
      }

      finally {
         fileSep = null;
         confClass = null;
         bod = null;
         tmpList = null;
         tmpElmnt = null;
         objElmnt = null;
         objNode = null;
         objList = null;
      }
   }
}
