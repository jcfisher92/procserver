package com.emerywaterhouse.conf;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.emerywaterhouse.bean.Recipient;
import com.emerywaterhouse.oag.OagConst;
import com.emerywaterhouse.server.ConnPool;
import com.emerywaterhouse.server.ProcessServer;
import com.emerywaterhouse.utils.DataSender;
import com.emerywaterhouse.utils.DbUtils;

public class CreateConfBodProc extends BodProcessor 
{
   private String m_Bod;                       // The ConfirmBOD document.
   private String m_BodId;                     // BODID of confirmation document
   private String m_Confirmation;              // When to send the confirmation (Always, OnError, etc)
   private String m_OrigBodId;                 // The bodId of the bod being confirmed
   private String m_OrigComponent;             // The value of the component elem in bod being confirmed
   private ArrayList<Recipient> m_Recipients;  // List of recipients to notify
   private boolean m_SendConf;                 // If true, then send out notification msg
   private String m_Task;                      // Task description in app area
   
   public CreateConfBodProc() 
   {
      super();
      
      m_Name = String.format("CreateOrdConfProc-%d", m_Id);
      
      m_Recipients = new ArrayList<Recipient>();
      m_Task = "";
      m_Confirmation = OagConst.sALWAYS;
      m_OrigBodId = "";
      m_SendConf = true;
   }

   public CreateConfBodProc(ConfApp app, String bod) 
   {
      super(app, bod);   
   }

   /**
    * Cleanup
    * @throws Throwable
    */
   @Override
   public void finalize() throws Throwable
   {
      m_Bod = null;
      m_Thread = null;
      
      if ( m_Recipients != null ) {
         m_Recipients.clear();
         m_Recipients = null;
      }

      super.finalize();
   }
   
   /**
    * Gets the ConfirmBOD as a text message by applying an XSLT transformation.
    * The resulting message is used when notifying users.
    *
    * @param mesgStyle String - Recipient message style (xsl url).
    * @return String - the result of the transformation.
    *
    * @throws javax.xml.transform.TransformerException - If an error occurs during the course of the transformation.
    * @throws javax.xml.transform.TransformerConfigurationException - If an error occurs whilst creating the transformer.
    */
   private String getConfTextMsg(String mesgStyle) throws TransformerException, TransformerConfigurationException
   {
      TransformerFactory factory;
      Transformer transformer;
      StringWriter textOut;
      Source xmlSource;
      Source xslSource;

      try {
         factory = TransformerFactory.newInstance();

         //
         // Check the recipient message style to determine which stylesheet to use.
         // If this is not set, just use the default.
         if ( mesgStyle != null && mesgStyle.length() > 0 )
            xslSource = new StreamSource(mesgStyle);
         else
            xslSource = new StreamSource(System.getProperty("conf.xsl"));

         xmlSource = new StreamSource(new StringReader(m_Bod));

         textOut = new StringWriter();
         transformer = factory.newTransformer(xslSource);
         transformer.transform(xmlSource, new StreamResult(textOut));
      }

      finally {
         xmlSource = null;
         xslSource = null;
         factory = null;
         transformer = null;
      }

      return textOut.toString();
   }
   
   /**
    * Returns list of recipient addresses as a string.  Used in status page.
    *
    * @return String - List of recipient addresses.
    */
   public String getRecipAddresses()
   {
      StringBuffer tmp = new StringBuffer();
      int size = m_Recipients.size();

      for ( int i = 0; i < size; i++ ) {
         tmp.append(m_Recipients.get(i).address);

         if ( i < size-1 )
            tmp.append("  ");
      }

      return tmp.toString();
   }

   /**
    * 
    * @return
    */
   public String getTask()
   {
      return m_Task;
   }

   /**
    * 
    * @return
    */
   public String getOrigBodId()
   {
      return m_OrigBodId;
   }

   /**
    * 
    * @return
    */
   public String getOrigComponent()
   {
      return m_OrigComponent;
   }
   
   /**
    * Parses the ConfirmBOD to get recipient list, etc.  All the parsing is done in this
    * method instead of other functions.  The amount of xml to parse is small.
    *
    * @throws Exception When a parser exception occurs.
    */
   private void parseBOD() throws Exception
   {
      Node bodSuccess;
      DocumentBuilder builder = null;
      Document document = null;
      DocumentBuilderFactory domFactory = null;
      NodeList recipients;
      XPathFactory xfactory = null;
      XPath xpath = null;
      Recipient recip;

      m_Recipients.clear();
      m_SendConf = true;

      domFactory = DocumentBuilderFactory.newInstance();
      builder = domFactory.newDocumentBuilder();
      document = builder.parse(new ByteArrayInputStream(m_Bod.getBytes()));

      xfactory = XPathFactory.newInstance();
      xpath = xfactory.newXPath();

      m_BodId = (String) xpath.evaluate("/ConfirmBOD/ApplicationArea/BODId/text()", document, XPathConstants.STRING);
      m_Task = (String) xpath.evaluate("/ConfirmBOD/ApplicationArea/Sender/Task/text()", document, XPathConstants.STRING);
      m_Confirmation = (String) xpath.evaluate("/ConfirmBOD/ApplicationArea/Sender/Confirmation/text()", document, XPathConstants.STRING);
      m_OrigBodId = (String) xpath.evaluate("/ConfirmBOD/DataArea/BOD/Header/OriginalApplicationArea/BODId/text()", document, XPathConstants.STRING);
      m_OrigComponent = (String) xpath.evaluate("/ConfirmBOD/DataArea/BOD/Header/OriginalApplicationArea/Sender/Component/text()", document, XPathConstants.STRING);
      bodSuccess = (Node) xpath.evaluate("/ConfirmBOD/DataArea/BOD/Header/BODSuccess", document, XPathConstants.NODE);

      recipients = (NodeList) xpath.evaluate("/ConfirmBOD/ApplicationArea/UserArea/RoutingInfo/Recipient", document, XPathConstants.NODESET);

      for ( int i = 0; i < recipients.getLength(); i++ ) {
         recip = new Recipient();

         recip.address = (String) xpath.evaluate("Address/text()", recipients.item(i), XPathConstants.STRING);
         recip.transport = (String) xpath.evaluate("Transport/text()", recipients.item(i), XPathConstants.STRING);
         recip.userId = (String) xpath.evaluate("UserId/text()", recipients.item(i), XPathConstants.STRING);
         recip.password = (String) xpath.evaluate("Password/text()", recipients.item(i), XPathConstants.STRING);
         recip.msgStyle = (String) xpath.evaluate("MessageStyle/text()", recipients.item(i), XPathConstants.STRING);

         m_Recipients.add(recip);
      }

      //
      // Don't send notification if Confirmation element set to Never, or if bod processing was successful and the
      // notification should only be sent out if an error occurred during bod processing.
      if ( m_Confirmation.equals(OagConst.sNEVER) || (bodSuccess != null && m_Confirmation.equals(OagConst.sONERROR)) ) {
         m_SendConf = false;
      }

      //
      // If bod outcome is BODSuccess, then remove the original bod being confirmed
      if ( bodSuccess != null && (m_OrigBodId != null && m_OrigBodId.length()>0) ) {
         removeOriginalBod();
      }
   }
   
   @Override
   protected void processBod() 
   {
      int i;
      String msg = null;
      Recipient recipient = null;
      int size;

      try {
         parseBOD();
         size = m_Recipients.size();

         //
         // If don't want to send out any notifications, then quit
         if ( !m_SendConf )
            return;

         //
         // Send the confirmation message off to all the recipients

         for ( i = 0; i < size ; i++ ) {
            recipient = m_Recipients.get(i);

            if ( recipient != null ) {
               //
               // Transform confirmation xml using the recipient's style
               msg = getConfTextMsg(recipient.msgStyle);

               sendMsg(recipient, msg, "System confirmation regarding " + m_Task, "conf_" + m_BodId);
            }
         }
      }

      catch ( Exception ex ) {
         ProcessServer.log.fatal("[CreateConfBodProc]", ex);
         m_App.getServer().notifyMis("Unable to start the confirm bod processor.  See the log for details");
      }
   }
   
   /**
    * Private method that removes the original bod being confirmed from the bod table.
    */
   private void removeOriginalBod()
   {
      Connection conn = null;
      PreparedStatement stmt = null;
      StringBuffer sql = new StringBuffer();

      try {
         conn = ConnPool.getInstance().getEDBConn();
         conn.setAutoCommit(true);
         sql.append("delete from bod where document_id = ? ");
         stmt = conn.prepareStatement(sql.toString());
         stmt.setString(1, m_OrigBodId);
         stmt.executeUpdate();
      }

      catch ( Exception e ) {
         ProcessServer.log.error("[ConfProcessor] Error when removing bod being confirmed; bodId: " + m_OrigBodId, e);
      }

      finally {
         DbUtils.closeDbConn(conn, stmt, null);
         stmt = null;
         conn = null;
         sql = null;
      }
   }

   /**
    * Sends a message out to this recipient.
    *
    * @param msg String - the message text.
    * @param msgTitle String - title of the message.
    * @param msgFileName String - file name of the message, if applicable.
    *
    * @throws Exception - if some error occurred while sending the message.
    */
   void sendMsg(Recipient recipient, String msg, String msgTitle, String msgFileName) throws Exception
   {
      int responseCode;  // HTTP response code
      StringBuffer tmp = new StringBuffer();

      //
      //Prepend Test environment indicator to subject
      tmp.append(m_App.getServer().getEnv() == ProcessServer.Environment.Test ? "[TEST]" : "");
      tmp.append(msgTitle);

      //
      // Send via http
      if ( recipient.transport.equalsIgnoreCase("http") ) {
         responseCode = DataSender.http(recipient.address, msg);

         if ( responseCode != 200 )
            throw new Exception("HTTP error when sending message: HTTP code=" + responseCode);
      }

      //
      // Send via HTTPS
      if ( recipient.transport.equalsIgnoreCase("https") ) {
         responseCode = DataSender.https(recipient.address, msg);

         if ( responseCode != 200 )
            throw new Exception("HTTPS error when sending message: HTTP code=" + responseCode);
      }

      //
      // Send via FTP
      if ( recipient.transport.equalsIgnoreCase("ftp") ) {
         DataSender.ftp(recipient.address, recipient.userId, recipient.password, msgFileName, msg);
      }

      //
      // Send via smtp
      if ( recipient.transport.equalsIgnoreCase("smtp") ) {
         DataSender.smtp(System.getProperty("mail.from", "noreply@emeryonline.com"), new String[]{recipient.address}, tmp.toString(), msg);
      }
   }
   
   /**
    * Sets the internal BOD member.
    *
    * @param bod The bod.
    * @throws Exception when the bod var is null.
    */
   public void setBOD(String bod) throws Exception
   {
      if ( bod != null)
         m_Bod = bod;
      else
         throw new Exception("attempt to set bod to null");
   }

}
