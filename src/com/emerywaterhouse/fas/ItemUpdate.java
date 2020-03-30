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

public class ItemUpdate extends FasJobProc 
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
   private PreparedStatement m_ItemQry;   
   private PreparedStatement m_ShipUom;
   private PreparedStatement m_VndItem;
   private PreparedStatement m_VelVal;   
   private PreparedStatement m_SubItem;
   private PreparedStatement m_ItemUpdData;
   private PreparedStatement m_ItemUpdDel;
   private PreparedStatement m_ItemExists;
      
   private Connection m_FasConn;
   private Connection m_EdbConn;
      
   public ItemUpdate()
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
      DbUtils.closeDbConn(null, m_ItemUpdDel, null);
      DbUtils.closeDbConn(null, m_ItemQry, null);
      DbUtils.closeDbConn(null, m_ItemUpdData, null);
      DbUtils.closeDbConn(null, m_UpdFascor, null);
      DbUtils.closeDbConn(null, m_ShipUom, null);
      DbUtils.closeDbConn(null, m_VndItem, null);
      DbUtils.closeDbConn(null, m_VelVal, null);
      DbUtils.closeDbConn(null, m_ItemExists, null);
      
      m_ItemUpdDel = null;
      m_ItemQry = null;
      m_ItemUpdData = null;
      m_UpdFascor = null;
      m_ShipUom = null;
      m_VndItem = null;
      m_VelVal = null;
      m_ItemExists = null;
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
      
      ProcessServer.log.info("[ItemUpdadte] Starting update processing");
      
      try {
         getDbConnections();

         if ( m_EdbConn != null && m_FasConn != null ) {
            if ( prepareStatements() )
               processItems();
         }
         else
            ProcessServer.log.error("[ItemUpdate] null DB connections");
      }

      catch ( Exception ex ) {
         ProcessServer.getInstance().notifyMis("[FasApp - ItemUpdate]\r\n" + ex.getMessage());
         ProcessServer.log.error("[ItemUpdate]", ex);
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
   private FascorMsg formatFascorMsg(Item item, String facility) throws Exception
   {
      FascorMsg fasMsg = new FascorMsg();
      StringBuffer msg = new StringBuffer();
      char[] filler = new char[321];
      String vndItem = null;
      String vndId = null;
      String wgt = null;
      String batch = null;
      String desc = null;
      String velocity = null;
      String subItem = null;
      String shipUnit = null;
      String itemCost = null;
      String masterPack = null;  // stock pack or pallet qty
      String bulkPick = null;
      int Len = 0;

      //
      // Get the shipping units for use later with the message.  It's more efficient to get
      // this once and use it multiple times since it makes db calls.
      shipUnit = getShipUnit(item.shipUnitId);

      //
      // Get the current time in seconds and truncate it to
      // 7 characters so it fits in fascor.
      batch = String.valueOf(System.currentTimeMillis()/1000);
      batch = batch.substring(0, 6);

      Arrays.fill(filler, ' ');
      msg.append(filler);

      msg.replace(0, 4, "1110");

      //
      // If the item is not in sku master, change th action to be an add even though it's a change record.
      if ( m_Action == Action.Change && !itemExists(item.itemId, facility) )
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
      
      msg.replace(5, 6, facility);
      msg.replace(7, 14, item.itemId);

      //
      // Some items don't have a upc code so it will be null.
      if ( item.upc != null )
         msg.replace(14, 14 + item.upc.length(), item.upc);

      //
      // If the item is being deleted, don't fill in the rest of the information
      if ( m_Action != Action.Delete ) {
         velocity = getVelocity(item.velocityId);

         if ( velocity != null ) {
            msg.replace(39, 39, velocity);

            // Don't update the cycle count velocity.  They are calculated
            // differently by Fascor.  Only populate on an add.
            if ( m_Action == Action.Add )
               msg.replace(40, 40, velocity);
         }

         //
         // Tool Repair project.  Items of type TOOL REPAIR and TOOL PART
         // have an item class of 09
         if ( item.itemType.equals("TOOL REPAIR") || item.itemType.equals("TOOL PART") )
            msg.replace(29, 30, "09");

         //
         // Fascor has it's description field broken down into
         // two separate 35 character fields however we can just paste across
         // the fields.  Have to truncate at 70.
         if ( item.itemType.equalsIgnoreCase("ACE") )
            desc = "ACE Virtual NO STOCK";
         else
            desc = item.desc;

         Len = desc.length();

         if ( Len > 70 ) {
            Len = 70;
            desc = desc.substring(0, 69);
         }

         msg.replace(41, 41 + Len, desc);

         //
         // Have to move the decimal over two places and then make sure
         // the length will fit.  Also have to right align the value.
         wgt = rightAlign(item.weight);
         msg.replace(153, 160, wgt);

         msg.setCharAt(161, 'N');
         vndItem = getVndItem(item.itemEaId, item.vendorId);

         if ( vndItem != null )
            msg.replace(162, 162 + vndItem.length(), vndItem);

         vndId = Long.toString(item.vendorId);
         msg.replace(192, 192 + vndId.length(), vndId);

         //
         // Set the bulk qty (stock pack or pallet qty) field and right align with 0s
         msg.replace(276, 283, "0000000");

         //
         // If the bulk pick flag is set and pallet qty > 0, set the master pack
         // as the pallet qty, otherwise, set as item stock pack
         if( item.bulkPick ) {
            bulkPick = "Y";

            if( item.palletQty > 0 )
               masterPack = Integer.toString(item.palletQty);
            else
               masterPack = Integer.toString(item.stockPack);
         }
         else {
            bulkPick = "N";
            masterPack = Integer.toString(item.stockPack);
         }

         msg.replace(283-masterPack.length(), 283, masterPack);

         msg.replace(283, 283 + shipUnit.length(), shipUnit);

         if (item.cost > 0 ) {
            //
            // right align and pad with zeros
            itemCost = rightAlign(item.cost);
            msg.replace(289, 296, itemCost);
         }

         subItem = item.subItem;
         if ( subItem != null && !isVirtual(subItem) )
            msg.replace(297, 303, subItem);

         msg.replace(304, 304 + shipUnit.length(), shipUnit);

         //
         // Per Dale/Lisa/Dean - If doing an item change, don't set the bulk pick flag.
         // According to Dale it can be left blank.
         if ( m_Action == Action.Add )
            msg.replace(308, 309, bulkPick);

         msg.setLength(321);
      }

      try {
         fasMsg.setTrans( "1110" );
         fasMsg.setMsg( msg.toString() );
         fasMsg.setBatch( batch );
      }
      catch ( Exception ex ) {
         ProcessServer.log.error("[ItemUpdate]", ex);
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
    * Returns the shipping uom
    */
   private String getShipUnit(int shipUnitId) throws SQLException
   {
      String Uom = null;
      ResultSet rset = null;

      try {
         m_ShipUom.setInt(1, shipUnitId);
         rset = m_ShipUom.executeQuery();

         if ( rset.next() )
            Uom = rset.getString(1);
      }

      finally {
         if ( rset != null )
            rset.close();

         rset = null;
      }

      return Uom;
   }

   /**
    * Pulls the velocity value out of the oracle db based on the
    * items velocity code id field.
    */
   private String getVelocity(int velId) throws SQLException
   {
      String Velocity = null;
      ResultSet RSet = null;

      try {
         m_VelVal.setInt(1, velId);
         RSet = m_VelVal.executeQuery();

         if ( RSet.next() )
            Velocity = RSet.getString(1);
      }

      finally {
         if ( RSet != null )
            RSet.close();
      }

      return Velocity;
   }

   /**
    * Gets the vendor's part number for the specified item.
    *
    * @return String The vendor item number.
    * @throws SQLException
    */
   private String getVndItem(long itemEaId, long vndId) throws SQLException
   {
      String VndItem = null;
      ResultSet RSet = null;

      try {
         m_VndItem.setLong(1, itemEaId);
         m_VndItem.setLong(2, vndId);

         RSet = m_VndItem.executeQuery();

         if ( RSet.next() )
            VndItem = RSet.getString(1);
      }

      finally {
         if ( RSet != null )
            RSet.close();
      }

      return VndItem;
   }
   
   /**
    * Checks a substitute item to determine if it is a virtual item.  Virtual
    * items can't go to fascor.
    *
    * @param subItem String - the substitute item in question.
    * @return boolean true if the item is virtual, false if not.
    *
    * History:
    *   04/22/2004 - Use item type instead of item.virtual  pjr
    */
   private boolean isVirtual(String subItem) throws SQLException
   {
      boolean virtual = false;
      ResultSet rset = null;

      try {
         if ( subItem != null ) {
            m_SubItem.setString(1, subItem);
            rset = m_SubItem.executeQuery();

            if ( rset.next() )
               virtual = rset.getString(1).equals("VIRTUAL");
         }
      }

      finally {
         if ( rset != null )
            rset.close();

         rset = null;
      }

      return virtual;
   }
  
   /**
    * Returns true if an item exists in the specified Fascor facility
    * Note - right now just checking Portland.
    *
    * @param itemId String - the item id to search for
    * @param facility String - the Fascor facility id
    * @return boolean - true if the item exists
    */
   protected boolean itemExists(String itemId, String facility)
   {    
      ResultSet rs = null;
      boolean result = false;

      try {
         m_ItemExists.setString(1, itemId);
         rs = m_ItemExists.executeQuery();

         result = rs.next();
         rs.close();
      }

      catch ( Exception e ) {
         ProcessServer.log.error("[ItemUpdate]", e);
      }

      finally {        
         rs = null;        
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
            sql.append("from fas_update_obj where trans = '1110' ");
            
            if ( m_UpdId > 0 )
               sql.append("and upd_id = ?");
            
            m_ItemUpdData = m_EdbConn.prepareStatement(sql.toString());
            
            sql.setLength(0);
            sql.append("select ");
            sql.append("   item_entity_attr.item_ea_id, item_entity_attr.item_id, item_entity_attr.description, item_entity_attr.vendor_id, ");
            sql.append("   item_entity_attr.ship_unit_id, pallet_qty, stock_pack, velocity_id, upc_code, ");
            sql.append("   item_type.itemtype, weight, buy, sub_item.item_id as sub_item_id, ");
            sql.append("   decode(nvl(item_option_def.description, 'N'), 'BULK PICK', true, false)::boolean as bulk_pick ");            
            sql.append("from item_entity_attr ");
            sql.append("join warehouse on warehouse.warehouse_id = ? ");
            sql.append("join ejd_item on ejd_item.ejd_item_id = item_entity_attr.ejd_item_id ");
            sql.append("join item_type on item_type.item_type_id = item_entity_attr.item_type_id ");            
            sql.append("left outer join ejd_item_whs_upc on ejd_item_whs_upc.ejd_item_id = item_entity_attr.ejd_item_id and ");
            sql.append("   ejd_item_whs_upc.warehouse_id = warehouse.warehouse_id ");
            sql.append("left outer join ejd_item_warehouse on ejd_item_warehouse.ejd_item_id = item_entity_attr.ejd_item_id and ");
            sql.append("   ejd_item_warehouse.warehouse_id = warehouse.warehouse_id ");
            sql.append("left outer join ejd_item_price on ejd_item_price.ejd_item_id = item_entity_attr.ejd_item_id and ");
            sql.append("   ejd_item_price.warehouse_id = warehouse.warehouse_id ");
            sql.append("left outer join item_ea_sub on item_ea_sub.item_ea_id = item_entity_attr.item_ea_id and (auto_sub = 1 or required_sub = 1) ");
            sql.append("left outer join item_entity_attr sub_item on sub_item.item_ea_id = item_ea_sub.subitem_ea_id and rank = 1 ");
            
            // Check for a BULK PICK option only -epearson
            sql.append("left outer join item_ea_option on item_ea_option.item_ea_id = item_entity_attr.item_ea_id ");
            sql.append("left outer join item_option_def on item_option_def.item_opt_def_id = item_ea_option.item_opt_def_id and ");
            sql.append("   item_option_def.description = 'BULK PICK' ");

            sql.append("where item_entity_attr.item_ea_id = ?");            
            m_ItemQry = m_EdbConn.prepareStatement(sql.toString());

            sql.setLength(0);
            sql.append("delete fas_update_obj where upd_id = ?");
            m_ItemUpdDel = m_EdbConn.prepareStatement(sql.toString());

            m_ShipUom = m_EdbConn.prepareStatement("select unit from ship_unit where unit_id = ?");
            
            sql.setLength(0);
            sql.append("select vendor_item_num from vendor_item_ea_cross where item_ea_id = ? and vendor_id = ?");
            m_VndItem = m_EdbConn.prepareStatement(sql.toString());

            sql.setLength(0);
            sql.append("select velocity from item_velocity where velocity_id = ?");
            m_VelVal = m_EdbConn.prepareStatement(sql.toString());

            sql.setLength(0);
            sql.append("select item_type.itemtype from item, item_type ");
            sql.append("where item.item_id = ? and item_type.item_type_id = item.item_type_id ");
            m_SubItem = m_EdbConn.prepareStatement(sql.toString());
            
            sql.setLength(0);
            sql.append("insert into inbound(batch, trans, text, update_user_id, update_pid) ");
            sql.append("values(?, ?, ?, 'EIS_EMERY', 'ITEM_UPD')");
            m_UpdFascor = m_FasConn.prepareStatement(sql.toString());
            
            sql.setLength(0);
            sql.append("select SKU from DC01EWH.DBO.SKU_Master where SKU = ?");
            m_ItemExists = m_FasConn.prepareStatement(sql.toString());
            
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
   private void processItems()
   {
      ResultSet rs = null;
      ResultSet itemRs = null;      
      boolean updateOk = true;
      boolean aceChange = false;
      long itemEaId;
      FascorMsg msg = null;
      Item item = new Item();
      boolean isProduction = m_App.getServer().getEnv() == ProcessServer.Environment.Production;
      
      try {
         m_CurProcStatus = "Processing item updates";

         //
         // query the database for any 1110 records         
         if ( m_UpdId > 0 )
            m_ItemUpdData.setInt(1, m_UpdId);
         
         rs = m_ItemUpdData.executeQuery();
         
         while ( rs.next() && m_Status == ProcessServer.running ) {
            try {
               itemEaId = rs.getLong("entity_key"); 
               m_Action = getAction(rs.getString("msg"));
               m_CurProcStatus = "Processing item_ea_id " + itemEaId;
               
               //
               // now that we know the item id, query the rest of the item data
               m_ItemQry.setInt(1, 1);
               m_ItemQry.setLong(2, itemEaId);
               itemRs = m_ItemQry.executeQuery();
               
               if ( itemRs.next() ) {
                  updateOk = true;
                  aceChange = false;
                  
                  item.itemEaId = itemEaId;
                  item.itemId = itemRs.getString("item_id");
                  item.cost = itemRs.getDouble("buy");
                  item.desc = itemRs.getString("description");
                  item.palletQty = itemRs.getInt("pallet_qty");
                  item.shipUnitId = itemRs.getInt("ship_unit_id");
                  item.stockPack = itemRs.getInt("stock_pack");
                  item.subItem = itemRs.getString("sub_item_id");
                  item.velocityId = itemRs.getInt("velocity_id");
                  item.vendorId = itemRs.getInt("vendor_id");
                  item.itemType = itemRs.getString("itemtype"); // 04/24/2004 - use item type instead of virtual
                  item.weight = itemRs.getFloat("weight");
                  item.upc = itemRs.getString("upc_code");
                  item.bulkPick = itemRs.getBoolean("bulk_pick");
                  
                  aceChange = ( m_Action == Action.Change && (item.itemType.equals("ACE") || item.itemType.equals("EXPANDED ASST")) );
               }
               
               //
               // Portland
               if ( updateOk && !aceChange ) {
                  m_CurProcStatus = String.format("processing item: %s DC01", item.itemId);
                  msg = formatFascorMsg(item, (isProduction ? "01":"02"));
                  updateOk = updateInbound(msg);
                  m_CurProcStatus = msg.getMsg();
               }  
               
               //
               // Pittston
               if ( updateOk  && !aceChange ) {
                  m_CurProcStatus = String.format("processing item: %s DC04", item.itemId);
                  msg = formatFascorMsg(item, (isProduction ? "04":"05"));
                  updateOk = updateInbound(msg);
                  m_CurProcStatus = msg.getMsg();
               }
                              
               if ( updateOk || aceChange ) {
                  m_ItemUpdDel.setInt( 1, rs.getInt("upd_id"));
                  m_ItemUpdDel.execute();
                  m_EdbConn.commit();
               }
               else {                
                  ProcessServer.log.error("[ItemUpdate] Failed to update fascor with item " + item.itemId);
                  m_EdbConn.rollback();
               }
                              
               itemRs.close();
               msg = null;
            }

            catch ( Exception ex ) {
               ProcessServer.log.error("[ItemUpdate]", ex);
            }
         }
         
         rs.close();
      }

      catch ( Exception ex ) {
         ProcessServer.log.error("[ItemUpdate]", ex);
      }

      finally {
         rs = null;
         itemRs = null;
      }
   }
   
   /**
    * Converts a double to a 0 padded right aligned string that is eight characters long.
    * This is used in the fascor messages where the decimal place is implied.
    *
    * @param data double - this is the data to convert.
    * @return String - the formatted string right aligned and 0 padded.
    */
   private String rightAlign(double data)
   {
      StringBuffer buf = new StringBuffer("00000000");
      String tmp = String.valueOf((int)(data*100));

      if ( tmp.length() > 8 )
         tmp = tmp.substring(0, 7);

      buf.replace((7-tmp.length())+1, 8, tmp);

      return buf.toString();
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
         ProcessServer.log.error("[ItemUpdate]", ex);
         success = false;
      }

      return success;
   }
   
   public class Item 
   {
      public boolean bulkPick;
      public double cost;
      public String desc;
      public String itemId;
      public String itemType;
      public int palletQty;
      public int shipUnitId;
      public int stockPack;
      public String subItem;
      public String upc;
      public int velocityId;
      public long vendorId;
      public String vendorName;
      public double weight;
      public long itemEaId;
   };
}
