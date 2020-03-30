/**
 * File: DefaultSyncFaxConf.java
 * Description: Creates the default confirmation email message for a SyncFaxDisposition BOD
 *
 * @author Jeff Fisher
 *
 * Create Date: 06/27/2011
 * Last Update: $Id: DefaultSyncFaxConf.java,v 1.3 2011/09/22 21:49:28 jfisher Exp $
 *
 * History:
 *    $Log: DefaultSyncFaxConf.java,v $
 *    Revision 1.3  2011/09/22 21:49:28  jfisher
 *    Switched to the FaxFinderStatus object.
 *
 *    Revision 1.2  2011/09/16 18:27:15  jfisher
 *    Initial changes to deal with using the fax server vs efax
 *
 *    Revision 1.1  2011/06/27 20:26:40  jfisher
 *    Initial add
 *
 *
 */
package com.emerywaterhouse.edoc;

import java.lang.reflect.Method;

import com.emerywaterhouse.fax.FaxFinderStatus;

public class DefaultSyncFaxConf extends EmailConf
{

   private static final String parentName = "com.emerywaterhouse.conf.SyncFaxDispProc";

   /**
    *
    */
   public DefaultSyncFaxConf()
   {
      super();
   }

   /**
    * Cleanup resources.
    *
    * @see com.emerywaterhouse.edoc.EmailConf#finalize()
    */
   @Override
   public void finalize() throws Throwable
   {
      super.finalize();
   }

   /**
    * Overrides the base class method and manages the formatting of the confirmation email.
    *
    * Note - We have to use reflection to get the correct method from the SyncFacBod class
    *    because of some class loader issues and class cast exceptions.
    *
    * @see com.emerywaterhouse.edoc.EmailConf#formatMsg()
    */
   @Override
   protected void formatMsg() throws Exception
   {
      StringBuffer msg = new StringBuffer();
      String url = Byte.parseByte(System.getProperty("server.mode")) == 2 ?
            "http://www.emeryonline.com/efax/faxdisp?trans=" :
               "http://testapp2/efax/faxdisp?trans=";
      FaxFinderStatus fs = null;
      Method method = null;
      Object[] params = new Object[0];

      try {
         m_Subj = "Fax Confirmation";

         if ( m_Parent != null ) {
            method = m_Parent.getClass().getMethod("getFaxStatus", (Class<?>[])null);
            fs = (FaxFinderStatus)method.invoke(m_Parent, params);
            msg.append(String.format("Disposition for fax transmission %s on %s at %s\r\n", fs.docId, fs.lastDate, fs.lastTime));
            msg.append(String.format("Outcome: %s-%s\r\n", fs.state, fs.outcome));
            msg.append(String.format("Status Message: %s\r\n", fs.statusMsg));
            msg.append(String.format("Recipient Company:  %s\r\n", fs.recipientCompany));
            msg.append(String.format("Recipient Name:  %s\r\n", fs.recipientName));
            msg.append(String.format("Recipient FAX Number: %s", fs.recipientFax));
            msg.append("\r\n\r\n\r\n");
            msg.append("To get the complete fax disposition use this link: ");
            msg.append(url);
            msg.append(fs.transmissionId);
            //TODO - Remove the efax url and replace with a fax server url.
         }
         else {
            log.warn("[DefaultSyncFaxConf] formatMsg - null parent");
            msg.append("There was an issue getting complete fax disposition information. \r\n");
            msg.append("Contact the MIS department for help with determining the success of sending ");
            msg.append("the fax.");
         }

         msg.append("\r\n\r\n\r\n");
         msg.append("This is an automated response, please do not respond to this email. ");
         msg.append("If you believe this message was received in error, contact the MIS department. ");

         m_Msg = msg.toString();
      }

      finally {
         msg = null;
         url = null;
         fs = null;
         params = null;
      }
   }


   /**
    * Override the base class method so that we can do a check for the correct
    * object type.
    *
    * Note - Because of the vagaries between the Windows VM and the Linux VM the use
    *    of instanceof couldn't be used because the Linux version return false while the
    *    Windows version returned true.  Probably a class loader issue.  The class name
    *    comparison provides a way around that issue.
    *
    * @param obj A reference to the object that created this object.
    */
   @Override
   public void setParent(Object obj)
   {
      String name = obj.getClass().getName();

      if ( name.equals(parentName) )
         super.setParent(obj);
      else
         log.error(String.format("DefaultSyncFaxConf] setParent - %s is not SyncFaxDispProc", name));
   }
}
