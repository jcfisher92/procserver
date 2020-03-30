package com.emerywaterhouse.ace.catalog;

import com.emerywaterhouse.oag.OagisUtils;
import com.emerywaterhouse.server.ProcessServer.Environment;
import org.apache.log4j.Logger;

import java.util.HashMap;


public class ReportModule
{
   private static final String paramTag = "<Param pname=\"%s\" ptype=\"%s\" value=\"%s\"/>";
   private static final String recipTag = "<Recipient name=\"%s\" email=\"%s\"/>";
   public static final String ftpTag   = "<Ftp url=\"%s\" uid=\"%s\" pwd=\"%s\" />";

   private static final String procRptReqBod =
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
      "<ProcessReportRequest " +
      "xmlns=\"http://www.openapplications.org/oagis\" " +
      "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
      "xsi:schemaLocation=\"http://www.openapplications.org/oagis\" " +
      "revision=\"8.0\" environment=\"Production\" lang=\"en-US\">" +
      "<ApplicationArea>" +
      "<Sender>" +
      "<LogicalId>emery</LogicalId>" +
      "<Confirmation>Never</Confirmation>" +
      "<AuthorizationId>dfc87f3309372eae7fa04ea4f324627ba0639ea2e127ece31987b98d</AuthorizationId>" +
      "</Sender>" +
      "<CreationDateTime>%s</CreationDateTime>" +
      "<BODId>%s</BODId>" +
      "<UserArea xmlns:em=\"http://www.emeryonline.com/oagis\" xsi:schemaLocation=\"http://www.emeryonline.com/oagis http://www.emeryonline.com/oagis/Overlays/Emery/Resources/emery.xsd\">" +
      "<em:RoutingInfo></em:RoutingInfo>" +
      "</UserArea>" +
      "</ApplicationArea>" +
      "<DataArea>" +
      "<Process acknowledge=\"Always\" confirm=\"Always\"/>" +
      "<ReportRequest zipped=\"%s\" attachment=\"%s\">" +
      "   <ReportName>%s</ReportName>" +
      "   <ReportClass>%s</ReportClass>" +
      "   <UserId>ACE Service</UserId>" +
      "   <Password>pwd</Password>" +
      "   <Params>%s</Params>" +
      "   <Recipients>%s</Recipients>" +
      "   %s" +
      "</ReportRequest>" +
      "</DataArea>" +
      "</ProcessReportRequest>";

   private HashMap<String, String> m_RptList;
   private StringBuffer m_Params;
   private StringBuffer m_Recips;

   private Logger m_Log;
   private Environment m_Env;

   /**
    * Default constructor
    */
   public ReportModule()
   {
      m_RptList = new HashMap<>();
      m_Params = new StringBuffer();
      m_Recips = new StringBuffer();
   }

   /**
    * Creates the report module, configures it and assigns the calling service.
    *
    * @param env environment
    */
   public ReportModule(Environment env)
   {
      this();

      m_Env = env;
      configure();
   }
   
   /**
    * Creates the report module, configures it and assigns the calling service.
    * Overloaded to pass in the logger, which is needed at configure time.
    *
    * @param env environment
    */
   public ReportModule(Environment env, Logger log)
   {
      this();

      m_Env = env;
      m_Log = log;
      configure();
   }

   /**
    *
    */
   public void close()
   {
      m_RptList.clear();
      m_RptList = null;
      m_Params = null;
      m_Recips = null;
   }

   /**
    * Builds all of the report XML documents and adds them to the hash table.
    */
   private void configure()
   {
      m_Log.debug("[ReportModule#Configure]Starting configuration...");

      String xml;
      
      //
      // ACE import report showing items added and exceptions
      m_Params.append(String.format(paramTag, "dummy", "String", ""));

      if ( m_Env != null && m_Env == Environment.Test )
         m_Recips.append(String.format(recipTag, "", "programming@emeryonline.com"));
      else
         m_Recips.append(String.format(recipTag, "", "acemerchandising@emeryonline.com"));

      xml = String.format(procRptReqBod,
            OagisUtils.getOagisDateTime(),
            OagisUtils.createBODId(),
            "no", "yes",
            "ACE Item Adds",
            "com.emerywaterhouse.rpt.spreadsheet.AceItemImport",
            m_Params.toString(),
            m_Recips.toString(),
            "");

      m_RptList.put("AceItemAdd", xml);

      //
      // ACE item change report
      xml = String.format(procRptReqBod,
            OagisUtils.getOagisDateTime(),
            OagisUtils.createBODId(),
            "no", "yes",
            "ACE Item Change",
            "com.emerywaterhouse.rpt.spreadsheet.AceItemChange",
            m_Params.toString(),
            m_Recips.toString(),
            "");

      m_RptList.put("AceItemChange", xml);
   }

   /**
    * Get the XML report request
    *
    * @param rptName that contains the name of a report.
    * @return String The fully formed XML report request.
    */
   public String getReportRequest(String rptName)
   {
      String xml = m_RptList.get(rptName);

      return xml != null ? xml : "";
   }

   /**
    * Resets the data that is used based on the environment.
    * @param env environment
    */
   public void setEnv(Environment env)
   {
      m_Recips.setLength(0);

      if ( env == Environment.Test )
         m_Recips.append(String.format(recipTag, "", "programming@emeryonline.com"));
      else
         m_Recips.append(String.format(recipTag, "", "acemerchandising@emeryonline.com"));
   }

   /**
    * Set the logger.
    * @param log log
    */
   public void setLogger(Logger log)
   {
      m_Log = log;
   }
}
