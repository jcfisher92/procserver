/**
 * File: SyncFaxDispProc.java
 * Description: The SyncFaxDisposition BOD processing class.  Processes the efax fax disposition and
 *    sends a notification to the originator.
 *
 * @author Jeff Fisher
 *
 * Create Date: 10/09/2007
 * Last Update: $Id: SyncFaxDispProc.java,v 1.16 2013/02/12 12:30:36 jfisher Exp $
 *
 * History:
 *    $Log: SyncFaxDispProc.java,v $
 *    Revision 1.16  2013/02/12 12:30:36  jfisher
 *    Uses the LWServer connection createSession method
 *
 *    Revision 1.15  2012/01/03 18:44:02  jfisher
 *    synced topic names and added static identifier.
 *
 *    Revision 1.14  2012/01/03 16:21:06  jfisher
 *    Removed efax references
 *
 *    Revision 1.13  2011/11/02 20:59:46  jfisher
 *    Added maxHttpTries and some extra logging
 *
 *    Revision 1.12  2011/10/13 16:57:05  jfisher
 *    Added some additional logging
 *
 *    Revision 1.11  2011/09/27 20:34:54  jfisher
 *    Fixed a bug where the docid was not getting set in the getEfaxStatus method.
 *
 *    Revision 1.10  2011/09/22 21:46:52  jfisher
 *    Added the getEFaxStatus to prevent class casting errors.
 *
 *    Revision 1.9  2011/09/16 18:08:35  jfisher
 *    Changes to handle both Joram and ActiveMQ
 *
 *    Revision 1.8  2009/07/29 14:16:37  jfisher
 *    Moved the email address member var to the base class.
 *
 *    Revision 1.7  2009/07/23 19:02:46  jfisher
 *    Moved the getRecipent function to the LWServer class.
 *
 *    Revision 1.6  2009/07/17 15:59:54  jfisher
 *    Removed the email formatting and configuration and moved it to a separate set of classes.
 *
 *    Revision 1.5  2007/12/12 00:32:50  jfisher
 *    Added a longer read timeout to the web service call
 *
 *    Revision 1.4  2007/10/12 01:18:23  jfisher
 *    Preproduction #2
 *
 *
 */
package com.emerywaterhouse.conf;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;

import com.emerywaterhouse.edoc.EmailConf;
import com.emerywaterhouse.edoc.EmailConfFactory;
import com.emerywaterhouse.fax.FaxApp;
import com.emerywaterhouse.fax.FaxFinderStatus;
import com.emerywaterhouse.server.ConnPool;
import com.emerywaterhouse.server.ProcessServer;
import com.emerywaterhouse.utils.DbUtils;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.ximpleware.AutoPilot;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;
import com.ximpleware.XMLModifier;

public class SyncFaxDispProc extends BodProcessor
{
   private static final String faxServerGetUrl = "http://10.128.0.79/ffws/v1/ofax/%s/%s";
      
   private static final int maxRetries = 20;
   private static final int maxHttpRetries = 4;

   private String m_Sender; 
   private int m_Delay;
   private String m_DocId;
   private FaxFinderStatus m_FaxStatus;
   private StringBuffer m_Msg;
   private int m_SyncCount;
   private int m_RetryCount;
   private Connection m_EdbConn = null;
   private PreparedStatement m_UpdDisp = null;
   private PreparedStatement m_GetSys = null;

   /**
    * Default constructor
    */
   public SyncFaxDispProc()
   {
      super();
      
      m_Msg = new StringBuffer();
      m_Name = String.format("SyncFaxDisp-%d", m_Id);
   }

   /**
    * Bod constructor.
    * @param bod
    */
   public SyncFaxDispProc(ConfApp app, String bod)
   {
      super(app, bod);      
   }

   /**
    * Closes the prepared statements.
    */
   private void closeStatements()
   {
      DbUtils.closeDbConn(null, m_UpdDisp, null);
      DbUtils.closeDbConn(null, m_GetSys, null);
   }

   /**
    * Clean up allocated resources.
    * @throws Throwable
    */
   @Override
   public void finalize() throws Throwable
   {
      m_Sender = null;
      m_Msg = null;
      m_DocId = null;
      m_FaxStatus = null;
      m_EdbConn = null;

      super.finalize();
   }

   /**
    * Generates the default fax acknowledgment message.
    *
    * @return The default fax message.
    */
   private String getDefaultMsg()
   {
      StringBuffer msg = new StringBuffer();

      try {
         msg.append(String.format("Disposition: for fax transmission %s on %s.%s\r\n",
               m_FaxStatus.docId, m_FaxStatus.lastDate, m_FaxStatus.lastTime));
         msg.append(String.format("Outcome: %s-%s\r\n", m_FaxStatus.state, m_FaxStatus.outcome));
         msg.append(String.format("Status Message: %s\r\n", m_FaxStatus.statusMsg));
         msg.append(String.format("Recipient Company:  %s\r\n", m_FaxStatus.recipientCompany));
         msg.append(String.format("Recipient Name:  %s\r\n", m_FaxStatus.recipientName));
         msg.append(String.format("Recipient FAX Number: %s", m_FaxStatus.recipientFax));

         return msg.toString();
      }

      finally {
         msg = null;
      }
   }

   /**
    * Wrapper method that calls the correct fax disposition methos based on who the sender was.
    * @throws Exception
    */
   private void getFaxDisposition() throws Exception
   {
      getFaxServerDisposition();
   }

   /**
    * Gets the fax disposition information from the fax server.
    * @throws Exception
    */
   private void getFaxServerDisposition() throws Exception
   {
      String[] result = m_DocId.split("[-]");
      HttpClient client = new HttpClient();
      HttpState state = null;
      Credentials credential = null;
      GetMethod get = null;
      String httpResp = null;
      int respCode = 0;

      if ( result.length == 2 ) {
         if ( m_RetryCount <= maxHttpRetries ) {
            get = new GetMethod(String.format(faxServerGetUrl, result[0], result[1]));
            get.setRequestHeader("content-type", "application/xml");
            credential = new UsernamePasswordCredentials("admin", "admin");
            state = client.getState();
            state.setCredentials(AuthScope.ANY, credential);

            try {
               respCode = client.executeMethod(get);
               httpResp = get.getResponseBodyAsString();

               if ( httpResp != null && httpResp.length() > 0 ) {
                  m_FaxStatus = parseFaxResp(httpResp.getBytes());
                  m_FaxStatus.statusCode = String.valueOf(respCode);
                  ProcessServer.log.info(String.format("[SyncFaxDispProc] successful fax status update - doc id: %s, pages: %d, state: %s", m_DocId, m_FaxStatus.pagesSent, m_FaxStatus.state));
               }
               else
                  ProcessServer.log.warn("[SyncFaxDispProc] unable to get the http response body");
            }

            catch (java.net.SocketException ex ) {
               m_RetryCount++;
               ProcessServer.log.warn(String.format("[SyncFaxDispProc] lost http connection, retrying - retry count: %d", m_RetryCount));
               getFaxServerDisposition();
            }
         }
         else
            throw new Exception("Exceeded the maximum retry attempts for a lost http connection");
      }
      else
         throw new Exception(String.format("invalid fax server document id; expecting nnnn-nnn got %s", m_DocId));
   }

   /**
    * Returns the internal reference to the fax status object
    * @return the FaxStatus object.
    */
   public FaxFinderStatus getFaxStatus()
   {
      return m_FaxStatus;
   }

   /**
    * Pulls the original system, component, and email from the database.
    * @throws Exception
    */
   private void getSystemInfo() throws Exception
   {
      ResultSet rs = null;

      try {
         m_GetSys.setString(1, m_FaxStatus.docId);
         rs = m_GetSys.executeQuery();

         if ( rs.next() ) {
            m_System = rs.getString("system");
            m_Component = rs.getString("component");
            m_EmailAddr = rs.getString("sender_email");

         }
         else
            throw new Exception("Unable to get the database information for incoming FAX acknowledgement.");
      }

      finally {
         DbUtils.closeDbConn(null, null, rs);
      }
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

      vg.setDoc(m_Bod.getBytes());
      vg.parse(true);
      vn = vg.getNav();
      ap.bind(vn);

      //
      // get the delay time
      ap.selectXPath("/SyncFaxDisposition/ApplicationArea/Sender/Component");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            m_Sender = vn.toNormalizedString(i);
      }

      //
      // get the delay time
      ap.selectXPath("/SyncFaxDisposition/DataArea/FaxDisposition");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getAttrVal("delay");

         if ( i != -1 )
            m_Delay = Integer.parseInt(vn.toNormalizedString(i));
      }

      //
      // get the document id.  It's all we need.
      ap.selectXPath("/SyncFaxDisposition/DataArea/FaxDisposition/DocId");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            m_DocId = vn.toNormalizedString(i);
      }
      else
         throw new Exception("unable to locate the fax document id in the SyncFaxDisposition document");
   }

   /**
    * Parses the response from the fax server when querying the dispostion of a fax.
    *
    * @param resp The response from the fax server.
    * @return A FaxStatus object that has been created from the fax server response data.
    * @throws Exception
    */
   private FaxFinderStatus parseFaxResp(byte[] resp)
   throws Exception
   {
      FaxFinderStatus fs = new FaxFinderStatus();
      VTDGen vg = new VTDGen();
      AutoPilot ap = new AutoPilot();
      VTDNav vn = null;
      int i = -1;
      int k = -1;
      StringBuffer tmp = new StringBuffer();

      vg.setDoc(resp);
      vg.parse(true);
      vn = vg.getNav();
      ap.bind(vn);

      //
      // get the response message
      ap.selectXPath("/response/message");
      if ( ap.evalXPath()!=-1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.statusMsg = vn.toNormalizedString(i);
      }

      ap.selectXPath("/response/fax_entry/fax_entry_url");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         //
         // parse out the fax key and the entry key.  Maybe not elegant but it works.
         // https://10.128.0.79/ffws/v1/ofax/00000005/0000  /ffws/v1/ofax/FAXKEY/ENTRYKEY
         if ( i != -1 ) {
            tmp.setLength(0);
            tmp.append(vn.toNormalizedString(i));
            k = tmp.lastIndexOf("/");
            fs.entryKey = tmp.substring(k+1);
            tmp.delete(k, tmp.length());
            k = tmp.lastIndexOf("/");
            fs.faxKey = tmp.substring(k+1);

            //
            // Use the two together to makup the document id
            fs.docId = fs.faxKey + "-" + fs.entryKey;
         }
      }

      ap.selectXPath("/response/fax_entry/state");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.state = vn.toNormalizedString(i);
      }

      ap.selectXPath("/response/fax_entry/schedule_message");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.outcome = vn.toNormalizedString(i);
      }

      ap.selectXPath("/response/fax_entry/stime");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         //
         // parse out the data and time.  2011-05-16T15:47:58
         if ( i != -1 ) {
            tmp.setLength(0);
            tmp.append(vn.toNormalizedString(i));
            k = tmp.indexOf("T");
            fs.lastTime = tmp.substring(k+1);
            fs.lastDate = tmp.substring(0, k);
         }
      }

      //
      // retries are n-1 on the try number response field.  The first try doesn't
      // count as a retry.
      ap.selectXPath("/response/fax_entry/try_number");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.retries = Integer.parseInt(vn.toNormalizedString(i)) - 1;
      }

      //
      // Sender Information
      //
      ap.selectXPath("/response/fax_entry/sender/username");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.senderUid = vn.toNormalizedString(i);
      }

      ap.selectXPath("/response/fax_entry/sender/name");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.senderName = vn.toNormalizedString(i);
      }

      ap.selectXPath("/response/fax_entry/sender/phone_number");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.senderPhone = vn.toNormalizedString(i);
      }

      ap.selectXPath("/response/fax_entry/sender/email_address");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.senderEmail = vn.toNormalizedString(i);
      }

      //
      // Recipeint Information
      //

      ap.selectXPath("/response/fax_entry/recipient/name");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.recipientName = vn.toNormalizedString(i);
      }

      ap.selectXPath("/response/fax_entry/recipient/organization");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.recipientCompany = vn.toNormalizedString(i);
      }

      ap.selectXPath("/response/fax_entry/recipient/fax_number");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.recipientFax = vn.toNormalizedString(i);
      }

      ap.selectXPath("/response/fax_entry/recipient/phone_number");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.recipientPhone = vn.toNormalizedString(i);
      }

      ap.selectXPath("/response/fax_entry/pages");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.pagesSent = Integer.parseInt(vn.toNormalizedString(i));
      }

      ap.selectXPath("/response/fax_entry/approver");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.approver = vn.toNormalizedString(i);
      }

      ap.selectXPath("/response/fax_entry/approval_time");
      if ( ap.evalXPath() != -1 ) {
         i = vn.getText();

         if ( i != -1 )
            fs.approvalTime = vn.toNormalizedString(i);
      }

      return fs;
   }

   /**
    * Prepares the sql statements
    *
    * @throws Exception on errors
    */
   private void prepareStatements() throws Exception
   {
      StringBuffer sql = new StringBuffer();

      if ( m_EdbConn == null )
         throw new Exception("Unable to get EnterpriseDB database connection");
      
      try {
         sql.append("update b2b_fax_disposition ");
         sql.append("set ");
         sql.append("   approver = ?, approval_time = ?, last_date = ?, last_time = ?, ");
         sql.append("   outcome = ?, pages_sent = ?, retries = ?, status_msg = ?, state = ? ");
         sql.append("where document_id = ?");
         m_UpdDisp = m_EdbConn.prepareStatement(sql.toString());

         sql.setLength(0);
         sql.append("select system, component, sender_email ");
         sql.append("from b2b_fax_disposition ");
         sql.append("where document_id = ?");
         m_GetSys = m_EdbConn.prepareStatement(sql.toString());
      }

      finally {
         sql = null;
      }
   }

   /**
    * Process the SyncFaxDisposition bod.  Overrides the base class method.
    * This is the main method for processing the bod.  Called one time per
    * BOD.
    */
   @Override
   protected void processBod()
   {      
      int delayDelta = 0;

      try {         
         m_EdbConn = ConnPool.getInstance().getEDBConn();
         prepareStatements();
         parseBod();

         if ( m_SyncCount <= maxRetries ) {
            //
            // If there was a delay attribute set, sleep for that amount of time before pulling the fax
            // information from the fax server.  This gives the server time to process the fax and also
            // keeps this process from constantly checking and updating.
            if ( m_Delay > 0 )
               Thread.sleep(m_Delay * 1000);

            getFaxDisposition();
            updateFaxDisposition();

            if ( m_Sender != null && m_Sender.equals("fax server ws") ) {
               //
               // Only send a confirmation if the process has finished.  There may be several retry attempts before the fax
               // is sent or it fails.  Any status that is not a final status will trigger a resend of the Sync bod.  This is
               // preferable to looping because it allows other process a chance to run.
               // See http://www.multitech.net/developer/products/faxfinder/web-services-api/
               // Can be one of:
               //    preprocessing
               //    approval_pending
               //    pending
               //    sending
               //    sent
               //    aborted
               //    failed
               //    dead
               if ( m_FaxStatus.state.equals("sent") || m_FaxStatus.state.equals("aborted") ||
                     m_FaxStatus.state.equals("failed") || m_FaxStatus.state.equals("dead") )
                  sendConfirmation();
               else {
                  //
                  // On the second time through, cut the delay in half since the processing should be
                  // finished.  Otherwise keep doubling until we hit 10 minutes.
                  if ( m_SyncCount == 2)
                     delayDelta = (m_Delay/2) * -1;
                  else {
                     if ( m_SyncCount > 10 && m_Delay <= 600 )
                        delayDelta = m_Delay * 2;
                  }

                  repostSyncFaxBod(delayDelta);
               }
            }
            else
               sendConfirmation();
         }
         else {
            m_Msg.setLength(0);
            m_Msg.append("[SyncFaxDispProc] exceeded number of retries for getting fax disposition, check the fax server");
            ProcessServer.log.warn(m_Msg.toString());
            m_App.getServer().notifyMis(m_Msg.toString());
         }
      }

      catch ( Exception ex ) {
         ProcessServer.log.error("[SyncFaxDispProc]", ex);
         m_Msg.setLength(0);
         m_Msg.append("[SyncFaxDispProc]\r\n\r\n");
         m_Msg.append("There was an exception while processing a fax response.  ");
         m_Msg.append("See the log for the stack trace.\r\n\r\nThe exception was:\r\n");
         m_Msg.append(ex.getClass().getName());
         m_Msg.append(" - ");
         m_Msg.append(ex.getMessage());
         m_Msg.append("\r\n\r\n");

         if ( m_FaxStatus != null ) {
            m_Msg.append(String.format("Document ID: %s\r\n", m_FaxStatus.docId));
            m_Msg.append(String.format("FAX Nbr: %s\r\n", m_FaxStatus.recipientFax));
         }
         else
            m_Msg.append("No fax status information available.  Probable web service call failure\r\n");

         m_App.getServer().notifyMis(m_Msg.toString());
      }

      finally {
         closeStatements();

         if ( m_Msg.length() > 0 )
            m_App.getServer().notifyMis(m_Msg.toString());
      }
   }

   /**
    * Reposts the SyncFaxDisposition BOD on the bus
    *
    * @param delayDelta The amount to change the delay attribute by. Can be + or =
    * @throws Exception
    */
   private void repostSyncFaxBod(int delayDelta) throws Exception
   {      
      ConnectionFactory cnxFactory = null;
      com.rabbitmq.client.Connection cnx = null;
      Channel channel = null;
      String user = System.getProperty("msgbroker.user");
      String passwd = System.getProperty("msgbroker.passwd");
      String host = System.getProperty("msgbroker.host");
      int port = Integer.parseInt(System.getProperty("msgbroker.port"));
      
      VTDGen vg = null;
      XMLModifier xm = null;
      VTDNav vn = null;
      int i = -1;

      //
      // Parse out the attributes and modify them as needed.
      vg = new VTDGen();
      xm = new XMLModifier();
      vg.setDoc(m_Bod.getBytes());
      vg.parse(true);

      vn = vg.getNav();
      xm.bind(vn);

      i = vn.getAttrVal("syncCount");

      //
      // Update the sync count so we know how many times we've been through here.
      if ( i != -1 )
         xm.updateToken(i, String.valueOf(++m_SyncCount));

      if ( delayDelta != 0 ) {
         m_Delay = m_Delay + delayDelta > 0 ? m_Delay + delayDelta : 0;

         i = vn.getAttrVal("delay");

         //
         // Update the amount to delay processing.
         if ( i != -1 )
            xm.updateToken(i, String.valueOf(m_Delay));
      }
      
      try {         
         cnxFactory = new ConnectionFactory();         
         cnxFactory.setAutomaticRecoveryEnabled(true);
         
         cnxFactory.setUsername(user);
         cnxFactory.setPassword(passwd);
         cnxFactory.setHost(host);
         cnxFactory.setPort(port);
         cnx = cnxFactory.newConnection();
         channel = cnx.createChannel();
            
         //
         // params are exchange(default empty string), routing_key aka queue,
         channel.basicPublish(FaxApp.exchange, FaxApp.faxConfRouteKey, MessageProperties.PERSISTENT_TEXT_PLAIN, m_Bod.getBytes());         
      }

      catch ( Exception ex ) {                  
         ProcessServer.log.error("[SyncFaxDispProc]", ex);

         m_Msg.setLength(0);
         m_Msg.append(ex.getClass().getName());
         m_Msg.append("\r\n");

         if ( ex.getMessage() != null )
            m_Msg.append(ex.getMessage());
      }

      finally {
         if ( channel != null ) {
            try {
               channel.close();
            }
            
            catch ( Exception ex ) {
               ;
            }
         }
         
         if ( cnx != null ) {
            try {
               cnx.close();
               cnx = null;
            }

            catch ( Exception ex ) {
               ;
            }
         }
         vg = null;
         xm = null;
         vn = null;
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
         getSystemInfo();

         //
         // If we can't get a confirmation class, set a warning message and just use the
         // default.
         try {
            conf = f.getEmailConf(m_System, m_Component);
         }

         catch ( Exception ex ) {
            ProcessServer.log.warn("unable to load conf class, using default message");
         }

         //
         // If we have a confirmation object use that, otherwise just use the default fax message.
         if ( conf != null ) {
            conf.setConnection(m_EdbConn);
            conf.setDefaultEmailAddr(m_EmailAddr);
            conf.setParent(this);
            conf.sendConfirmation();
         }
         else
            sendAcknowledgement(getDefaultMsg(), "FAX Status Notification");
      }

      finally {
         f = null;
         conf = null;
      }
   }

   /**
    * Saves the fax disposition data to the database.  Takes the complete data from
    * the web service call to efax and stores it in the database.
    *
    * Note - The original status code and system data is not updated.
    *
    * @throws Exception
    */
   private void updateFaxDisposition() throws Exception
   {
      m_UpdDisp.setString(1, m_FaxStatus.approver);
      m_UpdDisp.setString(2, m_FaxStatus.approvalTime);
      m_UpdDisp.setString(3, m_FaxStatus.lastDate);
      m_UpdDisp.setString(4, m_FaxStatus.lastTime);
      m_UpdDisp.setString(5, m_FaxStatus.outcome);
      m_UpdDisp.setInt(6, m_FaxStatus.pagesSent);
      m_UpdDisp.setInt(7, m_FaxStatus.retries);
      m_UpdDisp.setString(8, m_FaxStatus.statusMsg);
      m_UpdDisp.setString(9, m_FaxStatus.state);
      m_UpdDisp.setString(10, m_FaxStatus.docId);

      if ( m_UpdDisp.executeUpdate() == 0 )
         throw new Exception("Unable to save fax disposition data to the database.");
   }
}
