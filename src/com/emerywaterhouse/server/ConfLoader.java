/**
 * File: ConfLoader.java
 * Description: Interface for classes that want to use dynamic config file loading managed by the ProcessServer class.
 *
 * @author Jeff Fisher
 *
 * Create Date: 11/11/2011
 * Last Update: $Id: ConfLoader.java,v 1.1 2012/03/07 16:13:00 jfisher Exp $
 *
 * History:
 *    $Log: ConfLoader.java,v $
 *    Revision 1.1  2012/03/07 16:13:00  jfisher
 *    Initial add
 *
 */
package com.emerywaterhouse.server;

public interface ConfLoader
{
   /**
    * Method used to load a configuration file.  Each class must implement it's own
    * way of loading the configuration file.
    */
   public void loadConf();
}
