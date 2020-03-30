package com.emerywaterhouse.ace;

import java.io.IOException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.TimeoutException;

import javax.jms.JMSException;
import javax.naming.NamingException;

import org.apache.log4j.Logger;

import com.emerywaterhouse.ace.catalog.AceItemBean;
import com.emerywaterhouse.ace.catalog.DataModule;
import com.emerywaterhouse.ace.catalog.ReportModule;
import com.emerywaterhouse.server.ConnPool;
import com.emerywaterhouse.server.ProcessServer;
import com.emerywaterhouse.utils.DbUtils;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;

public class ArticleFeed extends AceJobProc {
   private Logger m_Log;

   private long m_AddCnt;
   private long m_ChgCnt;

   private static final String itemInQueue = "Ace.Item.In";
   private static final String rptReqQueue = "ha.RptReq";

   private java.sql.Connection m_EdbConn;

   private com.rabbitmq.client.Connection m_RmqConn;
   private Channel m_Channel;

   public ArticleFeed()
   {
      super();

      m_Log = Logger.getLogger(ArticleFeed.class);
   }

   public ArticleFeed(AceApp app, String name)
   {
      super(app, name);

      m_Log = Logger.getLogger(ArticleFeed.class);
   }

   /**
    * Perform cleanup on the objects and close db connections ets.
    */
   protected void cleanup()
   {
      closeStatements();
      
      DbUtils.closeDbConn(m_EdbConn, null, null);
      closeRMQConnection();
   }

   /**
    * Closes the connections to the ActiveMQ message broker.
    */
   private void closeRMQConnection()
   {
      try {
         if ( m_Channel != null ) {
            m_Channel.close();
            m_Channel = null;
         }
         
         if ( m_RmqConn != null ) {
            m_RmqConn.close();
            m_RmqConn = null;
         }
      }
      catch ( IOException | TimeoutException ex ) {
         ProcessServer.log.error("[ArticleFeed] Failed to close RMQ Connections.", ex);
      }      
   }

   /**
    * Close prepared statements
    */
   public void closeStatements()
   {
      // DbUtils.closeDbConn(null, m_ItemUpdDel, null);
   }

   /**
    * Connects to the RMQ message broker. Note that the consuming of messages does not happen here.
    *
    * @throws NamingException ex
    * @throws JMSException ex
    * @throws UnknownHostException ex
    */
   public boolean connectToRMQ() throws Exception
   {
      ConnectionFactory cnxFactory = null;
      String user = System.getProperty("msgbroker.user");
      String passwd = System.getProperty("msgbroker.passwd");
      String host = System.getProperty("msgbroker.host");
      int port = Integer.parseInt(System.getProperty("msgbroker.port"));

      cnxFactory = new ConnectionFactory();
      cnxFactory.setAutomaticRecoveryEnabled(true);
      cnxFactory.setUsername(user);
      cnxFactory.setPassword(passwd);
      cnxFactory.setHost(host);
      cnxFactory.setPort(port);

      m_RmqConn = cnxFactory.newConnection("[AceApp]#ArticleFeed");
      m_Channel = m_RmqConn.createChannel();

      return m_Channel != null;
   }

   /**
    *
    */
   @Override
   public void doProcessing()
   {
      SimpleDateFormat df = new SimpleDateFormat("HH:mm");
      Calendar cal = Calendar.getInstance();
      GetResponse response;

      ProcessServer.log.info("[ArticleFeed] Starting processing");

      try {
         getDbConnection();
         connectToRMQ();

         if ( m_EdbConn != null && m_Channel != null )
            while ( m_Status == ProcessServer.running && m_Channel.messageCount(itemInQueue) > 0 ) {
               response = m_Channel.basicGet(itemInQueue, true);
               setData(response.getBody());
               processData();
            }
         else
            ProcessServer.log.error("[ArticleFeed] unable to establish connections");
      }
      catch ( Exception ex ) {
         ProcessServer.getInstance().notifyMis("[AceApp - ArticleFeed]\r\n" + ex.getMessage());
         ProcessServer.log.error("[ArticleFeed]", ex);
      }
      finally {
         cleanup();

         cal.setTimeInMillis(System.currentTimeMillis() + m_Interval);
         m_CurProcStatus = String.format("Update complete; next runtime: %s", df.format(cal.getTime()));
      }
   }

   /**
    * @throws Exception ex
    */
   public void getDbConnection() throws Exception
   {
      m_CurProcStatus = "Connecting to Postgres";
      m_EdbConn = ConnPool.getInstance().getEDBConn();
   }

   /**
    * Process the data retrieved from the queue
    */
   @Override
   public void processData()
   {
      String data = new String(m_Data);

      // ace routes sends end:item.xml when it finishes splitting the xml from ace.
      if ( data.equals("end:item.xml") ) {
         m_Log.info("[ArticleFeed] end processing, sending report");
         sendReports();
      }
      else {
         AceItemBean bean = new AceItemBean(data);

         DataModule dm = new DataModule();

         dm.setConnection(m_EdbConn);
         dm.setLogger(m_Log);

         m_ProcResult = dm.addDbRecord(bean);

         switch ( m_ProcResult ) {
            case DataModule.noUpdate:
            break;

            case DataModule.itemAdd:
               m_AddCnt++;
            break;

            case DataModule.itemChange:
               m_ChgCnt++;
            break;

            case DataModule.itemDelete:
            break;
         }

         dm.close();
      }

      super.processData();
   }

   private void sendReports()
   {
      ReportModule rm = new ReportModule(m_App.getServer().getEnv(), m_Log);
      rm.setEnv(m_App.getServer().getEnv());
      String rptReq = null;

      try {
         if ( m_AddCnt > 0 ) {
            rptReq = rm.getReportRequest("AceItemAdd");

            if ( rptReq.length() == 0 )
               m_Log.error("[ArticleFeed#sendReport] AceItemAdd - missing report request body.");
         }

         if ( m_ChgCnt > 0 ) {
            rptReq = rm.getReportRequest("AceItemChange");

            if ( rptReq.length() == 0 )
               m_Log.error("[ArticleFeed#sendReport] AceItemChange - missing report request body.");            
         }
         
         if ( rptReq != null )
            m_Channel.basicPublish("", rptReqQueue, null, rptReq.getBytes());
      }
      
      catch ( IOException ex ) {
         m_Log.error("[ArticleFeed#sendReport]", ex);
      }
   }
}
