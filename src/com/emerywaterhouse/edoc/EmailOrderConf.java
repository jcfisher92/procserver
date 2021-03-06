/**
 * File: EmailOrderConf.java
 * Description: Descendant class for sending out email based order confirmations.
 *
 * @author Jeff Fisher
 *
 * Create Date: 06/08/2011
 * Last Update: $Id: EmailOrderConf.java,v 1.3 2015/03/17 13:15:52 smartel Exp $
 *
 * History:
 *    $Log: EmailOrderConf.java,v $
 *    Revision 1.3  2015/03/17 13:15:52  smartel
 *    Changed order confirmation From address from customerservice to noreply
 *
 *    Revision 1.2  2015/01/30 15:09:59  ebrownewell
 *    updated query to retrieve customer ID and eligible for best price variable. This was preventing some customer IDs from filling properly, and causing them to show up as null in the email subject.
 *
 *    Revision 1.1  2011/06/23 21:40:18  jfisher
 *    Initial add
 *
 */
package com.emerywaterhouse.edoc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import com.emerywaterhouse.email.OrdConfirmEmail;
import com.emerywaterhouse.utils.DbUtils;



public class EmailOrderConf extends EmailConf
{
   boolean m_BestPrcElig;
   String m_CustId;
   long m_OrderId;
   OrdConfirmEmail m_OrdConfEmail;

   PreparedStatement m_OrderInfo;

   /**
    * Default constructor
    */
   public EmailOrderConf()
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

      closeStatements();
      m_OrdConfEmail = null;
   }

   /**
    * Cleans up the prepared statements.
    */
   @Override
   protected void closeStatements()
   {
      DbUtils.closeDbConn(null, m_OrderInfo, null);

      m_OrderInfo = null;
   }

   /**
    * Get any needed order information.
    */
   private void getOrderInfo() throws Exception
   {
      ResultSet rs = null;

      try {
         m_OrderInfo.setLong(1, m_OrderId);
         rs = m_OrderInfo.executeQuery();

         if ( rs.next() ) {
            m_CustId = rs.getString("customer_id");
            m_BestPrcElig = rs.getString("description") != null;
         }
      }

      finally {
         DbUtils.closeDbConn(null, null, rs);
         rs = null;
      }
   }

   /**
    * @see com.emerywaterhouse.edoc.EmailConf#formatMsg()
    */
   @Override
   protected void formatMsg() throws Exception
   {
      prepareStatements();
      getOrderInfo();

      m_OrdConfEmail = new OrdConfirmEmail(m_Conn, m_OrderId, m_BestPrcElig);
      m_Msg = m_OrdConfEmail.buildEmailText();
      m_Subj = String.format("This is to confirm order %d placed with Emery-Waterhouse by %s", m_OrderId, m_CustId);
      m_From = "noreply@emeryonline.com";
   }

   /**
    * Prepares the sql statements for execution.
    * @throws SQLException
    */
   private void prepareStatements() throws SQLException
   {
      StringBuffer sql = new StringBuffer();

      if ( m_Conn != null ) {
         try {
            sql.append("select order_header.customer_id, description ");
            sql.append("from order_header ");
            sql.append("left outer join cust_price_method cpm on cpm.customer_id = order_header.customer_id ");
            sql.append("left outer join price_method on price_method.price_method_id = cpm.price_method_id and ");
            sql.append("   price_method.description = 'ELIGIBLE FOR LOWEST PRICE'");
            sql.append("where ");
            sql.append("order_header.order_id = ?");

            m_OrderInfo = m_Conn.prepareStatement(sql.toString());
         }

         finally {
            sql = null;
         }
      }
      else
         throw new SQLException("missing database connection");
   }

   /**
    * Overrides the base class method to allow for additional processing or for sending using
    * a different method or format.
    * 
    * @param recips - The recipient list.
    * @param subj - The subject of the email
    * @param msg - The email message.
    */
   @Override
   protected void sendEmail(ArrayList<String> recips, String subj, String msg) throws Exception
   {
      String[] tmp = null;

      if ( recips != null && recips.size() > 0 ) {
         try {
            tmp = new String[recips.size()];
            recips.toArray(tmp);
            //
            // Use the OrderConfEmail class to send the email for now because it has the code to
            // for the subject line.  This will get changed when we convert to HTML email messages.
            m_OrdConfEmail.sendConfirmationEmail(m_Conn, m_CustId, tmp, msg);
         }

         finally {
            tmp = null;
         }
      }
      else
         throw new Exception("attempted to send an order confirmation with an empty recipient list");
   }

   /**
    * Sets the extra information needed to process the order confirmation.
    * Currently it's the order id.
    * 
    * @param data The data to be used for processing.
    */
   @Override
   public void setData(Object data) throws Exception
   {
      if ( data != null ) {
         m_OrderId = (Integer)data;
      }
   }
}
