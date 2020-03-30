/**
 * File: VndPoFaxConfirmation.java
 * Description: Sends an email confirmation for PO faxes sent to vendors.
 *
 * @author Jeff Fisher
 *
 * Create Date: 07/16/2009
 * Last Update: $Id: VndPoFaxConfirmation.java,v 1.9 2012/01/04 12:02:42 jfisher Exp $
 *
 * History:
 *    $Log: VndPoFaxConfirmation.java,v $
 *    Revision 1.9  2012/01/04 12:02:42  jfisher
 *    Changed processing to use FaxFinderStatus vs FaxStatus
 *
 *    Revision 1.8  2011/12/20 13:07:30  jfisher
 *    Switched to fax server fax status.
 *
 *    Revision 1.7  2011/09/27 20:35:21  jfisher
 *    Extra logging
 *
 *    Revision 1.6  2011/09/22 21:48:39  jfisher
 *    Changed method signatures.
 *
 *    Revision 1.5  2011/09/16 18:27:16  jfisher
 *    Initial changes to deal with using the fax server vs efax
 *
 *    Revision 1.4  2011/06/23 21:38:29  jfisher
 *    Mods for email confirmation
 *
 *    Revision 1.3  2009/07/27 17:42:56  jfisher
 *    Class loader and reflection changes.
 *
 *    Revision 1.2  2009/07/23 19:42:17  jfisher
 *    Modified the message formatting
 *
 *    Revision 1.1  2009/07/17 15:57:12  jfisher
 *    initial add
 *
 */
package com.emerywaterhouse.edoc;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.emerywaterhouse.fax.FaxFinderStatus;
import com.emerywaterhouse.utils.DbUtils;

public class VndPoFaxConfirmation extends EmailConf
{
   private static final String parentName = "com.emerywaterhouse.conf.SyncFaxDispProc";
   private PreparedStatement m_GetVndPoDat = null;

   /**
    *
    */
   public VndPoFaxConfirmation()
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
         m_Subj = "Fax Confirmation - Vendor PO";

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
            log.warn("[VndPoFaxConfirmation] formatMsg - null parent");
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
    * Creates a specific message for vendor po faxes.  It contains information
    * about the PO and vendor.
    *
    * @param fs A reference to the FaxStatus object.
    *
    * @return The vendor po fax message.
    */
   private String getVndPoMsg(FaxFinderStatus fs) throws Exception
   {
      StringBuffer msg = new StringBuffer();
      ResultSet rs = null;
      String poNum = "";
      int vndId = 0;
      String vndName = "";
      String tranDate = "";

      try {
         m_GetVndPoDat.setString(1, fs.docId);
         rs = m_GetVndPoDat.executeQuery();

         if ( rs.next() ) {
            poNum = rs.getString(1);
            vndId = rs.getInt(2);
            vndName = rs.getString(3);
            tranDate = rs.getString(4);
         }
         else
            log.error(String.format("[VndPoFaxConfirmation] no results returned from query; docId = %s", fs.docId));

         msg.append(String.format("Disposition for fax transmission %s on %s at %s\r\n", fs.docId, fs.lastDate, fs.lastTime));
         msg.append("\r\n");
         msg.append(String.format("Purchase Order: %s\r\n", poNum));
         msg.append(String.format("Sent on: %s\r\n", tranDate));
         msg.append(String.format("Sent to vendor: %d %s\r\n", vndId, vndName));
         msg.append("\r\n");
         msg.append(String.format("Outcome: %s\r\n", fs.outcome));
         msg.append(String.format("Status Message: %s\r\n", fs.statusMsg));
         msg.append("\r\n");
         msg.append(String.format("Recipient Company: %s\r\n", fs.recipientCompany));
         msg.append(String.format("Recipient Name: %s\r\n", fs.recipientName));
         msg.append(String.format("FAX Number: %s\r\n", fs.recipientFax));

         //
         // Set the subject while we have the PO data
         m_Subj = String.format("Fax Confirmation - Vendor %d, PO %s", vndId, poNum);

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
         throw new Exception("Unable to get edb database connection");

      try {
         sql.append("select ");
         sql.append("   po_nbr, vendor_id, vendor_name, trans_date ");
         sql.append("from ");
         sql.append("   b2b_fax_disposition bfd ");
         sql.append("join po_fax on po_fax.fax_disp_id = bfd.fax_disp_id ");
         sql.append("join po_hdr on  po_fax.po_hdr_id = po_hdr.po_hdr_id ");
         sql.append("where ");
         sql.append("   document_id = ?");

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
         log.error(String.format("[VndPoFaxConfirmation] setParent - %s is not SyncFaxDispProc", name));
   }
}
