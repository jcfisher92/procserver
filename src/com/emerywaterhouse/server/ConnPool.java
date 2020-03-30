/**
 * File: ConnPool.java
 * Description: Data connector class.  Encapsulates a data connection to multiple databases.
 *
 * @author Jeff Fisher
 *
 * Create Date: 11/02/2011
 * Last Update: $Id: ConnPool.java,v 1.2 2012/11/06 15:09:16 jfisher Exp $
 *
 * History:
 *    $Log: ConnPool.java,v $
 *    Revision 1.2  2012/11/06 15:09:16  jfisher
 *    Removed wasp references and changed some static references.
 *
 *    Revision 1.1  2012/03/07 16:13:00  jfisher
 *    Initial add
 *
 */

package com.emerywaterhouse.server;

//
// import java.sql package to use JDBC
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import com.emerywaterhouse.codes.ReturnCodes;
import com.emerywaterhouse.utils.Crypto;

/**
 * The connection pooling class.
 */
public class ConnPool
{
   private static ConnPool m_Instance;
   
   private String m_EDBUrl;
   private String m_EDBUid;
   private String m_EDBPwd;
   
   /**
    * Default constructor - private so it can't be called.
    */
   private ConnPool()
   {
      super();

      Properties props = System.getProperties();
      Crypto crypto = new Crypto();

      switch( ProcessServer.getInstance().getEnv() ) {
         case Test:            
            m_EDBUrl = props.getProperty("db.edb.test.url");
            m_EDBUid = props.getProperty("db.edb.test.uid");
            m_EDBPwd = props.getProperty("db.edb.test.pwd");
         break;

         case Production:                        
            m_EDBUrl = props.getProperty("db.edb.prod.url");
            m_EDBUid = props.getProperty("db.edb.prod.uid");
            m_EDBPwd = props.getProperty("db.edb.prod.pwd");
         break;
      }

      try {         
         m_EDBPwd = crypto.decrypt(m_EDBPwd);
      }

      catch ( Exception ex ) {
         ProcessServer.log.fatal("[ConnPool]", ex);
         ProcessServer.getInstance().notifyMis("Unable to decrypt database passwords, server startup aborted.  See log for details.");
         System.exit(ReturnCodes.EXCEPTION);
      }


      try {
         DriverManager.registerDriver(new net.sourceforge.jtds.jdbc.Driver());
      }

      catch( Exception ex ) {
         ProcessServer.log.error("[ConnPool]", ex);
         ProcessServer.getInstance().notifyMis("error registering jtds driver, see log for details");
      }
      
      try {
         DriverManager.registerDriver(new com.edb.Driver());
      }

      catch( Exception ex ) {
         ProcessServer.log.error("[ConnPool]", ex);
         ProcessServer.getInstance().notifyMis("error registering enterprisedb driver, see log for details");
      }      
   }

   /**
    * Clean up resource allocation by closing all the open connections and
    * setting anything else allocated to null.
    * @throws Throwable
    */
   @Override
   public void finalize() throws Throwable
   {
      super.finalize();
   }

   /**
    * Creates a fascor connection object and returns a reference to it.  This object is
    * not in a connection pool and is used for db jobs etc.  This allows the end user app
    * to create a connection without having to worry about connection details.
    *
    * @param facId The fascor facility ID that matches the database to connect to.  This will
    *    be "00", "01", etc.
    *
    * @return  A reference to the newly created fascor connection object.
    * @throws Exception when a db connection error is encountered.
    */
   public Connection getFascorConn(String facId) throws Exception
   {
      Properties props = System.getProperties();
      Crypto crypto = new Crypto();
      String fasUrl = null;
      String fasUid = null;
      String fasPwd = null;
      Connection conn = null;

      if ( facId == null || facId.length() < 2 )
         throw new Exception("invalid fascor facility id");

      try {
         fasUrl = props.getProperty(String.format("db.fas.%s.url", facId));
         fasUid = props.getProperty(String.format("db.fas.%s.uid", facId));
         fasPwd = props.getProperty(String.format("db.fas.%s.pwd", facId));
         fasPwd = crypto.decrypt(fasPwd);

         conn = DriverManager.getConnection (fasUrl, fasUid, fasPwd);
      }

      finally {
         props = null;
         crypto = null;
         fasUrl = null;
         fasUid = null;
         fasPwd = null;
      }

      return conn;
   }

   /**
    * Create and return a ConnPool object
    * @return a reference to the ConnPool object.
    */
   public static ConnPool getInstance()
   {
      if ( m_Instance == null ) {
         m_Instance = new ConnPool();
      }

      return m_Instance;
   }
      
   public Connection getEDBConn() throws SQLException
   {
      Connection conn = DriverManager.getConnection(m_EDBUrl, m_EDBUid, m_EDBPwd);
      return conn;
   }
}