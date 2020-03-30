<!--
   XSLT stylesheet for transforming a Credit request ConfirmBOD to a text message
   for reviewing credit shortage requests. 

   Author: Paul Davidson

   Date created: 07/26/2006
   Last update : $Id: confirmbod_shortagecredit.xsl,v 1.1 2009/02/04 16:08:00 pdavidson Exp $

   History:
      $Log: confirmbod_shortagecredit.xsl,v $
      Revision 1.1  2009/02/04 16:08:00  pdavidson
      First commit

      Revision 1.1  2009/02/04 15:35:14  pdavidson
      First commit

      Revision 1.2  2007/07/11 04:54:49  pdavidson
      Merged from credit shortage branch

-->
<xsl:stylesheet version="1.0"
   xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
   xmlns:em="http://www.emeryonline.com/oagis"
   xmlns:oa="http://www.openapplications.org/oagis">

<xsl:output method="text"/>

<!-- Make sure any whitespace not specified in xsl:text is stripped -->
<xsl:strip-space elements="*"/>

<xsl:template match="oa:ApplicationArea">
   <!-- Title, followed by 2 carriage returns -->
   <xsl:text>This request contains lines with a reason of SHORTAGE.  Thus it needs to be reviewed by warehouse personnel (e.g. Lisa Rinz) and Customer Service.</xsl:text>
   <xsl:text>
</xsl:text>
   <xsl:text>
</xsl:text>

   <!-- Customer that placed the request -->
   <xsl:text>Placed by customer: </xsl:text>
   <xsl:value-of select="oa:Sender/oa:ReferenceId"/> 
   <xsl:text>
</xsl:text>
   
   <!-- Time request was received (mm/dd/yyyy hh:mm:ss) -->
   <xsl:variable name="time" select="../oa:DataArea/oa:BOD/oa:Header/oa:OriginalApplicationArea/oa:CreationDateTime"/>
   <xsl:text>Time received from customer: </xsl:text>
   <xsl:value-of select="concat(substring($time, 6, 2), '/', substring($time, 9, 2), '/', substring($time, 1, 4))"/>
   <xsl:value-of select="concat(' ', substring($time, 12, 8))"/>
   <xsl:text>
</xsl:text>   

   <!-- Credit request# -->
   <xsl:text>Credit request number: </xsl:text>
   <xsl:value-of select="../oa:DataArea/oa:BOD/oa:NounOutcome/oa:DocumentIds/oa:DocumentId/oa:Id"/>
   
   <xsl:text>
</xsl:text>

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
   <xsl:text>
</xsl:text>
   
   <!-- Show BODId of document being confirmed for error tracking -->
   <xsl:text>(BOD Id: </xsl:text>
   <xsl:value-of select="oa:BOD/oa:Header/oa:OriginalApplicationArea/oa:BODId"/>
   <xsl:text>)</xsl:text>
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
   <xsl:value-of select="oa:Description"/>
   <xsl:text>
</xsl:text>
   <xsl:text>
</xsl:text>      
</xsl:template>

</xsl:stylesheet>