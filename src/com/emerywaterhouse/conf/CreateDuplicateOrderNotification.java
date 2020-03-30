/**
 * File: DuplicateOrderNotification.java
 * Description: Handles processing the duplicate order notification bods.  Instantiates the actual email processing
 *    class.
 * 
 * @author Tony Li
 * 
 * Create Date: 01/02/2014
 *
 */
package com.emerywaterhouse.conf;

import java.sql.Connection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import com.emerywaterhouse.bean.DuplicateOrder;
import com.emerywaterhouse.edoc.EmailConf;
import com.emerywaterhouse.edoc.EmailConfFactory;
import com.emerywaterhouse.server.ConnPool;
import com.emerywaterhouse.server.ProcessServer;
import com.emerywaterhouse.utils.DbUtils;
import com.ximpleware.AutoPilot;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;

public class CreateDuplicateOrderNotification extends BodProcessor
{
   private StringBuffer m_Msg;
   private ArrayList<String> m_Recipients;
   private int m_OrderId;
   private int m_ExistingOrderId;
  
   private  DuplicateOrder m_DuplicateOrder;

   /**
    * Default constructor - calls the bod constructor so initialization need only be there.
    */
   public CreateDuplicateOrderNotification()
   {
      super();
      
      m_Msg = new StringBuffer();
      m_Name = String.format("CreateDupOrdNotifyProc-%d", m_Id);
      m_Recipients = new ArrayList<String>();
   }

   /**
    * Bod constructor.
    * 
    * @param bod
    */
   public CreateDuplicateOrderNotification(ConfApp app, String bod)
   {
      super(app, bod);
   }

   /**
    * Clean up allocated resources.
    * @throws Throwable
    */
   @Override
   public void finalize() throws Throwable
   {
      m_Msg = null;
      m_Recipients.clear();
      m_Recipients = null;
      
      super.finalize();
   }

   /**
    * Generates the default email confirmation message.
    * 
    * @return The default message.
    */
   private String getDefaultMsg()
   {
      StringBuffer msg = new StringBuffer();
      msg.append("This order " + m_OrderId + " may be duplicated with the existing order " + m_ExistingOrderId);
      return msg.toString();
   }

   /**
    * Overrides the base class method and returns the recipients that were in the BOD
    * 
    * @return The internal list of recipients.
    */
   @Override
   public ArrayList<String> getRecipients()
   {
      return m_Recipients;
   }
   
   public DuplicateOrder getDuplicateOrder()
   {
      return m_DuplicateOrder;
   }

   /**
    * Parses the DuplicateOrderNotification bod and retrieves the transmission id and the document id.
    * These are stored in the database and will be used to get the rest of the disposition from efax.
    * 
    * @throws Exception on error
    */
   protected void parseBod() throws Exception
   {
	  Date m_OrderEnteredDate = null;
	  Date m_ExistingOrderEnteredDate = null;
	  String m_CustomerName = null;
	  String m_CustomerId = null;
	  String m_PurchaseOrderNum = null;
	   
      VTDGen vg = new VTDGen();
      AutoPilot ap = new AutoPilot();
      VTDNav vn = null;
      int i = -1;
      String tmp = null;

      ap.declareXPathNameSpace("ns1", "http://www.emeryonline.com/oagis");      
      vg.setDoc(m_Bod.getBytes());
      vg.parse(true);
      vn = vg.getNav();
      ap.bind(vn);

      //
      // get the system and component.
      ap.selectXPath("/ns1:CreateDuplicatePurchaseOrder/ns1:DataArea/ns1:DuplicatePurchaseOrder");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getAttrVal("system");
         if ( i != -1 )
            m_System = vn.toNormalizedString(i);

         i = vn.getAttrVal("component");
         if ( i != -1 )
            m_Component = vn.toNormalizedString(i);
      }

      //
      // Get the order id
      ap.selectXPath("/ns1:CreateDuplicatePurchaseOrder/ns1:DataArea/ns1:DuplicatePurchaseOrder/ns1:OrderId");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            m_OrderId = Integer.parseInt(vn.toNormalizedString(i));
      }
      else
         throw new Exception("[CreateOrderConfProc] missing order id");

      //
      // Get the existing order id
      ap.selectXPath("/ns1:CreateDuplicatePurchaseOrder/ns1:DataArea/ns1:DuplicatePurchaseOrder/ns1:ExistingOrderId");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            m_ExistingOrderId = Integer.parseInt(vn.toNormalizedString(i));
      }
      else
         throw new Exception("[CreateOrderConfProc] missing existing order id");

      //
      // Get the order entered date
      ap.selectXPath("/ns1:CreateDuplicatePurchaseOrder/ns1:DataArea/ns1:DuplicatePurchaseOrder/ns1:OrderEnteredDate");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 ) {
        	 DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH);
        	 m_OrderEnteredDate =  df.parse(vn.toNormalizedString(i));
         }
      }
      else
    	  m_OrderEnteredDate = new Date();

      //
      // Get the existing order entered date
      ap.selectXPath("/ns1:CreateDuplicatePurchaseOrder/ns1:DataArea/ns1:DuplicatePurchaseOrder/ns1:ExistingOrderEnteredDate");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 ) {
        	 DateFormat df = new SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH);
        	 m_ExistingOrderEnteredDate =  df.parse(vn.toNormalizedString(i));
         }
      }
      else
         throw new Exception("[CreateOrderConfProc] missing existing order entered date");

      
      //
      // Get the customer id
      ap.selectXPath("/ns1:CreateDuplicatePurchaseOrder/ns1:DataArea/ns1:DuplicatePurchaseOrder/ns1:CustomerId");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            m_CustomerId = vn.toNormalizedString(i);
      }
      else
         throw new Exception("[CreateOrderConfProc] missing customer id");
      
      //
      // Get the customer name
      ap.selectXPath("/ns1:CreateDuplicatePurchaseOrder/ns1:DataArea/ns1:DuplicatePurchaseOrder/ns1:CustomerName");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            m_CustomerName = vn.toNormalizedString(i);
      }
      else
    	  m_CustomerName="";

      //
      // Get the purchase order number
      ap.selectXPath("/ns1:CreateDuplicatePurchaseOrder/ns1:DataArea/ns1:DuplicatePurchaseOrder/ns1:PurchaseOrderNumber");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            m_PurchaseOrderNum = vn.toNormalizedString(i);
      }
      else
    	 m_PurchaseOrderNum =""; 
      
      //
      // Get the recipient list.  The current email function doesn't accept any name value,
      // just the email address.
      ap.selectXPath("/ns1:CreateDuplicatePurchaseOrder/ns1:DataArea/ns1:DuplicatePurchaseOrder/ns1:Recipients/ns1:Recipient/ns1:Email");
      while ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 ) {
            tmp = vn.toNormalizedString(i);

            if ( tmp != null && tmp.length() > 0 )
               m_Recipients.add(vn.toNormalizedString(i));
         }
      }
      
      m_DuplicateOrder = new DuplicateOrder(m_OrderId, m_OrderEnteredDate, 
   		   							m_ExistingOrderId, m_ExistingOrderEnteredDate, 
   		   							m_CustomerName, m_CustomerId, m_PurchaseOrderNum);
   }

   /**
    * @see com.emerywaterhouse.adapter.BodProcessor#processBod()
    */
   @Override
   protected void processBod()
   {
      Connection conn = null;
      
      try {
         conn = ConnPool.getInstance().getEDBConn();
         parseBod();
         sendConfirmation();
      }

      catch ( Exception ex ) {
         ProcessServer.log.error("[CreateDuplicateOrderNotification]", ex);
         
         m_Msg.setLength(0);
         m_Msg.append("[CreateDuplicateOrderNotification]\r\n\r\n");
         m_Msg.append("There was an exception while processing a duplicate order notification request.  ");
         m_Msg.append("See the log for the stack trace.\r\n\r\nThe exception was:\r\n");
         m_Msg.append(ex.getClass().getName());
         m_Msg.append(" - ");
         m_Msg.append(ex.getMessage());
         m_Msg.append("\r\n\r\n");
         m_Msg.append("Additional Information:\r\n");
         m_Msg.append(String.format("order id: %d\r\n", m_OrderId));
         m_Msg.append(String.format("existing order id: %d\r\n", m_ExistingOrderId));
         
         m_App.getServer().notifyMis(m_Msg.toString());
      }

      finally {
         DbUtils.closeDbConn(conn, null, null);
         conn = null;
      }
   }

   /**
    * Formats the confirmation and sends it to the recipients.
    * @throws Exception
    */
   private void sendConfirmation() throws Exception
   {
      EmailConf conf = null;
      EmailConfFactory f = EmailConfFactory.getInstance();

      try {
         //
         // If we can't get a confirmation class, set a warning message and just use the default.
         try {
            conf = f.getEmailConf(m_System, m_Component);
         }

         catch ( Exception ex ) {
            ProcessServer.log.warn("[CreateOrderConfProc] unable to load order confirmation class, using default message");
         }

         //
         // If we have a confirmation object use that, otherwise just use the default order confirmation message.
         if ( conf != null ) {
            try {
               conf.setDefaultEmailAddr(m_EmailAddr);
               conf.setParent(this);
               conf.setData(m_DuplicateOrder);
               conf.sendConfirmation();
            }

            finally {
               conf.close();
            }
         }
         else
            sendAcknowledgement(getDefaultMsg(), "Possible Duplicate Order Notification");
      }

      finally {
         f = null;
         conf = null;
      }
   }
}
