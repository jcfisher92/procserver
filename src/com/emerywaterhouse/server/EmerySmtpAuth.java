/**
 * File: EmerySmtpAuth.java
 * Description: Implementation of SMTP Authenticator
 *
 * @author Erik Pearson
 *
 * Create Date: 1/24/2013
 * Last Update: $Id: EmerySmtpAuth.java,v 1.1 2013/01/24 14:43:57 epearson Exp $
 *
 * History:
 *    $Log: EmerySmtpAuth.java,v $
 *    Revision 1.1  2013/01/24 14:43:57  epearson
 *    initial add
 *
 *
 */
package com.emerywaterhouse.server;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;

/**
 * Implementation of SMTP Authenticator
 * 
 * @author epearson
 *
 */
public class EmerySmtpAuth extends Authenticator{

   @Override
   protected PasswordAuthentication getPasswordAuthentication() {
      return new PasswordAuthentication(System.getProperty("mail.smtp.user"), 
            System.getProperty("mail.smtp.password"));
   }

}
