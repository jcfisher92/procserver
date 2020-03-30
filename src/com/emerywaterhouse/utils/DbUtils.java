/**
 * File: DbUtils.java
 * Description: Database utilities that are used by web services.
 *
 * @author Jeffrey Fisher
 *
 * Create Date: 04/04/2006
 * Last Update: $Id: DbUtils.java,v 1.2 2014/06/16 14:41:55 epearson Exp $
 *
 * History
 *    $Log: DbUtils.java,v $
 *    Revision 1.2  2014/06/16 14:41:55  epearson
 *    miscellaneous updates
 *
 *    Revision 1.1  2014/05/06 18:13:03  epearson
 *    initial add
 *
 *    Revision 1.14  2013/07/29 18:21:17  jfisher
 *    Fixed the datasource binding
 *
 *    Revision 1.13  2013/07/29 16:22:21  jfisher
 *    Depricated old getTms method and added a new one with the app container.
 *
 *    Revision 1.12  2013/04/29 13:17:17  epearson
 *    removed deprecated annotation from getConn
 *
 *    Revision 1.11  2012/09/21 15:55:44  jfisher
 *    Fixed the getOracConn name so it doesn't have the extra "c"
 *
 *    Revision 1.10  2012/09/21 15:18:14  jfisher
 *    Fixed a fatinger on the fascor ds
 *
 *    Revision 1.9  2012/09/19 22:01:35  jfisher
 *    Modified the accpac connection methods
 *
 *    Revision 1.8  2012/09/05 17:54:56  jfisher
 *    Added methods to get connections based on the container.
 *
 *    Revision 1.7  2011/08/31 08:59:28  jfisher
 *    Added a generic connection method for use with Tomcat 7.x
 *
 *    Revision 1.6  2010/05/04 15:33:11  npasnur
 *    Added method to get a Catapult database connection
 *
 *    Revision 1.5  2008/10/08 18:14:24  jfisher
 *    New fascor connection routines
 *
 *    Revision 1.4  2007/09/06 11:06:03  prichter
 *    Added a method to get a Prescient database connection.
 *
 *    Revision 1.3  2006/05/31 11:56:01  jfisher
 *    Set auto commit to false on the Oracle connections.  This stops idiotic jboss exceptions.
 *
 *    Revision 1.2  2006/05/25 20:01:45  prichter
 *    Added method getAccPacConn()
 *
 *    Revision 1.1  2006/04/05 13:47:50  jfisher
 *    initial add
 *
 */
package com.emerywaterhouse.utils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Web service db utilities class
 */
public class DbUtils {

	/**
	 * Convenience method that closes a database connection, statement, and
	 * resultset. Allows the calling method to close statements and other
	 * related db elements without having to deal with the exceptions.
	 *
	 * @param conn
	 *            The database connection to close.
	 * @param stmt
	 *            The statement to close.
	 * @param rs
	 *            The resultset to close.
	 */
	public static void closeDbConn(Connection conn, Statement stmt, ResultSet rs) {
		if (rs != null) {
			try {
				rs.close();
			}

			catch (SQLException ex) {

			}
		}

		if (stmt != null) {
			try {
				stmt.close();
			}

			catch (SQLException ex) {

			}
		}

		if (conn != null) {
			try {
				conn.close();
			}

			catch (SQLException ex) {

			}
		}
	}

	/**
	 * Closes the objects in the order which they are passed
	 * 
	 * @param closeables
	 */
	public static void close(AutoCloseable... closeables) {
		if (closeables != null && closeables.length > 0) {
			for (AutoCloseable closeable : closeables) {
				if (closeable != null) {
					try {
						closeable.close();
					} catch (Exception ex) {
						/* eat the exception */
					}
				}
			}
		}
	}
}
