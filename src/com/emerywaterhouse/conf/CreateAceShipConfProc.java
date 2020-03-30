/**
 * File: CreateAceShipConfProc.java
 * Description: Handles processing the ace order confirmation bods.  Instantiates the actual email processing
 *    class.
 * 
 * @author Eric Brownewell
 * @author Jeff Fisher
 * 
 * Create Date: 01/21/2015
 *
 */
package com.emerywaterhouse.conf;

import java.sql.Connection;
import java.util.ArrayList;

import com.emerywaterhouse.edoc.EmailConf;
import com.emerywaterhouse.edoc.EmailConfFactory;
import com.emerywaterhouse.server.ConnPool;
import com.emerywaterhouse.server.ProcessServer;
import com.emerywaterhouse.utils.DbUtils;
import com.ximpleware.AutoPilot;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;

public class CreateAceShipConfProc extends BodProcessor
{
   private StringBuffer m_Msg;   
   private Connection m_EdbConn = null;
   private ArrayList<String> m_Recipients;
   private String m_OrderId;
   private String m_Carrier;
   private String m_TrackingId;

   /**
    * Default constructor - calls the bod constructor so initialization need only be there.
    */
   public CreateAceShipConfProc()
   {
      super();
      
      m_Name = String.format("CreateAceShipConfProc-%d", m_Id);
      m_Recipients = new ArrayList<String>();
      m_Msg = new StringBuffer();
   }

   /**
    * Bod constructor.
    * 
    * @param bod
    */
   public CreateAceShipConfProc(ConfApp app, String bod)
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
      m_EdbConn = null;
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
      //TODO Add a default order message.  Probably a link to the web site with the order number.
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

   /**
    * Parses the SyncFaxDisposition bod and retrieves the transmission id and the document id.
    * These are stored in the database and will be used to get the rest of the disposition from efax.
    * 
    * @throws Exception on error
    */
   protected void parseBod() throws Exception
   {
      VTDGen vg = new VTDGen();
      AutoPilot ap = new AutoPilot();
      VTDNav vn = null;
      int i = -1;
      String tmp = null;

      vg.setDoc(m_Bod.getBytes());
      vg.parse(true);
      vn = vg.getNav();
      ap.bind(vn);

      //
      // get the system and component.
      ap.selectXPath("/CreateAceShipmentConfirmation/DataArea/AceShipConfirmation");
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
      ap.selectXPath("/CreateAceShipmentConfirmation/DataArea/AceShipConfirmation/OrderId");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            m_OrderId = vn.toNormalizedString(i);
        	 //m_OrderId = Integer.parseInt(vn.toNormalizedString(i));
      }
      else
         throw new Exception("[CreateAceShipConfProc] missing order id");
      
      //
      // Get the Carrier and Tracking ID
      ap.selectXPath("/CreateAceShipmentConfirmation/DataArea/AceShipConfirmation/Carrier");
      
      if ( ap.evalXPath() != -1 ) {
    	  i = vn.getText();
    	  
    	  if ( i != -1 )
    		  m_Carrier = vn.toNormalizedString(i);
      }
      
      if ( m_Carrier.equals("UPS") ){
          ap.selectXPath("/CreateAceShipmentConfirmation/DataArea/AceShipConfirmation/TrackingId");
          
          if ( ap.evalXPath() != -1 ) {
        	  i = vn.getText();
        	  
        	  if ( i != -1 )
        		  m_TrackingId = vn.toNormalizedString(i);
          }
          else
        	  throw new Exception ("[CreateAceShipConfProc] Missing Tracking ID for UPS Carrier");
      }

      //
      // Get the recipient list.  The current email function doesn't accept any name value,
      // just the email address.
      ap.selectXPath("/CreateAceShipmentConfirmation/DataArea/AceShipConfirmation/Recipients/Recipient/Email");
      while ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 ) {
            tmp = vn.toNormalizedString(i);

            if ( tmp != null && tmp.length() > 0 )
               m_Recipients.add(vn.toNormalizedString(i));
         }
      }
   }

   /**
    * @see com.emerywaterhouse.adapter.BodProcessor#processBod()
    */
   @Override
   protected void processBod()
   {
      try {
         m_EdbConn = ConnPool.getInstance().getEDBConn();
         parseBod();
         sendConfirmation();
      }

      catch ( Exception ex ) {
         ProcessServer.log.error("[CreateAceShipConfProc]", ex);
         m_Msg.setLength(0);
         m_Msg.append("[CreateAceShipConfProc]\r\n\r\n");
         m_Msg.append("There was an exception while processing an ace order confirmation request.  ");
         m_Msg.append("See the log for the stack trace.\r\n\r\nThe exception was:\r\n");
         m_Msg.append(ex.getClass().getName());
         m_Msg.append(" - ");
         m_Msg.append(ex.getMessage());
         m_Msg.append("\r\n\r\n");
         m_Msg.append("Additional Information:\r\n");
         m_Msg.append(String.format("order id: %d\r\n", m_OrderId));

         m_App.getServer().notifyMis(m_Msg.toString());
      }

      finally {
         DbUtils.closeDbConn(m_EdbConn, null, null);
         m_EdbConn = null;
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
            ProcessServer.log.warn("[CreateAceShipConfProc] unable to load order confirmation class, using default message");
         }

         //
         // If we have a confirmation object use that, otherwise just use the default order confirmation message.
         if ( conf != null ) {
            try {
               conf.setConnection(m_EdbConn);               
               conf.setDefaultEmailAddr(m_EmailAddr);
               conf.setParent(this);
               String[] data = {m_OrderId, m_Carrier, m_TrackingId};
               conf.setData(data);
               conf.sendConfirmation();
            }

            finally {
               conf.close();
            }
         }
         else
            sendAcknowledgement(getDefaultMsg(), "Ace Shipment Confirmation From Emery-Waterhouse");
      }

      finally {
         f = null;
         conf = null;
      }
   }
}
