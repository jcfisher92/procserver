/**
 *
 */
package com.emerywaterhouse.fax;

import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;

import com.emerywaterhouse.oag.build.bod.SyncFaxDisposition;
import com.emerywaterhouse.oag.build.noun.FaxDisposition;
import com.emerywaterhouse.server.ConnPool;
import com.emerywaterhouse.server.ProcessServer;
import com.emerywaterhouse.utils.DbUtils;
import com.ximpleware.AutoPilot;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;

public class FaxProcessor implements Runnable 
{
   private static long startId = 0;
      
   private String m_Bod;            // The internal BOD that was pulled from the message broker
   private String m_Comp;           // The component that sent the fax request.
   private String m_FaxXml;         // Fax request xml
   private FaxStatus m_FaxStatus;   // The fax status object that returns the processing information.
   private String m_FFResp;         // FaxFinder web service response data
   private long m_Id;               // An id for identifying an instance of a fax processor.
   private String m_LinkVal;        // A system/component dependent value that's used to link to other process data.
   private int m_MaxTries;          // The maximum number of times to try and send a fax to the fax server
   private FaxApp m_App;            // Pointer to the fax applicaion instance.
   private String m_Name;           // The name of the processor.
   private String m_Sys;            // The system that sent the fax request.
   @SuppressWarnings("unused")
   private String m_SenderComp;     // The component of the sender that called the web service
   private String m_SenderTask;     // The task of the sender that called the web service.
   private Thread m_Thread;         // The thread that runs the processing.

   /**
    * default constructor. Hidden to prevent actual use.
    */
   protected FaxProcessor() 
   {
      super();

      //
      // Currently this is not going to be multi-threaded. We want to keep the
      // mechanisms
      // in place in case we change later. Also, the fax application process
      // needs to know
      // how to call methods on this object.
      m_Id = startId++;
      m_Name = String.format("FaxProcessor-%d", m_Id);
      m_MaxTries = 1;
   }

   /**
    * Creates a FaxProcessor object with a bod and an instance to the fax
    * application class.
    *
    * @param app An instance of the FaxApp class.
    * @param bod The CreateFaxEntry BOD.
    */
   public FaxProcessor(FaxApp app, String bod) 
   {
      this();

      try {
         setApplication(app);
         setBOD(bod);
         String val = app.getProperty("maxTries");

         if ( val.length() > 0 )
            m_MaxTries = Integer.parseInt(val);

         if (m_MaxTries <= 0)
            m_MaxTries = 1;
      }

      catch ( Exception ex ) {
         ProcessServer.log.error("[FaxProcessor] ", ex);
      }
   }

   /**
    * Gets the status of the running fax processor.
    *
    * @return A newly created FaxStatus object.
    */
   public FaxStatus getFaxStatus() 
   {
      m_FaxStatus.runTime = System.currentTimeMillis() - m_FaxStatus.startTime;
      return m_FaxStatus;
   }

   /**
    * Return the id of the processor.
    *
    * @return The internal processor id used for identify this process.
    */
   public long getId() 
   {
      return m_Id;
   }

   /**
    * Links an emery order to a fax transmission.
    *
    * @param orderId The order number that was transmitted
    * @param docId The document id of the fax.
    * @param conn The database connection currently being used.
    *
    * @throws Exception when errors occurred.
    */
   private void linkOrderToFax(long orderId, String docId, Connection conn) throws Exception 
   {
      StringBuffer sql = new StringBuffer();
      PreparedStatement stmt = null;

      if ( conn != null ) {
         try {
            if ( docId != null && docId.length() > 0 ) {
               sql.setLength(0);
               sql.append("insert into order_fax(fax_disp_id, order_id) ");
               sql.append("values( (select fax_disp_id from b2b_fax_disposition where document_id = ?), ? )");
               stmt = conn.prepareStatement(sql.toString());

               //
               // Link the fax to the order
               stmt.setString(1, docId);
               stmt.setLong(2, orderId);
               stmt.executeUpdate();
            } 
            else
               ProcessServer.log.error("[FaxProcessor] error - missing document id, unable to link order to fax");
         }

         finally {
            DbUtils.closeDbConn(null, stmt, null);
            stmt = null;
            sql = null;
         }
      }
   }

   /**
    * Links a purchase order to a fax transmission.
    *
    * @param poNbr The po number that was transmitted
    * @param docId The document id of the fax
    * @param conn The EnterpriseDB database connection currently being used.
    *
    * @throws Exception when errors occurred.
    */
   private void linkPoToFax(String poNbr, String docId, Connection conn) throws Exception 
   {
      StringBuffer sql = new StringBuffer();
      PreparedStatement stmt = null;
      Integer faxDispId = null;

      try {
         sql.setLength(0);
         sql.append("select fax_disp_id from b2b_fax_disposition where document_id = ?");
         stmt = conn.prepareStatement(sql.toString());
         stmt.setString(1, docId);
         ResultSet rs = stmt.executeQuery();

         if ( rs.next() ) {
            faxDispId = rs.getInt("fax_disp_id");
         } 
         else {
            ProcessServer.log.error("[FaxProcessor] Unable to find the fax_disp_id for document_id: " + docId
                  + ", can't link PO to Fax.");
         }
         
         stmt.close();

         if (faxDispId != null) {
            sql.setLength(0);
            sql.append("insert into po_fax(fax_disp_id, po_hdr_id) ");
            sql.append("values ( ?, (select po_hdr_id from po_hdr where po_nbr = ?) )");
            stmt = conn.prepareStatement(sql.toString());

            //
            // Link the fax to the po
            stmt.setInt(1, faxDispId);
            stmt.setString(2, poNbr);
            stmt.executeUpdate();
         }
      }

      finally {
         DbUtils.closeDbConn(null, stmt, null);
         stmt = null;
         sql = null;
      }
   }

   /**
    * Links a quote to a fax
    * 
    * @param quoteId The id of the quote
    * @param docId The fax document id.
    * @param conn The database connection.
    *
    * @throws Exception
    */
   private void linkQuoteToFax(int quoteId, String docId, Connection conn) throws Exception 
   {
      StringBuffer sql = new StringBuffer();
      PreparedStatement stmt = null;

      try {
         sql.setLength(0);
         sql.append("insert into apg_fax(quote_id, fax_disp_id) ");
         sql.append("values ( ?, (select fax_disp_id from b2b_fax_disposition where document_id = ?) )");
         stmt = conn.prepareStatement(sql.toString());

         //
         // Link the fax to the quote
         stmt.setInt(1, quoteId);
         stmt.setString(2, docId);
         stmt.executeUpdate();
      }

      finally {
         DbUtils.closeDbConn(null, stmt, null);
         stmt = null;
         sql = null;
      }
   }

   /**
    * Parses the CreateFaxEntry BOD and pulls out the original fax entry XML
    * document that gets sent to the fax server.
    */
   private void parseBOD() 
   {
      VTDGen vg = new VTDGen();
      AutoPilot ap = new AutoPilot();
      VTDNav vn = null;
      int i = -1;

      try {
         vg.setDoc(m_Bod.getBytes());
         vg.parse(true);
         vn = vg.getNav();
         ap.bind(vn);

         //
         // get the system and component
         ap.selectXPath("/CreateFaxEntry/DataArea/FaxEntry");
         if ( ap.evalXPath() != -1 ) {
            i = vn.getAttrVal("system");

            //
            // Get the system attribute. Should be system but may be sys.
            if ( i != -1 )
               m_Sys = vn.toNormalizedString(i);
            else {
               i = vn.getAttrVal("sys");

               if ( i != -1 )
                  m_Sys = vn.toNormalizedString(i);
            }

            //
            // Get the component attribute. Might also come in as comp even
            // though
            // the schema says component
            i = vn.getAttrVal("component");

            if ( i != -1 )
               m_Comp = vn.toNormalizedString(i);
            else {
               i = vn.getAttrVal("comp");

               if ( i != -1 )
                  m_Comp = vn.toNormalizedString(i);
            }

            i = vn.getAttrVal("linkval");

            if ( i != -1 )
               m_LinkVal = vn.toNormalizedString(i);
         }

         //
         // get sender component
         ap.selectXPath("/CreateFaxEntry/ApplicationArea/Sender/Component");
         if ( ap.evalXPath() != -1 ) {
            i = vn.getText();

            if ( i != -1 )
               m_SenderComp = vn.toNormalizedString(i);
         }

         //
         // get sender task
         ap.selectXPath("/CreateFaxEntry/ApplicationArea/Sender/Task");
         if ( ap.evalXPath() != -1 ) {
            i = vn.getText();

            if ( i != -1 )
               m_SenderTask = vn.toNormalizedString(i);
         }

         //
         // get the FaxFinder XML data
         ap.selectXPath("/CreateFaxEntry/DataArea/FaxEntry/FaxData");
         if ( ap.evalXPath() != -1 ) {
            i = vn.getText();

            if ( i != -1 )
               m_FaxXml = vn.toNormalizedString(i);
         }
      }

      catch ( Exception ex ) {
         ProcessServer.log.error("[FaxProcessor] ", ex);
      }
   }

   /**
    * Parses the response from the fax web service and creates a FaxStatus
    * object with the data.
    *
    * @param resp The http post body returned from the fax web service as a byte array.
    * @return The populated FaxStatus object based on the XML from the call to
    *         the web service.
    */
   private FaxFinderStatus parseFaxResp(byte[] resp) 
   {
      FaxFinderStatus fs = new FaxFinderStatus();
      VTDGen vg = new VTDGen();
      AutoPilot ap = new AutoPilot();
      VTDNav vn = null;
      int i = -1;
      int k = -1;
      StringBuffer tmp = new StringBuffer();

      try {
         vg.setDoc(resp);
         vg.parse(true);
         vn = vg.getNav();
         ap.bind(vn);

         //
         // get the response message
         ap.selectXPath("/response/message");
         if ( ap.evalXPath() != -1 ) {
            i = vn.getText();

            if ( i != -1 )
               fs.statusMsg = vn.toNormalizedString(i);
         }

         ap.selectXPath("/response/fax_entry/fax_entry_url");
         if ( ap.evalXPath() != -1 ) {
            i = vn.getText();

            //
            // parse out the fax key and the entry key. Maybe not elegant
            // but it
            // works.
            // https://10.128.0.79/ffws/v1/ofax/00000005/0000
            // /ffws/v1/ofax/FAXKEY/ENTRYKEY
            if ( i != -1 ) {
               tmp.setLength(0);
               tmp.append(vn.toNormalizedString(i));
               k = tmp.lastIndexOf("/");
               fs.entryKey = tmp.substring(k + 1);
               tmp.delete(k, tmp.length());
               k = tmp.lastIndexOf("/");
               fs.faxKey = tmp.substring(k + 1);

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
            // parse out the data and time. 2011-05-16T15:47:58
            if ( i != -1 ) {
               tmp.setLength(0);
               tmp.append(vn.toNormalizedString(i));
               k = tmp.indexOf("T");
               fs.lastTime = tmp.substring(k + 1);
               fs.lastDate = tmp.substring(0, k);
            }
         }

         //
         // retries are n-1 on the try number response field. The first try
         // doesn't
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
         if (ap.evalXPath() != -1) {
            i = vn.getText();

            if (i != -1)
               fs.recipientPhone = vn.toNormalizedString(i);
         }

         //
         // Misc transmission data
         //
         ap.selectXPath("/response/fax_entry/max_tries");
         if (ap.evalXPath() != -1) {
            i = vn.getText();

            if (i != -1)
               fs.retriesMax = Integer.parseInt(vn.toNormalizedString(i));
         }

         ap.selectXPath("/response/fax_entry/try_interval");
         if (ap.evalXPath() != -1) {
            i = vn.getText();

            if (i != -1)
               fs.retryInterval = Integer.parseInt(vn.toNormalizedString(i));
         }

         ap.selectXPath("/response/fax_entry/pages");
         if (ap.evalXPath() != -1) {
            i = vn.getText();

            if (i != -1)
               fs.pagesSent = Integer.parseInt(vn.toNormalizedString(i));
         }

         ap.selectXPath("/response/fax_entry/approver");
         if (ap.evalXPath() != -1) {
            i = vn.getText();

            if (i != -1)
               fs.approver = vn.toNormalizedString(i);
         }

         ap.selectXPath("/response/fax_entry/approval_time");
         if ( ap.evalXPath() != -1 ) {
            i = vn.getText();

            if ( i != -1 )
               fs.approvalTime = vn.toNormalizedString(i);
         }
      }

      catch (Exception ex) {
         ;
      }

      return fs;
   }

   /**
    * Public interface to start the bod processing. Starts the thread if it has
    * been assigned which will start the processing of the BOD document. This
    * method will return before the processing is finished.
    *
    * Note - The thread start method is not being called and has been replaced
    * with a call to the run method. This is because we're not going to run in
    * multiple threads at this time. This may change. JCF -11/03/2011
    */
   public void processBOD() 
   {
      m_Thread = new Thread(this, m_Name);
      m_Thread.setDaemon(true);
      // m_Thread.start();
      run();
   }

   /**
    * Reposts the original CreateFaxEntry BOD by sending it back to the web
    * service.
    *
    * Note - Sending through the web service should eliminate some throughput
    * or contention issues with trying to pull data and push data to the
    * message broker through the same connection and session.
    */
   private void repostFaxBod(int errorCode) 
   {
      String faxServerUrl = System.getProperty("restsvc.url");
      HttpClient client = new HttpClient();
      PostMethod post = null;
      InputStreamRequestEntity re = null;
      int respCode = 0;
      StringBuffer errMsg = null;
      String service = null;

      //
      // Depending on the error code, the fax will be reposted to different
      // locations.
      try {
         switch (errorCode) {
            case 400: {
               service = "errorfax";
               break;
            }
   
            case 500: {
               service = "sendfax";
               break;
            }
   
            default: {
               service = "sendfax";
            }
         }

         post = new PostMethod(String.format("%s/FaxUtils/%s/%s/%s", faxServerUrl, service, m_Sys, m_Comp));
         post.setRequestHeader("content-type", "application/xml");
         re = new InputStreamRequestEntity(new ByteArrayInputStream(m_Bod.getBytes("UTF-8")));
         post.setRequestEntity(re);

         respCode = client.executeMethod(post);

         if ( respCode != 200 ) {
            errMsg = new StringBuffer();
            errMsg.append("[repostFaxBod]\r\n");
            errMsg.append(String.format("\tresponse code: %d\r\n", respCode));
            errMsg.append(String.format("\tresponse message: %s\r\n\r\n", post.getResponseBodyAsString()));
            errMsg.append("See the FaxUtils web service log for more details /usr/local/tomcat/logs/fax.log");

            ProcessServer.log.error("");
         }
      }

      catch ( Exception ex ) {
         ProcessServer.log.error("[FaxProcessor] ", ex);
         errMsg = new StringBuffer();
         errMsg.append("[repostFaxBod]\r\n");
         errMsg.append("There was an exception while reposting the FAX BOD. The FAX has not been sent.\r\n");
         errMsg.append(String.format("Exception %s; Message: %s", ex.getClass().getName(), ex.getMessage()));
         errMsg.append("See the FaxApp log for more details /usr/local/procserver/log/server.log");
         errMsg.append("\r\n\r\nThe BOD is below, repost through AMQ console\r\n\r\n");
         errMsg.append(m_Bod);
      }

      finally {
         if ( post != null )
            post.releaseConnection();

         if ( errMsg != null )
            ProcessServer.getInstance().notifyMis(errMsg.toString());

         client = null;
         post = null;
         re = null;
         errMsg = null;
         faxServerUrl = null;
      }
   }

   /**
    * Implements the Runnable interface. This is the main processing method.
    * Parses the BOD and sends the actual FAX server XML to the FAX server's
    * web service.
    *
    * Will try multiple times before admitting defeat and reposting.
    */
   public void run() 
   {
      FaxFinderStatus fs = null;
      int respCode = 0;
      int tryCount = 0;
      Connection edbConn = null;

      m_FaxStatus = new FaxStatus();
      m_FaxStatus.startTime = System.currentTimeMillis();
      m_FaxStatus.id = m_Id;

      try {
         m_FaxStatus.currentAction = "processing bod";
         parseBOD();

         if ( m_FaxXml != null && m_FaxXml.length() > 0 ) {
            while ( respCode != 201 && tryCount <= m_MaxTries ) {
               if ( tryCount > 0 )
                  ProcessServer.log.info(String.format("attempting fax retry number: %d ", tryCount));

               m_FaxStatus.currentAction = "sending fax";
               respCode = sendFax();
               tryCount++;
            }

            if ( respCode == 201 ) {
               m_FaxStatus.currentAction = "process fax response";

               if ( m_FFResp != null && m_FFResp.length() > 0 ) {
                  fs = parseFaxResp(m_FFResp.getBytes());
                  m_FaxStatus.uid = fs.senderUid;
                  fs.statusCode = String.valueOf(respCode);
                  ProcessServer.log.info(String.format("[FaxProcessor] respcode: %s, docid: %s", respCode, fs.docId));

                  m_FaxStatus.currentAction = "database processing";
                  
                  //
                  // Database processing.
                  try {
                     edbConn = ConnPool.getInstance().getEDBConn();
                     edbConn.setAutoCommit(true);
                     fs.dispositionId = saveDisposition(fs, edbConn);

                     if ( m_SenderTask.equalsIgnoreCase("confirm order") )
                        linkOrderToFax(Long.parseLong(m_LinkVal), fs.docId, edbConn);

                     if ( m_SenderTask.equalsIgnoreCase("send po") )
                        linkPoToFax(m_LinkVal, fs.docId, edbConn);

                     //
                     // This is the general fax sending web service
                     if ( m_SenderTask.equalsIgnoreCase("send fax") ) {
                        if ( m_Sys.equalsIgnoreCase("eis") && m_Comp.equalsIgnoreCase("apgquote") ) {
                           if ( m_LinkVal != null && m_LinkVal.length() > 0 )
                              linkQuoteToFax(Integer.parseInt(m_LinkVal), fs.docId, edbConn);
                        }
                     }

                     m_FaxStatus.currentAction = "syncing the fax disposition";
                     syncFaxDisposition(fs.docId);
                  }

                  catch ( Exception ex ) {
                     ProcessServer.log.error("[FaxProcessor]", ex);
                  }

                  finally {
                     DbUtils.closeDbConn(edbConn, null, null);
                     edbConn = null;
                  }
               }
               else {
                  ProcessServer.log.error("[FaxProcessor] http post missing response body");
               }
            } 
            else {
               ProcessServer.log.error(String.format("[FaxProcessor] exceeded %d fax attempts; reposting the BOD.  respcode: %s, ", m_MaxTries, respCode));
               repostFaxBod(respCode);

               if ( respCode == 400 ) {
                  StringBuffer msg = new StringBuffer();
                  msg.append("FAX server response code 400; bad XML document or missing file.  See the FaxError queue for the document.\r\n");
                  msg.append(String.format("system: %s\r\n", m_Sys));
                  msg.append(String.format("component: %s\r\n", m_Comp));
                  msg.append(String.format("task: %s\r\n", m_SenderTask));
                  msg.append(String.format("/r/n/r/n%s", m_FaxXml));
                  ProcessServer.getInstance().notifyMis(msg.toString());
                  msg = null;
               }
            }
         }
      }

      finally {
         m_FaxStatus = null;
         m_App.remFaxProc(this);
      }
   }

   /**
    * Saves the preliminary fax disposition data to the database.
    *
    * @param fs A reference to the FaxFinderStatus object.
    *
    * @return The id of the newly inserted record in the database; 0 if it failed.
    */
   private long saveDisposition(FaxFinderStatus fs, Connection conn) 
   {

      PreparedStatement stmt = null;
      ResultSet rs = null;
      StringBuffer sql = new StringBuffer();
      long dispId = 0;

      try {
         if ( conn != null ) {
            sql.append("insert into b2b_fax_disposition( ");
            sql.append("   system, component, trans_id, document_id, state, err_msg, last_date, last_time, ");
            sql.append("   outcome, pages_sched, pages_sent, sender_name, sender_uid, sender_email, sender_phone, ");
            sql.append("   recipient_fax, recipient_company, recipient_name, retries, max_retries, try_interval, ");
            sql.append("   fax_key, entry_key, approver, approval_time, status_code, status_msg ");

            sql.append(") ");
            sql.append("values ( ");
            sql.append(" ?, ?, ?, ?, ?, ?, ?, ");
            sql.append(" ?, ?, ?, ?, ?, ?, ?, ");
            sql.append(" ?, ?, ?, ?, ?, ?, ?, ");
            sql.append(" ?, ?, ?, ?, ?, ? ");
            sql.append(") returning fax_disp_id ");
            stmt = conn.prepareStatement(sql.toString());

            //
            // Insert the fax disposition data. Most of it will be null at
            // this
            // point
            stmt.setString(1, m_Sys);
            stmt.setString(2, m_Comp);
            stmt.setString(3, fs.transmissionId);
            stmt.setString(4, fs.docId);
            stmt.setString(5, fs.state);
            stmt.setString(6, fs.errMsg);
            stmt.setString(7, fs.lastDate);
            stmt.setString(8, fs.lastTime);
            stmt.setString(9, fs.outcome);
            stmt.setInt(10, fs.pagesSched);
            stmt.setInt(11, fs.pagesSent);
            stmt.setString(12, fs.senderName);
            stmt.setString(13, fs.senderUid);
            stmt.setString(14, fs.senderEmail);
            stmt.setString(15, fs.senderPhone);
            stmt.setString(16, fs.recipientFax);
            stmt.setString(17, fs.recipientCompany);
            stmt.setString(18, fs.recipientName);
            stmt.setInt(19, fs.retries);
            stmt.setInt(20, fs.retriesMax);
            stmt.setInt(21, fs.retryInterval);
            stmt.setString(22, fs.faxKey);
            stmt.setString(23, fs.entryKey);
            stmt.setString(24, fs.approver);
            stmt.setString(25, fs.approvalTime);
            stmt.setString(26, fs.statusCode);
            stmt.setString(27, fs.statusMsg);

            rs = stmt.executeQuery();
            
            if ( rs.next() ) {
               dispId = rs.getLong(1);
            }
         } 
         else
            ProcessServer.log.error("[FaxProcessor} Unable to get database connection");
      }
      catch ( Exception ex ) {
         ProcessServer.log.error("[FaxProcessor]", ex);                  
      }

      finally {
         DbUtils.closeDbConn(null, stmt, rs);
         conn = null;
         stmt = null;
      }

      return dispId;
   }

   /**
    * Sends a fax through the fax server.
    *
    * @return The response code from the FaxFinder web service.
    */
   private int sendFax() 
   {
      HttpClient client = null;
      HttpState state = null;
      Credentials credential = null;
      PostMethod post = null;
      RequestEntity re = null;
      int respCode = 0;

      try {
         client = new HttpClient();

         //
         // Create post method.
         post = new PostMethod("http://10.128.0.79/ffws/v1/ofax");
         post.setRequestHeader("content-type", "application/xml");

         credential = new UsernamePasswordCredentials("admin", "admin");
         state = client.getState();
         state.setCredentials(AuthScope.ANY, credential);

         re = new InputStreamRequestEntity(new ByteArrayInputStream(m_FaxXml.getBytes("UTF-8")));
         post.setRequestEntity(re);

         respCode = client.executeMethod(post);
         m_FFResp = post.getResponseBodyAsString();
      }

      catch (Exception ex) {
         ProcessServer.log.error("[FaxProcessor]", ex);
      }

      finally {
         if ( post != null )
            post.releaseConnection();
      }

      return respCode;
   }

   /**
    * Sets the internal BOD member.
    *
    * @param bod The bod.
    * @throws Exception when the bod var is null.
    */
   public void setBOD(String bod) throws Exception 
   {
      if ( bod != null )
         m_Bod = bod;
      else
         throw new Exception("[FaxProcessor] attempt to set bod to null");
   }

   /**
    * Sets the parent application var.
    *
    * @param app A reference to the parent application object.
    * @throws Exception when the monitor var is null.
    */
   public void setApplication(FaxApp app) throws Exception 
   {
      if ( app != null ) {
         m_App = app;
         m_App.addFaxProc(this);
      } 
      else
         throw new Exception("[FaxProcessor] parent fax application can't be set to null");
   }

   /**
    * Creates a SyncFaxDisposition BOD and puts in on the bus for further
    * processing by the MT bod processor.
    *
    * @param docId The document id created from the fax server web server.
    */
   private void syncFaxDisposition(String docId) 
   {
      SyncFaxDisposition sfd = new SyncFaxDisposition();
      FaxDisposition fd = sfd.addDisposition();
      StringBuffer errMsg = new StringBuffer();
      
      try {
         fd.setDocId("", docId);
         fd.addAttribute("delay", "", "60");
         fd.addAttribute("syncCount", "", "1");
         sfd.getApplicationArea().getSender().setComponent("fax processor");
         sfd.getApplicationArea().getSender().setTask("sync disp");
         
         m_App.getChannel().basicPublish(FaxApp.exchange, FaxApp.faxConfRouteKey, null, sfd.toString().getBytes());
 
         ProcessServer.log.info("[syncFaxDisposition] sent fax disposition for: " + docId);
      }

      catch ( Exception ex ) {
         ProcessServer.log.error("[FaxProcessor]", ex);

         errMsg.append("There was an exception while sending the fax disposition; see the log for the stack trace.");
         errMsg.append("\r\n");
         errMsg.append("/usr/local/procserver/logs/server.log");
         errMsg.append("\r\n");
         errMsg.append(String.format("Use the fax document id: %s to get the status. \r\n", docId));
         errMsg.append("The sender should be notified");
      }

      finally {
         fd = null;
         sfd = null;         
      }

      if ( errMsg.length() > 0 )
         ProcessServer.getInstance().notifyMis(errMsg.toString());
   }

}
