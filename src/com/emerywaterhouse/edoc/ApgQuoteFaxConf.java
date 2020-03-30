/**
 * File: ApgQuoteFaxConf.java
 * Description: Sends an email confirmation for APG Quote faxes sent.
 *
 * @author Erik Pearson
 *
 * Create Date: 06/19/2010
 *
 */
package com.emerywaterhouse.edoc;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.emerywaterhouse.fax.FaxFinderStatus;
import com.emerywaterhouse.utils.DbUtils;

public class ApgQuoteFaxConf extends EmailConf{

   private static final String parentName = "com.emerywaterhouse.conf.SyncFaxDispProc";
   private PreparedStatement m_GetQuoteDat = null;

   /**
    *
    */
   public ApgQuoteFaxConf()
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
         m_Subj = "Fax Confirmation - APG QUOTE";

         if ( m_Parent != null ) {
            method = m_Parent.getClass().getMethod("getFaxStatus", (Class<?>[])null);
            fs = (FaxFinderStatus)method.invoke(m_Parent, params);
            prepareStatements();

            msg.append(getQuoteMsg(fs));
            msg.append("\r\n\r\n\r\n");
            msg.append("To get the complete fax disposition use this link: ");
            msg.append(fs.transmissionId);
         }
         else {
            log.warn("[ApgQuoteFaxConf] formatMsg - null parent");
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
    * Creates a specific message for APG Quote faxes.
    *
    * @param fs A reference to the FaxStatus object.
    *
    * @return The APG quote fax message.
    */
   private String getQuoteMsg(FaxFinderStatus fs) throws Exception
   {
      StringBuffer msg = new StringBuffer();
      ResultSet rs = null;
      String quoteId = "";
      String custId = "";
      String custName = "";
      String tranDate = "";

      try {
         m_GetQuoteDat.setString(1, fs.docId);
         rs = m_GetQuoteDat.executeQuery();

         if ( rs.next() ) {
            quoteId = rs.getString(1);
            custId = rs.getString(2);
            custName = rs.getString(3);
            tranDate = rs.getString(4);
         }

         msg.append(String.format("Disposition for fax transmission %s on %s at %s\r\n", fs.docId, fs.lastDate, fs.lastTime));
         msg.append("\r\n");
         msg.append(String.format("Quote ID: %s\r\n", quoteId));
         msg.append(String.format("Customer: %s %s\r\n", custId, custName));
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
         m_Subj = String.format("APG Quote Fax Confirmation - Customer %s, Quote %s", custId, quoteId);

         return msg.toString();
      }

      finally {
         msg = null;
         DbUtils.closeDbConn(null, m_GetQuoteDat, rs);
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
         sql.append("   quote.quote_id, cust_id.customer_id, name, trans_date ");
         sql.append("from ");
         sql.append("   b2b_fax_disposition bfd, quote, cust_id, apg_fax ");
         sql.append("where ");
         sql.append("   trans_id = ? and ");
         sql.append("   bfd.fax_disp_id = apg_fax.fax_disp_id and ");
         sql.append("   apg_fax.quote_id = quote.quote_id and ");
         sql.append("   quote.customer_id = cust_id.customer_id ");

         m_GetQuoteDat = m_Conn.prepareStatement(sql.toString());
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
         log.error(String.format("[ApgQuoteFaxConf] setParent - %s is not SyncFaxDispProc", name));
   }
}

