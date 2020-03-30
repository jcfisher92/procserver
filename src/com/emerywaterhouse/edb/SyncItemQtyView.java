/**
 * 
 */
package com.emerywaterhouse.edb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.emerywaterhouse.server.ConnPool;
import com.emerywaterhouse.server.ProcessServer;
import com.emerywaterhouse.utils.DbUtils;

/**
 * @author JFisher
 *
 */
public class SyncItemQtyView extends DbJobProc 
{
   String m_CurView;
   String m_CurStatus;
   
   /**
    * 
    */
   public SyncItemQtyView() 
   {
      super();
      
      m_CurView = "";
      m_CurStatus = "";
   }

   /**
    * @param app
    * @param name
    */
   public SyncItemQtyView(EDBApp app, String name) 
   {
      super(app, name);
   }

   /**
    * Pulls the data from the Fascor tables to create the item_qty_view data.
    * 1. truncate the secondary table.
    * 2. add the data from Fascor.
    * 3. Update the indexes.
    * 4. Switch the synonym to the new table.
    */
   @Override
   public void doProcessing()
   {      
      Connection edbConn = null;
      Statement stmt = null;
      ResultSet rs = null;
            
      try {
         m_CurStatus = "Preparing to update Postgres item quantity data";
         edbConn = ConnPool.getInstance().getEDBConn();
         
         if ( edbConn != null ) {
            stmt = edbConn.createStatement();
            rs = stmt.executeQuery("select ejd.hsdb_procs.refresh_item_qoh(1)");
            
            if ( rs.next() ) {
               m_CurView = rs.getString(1);
               m_CurStatus = "Updating table " + m_CurView;
            }
            else
               m_CurStatus = "Unable to update the current view";
                        
            DbUtils.closeDbConn(null, null, rs);
            stmt.executeUpdate(String.format("insert into %s select * from item_qty_view@oraprod", m_CurView));
            
            rs = stmt.executeQuery("select ejd.hsdb_procs.refresh_item_qoh(3)");
            DbUtils.closeDbConn(null, null, rs);
            
            rs = stmt.executeQuery("select ejd.hsdb_procs.refresh_item_qoh(2)");
            DbUtils.closeDbConn(null, null, rs);
            m_CurStatus = "Update of " + m_CurView + " complete";
         }
         else {
            m_CurStatus = "Unable to get db connection";
            ProcessServer.log.error("[SyncItemQtyView] unable to get db connections");
         }
      } 
      
      catch ( SQLException ex ) {
         m_CurStatus = "Error: " + ex.getMessage() + " see log for stacktrace";
         ProcessServer.log.error("[SyncItemQtyView]", ex);
      }
      
      finally {                  
         DbUtils.closeDbConn(edbConn, stmt, null);
         edbConn = null;
         rs = null;         
      }
   }
   
   @Override
   public synchronized String getProcessStatus()
   {
      StringBuffer html = new StringBuffer();
      html.append(getName() + " ");
      
      switch ( m_Status ) {
         case ProcessServer.idle: {
            m_CurStatus = "idle <br>";            
            break;
         }
         
         case ProcessServer.init: {
            m_CurStatus = "initializing <br>";
            break;
         }
                           
         case ProcessServer.stopped: {
            m_CurStatus = "stopped <br>";
            break;
         }
      }
      
      html.append(m_CurStatus);
      
      return html.toString();
   }
}
