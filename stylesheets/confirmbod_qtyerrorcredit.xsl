<!--
   Creates message for reviewing return credit requests that contain quantity warnings (the 
   adjusted requested qty does not match the received qty).
   
   Author: Paul Davidson

   Date created: 04/01/2008
   Last update : $Id: confirmbod_qtyerrorcredit.xsl,v 1.1 2009/02/04 16:08:00 pdavidson Exp $

   History:
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
   <xsl:text>This request contains lines where the received quantity does not match the adjusted credit quantity, and needs to be reviewed.</xsl:text>
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
	<xsl:if test="contains(oa:Description, 'QtyErr:')">
	   <xsl:value-of select="substring(oa:Description, string-length('QtyErr:')+1, string-length(oa:Description)-string-length('QtyErr:'))"/>
	   <xsl:text>
</xsl:text>
	   <xsl:text>
</xsl:text>      
   </xsl:if>    
</xsl:template>

</xsl:stylesheet>