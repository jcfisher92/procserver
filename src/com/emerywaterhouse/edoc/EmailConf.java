/**
 * File: EmailConf.java
 * Description: Base class for sending out confirmation email based.  Allows for dynamic creation of the
 *    descendant classes based on the system and component that generates the confirmation.
 *
 * @author Jeff Fisher
 *
 * Create Date: 07/16/2009
 * Last Update: 05/01/2018
 *
 * History:
 *    $Log: EmailConf.java,v $
 *    Revision 1.7  2012/11/07 14:17:01  jfisher
 *    took out the string[] cast which caused a runtime exception
 *
 *    Revision 1.6  2012/11/01 21:03:01  jfisher
 *    Switched to the LWServer send email method from the wasp web service.
 *
 *    Revision 1.5  2011/09/22 21:48:14  jfisher
 *    Put the sendMail in a try catch block to provide better logging.
 *
 *    Revision 1.4  2011/06/23 21:39:41  jfisher
 *    Changes to deal with the CreateOrderConfirmation BOD and making things generic.
 *
 *    Revision 1.3  2009/07/29 14:19:27  jfisher
 *    Modified the sendConfirmation method to use the default email address if no recips
 *
 *    Revision 1.2  2009/07/23 19:04:05  jfisher
 *    Moved the getRecipent function to the LWServer class.
 *
 *    Revision 1.1  2009/07/17 15:55:56  jfisher
 *    Initial add
 *
 */
package com.emerywaterhouse.edoc;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import com.emerywaterhouse.server.ProcessServer;

public abstract class EmailConf
{
   protected String m_Component;
   protected Connection m_Conn;   
   protected String m_EmailAddr;
   protected String m_From;
   protected String m_Msg;
   protected Object m_Parent;
   protected String m_Subj;
   protected String m_System;

   //
   // Log4j logger
   protected static Logger log = Logger.getLogger("com.emerywaterhouse.edoc.EmailConf");

   /**
    *
    */
   public EmailConf()
   {
      super();
      m_From = "noreply@emeryonline.com";
   }

   /**
    * Clean up the resources.
    *
    * @see java.lang.Object#finalize()
    */
   @Override
   public void finalize() throws Throwable
   {
      m_Conn = null;
      
      super.finalize();
   }

   /**
    * Does any end of job cleanup needed.  Descendant classes should
    * override this if they need more cleanup.
    */
   public void close()
   {
      m_Conn = null;
      
      closeStatements();
   }

   /**
    * Descendant classes should override this method to close any open statements.
    */
   protected void closeStatements()
   {

   }

   /**
    * Abstract method that must be overridden.  Creates the format for the message.
    *
    * @throws Exception
    */
   protected abstract void formatMsg() throws Exception;


   /**
    * Sends an email confirmation.
    *
    * @throws Exception
    */
   @SuppressWarnings("unchecked")
   public void sendConfirmation() throws Exception
   {
      Method method = null;
      Object[] params = new Object[0];

      //
      // To be completely generic and not worry about possible class loading issues, we need to use
      // reflection to call the parent's method.
      if ( m_Parent != null ) {
         method = m_Parent.getClass().getMethod("getRecipients", (Class<?>[])null);

         if ( method != null ) {
            formatMsg();

            try {
               sendEmail((ArrayList<String>)method.invoke(m_Parent, params), m_Subj, m_Msg);
            }

            catch ( Exception ex ) {
               log.error("[EmailConf] sendConfirmation", ex);
            }
         }
         else
            throw new Exception("parent missing required method");
      }
   }

   /**
    * Sends an email notification.  Descendant classes can override this if they need specific
    *    emailing needs.  The default from is "noreply@emeryonline.com"
    *
    * @param recips The list of email addresses.
    * @param subj The email subject
    * @param msg The email message
    *
    * @throws Exception
    */
   protected void sendEmail(ArrayList<String> recips, String subj, String msg) throws Exception
   {
      if ( recips == null || recips.size() == 0 )
         throw new Exception("missing email recipients");

      if ( subj == null || subj.length() == 0 )
         throw new Exception("missing email subject");

      if (msg == null || msg.length() == 0 )
         throw new Exception("missing email message");

      ProcessServer.sendEmailNotification(recips, subj, msg);
   }

   /**
    * Sets the component field.
    *
    * @param component the component data to set.
    */
   public void setComponent(String component)
   {
      m_Component = component;
   }

   /**
    * Sets the oracle database connection.
    *
    * @param conn an active Oracle DB connection.
    */
   public void setConnection(Connection conn)
   {
      m_Conn = conn;
   }

   /**
    * Sets a default email address in case no routing information could be found.
    * @param addr The email address to set.
    */
   public void setDefaultEmailAddr(String addr)
   {
      m_EmailAddr = addr;
   }

   /**
    * Generic method to allow descendant classes the ability to process
    * extra data that may come in a request.
    *
    * @param data The data to be used for processing.
    * @throws Exception - Processing errors
    */
   public void setData(Object data) throws Exception
   {
      ;
   }

   /**
    * Sets the parent of this class, which would be the container or the class that
    * instantiated this object.
    *
    * @param obj A reference to the object that created this object.
    */
   public void setParent(Object obj)
   {
      m_Parent = obj;
   }

   /**
    * Sets a list of email recipients.  Place holder for descendant classes that need to provide
    * functionality beyond the default.
    *
    * @param recips The recipient list.
    */
   public void setRecipients(ArrayList<String> recips)
   {
      ;
   }

   /**
    * Sets the system field.
    *
    * @param system The system to set.
    */
   public void setSystem(String system)
   {
      m_System = system;
   }
}
