/**
 * File: DuplicateOrderNotification.java
 * Description: Creates the default notification email message for a DuplicateOrderNotification BOD
 *
 * @author Tony Li
 *
 * Create Date: 01/02/2014
 *
 *
 */
package com.emerywaterhouse.edoc;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;

import com.emerywaterhouse.bean.DuplicateOrder;

public class DuplicateOrderNotification extends EmailConf
{

   private static final String parentName = "com.emerywaterhouse.conf.CreateDuplicateOrderNotification";

   DuplicateOrder m_DuplicateOrder;
   
   /**
    *
    */
   public DuplicateOrderNotification()
   {
      super();
   }

   /**
    * Cleanup resources.
    *
    * @see com.emerywaterhouse.edoc.EmailConf#finalize()
    */
   @Override
   public void finalize() throws Throwable
   {
      super.finalize();
   }

   /**
    * Overrides the base class method and manages the formatting of the confirmation email.
    *
    * Note - We have to use reflection to get the correct method from the SyncFacBod class
    *    because of some class loader issues and class cast exceptions.
    *
    * @see com.emerywaterhouse.edoc.EmailConf#formatMsg()
    */
   @Override
   protected void formatMsg() throws Exception
   {
      StringBuffer msg = new StringBuffer();
      DuplicateOrder dupOrder = null;
      Method method = null;
      Object[] params = new Object[0];
      SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
      
      try {
         m_Subj = "POSSIBLE ORDER DUPLICATION";

         if ( m_Parent != null ) {
            method = m_Parent.getClass().getMethod("getDuplicateOrder", (Class<?>[])null);
            dupOrder = (DuplicateOrder)method.invoke(m_Parent, params);
            msg.append(String.format("The just entered order %s (Date: %s) may be duplicate to existing order %s (Date: %s).\r\n", 
            		dupOrder.getOrderId(), sdf.format(dupOrder.getOrderEnteredDate()), dupOrder.getExistingOrderId(), sdf.format(dupOrder.getExistingOrderEnteredDate())));
            msg.append(String.format("Customer Id:  %s\r\n", dupOrder.getCustomerId()));
            msg.append(String.format("Purchase Order Number: %s\r\n\r\n", dupOrder.getPurchaseOrderNumber()));
            msg.append(String.format("The order %s is put on hold. Please review this order and change its status if needed.", dupOrder.getOrderId()));
            
            m_Subj += " Customer Id: " + dupOrder.getCustomerId();
         }
         else {
            log.warn("[DuplicateOrderNotification] formatMsg - null");
            msg.append("There was an issue getting duplicate order information. \r\n");
            msg.append("Contact the MIS department for help with determining the casue. ");
         }

         msg.append("\r\n\r\n\r\n\r\n");
         msg.append("This is an automated response, please do not respond to this email. ");
         msg.append("If you believe this message was received in error, contact the MIS department. ");

         m_Msg = msg.toString();
      }

      finally {
         msg = null;
         dupOrder = null;
         params = null;
      }
   }


   /**
    * Override the base class method so that we can do a check for the correct
    * object type.
    *
    * Note - Because of the vagaries between the Windows VM and the Linux VM the use
    *    of instanceof couldn't be used because the Linux version return false while the
    *    Windows version returned true.  Probably a class loader issue.  The class name
    *    comparison provides a way around that issue.
    *
    * @param obj A reference to the object that created this object.
    */
   @Override
   public void setParent(Object obj)
   {
      String name = obj.getClass().getName();

      if ( name.equals(parentName) )
         super.setParent(obj);
      else
         log.error(String.format("[DuplicateOrderNotification] setParent - %s is not CreateDuplicateOrderNotification", name));
   }
   

//   /**
//    * Sets the extra information needed to process the duplicate order.
//    * Currently it's the order id.
//    * 
//    * @param data The data to be used for processing.
//    */
//   @Override
//   public void setData(Object data) throws Exception
//   {
//      if ( data != null && data instanceof DuplicateOrder) {
//    	  m_DuplicateOrder = (DuplicateOrder)data;
//      }
//      else
//      {
//    	  log.error(String.format("DuplicateOrderNotification] setData - %s is not DuplicateOrder object.", data.toString()));
//      }
//   }
}
