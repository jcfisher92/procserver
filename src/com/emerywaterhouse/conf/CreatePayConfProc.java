/**
 * File: CreatePayConfProc.java
 * Description: Process CreatePaymentConfirmation bods, sends an html
 * formatted payment confirmation to the supplied email addresses.
 *
 * @author Erik Pearson
 *
 * Create Date: 1/24/2013
 * Last Update: $Id: CreatePayConfProc.java,v 1.3 2013/01/25 17:20:53 jfisher Exp $
 *
 * History:
 *    $Log: CreatePayConfProc.java,v $
 *    Revision 1.3  2013/01/25 17:20:53  jfisher
 *    updated the mail sending to use the mail.from property
 *
 *    Revision 1.2  2013/01/24 14:43:50  epearson
 *    cleaned up code
 *
 *
 */
package com.emerywaterhouse.conf;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.io.IOUtils;

import com.emerywaterhouse.server.EmerySmtpAuth;

import com.emerywaterhouse.server.ProcessServer;
import com.emerywaterhouse.utils.DataSender;
import com.ximpleware.AutoPilot;
import com.ximpleware.NavException;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;
import com.ximpleware.XPathEvalException;
import com.ximpleware.XPathParseException;

/**
 * @author epearson
 *
 */
public class CreatePayConfProc extends BodProcessor 
{
   private static String LOG_TEMPLATE = "[CreatePayConfProc] %s";
   private static String HTML_PART_TEMPLATE =
         "<div>" +
         "<img style=\"width:300px\" src=\"http://www.emeryonline.com/shared/images/emery-logo-blue-large.jpg\" alt=\"Emery|Waterhouse\">" +
         "</div>" +
         "<div style=\"font-size:14px;font-family: Verdana, Arial, Helvetica, sans-serif;color:#336;padding-left:10px;\">" +
         "<p>Your payment request has been received and will be processed within 3 business days. You can view the details of this payment online in the Pending Payments tab of Online Bill Pay service. If you have any questions or concerns regarding this payment please contact Customer Service at 1-800-283-0236.</p>" +
         "</div>" +
         "<div style=\"font-family: Verdana, Arial, Helvetica, sans-serif;font-size:13px;color:white;background-color:#336;padding:10px;margin:0px;text-align:center;\">" +
         "Emery-Waterhouse Company &#149; PO Box 659 &#149; Portland, ME 04104<br />" +
         "(800) 283-0236 &#149; <a style=\"color:white\" href=\"http://www.emeryonline.com\">www.emeryonline.com</a> &#149; <a style=\"color:white\" href=\"mailto:info@emeryonline.com\">info@emeryonline.com</a>" +
         "</div>";

   private String m_BodId = null;

   /**
    * Default constructor
    */
   public CreatePayConfProc () 
   {
      super();
      
      m_Name = String.format("CreatePayConfProc-%d", m_Id);
   }

   /**
    * Sets the BOD string
    *
    * @param bod - the BOD to set
    */
   public CreatePayConfProc (ConfApp app, String bod)
   {
      super(app, bod);
   }

   @Override
   protected void processBod() {
      VTDGen vg = new VTDGen();
      AutoPilot ap = new AutoPilot();
      VTDNav vn = null;
      List<InternetAddress> recipList = null;
      String sourceUrl = null;
      byte[] payConfPdf = null;

      ProcessServer.log.info(String.format(LOG_TEMPLATE, "Processing payment confirmation ... "));

      try {
         ap.declareXPathNameSpace("ns1", "http://www.emeryonline.com/oagis");
         vg.setDoc(m_Bod.getBytes());
         vg.parse(true); // turn on namespace awareness
         vn = vg.getNav();
         ap.bind(vn);

         m_BodId = getBodId(ap, vn);

         recipList = getRecipientList(ap, vn);

         // we do not need to go any further if the recipient list is empty
         if ( recipList != null && !recipList.isEmpty() ) {

            sourceUrl = getSourceUrl(ap, vn);

            payConfPdf = getPayConfPdf(sourceUrl);

            if (payConfPdf != null)
               sendPayConfEmail(recipList, payConfPdf);
            else {
               String errorMsg = String.format(LOG_TEMPLATE, "Unable to send the payment confirmation. The source url for the " +
                     "payment confirmation PDF at " + sourceUrl + " did not return any data. ");

               ProcessServer.log.error(errorMsg);

               m_App.getServer().notifyMis(errorMsg + "\n\n" + m_Bod);
            }
         }         
      }

      catch (Exception e) {
         ProcessServer.log.error(String.format(LOG_TEMPLATE,e.getMessage()), e);
      }
   }

   private String getBodId(AutoPilot ap, VTDNav vn) throws XPathParseException, XPathEvalException, NavException {
      String bodId = null;

      ap.selectXPath("/ns1:CreatePaymentConfirmation/ns1:ApplicationArea/ns1:BODId");

      if ( ap.evalXPath() != -1 ) {
         int index = vn.getText();

         if ( index != -1 ) {
            bodId = vn.toNormalizedString(index);
         }
      }

      return bodId;
   }

   private void sendPayConfEmail(List<InternetAddress> recipList, byte[] payConfPdf) throws IOException, MessagingException 
   {
      Session session = null;
      Authenticator auth = null;
      Message msg = null;
      Multipart multiPart = null;
      BodyPart pdfPart = null;
      BodyPart htmlPart = null;
      ByteArrayDataSource pdfSource = null;
      Properties props = DataSender.loadSmtpProps();

      auth = new EmerySmtpAuth();

      session = Session.getInstance(props, auth);
      msg = new MimeMessage(session);

      for (InternetAddress recipAddr : recipList) {
         msg.addRecipient(Message.RecipientType.TO, recipAddr);
      }

      msg.setFrom(new InternetAddress(System.getProperty("mail.from", "noreply@emeryonline.com")));

      if ( m_App.getServer().getEnv().equals(ProcessServer.Environment.Test) )
         msg.setSubject("[TEST] Emery-Waterhouse - Payment Confirmation");
      else
         msg.setSubject("Emery-Waterhouse - Payment Confirmation");

      htmlPart = new MimeBodyPart();
      htmlPart.setContent(HTML_PART_TEMPLATE, "text/html");

      pdfSource = new ByteArrayDataSource(payConfPdf, "application/pdf");
      pdfPart = new MimeBodyPart();
      pdfPart.setDataHandler(new DataHandler(pdfSource));
      pdfPart.setFileName("payment-confirmation.pdf");
      pdfPart.setDisposition("attachment");

      multiPart = new MimeMultipart();
      multiPart.addBodyPart(htmlPart);
      multiPart.addBodyPart(pdfPart);

      msg.setContent(multiPart);
      msg.setSentDate(new Date());
      Transport.send(msg);
   }

   private byte[] getPayConfPdf(String sourceUrl) throws IOException 
   {
      byte[] payConfPdf = null;
      HttpURLConnection httpConn = null;
      URL url = null;
      int statusCode = -1;
      InputStream in = null;
      String contentType = null;

      url = new URL(sourceUrl);

      httpConn = (HttpURLConnection) url.openConnection();

      httpConn.setRequestMethod("GET");
      httpConn.setDoInput(true);
      httpConn.setDoOutput(true);
      httpConn.setUseCaches(true);

      httpConn.setRequestProperty("Accept", "application/pdf");

      httpConn.connect();

      statusCode = httpConn.getResponseCode();

      if (statusCode >= 400) {
         String errorMsg = "There was an error while trying to retrieve the " +
               "payment confirmation PDF at url " + sourceUrl + ", status code " +
               statusCode;

         in = httpConn.getErrorStream();
         ProcessServer.log.error(String.format(LOG_TEMPLATE, errorMsg));
         m_App.getServer().notifyMis(errorMsg + "\n\nHTTP Response:\n\n" + IOUtils.toString(in) +
               "\n\n" + m_Bod);
      } 
      else
         in = httpConn.getInputStream();

      contentType = httpConn.getContentType();

      if (statusCode == 200)
         payConfPdf = IOUtils.toByteArray(in);

      return payConfPdf;
   }

   private String getSourceUrl(AutoPilot ap, VTDNav vn) throws XPathParseException, NavException, XPathEvalException 
   {
      String sourceUrl = null;

      ap.selectXPath("/ns1:CreatePaymentConfirmation/ns1:DataArea/ns1:PaymentConfirmation/ns1:Source");

      if ( ap.evalXPath() != -1 ) {
         int index = vn.getText();

         if ( index != -1 ) {
            sourceUrl = vn.toNormalizedString(index);
         }
      }

      return sourceUrl;
   }

   private List<InternetAddress> getRecipientList(AutoPilot ap, VTDNav vn) throws XPathParseException, XPathEvalException, NavException 
   {
      List<InternetAddress> recipList = new LinkedList<InternetAddress>();

      ap.selectXPath("/ns1:CreatePaymentConfirmation/ns1:DataArea/ns1:PaymentConfirmation/ns1:Recipients/ns1:Recipient/ns1:Email");

      while ( ap.evalXPath() != -1 ) {
         int index = vn.getText();
         String email = null;

         if ( index != -1 ) {
            email = vn.toNormalizedString(index);

            if ( email != null && !email.isEmpty() ) {
               InternetAddress internetEmail = null;

               try {
                  internetEmail = new InternetAddress(email);
               } 
               
               catch (AddressException e) {
                  ProcessServer.log.error(String.format(LOG_TEMPLATE, "The email address, " + email +
                     ", from the BOD " + m_BodId + " is not valid. Unable to " +
                     "send a payment confirmation to this recipient."));
               }

               if ( internetEmail != null )
                  recipList.add(internetEmail);
            }
         }
      }

      return recipList;
   }

   @Override
   public ArrayList<String> getRecipients()
   {
      return null;
   }

}
