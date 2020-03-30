/**
 * File: Recipient.java
 * Description: Pulled from the ConfProcessor class in the confserver project.  Just leaving all members as public to avoid having to 
 *      use getters and setters.
 * 
 * Create Date: 05/02/2018
 * Last Update: 05/02/2018
 * 
 * History:
 */
package com.emerywaterhouse.bean;

public class Recipient {
   public String address;    // Recipient address information
   public String msgStyle;   // Recipient message style
   public String password;   // Authentication password when communicating with recipient
   public String transport;  // Method of transport used to send information to recipient
   public String userId;     // Authentication user id when communicating with recipient
   
   public Recipient() {
      super();
   }

}
