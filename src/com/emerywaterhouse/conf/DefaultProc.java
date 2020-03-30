/**
 * File: DefaultProc.java
 * Description: Default BOD processor class.  Handles BODs that haven't been assigned any kind of processor.
 *
 * @author Jeff Fisher
 *
 * Create Date: 01/19/2012
 * Last Update: $Id: DefaultProc.java,v 1.1 2012/03/07 16:13:00 jfisher Exp $
 *
 * History:
 *    $Log: DefaultProc.java,v $
 *    Revision 1.1  2012/03/07 16:13:00  jfisher
 *    Initial add
 *
 */
package com.emerywaterhouse.conf;

import com.emerywaterhouse.server.ProcessServer;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;

public class DefaultProc extends BodProcessor
{      
   /**
    *
    */
   public DefaultProc()
   {
      super();
      
      m_Name = String.format("DefaultProc-%d", m_Id);
   }

   /**
    * @param bod
    */
   public DefaultProc(ConfApp app, String bod)
   {
      super(app, bod);
   }
   
   /**
    * Determines the type of bod and issues a log warning
    * @see com.emerywaterhouse.adapter.BodProcessor#processBod()
    */
   @Override
   protected void processBod()
   {
      VTDGen vg = new VTDGen();
      VTDNav vn = null;
      
      try {
         if ( m_Bod != null ) {
            vg.setDoc(m_Bod.getBytes());
            vg.parse(true);
            vn = vg.getNav();
            m_DocName = vn.toString(vn.getCurrentIndex());
         }
      }

      catch ( Exception ex ) {
         ProcessServer.log.error("[DefaultProc]", ex);
      }

      finally {
         vg = null;
         vn = null;
      }

      ProcessServer.log.warn(String.format("[DefaultBodProc] document %s has not been setup; skipping", m_DocName));
   }

}
