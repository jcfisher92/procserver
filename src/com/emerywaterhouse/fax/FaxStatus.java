/**
 * File: FaxStatus.java
 * Description: Simple class for moving data elements from one location to another.  Used to hold the
 *    status information from a running FAX processor.
 *
 * @author Jeff Fisher
 *
 * Create Date: 10/27/2011
 * Last Update: $Id: FaxStatus.java,v 1.1 2012/03/07 16:13:00 jfisher Exp $
 *
 * History
 *    $Log: FaxStatus.java,v $
 *    Revision 1.1  2012/03/07 16:13:00  jfisher
 *    Initial add
 *
 *
 */
package com.emerywaterhouse.fax;


public class FaxStatus
{
   public String currentAction;
   public long id;
   public String faxName;
   public long runTime;
   public long startTime;   
   public String uid;

   /**
    * default constructor
    */
   public FaxStatus()
   {
      super();

      currentAction = "";
      faxName = "";
      uid = "";
   }
}
