/**
 * File: ApgVndPoFaxConf.java
 * Description: Sends an email confirmation for APG PO faxes sent to vendors.
 *
 * @author Erik Pearson
 *
 * Create Date: 05/28/2010
 *
 * History:
 *    $Log: ApgVndPoFaxConf.java,v $
 *    Revision 1.7  2012/01/04 12:02:42  jfisher
 *    Changed processing to use FaxFinderStatus vs FaxStatus
 *
 *    Revision 1.6  2011/12/20 13:07:00  jfisher
 *    Switched to fax server fax status.
 *
 *    Revision 1.5  2011/09/22 21:48:39  jfisher
 *    Changed method signatures.
 *
 *    Revision 1.4  2011/09/16 18:27:15  jfisher
 *    Initial changes to deal with using the fax server vs efax
 *
 *    Revision 1.3  2011/06/23 21:39:05  jfisher
 *    Mods for email confirmation
 *
 *    Revision 1.2  2010/06/23 08:46:16  epearson
 *    Added vendor name, id, po# to fax confirmation.
 *
 *    Revision 1.1  2010/06/02 02:07:54  epearson
 *    Initial add
 *
 */
package com.emerywaterhouse.edoc;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.emerywaterhouse.fax.FaxFinderStatus;
import com.emerywaterhouse.utils.DbUtils;

public class ApgVndPoFaxConf extends EmailConf{

   private static final String parentName = "com.emerywaterhouse.conf.SyncFaxDispProc";
   private PreparedStatement m_GetVndPoDat = null;

   /**
    *
    */
   public ApgVndPoFaxConf()
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
      FaxFinderStatus fs = null;
      Method method = null;
      Object[] params = new Object[0];

      try {
         m_Subj = "Fax Confirmation - APG PO";

         if ( m_Parent != null ) {
            method = m_Parent.getClass().getMethod("getFaxStatus", (Class<?>[])null);
            fs = (FaxFinderStatus)method.invoke(m_Parent, params);
            prepareStatements();

            msg.append(getVndPoMsg(fs));
            msg.append("\r\n\r\n\r\n");
            msg.append("To get the complete fax disposition use this link: ");
            msg.append(fs.transmissionId);
         }
         else {
            log.warn("[ApgVndPoFaxConf] formatMsg - null parent");
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
         fs = null;
         params = null;
      }
   }

   /**
    * Creates a specific message for APG po faxes.  It contains information
    * about the PO, vendor, and customer.
    *
    * @param fs A reference to the FaxStatus object.
    *
    * @return The APG vendor po fax message.
    */
   private String getVndPoMsg(FaxFinderStatus fs) throws Exception
   {
      StringBuffer msg = new StringBuffer();
      ResultSet rs = null;
      String quoteId = "";
      String custId = "";
      String custName = "";
      String vndId = "";
      String vndName = "";
      String poNbr = "";
      String tranDate = "";

      try {
         m_GetVndPoDat.setString(1, fs.docId);
         rs = m_GetVndPoDat.executeQuery();

         if ( rs.next() ) {
            quoteId = rs.getString(1);
            custId = rs.getString(2);
            custName = rs.getString(3);
            vndId = rs.getString(4);
            vndName = rs.getString(5);
            poNbr = rs.getString(6);
            tranDate = rs.getString(7);
         }

         msg.append(String.format("Disposition for fax transmission %s on %s at %s\r\n", fs.docId, fs.lastDate, fs.lastTime));
         msg.append("\r\n");
         msg.append(String.format("Quote ID: %s\r\n", quoteId));
         msg.append(String.format("Customer: %s %s\r\n", custId, custName));
         msg.append(String.format("Vendor: %s %s\r\n", vndId, vndName));
         msg.append(String.format("PO Number: %s\r\n", poNbr));
         msg.append(String.format("Sent on: %s\r\n", tranDate));
         msg.append("\r\n");
         msg.append(String.format("Outcome: %s\r\n", fs.outcome));
         msg.append(String.format("Status Message: %s\r\n", fs.statusMsg));
         msg.append("\r\n");
         msg.append(String.format("Recipient Company: %s\r\n", fs.recipientCompany));
         msg.append(String.format("Recipient Name: %s\r\n", fs.recipientName));
         msg.append(String.format("FAX Number: %s\r\n", fs.recipientFax));

         //
         // Set the subject while we have the PO data
         m_Subj = String.format("APG PO Fax Confirmation - Customer %s, Quote %s", custId, quoteId);

         return msg.toString();
      }

      finally {
         msg = null;
         DbUtils.closeDbConn(null, m_GetVndPoDat, rs);
      }
   }

   /**
    * Prepares the sql statements
    *
    * @throws Exception on errors
    */
   private void prepareStatements() throws Exception
   {
      StringBuffer sql = new StringBuffer();

      if ( m_Conn == null )
         throw new Exception("Unable to get database connection");

      try {
         sql.append("select ");
         sql.append("   quote.quote_id, customer.customer_id, customer.name, ");
         sql.append("	vendor_id, vendor_name, po_nbr, trans_date ");
         sql.append("from ");
         sql.append("   b2b_fax_disposition bfd, quote, quote_po, vendor_po,customer, apg_fax ");
         sql.append("where ");
         sql.append("   document_id = ? and ");
         sql.append("   bfd.fax_disp_id = apg_fax.fax_disp_id and ");
         sql.append("   apg_fax.quote_id = quote.quote_id and ");
         sql.append("   quote.customer_id = customer.customer_id and ");
         sql.append("	quote_po.quote_id = quote.quote_id and ");
         sql.append("	quote_po.vnd_po_id = vendor_po.vnd_po_id ");

         m_GetVndPoDat = m_Conn.prepareStatement(sql.toString());
      }

      finally {
         sql = null;
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
         log.error(String.format("[ApgVndPoFaxConf] setParent - %s is not SyncFaxDispProc", name));
   }
}
