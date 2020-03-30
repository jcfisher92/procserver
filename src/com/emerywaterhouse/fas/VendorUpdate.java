package com.emerywaterhouse.fas;

import com.emerywaterhouse.server.ConnPool;
import com.emerywaterhouse.server.ProcessServer;
import com.emerywaterhouse.utils.DbUtils;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;

/**
 * @author JFisher
 */
public class VendorUpdate extends FasJobProc 
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

   private PreparedStatement m_GetVndAddr;
   private PreparedStatement m_VendorQry;
   private PreparedStatement m_VndUpdData;
   private PreparedStatement m_VndUpdDel;
   private PreparedStatement m_UpdFascor;

   private Connection m_FasConn;
   private Connection m_EdbConn;
   
   public VendorUpdate() 
   {
      super();
      
      m_UpdId = 0;
   }

   /**
    * @param app
    * @param name
    */
   public VendorUpdate(FasApp app, String name) 
   {
      super(app, name);
   }

   /**
    * Perform cleanup on the objects and close db connections ets.
    */
   private void cleanup() 
   {
      closeStatements();
      DbUtils.closeDbConn(m_FasConn, null, null);
      DbUtils.closeDbConn(m_EdbConn, null, null);           
   }

   /**
    * Close prepared statements
    */
   public void closeStatements() {
      DbUtils.closeDbConn(null, m_VendorQry, null);
      DbUtils.closeDbConn(null, m_VndUpdData, null);
      DbUtils.closeDbConn(null, m_GetVndAddr, null);
      DbUtils.closeDbConn(null, m_VndUpdDel, null);
      DbUtils.closeDbConn(null, m_UpdFascor, null);

      m_VendorQry = null;
      m_VndUpdData = null;
      m_GetVndAddr = null;
      m_VndUpdDel = null;
      m_UpdFascor = null;
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
      
      ProcessServer.log.info("[VendorUpdadte] Starting update processing");
      
      try {
         getDbConnections();

         if ( m_EdbConn != null && m_FasConn != null ) {
            if ( prepareStatements() )
               processVendors();
         }
         else
            ProcessServer.log.error("[VendorUpdate] null DB connections");
      }

      catch ( Exception ex ) {
         ProcessServer.getInstance().notifyMis("[FasApp - VendorUpdate]\r\n" + ex.getMessage());
         ProcessServer.log.error("[VendorUpdate]", ex);
      }

      finally {
         cleanup();
         
         cal.setTimeInMillis(System.currentTimeMillis() + m_Interval);
         m_CurProcStatus = String.format("Update complete; next runtime: %s", df.format(cal.getTime()));
      }      
   }
   
   /**
    * Sends the message to fascor. Formats the message and inserts it into the
    * inbound table in fascor.
    * <p>
    * Note - Use 02 for facility number when testing and 01 for production.
    *
    * @param facility String - the Fascor facility
    */
   private FascorMsg formatFascorMsg(long vndId, String vndName, String facility) throws Exception 
   {
      FascorMsg fasMsg = new FascorMsg();
      String fasId;
      String Name;
      String Batch;
      String Addr1;
      String Addr2;
      String City;
      String State;
      String Zip;
      String Country;
      int Len = 0;
      ResultSet rs = null;
      StringBuffer Msg = new StringBuffer();
      char[] Filler = new char[245];

      fasMsg.setTrans("0140");
      fasId = Long.toString(vndId);

      //
      // Get the current time in seconds and truncate it to
      // 7 characters so it fits in fascor.
      Batch = String.valueOf(System.currentTimeMillis() / 1000);
      Batch = Batch.substring(0, 6);

      Arrays.fill(Filler, ' ');
      Msg.append(Filler);

      Msg.replace(0, 3, "0140");

      switch ( m_Action ) {
      case Add: {
         Msg.setCharAt(4, 'A');
         break;
      }

      case Change: {
         Msg.setCharAt(4, 'C');
         break;
      }

      case Delete: {
         Msg.setCharAt(4, 'D');
         break;
      }

      case Unknown:
         throw new Exception("Unknown update action");
      }

      Msg.replace(5, 6, facility);
      Msg.replace(7, 7 + fasId.length(), fasId);

      if ( m_Action != Action.Delete ) {
         if (vndName == null)
            throw new Exception(vndId + " missing vendor name");

         //
         // Fascor's name field only has 40 characters. Ours is 75.
         // We have to trim down to 40 if it's larger.
         Len = vndName.length();
         Name = vndName.substring(0, ((Len > 40) ? 39 : Len));
         Msg.replace(13, 13 + Name.length(), Name);

         try {
            m_GetVndAddr.setLong(1, vndId);
            rs = m_GetVndAddr.executeQuery();

            if (rs.next()) {
               Addr1 = rs.getString("addr1");
               Addr2 = rs.getString("addr2");
               City = rs.getString("city");
               State = rs.getString("state");
               Zip = rs.getString("postal_code");
               Country = rs.getString("country");

               Len = Addr1.length();
               Addr1 = Addr1.substring(0, ((Len > 40) ? 39 : Len));
               Msg.replace(53, 53 + Addr1.length(), Addr1);

               if (Addr2 != null) {
                  Len = Addr2.length();
                  Addr2 = Addr2.substring(0, ((Len > 40) ? 39 : Len));
                  Msg.replace(93, 93 + Addr2.length(), Addr2);
               }

               Len = City.length();
               City = City.substring(0, ((Len > 40) ? 39 : Len));
               Msg.replace(173, 173 + City.length(), City);

               if (State.length() >= 2)
                  Msg.replace(213, 215, State.substring(0, 2));

               Len = Zip.length();
               Zip = Zip.substring(0, ((Len > 10) ? 9 : Len));
               Msg.replace(215, 215 + Zip.length(), Zip);

               Len = Country.length();
               Country = Country.substring(0, ((Len > 20) ? 19 : Len));
               Msg.replace(225, 225 + Country.length(), Country);
            }
         } 
         
         catch (Exception ex) {
            ProcessServer.log.error("[VendorUpdate]", ex);
         } 
         
         finally {
            Name = null;
            City = null;
            State = null;
            Addr1 = null;
            Addr2 = null;
            Zip = null;
            Country = null;
         }
      }

      try {
         Msg.setLength(245);
         fasMsg.setMsg(Msg.toString());
         fasMsg.setBatch(Batch);
      } 
      
      catch (Exception ex) {
         ProcessServer.log.error("[VendorUpdate]", ex);
      } 
      
      finally {
         Msg = null;
      }

      return fasMsg;
   }

   // Converts the db code to the enumeration
   private Action getAction(String dbCode) 
   {
      Action action = Action.Unknown;

      if (dbCode.equalsIgnoreCase("A"))
         action = Action.Add;
      else {
         if (dbCode.equalsIgnoreCase("C"))
            action = Action.Change;
         else {
            if (dbCode.equalsIgnoreCase("D"))
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
    * Prepare the oracle sql queries that we need to create the message
    */
   private boolean prepareStatements() 
   {
      boolean Prepared = false;
      StringBuilder sql = new StringBuilder();

      try {
         if ( m_EdbConn != null && m_FasConn != null ) {

            sql.setLength(0);
            sql.append("select upd_id, msg, entity_key ");
            sql.append("from fas_update_obj where trans = '0140' ");

            if ( m_UpdId > 0 )
               sql.append("and upd_id = ?");

            m_VndUpdData = m_EdbConn.prepareStatement(sql.toString());

            sql.setLength(0);
            sql.append("select name from vendor where vendor.vendor_id = ?");
            m_VendorQry = m_EdbConn.prepareStatement(sql.toString());

            sql.setLength(0);
            sql.append("delete fas_update_obj where upd_id = ?");
            m_VndUpdDel = m_EdbConn.prepareStatement(sql.toString());

            sql.setLength(0);
            sql.append("select addr1, addr2, city, state, postal_code, country ");
            sql.append("from vendor_address ");
            sql.append(
                  "join vendor_address_type vat on vat.vat_id = vendor_address.vat_id and vat.type in ('SHIPPING','SALES') ");
            sql.append("where vendor_id = ?");
            m_GetVndAddr = m_EdbConn.prepareStatement(sql.toString());

            sql.setLength(0);
            sql.append("insert into inbound(batch, trans, text, update_user_id, update_pid) ");
            sql.append("values(?, ?, ?, 'EIS_EMERY', 'VND_UPDATE')");
            m_UpdFascor = m_FasConn.prepareStatement(sql.toString());
            Prepared = true;
         }
      } 
      
      catch ( SQLException ex ) {
         ProcessServer.log.error("[VendorUpdate]", ex);
         Prepared = false;
      }

      return Prepared;
   }

   /**
    * Reads the fas_update_obj table searching for 0140 message.
    */
   private void processVendors() 
   {
      ResultSet rs = null;
      ResultSet vendorRs = null;
      FascorMsg msg = null;
      boolean updateOk = false;
      long vndId;    
      String vndName = null;
      boolean isProduction = m_App.getServer().getEnv() == ProcessServer.Environment.Production;

      try {
         m_CurProcStatus = "Processing vendor updates";
        
         
         // query the database for any 0140 records
         if ( m_UpdId > 0 )
            m_VndUpdData.setInt(1, m_UpdId);

         rs = m_VndUpdData.executeQuery();

         while ( rs.next() && m_Status == ProcessServer.running ) {
            try {
               vndId = rs.getLong("entity_key");
               m_Action = getAction(rs.getString("msg"));
                              
               //
               // now that we know the vendor id, query the rest of the
               // vendor data
               m_VendorQry.setLong(1, vndId);
               vendorRs = m_VendorQry.executeQuery();

               if ( vendorRs.next() ) {    
                  vndName = vendorRs.getString("name");
                                 
                  updateOk = true;
   
                  // Portland
                  m_CurProcStatus = String.format("processing vendor: %d DC01", vndId);
                  msg = formatFascorMsg(vndId, vndName, (isProduction ? "01":"02"));
   
                  if (!updateInbound(msg))
                     updateOk = false;
   
                  // Pittston
                  m_CurProcStatus = String.format("processing vendor: %d DC04", vndId);
                  msg = formatFascorMsg(vndId, vndName, (isProduction ? "04":"05"));
   
                  if ( updateOk ) {
                     if ( !updateInbound(msg) )
                        updateOk = false;
                  }
   
                  if ( updateOk ) {
                     m_VndUpdDel.setInt(1, rs.getInt("upd_id"));
                     m_VndUpdDel.execute();                  
                     m_EdbConn.commit();
                  } 
                  else {                  
                     ProcessServer.log.error("[VendorUpdate] Failed to update fascor with vendor " + vndId);
                     m_EdbConn.rollback();
                  }
   
                  ProcessServer.log.info(String.format("[VendorUpdate] Finished vndId update for %d", vndId));
               }

               vendorRs.close();
               msg = null;
            } 
            
            catch ( Exception ex ) {
               ProcessServer.log.error("[VendorUpdate]", ex);
            }
         }

         rs.close();
      } 
      
      catch (Exception ex) {
         m_CurProcStatus = "Error";
         ProcessServer.log.error("[VendorUpdate]", ex);
      } 
      
      finally {         
         rs = null;
         vendorRs = null;
      }
   }
     
   /**
    * Updates the inbound table with a fascor message. Returns a 1 if
    * successful; Places a message in the process log if not successful
    *
    * @param msg
    * String - the message to be placed in inbound
    * @return int 0=not successful, 1=successful
    */
   protected boolean updateInbound(FascorMsg msg) 
   {
      boolean success = true;

      try {
         m_UpdFascor.setString(1, msg.getBatch());
         m_UpdFascor.setString(2, msg.getTrans());
         m_UpdFascor.setString(3, msg.getMsg());

         m_UpdFascor.executeUpdate();
      } 
      
      catch (SQLException ex) {
         ProcessServer.log.error("[VendorUpdate]", ex);
         success = false;
      }

      return success;
   }   
}
