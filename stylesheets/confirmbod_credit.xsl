<!--
   XSLT stylesheet for transforming a Credit request ConfirmBOD to a user-friendly message
   so that this can be displayed in an email confirmation to a customer.

   Author: Paul Davidson

   Date created: 07/26/2006
   Last update : $Id: confirmbod_credit.xsl,v 1.1 2009/02/04 16:08:00 pdavidson Exp $

   History:
      $Log: confirmbod_credit.xsl,v $
      Revision 1.1  2009/02/04 16:08:00  pdavidson
      First commit

      Revision 1.1  2009/02/04 15:35:14  pdavidson
      First commit

      Revision 1.18  2008/04/11 16:12:38  pdavidson
      Added RGA number

      Revision 1.17  2008/04/11 16:02:27  pdavidson
      Added RGA number

      Revision 1.16  2008/04/11 15:53:52  pdavidson
      Added RGA number

      Revision 1.15  2008/04/11 13:40:36  pdavidson
      Added RGA number

      Revision 1.14  2008/04/10 15:54:55  pdavidson
      Added RGA number

      Revision 1.13  2008/03/27 20:17:43  pdavidson
      Build correct review URL depending on environment

      Revision 1.12  2007/07/11 04:54:49  pdavidson
      Merged from credit shortage branch

      Revision 1.11  2006/08/16 13:26:55  jheric
      Link to production web site, not test.

      Revision 1.10  2006/08/15 14:16:33  jheric
      Change contact information from customer service to credit department (at Cetta's request).

      Revision 1.9  2006/08/09 18:52:54  jheric
      Added customer number and hyperlink to submitted credit request detail page.

      Revision 1.8  2006/08/08 14:57:31  pdavidso
      Fixed for checking component text

      Revision 1.7  2006/08/04 13:41:22  pdavidso
      Check component text, and set title text appropriately

      Revision 1.6  2006/07/27 19:30:27  pdavidso
      Reworked

      Revision 1.5  2006/07/27 17:58:33  pdavidso
      Reworked how credit request# gets pulled

      Revision 1.4  2006/07/26 16:09:09  pdavidso
      Removed bodid stuff, as this may confuse customers

      Revision 1.3  2006/07/26 15:36:16  pdavidso
      New stylesheet for credit confirmations
-->
<xsl:stylesheet version="1.0"
   xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
   xmlns:em="http://www.emeryonline.com/oagis"
   xmlns:oa="http://www.openapplications.org/oagis">

<xsl:output method="text"/>

<!-- Make sure any whitespace not specified in xsl:text is stripped -->
<xsl:strip-space elements="*"/>

<xsl:template match="oa:ApplicationArea">
   <xsl:variable name="approve" select="oa:Sender/oa:Component[text()='approval of credit request']"/>
   
   <xsl:choose>
   <xsl:when test="count($approve)>0">
   <xsl:text>Your credit request has been reviewed and approved by Emery-Waterhouse.</xsl:text>
   </xsl:when>

   <xsl:when test="count($approve)=0">
   <xsl:text>This is to confirm your credit request placed with Emery-Waterhouse.</xsl:text>
   </xsl:when>
   </xsl:choose>
   
   <!-- Title, followed by 2 carriage returns -->
   <xsl:text>
</xsl:text>
   <xsl:text>
</xsl:text>

   <!-- Customer that placed the request -->
   <xsl:text>Placed by customer#: </xsl:text>
   <xsl:value-of select="oa:Sender/oa:ReferenceId"/>
   <xsl:text>
</xsl:text>

   <!-- Time request was received (mm/dd/yyyy hh:mm:ss) -->
   <xsl:variable name="time" select="../oa:DataArea/oa:BOD/oa:Header/oa:OriginalApplicationArea/oa:CreationDateTime"/>
   <xsl:text>Time received: </xsl:text>
   <xsl:value-of select="concat(substring($time, 6, 2), '/', substring($time, 9, 2), '/', substring($time, 1, 4))"/>
   <xsl:value-of select="concat(' ', substring($time, 12, 8))"/>
   <xsl:text>
</xsl:text>   

   <!-- Credit request# -->
   <xsl:text>Credit request number: </xsl:text>
   <xsl:value-of select="../oa:DataArea/oa:BOD/oa:NounOutcome/oa:DocumentIds/oa:DocumentId/oa:Id"/> 
   <xsl:text>
</xsl:text>

   <!-- RGA# (PO# is the RGA# - "Return Goods Authorization") -->
   <xsl:variable name="RGA" select="oa:Sender/oa:AuthorizationId"/>
   <xsl:choose>
      <xsl:when test="string-length($RGA)>0">
   <xsl:text>
</xsl:text>
      <xsl:text>RGA#: </xsl:text>
      <xsl:value-of select="oa:Sender/oa:AuthorizationId"/> 
   <xsl:text>
</xsl:text>
      </xsl:when>
   </xsl:choose>
   <xsl:choose>
      <xsl:when test="string-length($RGA)>0 and count($approve)=0">
         <xsl:text>(PLEASE PRINT THIS CONFIRMATION EMAIL AND INCLUDE IT WITH ANY PRODUCT YOU WILL BE RETURNING)</xsl:text>   
   <xsl:text>
</xsl:text>
      </xsl:when>
   </xsl:choose>
   
   <!-- Add another carriage return --> 
   <xsl:text>
</xsl:text> 
</xsl:template>

<!-- Apply template rules to Header and NounOutcome elements -->
<xsl:template match="oa:DataArea">
   <!-- Currently don't process header pattern, may do it in future
   <xsl:apply-templates select="oa:BOD/oa:Header"/>
   -->
   <xsl:text>Credit request detail:</xsl:text>
   <xsl:text>
</xsl:text>
   
   <xsl:apply-templates select="oa:BOD/oa:NounOutcome"/>

   <xsl:text>
</xsl:text>

   <!-- Get environment (production or test) -->
   <xsl:variable name="envmnt" select="../../oa:ConfirmBOD/@environment"/>

   <!-- direct them to the submitted credit detail page for more information -->
   <xsl:text>View complete detail for your Credit Request online at: </xsl:text>
   
   <xsl:choose>
      <xsl:when test="$envmnt='Production'">
         <xsl:text>http://www.emeryonline.com/emerywh/subscriber/my_account/credit_detail.jsp?ref=</xsl:text>
      </xsl:when>
      <xsl:otherwise>
         <xsl:text>http://testapp2/emerywh/subscriber/my_account/credit_detail.jsp?ref=</xsl:text>
      </xsl:otherwise>
   </xsl:choose>
   <xsl:value-of select="../oa:DataArea/oa:BOD/oa:NounOutcome/oa:DocumentIds/oa:DocumentId/oa:Id"/>      
       
   <xsl:text>
</xsl:text>  
   <xsl:text>
</xsl:text>     


   <!-- Footer message, followed by 3 carriage returns -->
   <xsl:text>If you have any questions about this credit request, please call the credit department.</xsl:text>
   <xsl:text>
</xsl:text>   
   <xsl:text>(800) 283-0236 option 8</xsl:text>
   <xsl:text>
</xsl:text>
   <xsl:text>
</xsl:text>
   
</xsl:template>

<xsl:template match="oa:DataArea/oa:BOD/oa:Header">   
   <!-- Get name of task being confirmed -->
   <xsl:variable name="task" select="../../../oa:ApplicationArea/oa:Sender/oa:Task"/>
   
   <!-- Check if task succeeded, failed or was partially successful -->
   <xsl:choose>
      <xsl:when test="count(oa:BODPartialSuccess)>0 or (count(../oa:NounOutcome/oa:NounSuccess)>0 and count(../oa:NounOutcome/oa:NounFailure)>0)">
         <xsl:text>The </xsl:text>
         <xsl:value-of select="$task"/>
         <xsl:text> was partially successfully.</xsl:text>
      </xsl:when>

      <xsl:when test="count(oa:BODSuccess)>0 and count(../oa:NounOutcome/oa:NounFailure)=0">
         <xsl:text>The </xsl:text>
         <xsl:value-of select="$task"/>
         <xsl:text> was successfully processed.</xsl:text>
      </xsl:when>

      <xsl:when test="count(oa:BODSuccess)>0 and count(../oa:NounOutcome/oa:NounFailure)>0">
         <xsl:text>The </xsl:text>
         <xsl:value-of select="$task"/>
         <xsl:text> was partially successfully.</xsl:text>
      </xsl:when>

      <xsl:when test="count(oa:BODFailure)>0">
         <xsl:text>The </xsl:text>
         <xsl:value-of select="$task"/>
         <xsl:text> failed to process.</xsl:text>
      </xsl:when>      
   </xsl:choose>
   
   <!-- Add some carriage returns -->
   <xsl:text>
</xsl:text>   
   <xsl:text>
</xsl:text>   

   <!-- Apply header error msg template rules -->   
   <xsl:apply-templates select="oa:BODFailure/oa:ErrorMessage"/>   
</xsl:template>

<xsl:template match="oa:DataArea/oa:BOD/oa:Header/oa:BODFailure/oa:ErrorMessage">
   <!-- BOD failure error description -->
   <xsl:text>Error description: </xsl:text>
   <xsl:value-of select="oa:Description"/>

   <!-- BOD failure error code -->
   <xsl:if test="count(oa:ReasonCode)>0">
      <xsl:text>
</xsl:text>
   
      <xsl:text>Error code:</xsl:text>
      <xsl:value-of select="oa:ReasonCode"/>
   </xsl:if>
   
   <xsl:text>
</xsl:text>
   <xsl:text>
</xsl:text>   
</xsl:template>

<xsl:template match="oa:DataArea/oa:BOD/oa:NounOutcome">
   <xsl:apply-templates select="oa:NounFailure/oa:ErrorMessage"/>
   <xsl:apply-templates select="oa:NounSuccess/oa:WarningMessage"/>   
</xsl:template>

<xsl:template match="oa:DataArea/oa:BOD/oa:NounOutcome/oa:NounFailure/oa:ErrorMessage">
   <!-- Noun failure error description -->
   <xsl:text>Error description: </xsl:text>
   <xsl:value-of select="oa:Description"/>

   <!-- Noun failure error code -->
   <xsl:if test="count(oa:ReasonCode)>0">
      <xsl:text>
</xsl:text>
   
      <xsl:text>Error code:</xsl:text>
      <xsl:value-of select="oa:ReasonCode"/>
   </xsl:if>
   
   <xsl:text>
</xsl:text>
   <xsl:text>
</xsl:text>   
</xsl:template>

<xsl:template match="oa:DataArea/oa:BOD/oa:NounOutcome/oa:NounSuccess/oa:WarningMessage">
<xsl:if test="not(contains(oa:Description, 'Additional Item Detail'))">
   <xsl:value-of select="oa:Description"/>
   <xsl:text>
</xsl:text>
   <xsl:text>
</xsl:text>      
</xsl:if>
</xsl:template>

</xsl:stylesheet>
