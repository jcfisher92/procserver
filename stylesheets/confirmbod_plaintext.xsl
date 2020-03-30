<!--
   XSLT stylesheet for transforming a ConfirmBOD XML document to some plain user-friendly text.

   Author: Paul Davidson

   Date created:  11/16/2004
   Last update :  $Id: confirmbod_plaintext.xsl,v 1.1 2009/02/04 16:08:00 pdavidson Exp $

   History:
      $Log: confirmbod_plaintext.xsl,v $
      Revision 1.1  2009/02/04 16:08:00  pdavidson
      First commit

      Revision 1.1  2009/02/04 15:35:14  pdavidson
      First commit

      Revision 1.7  2006/08/02 20:26:48  pdavidso
      Added noun failure warning messages

      Revision 1.6  2006/07/07 14:39:05  pdavidso
      Added some line feeds after each success warning description

      Revision 1.5  2006/07/05 21:27:02  pdavidso
      Added stuff for noun success warning messages

      Revision 1.4  2005/11/02 16:30:11  pdavidso
      Display appropriate text if bod processing failed or was partially successfull

      Revision 1.3  2005/11/02 14:09:25  pdavidso
      Changed description of bodId to "Reference#" instead as this msg may be viewed by customers.

      Revision 1.2  2005/09/23 13:45:19  pdavidso
      Reworked for more user friendly output
-->

<xsl:stylesheet version="1.0"
   xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
   xmlns:em="http://www.emeryonline.com/oagis"
   xmlns:oa="http://www.openapplications.org/oagis">

<xsl:output method="text"/>

<!-- Make sure any whitespace not specified in xsl:text is stripped -->
<xsl:strip-space elements="*"/>

<xsl:template match="oa:ApplicationArea">
   <xsl:text>Received confirmation from </xsl:text>
   <xsl:value-of select="oa:Sender/oa:LogicalId"/>
   <xsl:text> regarding </xsl:text>
   <xsl:value-of select="oa:Sender/oa:Task"/>
   <xsl:text>: </xsl:text>
   <xsl:text>
</xsl:text> 
   <xsl:text>
</xsl:text> 
</xsl:template>

<!-- Apply template rules to Header and NounOutcome elements -->
<xsl:template match="oa:DataArea">
   <xsl:apply-templates select="oa:BOD/oa:Header"/>
   <xsl:apply-templates select="oa:BOD/oa:NounOutcome"/>
   
   <!-- Show BODId of document being confirmed -->
   <xsl:text>(Reference#: </xsl:text>
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
   <xsl:apply-templates select="oa:NounFailure/oa:WarningMessage"/>   
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

<xsl:template match="oa:DataArea/oa:BOD/oa:NounOutcome/oa:NounFailure/oa:WarningMessage">
   <xsl:value-of select="oa:Description"/>
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