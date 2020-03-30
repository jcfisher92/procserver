package com.emerywaterhouse.fas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;

import com.emerywaterhouse.server.ConnPool;
import com.emerywaterhouse.server.ProcessServer;
import com.emerywaterhouse.utils.DbUtils;

public class UpcUpdate extends FasJobProc 
{
   public static enum Action {
      Unknown,
      Add,
      Change,
      Delete
   };
   
   public static enum FasFacility {
      DC00,
      DC01,
      DC02,
      DC03,
      DC04,
      DC05
   }
   
   private int m_UpdId;  /** comes in on the command line so the a single line can be processed. */
   
   private Action m_Action = Action.Unknown;
      
   private PreparedStatement m_UpdFascor;   
   private PreparedStatement m_UpcQry;
   private PreparedStatement m_UpcUpdData;
   private PreparedStatement m_UpcUpdDel;
   private PreparedStatement m_UpcExists01;
   private PreparedStatement m_UpcExists04;
   
   private Connection m_FasConn;
   private Connection m_EdbConn;
      
   public UpcUpdate()
   {
      super();
      
      m_UpdId = 0;
   }
   
   /**
    * Perform cleanup on the objects and close db connections ets.
    */   
   protected void cleanup()
   {
      closeStatements();
      DbUtils.closeDbConn(m_FasConn, null, null);
      DbUtils.closeDbConn(m_EdbConn, null, null);
   }

   /**
    * Close prepared statements
    */   
   public void closeStatements()
   { 
      DbUtils.closeDbConn(null, m_UpcUpdDel, null);
      DbUtils.closeDbConn(null, m_UpcQry, null);
      DbUtils.closeDbConn(null, m_UpcUpdData, null);
      DbUtils.closeDbConn(null, m_UpdFascor, null);
      DbUtils.closeDbConn(null, m_UpcExists01, null);
      DbUtils.closeDbConn(null, m_UpcExists04, null);
      
      m_UpcUpdDel = null;
      m_UpcQry = null;
      m_UpcUpdData = null;
      m_UpdFascor = null;      
      m_UpcExists01 = null;
      m_UpcExists04 = null;
   }

   /**
    * Handles the processing of the quantity on hand data in Fascor.  Checks the items in Oracle and then gets the 
    * quantity on hand out of Fascor and updates the data in the database.
    */
   @Override
   public void doProcessing() 
   {
      SimpleDateFormat df = new SimpleDateFormat("HH:mm");      
      Calendar cal = Calendar.getInstance();
      
      ProcessServer.log.info("[UpcUpdate] Starting update processing");
      
      try {
         getDbConnections();

         if ( m_EdbConn != null && m_FasConn != null ) {
            if ( prepareStatements() )
               processUpcs();
         }
         else
            ProcessServer.log.error("[UpcUpdate] null DB connections");
      }

      catch ( Exception ex ) {
         ProcessServer.getInstance().notifyMis("[FasApp - UpcUpdate]\r\n" + ex.getMessage());
         ProcessServer.log.error("[UpcUpdate]", ex);
      }

      finally {
         cleanup();
         
         cal.setTimeInMillis(System.currentTimeMillis() + m_Interval);
         m_CurProcStatus = String.format("Update complete; next runtime: %s", df.format(cal.getTime()));
      }      
   }
   
   /**
    * Sends the message to fascor.
    * Formats the message and inserts it into the inbound table in fascor.  We do
    * most of the work here so that the calling client can return asap.  This means
    * the calling client will not know if the data is successfully sent to fascor.  We
    * will need to stream any items to disk that don't make it to fascor.
    *
    * Note -
    *    Use 02 for facility number when testing and 01 for production.
    * @throws Exception 
    */
   private FascorMsg formatFascorMsg(UPC upc, FasFacility facility) throws Exception
   {
      FascorMsg fasMsg = new FascorMsg();
      StringBuffer msg = new StringBuffer();
      char[] filler = new char[99];
      String batch = null;
                  
      //
      // Get the current time in seconds and truncate it to
      // 7 characters so it fits in fascor.
      batch = String.valueOf(System.currentTimeMillis()/1000);
      batch = batch.substring(0, 6);

      Arrays.fill(filler, ' ');
      msg.append(filler);

      msg.replace(0, 4, "0150");

      //
      // If the item is not in sku master, change th action to be an add even though it's a change record.
      if ( m_Action == Action.Change && !upcExists(upc.upcCode, facility) )
         m_Action = Action.Add;
      
      switch ( m_Action ) {
         case Add: {
            msg.setCharAt(4, 'A');
            break;
         }
         
         case Change: {
            msg.setCharAt(4, 'C');            
            break;
         }
         
         case Delete: {
            msg.setCharAt(4, 'D');
            break;
         }
         
         case Unknown: {
            throw new Exception("Unknown update action");
         }
      }
      
      if ( upc.desc.length() > 70 )
         upc.desc = upc.desc.substring(0, 69);
      
      msg.replace(5, 6, getFacilityStr(facility));
      msg.replace(7, 7 + upc.upcCode.length(), upc.upcCode);
      msg.replace(22 , 22 + 7, upc.itemId);
      msg.replace(29, 29 + upc.desc.length(), upc.desc);
      msg.setLength(99);

      try {
         fasMsg.setTrans( "0150" );
         fasMsg.setMsg( msg.toString() );
         fasMsg.setBatch( batch );
      }
      catch ( Exception ex ) {
         ProcessServer.log.error("[UpcUpdate]", ex);
      }

      msg = null;
      filler = null;
      
      return fasMsg;
   }
      
   //
   // Converts the db code to the enumeration
   private Action getAction(String dbCode)
   {
      Action action = Action.Unknown;
      
      if ( dbCode.equalsIgnoreCase("A") )
         action = Action.Add;
      else {
         if ( dbCode.equalsIgnoreCase("C") )
            action = Action.Change;
         else {
            if ( dbCode.equalsIgnoreCase("D") )
               action = Action.Delete;
         }
      }
            
      return action;
   }

   protected void getDbConnections() throws Exception
   {      
      m_CurProcStatus = "Connecting to Postgres";
      m_EdbConn = ConnPool.getInstance().getEDBConn();
      
      //
      // Set this to auto commit so we don't have idle transactions.  This causes issues with EDB and DDL
      m_EdbConn.setAutoCommit(false);
      
      m_CurProcStatus = "Connecting to Fascor";
      m_FasConn = ConnPool.getInstance().getFascorConn(getFasFacId());
      m_FasConn.setAutoCommit(true);
   }
   
   /**
    * 
    * @param facility
    * @return
    */
   protected String getFacilityStr(FasFacility facility)
   {
      String result = null;
      
      switch ( facility ) {
         case DC00: {
            result = "00";
            break;
         }
         case DC01: {
            result = "01";
            break;
         }
         
         case DC02: {
            result = "02";
            break;
         }
         
         case DC03: {
            result = "03";
            break;
         }
         
         case DC04: {
            result = "04";
            break;
         }
         
         case DC05: {
            result = "05";
            break;
         }
      }
      
      return result;
   }
   
   /**
    * Returns true if a upc exists in the specified Fascor facility
    * Note - right now just checking Portland.
    *
    * @param upc String - the upc code to search for
    * @param facility FasFacility - the Fascor facility id
    * @return boolean - true if the item exists
    */
   protected boolean upcExists(String upc, FasFacility facility)
   {    
      ResultSet rs = null;
      boolean result = false;

      if ( facility == FasFacility.DC01 ) {
         try {
            m_UpcExists01.setString(1, upc);
            rs = m_UpcExists01.executeQuery();
   
            result = rs.next();
            rs.close();
         }
      
         catch ( Exception ex ) {
            ProcessServer.log.error("[UpcUpdate]", ex );
         }
      
         finally {         
            rs = null;        
         }
      }
      
      if ( facility == FasFacility.DC04 ) {
         try {
            m_UpcExists04.setString(1, upc);
            rs = m_UpcExists04.executeQuery();
   
            result = rs.next();
            rs.close();
         }
      
         catch ( Exception ex ) {
            ProcessServer.log.error("[UpcUpdate]", ex );
         }
      
         finally {         
            rs = null;        
         }
      }
      
      return result;
   }
   
   /**
    * Prepare the oracle sql queries that we need to create the message
    */
   private boolean prepareStatements()
   {
      boolean Prepared = false;
      StringBuffer sql = new StringBuffer();
      
      try {
         if ( m_EdbConn != null && m_FasConn != null ) { 
            sql.setLength(0);
            sql.append("select upd_id, msg, entity_key ");
            sql.append("from fas_update_obj where trans = '0150' ");
            
            if ( m_UpdId > 0 )
               sql.append("and upd_id = ?");
            
            m_UpcUpdData = m_EdbConn.prepareStatement(sql.toString());
            
            sql.setLength(0);
            sql.append("select upc_code, item_id, description, warehouse_id ");            
            sql.append("from ejd_item_whs_upc ");            
            sql.append("join item_entity_attr on item_entity_attr.ejd_item_id = ejd_item_whs_upc.ejd_item_id and item_type_id = 1 ");
            sql.append("where ejd_upc_id = ?");
            
            m_UpcQry = m_EdbConn.prepareStatement(sql.toString());

            sql.setLength(0);
            sql.append("delete fas_update_obj where upd_id = ?");
            m_UpcUpdDel = m_EdbConn.prepareStatement(sql.toString());
            
            sql.setLength(0);
            sql.append("insert into inbound(batch, trans, text, update_user_id, update_pid) ");
            sql.append("values(?, ?, ?, 'EIS_EMERY', 'ITEM_UPD')");
            m_UpdFascor = m_FasConn.prepareStatement(sql.toString());
            
            sql.setLength(0);
            sql.append("select UPC from DC01EWH.DBO.UPC where UPC = ?");
            m_UpcExists01 = m_FasConn.prepareStatement(sql.toString());
            
            sql.setLength(0);
            sql.append("select UPC from DC04EWH.DBO.UPC where UPC = ?");
            m_UpcExists04 = m_FasConn.prepareStatement(sql.toString());
            
            Prepared = true;
         }
      }

      catch( SQLException ex ) {
         ProcessServer.log.error("[ItemUpdate]", ex);
      }

      finally {         
         sql = null;
      }

      return Prepared;
   }
   
   /**
    * Reads the fas_update_obj table searching for 1110 message.
    */
   private void processUpcs()
   {
      ResultSet rs = null;
      ResultSet upcRs = null;      
      boolean updateOk = true;      
      long ejdUpcId;
      FascorMsg msg = null;
      UPC upc = new UPC();
      boolean isProduction = m_App.getServer().getEnv() == ProcessServer.Environment.Production;
      
      try {
         m_CurProcStatus = "Processing Upc updates";

         //
         // query the database for any 1110 records         
         if ( m_UpdId > 0 )
            m_UpcUpdData.setInt(1, m_UpdId);
         
         rs = m_UpcUpdData.executeQuery();
         
         while ( rs.next() && m_Status == ProcessServer.running ) {
            try {
               ejdUpcId = rs.getLong("entity_key"); 
               m_Action = getAction(rs.getString("msg"));
               m_CurProcStatus = "Processing UPC ID " + ejdUpcId;
               
               //
               // now that we know the item id, query the rest of the item data               
               m_UpcQry.setLong(1, ejdUpcId);
               upcRs = m_UpcQry.executeQuery();
               
               if ( upcRs.next() ) {
                  updateOk = true;
                                   
                  upc.upcCode = upcRs.getString("upc_code");
                  upc.itemId = upcRs.getString("item_id");                  
                  upc.desc = upcRs.getString("description");
                  upc.whsId = upcRs.getInt("warehouse_id");
               }
               
               //
               // Portland
               if ( updateOk && upc.whsId == 1 ) {
                  m_CurProcStatus = String.format("processing upc: %s DC01", upc.upcCode);
                  msg = formatFascorMsg(upc, (isProduction ? FasFacility.DC01:FasFacility.DC02));
                  updateOk = updateInbound(msg);
                  m_CurProcStatus = msg.getMsg();
               }  
               
               //
               // Pittston
               if ( updateOk && upc.whsId == 2 ) {
                  m_CurProcStatus = String.format("processing upc: %s DC04", upc.upcCode);
                  msg = formatFascorMsg(upc, (isProduction ? FasFacility.DC04:FasFacility.DC05));
                  updateOk = updateInbound(msg);
                  m_CurProcStatus = msg.getMsg();
               }

               if ( updateOk ) {
                  m_UpcUpdDel.setInt( 1, rs.getInt("upd_id"));
                  m_UpcUpdDel.execute();
                  m_EdbConn.commit();
               }
               else {                
                  ProcessServer.log.error("[UpcUpdate] Failed to update fascor with UPC " + upc.upcCode + ";" + upc.itemId);
                  m_EdbConn.rollback();
               }
                              
               upcRs.close();
               msg = null;
            }

            catch ( Exception ex ) {
               ProcessServer.log.error("[UpcUpdate]", ex);
            }
         }
         
         rs.close();
      }

      catch ( Exception ex ) {
         ProcessServer.log.error("[UpcUpdate]", ex);
      }

      finally {
         rs = null;
         upcRs = null;
      }
   }
         
   /**
    * Updates the inbound table with a fascor message.
    * Returns a 1 if successful;
    * Places a message in the process log if not successful
    *
    * @param  msg String - the message to be placed in inbound
    * @return int 0=not successful, 1=successful
    */
   protected boolean updateInbound(FascorMsg msg)
   {
      boolean success = true;
      String batch = msg.getBatch();
      String trans = msg.getTrans();
      String imsg = msg.getMsg();

      try {
         m_UpdFascor.setString(1, batch);
         m_UpdFascor.setString(2, trans);
         m_UpdFascor.setString(3, imsg);

         m_UpdFascor.executeUpdate();
      }

      catch ( SQLException ex ) {
         ProcessServer.log.error("[UpcUpdate]", ex);
         success = false;
      }

      return success;
   }
   
   public class UPC 
   {
      public String upcCode;
      public String itemId;
      public String desc;      
      public int whsId;
   };
}
