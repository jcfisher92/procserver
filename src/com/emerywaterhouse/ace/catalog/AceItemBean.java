/**
 * File: AceItemBean.java
 * Description: Class to bundle an Ace item record into a single object.
 *    Used when sending the data to different processes.
 *
 * @author Jeff Fisher
 *
 * Create Date: 06/13/2014
 * Last Update: $Id: AceItemBean.java,v 1.11 2014/11/25 18:36:19 jfisher Exp $
 *
 * History:
 *    $Log: AceItemBean.java,v $
 *    Revision 1.11  2014/11/25 18:36:19  jfisher
 *    Updated checks on number conversions and logging.
 *
 *    Revision 1.10  2014/11/19 20:16:35  jfisher
 *    vers 1.2.1
 *    Removed sending cross reference, only send add report on adds, fixed a bug when updating item attributes.
 *
 *    Revision 1.9  2014/10/27 19:03:08  jfisher
 *    New fields in the feed.
 *
 *    Revision 1.8  2014/10/06 15:24:49  jfisher
 *    Final tweaks for production
 *
 *    Revision 1.7  2014/10/03 20:06:34  jfisher
 *    Procuction version 1
 *
 *    Revision 1.5  2014/08/27 20:05:03  jfisher
 *    Pre-production version
 *
 *    Revision 1.4  2014/08/08 20:21:57  jfisher
 *    Added pricing, cross ref sending and report request sending
 *
 *    Revision 1.3  2014/06/20 19:40:13  jfisher
 *    save point #2
 *
 *    Revision 1.2  2014/06/19 19:46:36  jfisher
 *    Initial database adds
 *
 *    Revision 1.1  2014/06/16 20:21:42  jfisher
 *    Inititial add
 *
 */
package com.emerywaterhouse.ace.catalog;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Date;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;


public class AceItemBean
{
   //
   // Log4j logger
   private static Logger log = Logger.getLogger(AceItemBean.class.getName());

   //
   // XML Paths
   private final String statusExp = "/catalogItem/@status";
   private final String rscExp = "/catalogItem/@rsc";
   private final String mruExp = "/catalogItem/@mruInd";
   private final String dispExp = "/catalogItem/@disposition";
   private final String noReturnExp = "/catalogItem/@noReturn";
   private final String whsOnlyExp = "/catalogItem/@wholesalerOnly";
   private final String notForResaleExp = "/catalogItem/@notForResale"; //ebrownewell (08-03-2015): added to deal with the notForResale flag.
   private final String defPolicyExp = "/catalogItem/@defectPolicyCd";
   private final String skuExp = "/catalogItem/sku";
   private final String descExp = "/catalogItem/description";
   private final String upcExp = "/catalogItem/upc";
   private final String vndSkuExp = "/catalogItem/vendorSku";
   private final String vndIdExp = "/catalogItem/vendor/id";
   private final String vndNameExp = "/catalogItem/vendor/name";
   private final String imgSmExp = "/catalogItem/imageUrlSm";
   private final String imgMdExp = "/catalogItem/imageUrlMd";
   private final String imgLgExp = "/catalogItem/imageUrlLg";
   private final String caseExp = "/catalogItem/brokenCase";
   private final String dlrPackExp = "/catalogItem/dealerPack";
   private final String packOfExp = "/catalogItem/packOf";
   private final String casePackExp = "/catalogItem/casePackQty";
   private final String uomQtyExp = "/catalogItem/uomQty";
   private final String priceUnitAmtExp = "/catalogItem/priceUnitAmt";
   private final String costExp = "/catalogItem/cost";
   private final String retailExp = "/catalogItem/retail";
   private final String lengthExp = "/catalogItem/length";
   private final String widthExp = "/catalogItem/width";
   private final String heightExp = "/catalogItem/height";
   private final String weightExp = "/catalogItem/weight";
   private final String cubeExp = "/catalogItem/cube";
   private final String uomExp = "/catalogItem/uom";
   private final String mdcExp = "/catalogItem/mdc";
   private final String nrhaExp = "/catalogItem/nrha";
   private final String mdseClassExp = "/catalogItem/classification/mdseClassCd";
   private final String cmdtyGrpExp = "/catalogItem/classification/cmdtyGroup";
   private final String prodGrpExp = "/catalogItem/classification/prodGroup";
   private final String deptExp    = "/catalogItem/classification/department";
   private final String buyerExp    = "/catalogItem/classification/buyerCd";
   private final String brandNameExp = "/catalogItem/brandName";
   private final String retailAExp = "/catalogItem/retailA";
   private final String retailBExp = "/catalogItem/retailB";
   private final String oldMaterialNumExp = "/catalogItem/oldMaterialNum";
   private final String newMaterialNumExp = "/catalogItem/newMaterialNum";
   private final String velocityUnitCdExp = "/catalogItem/velocityUnitCd";
   private final String velocityDollarCdExp = "/catalogItem/velocityDollarCd";
   private final String regCostExp = "/catalogItem/tmpCost/regularCost";
   private final String tmpCostExp = "/catalogItem/tmpCost/cost";
   private final String tmpCostStartExp = "/catalogItem/tmpCost/startDate";
   private final String tmpCostEndExp = "/catalogItem/tmpCost/endDate";
   private final String dcAllocMaxExp = "/catalogItem/allocation/dcAllocMax";
   private final String dcAllocEndExp = "/catalogItem/allocation/dcAllocEnd";
   private final String custAllocMaxExp = "/catalogItem/allocation/custAllocMax";
   private final String custAllocEndExp = "/catalogItem/allocation/custAllocEnd";
   private final String bulletStringRawExp = "/catalogItem/bulleted_desc";

   private int ItemEaId;
   private int EjdItemWhsId;
   private int ejdItemUpcId;
   private int ejdItemId;

   private String aceSku;
   private long aceVndId;
   private String brandName;
   private boolean brokenCase;
   private int custAllocMax;
   private Date custAllocEnd;
   private String cmdtyGrp;
   private double cost;
   private double cube;
   private int dealerPack;
   private int dcAllocMax;
   private Date dcAllocEnd;
   private String defPolicyCd;
   private String desc;
   private String dept;
   private String emerySku;
   private boolean emeryStocked;
   private String flc;
   private double height;
   private String imageUrlLg;
   private String imageUrlMd;
   private String imageUrlSm;
   private double length;
   private String mdc;
   private String mdseClassCd;
   private String nrha;
   private int packOf;
   private String prodGrp;
   private double regCost;
   private double retail;
   private double retailA;
   private double retailB;
   private String rscCd;
   private int rscId;
   private String sourceData;
   private String status;
   private double tmpCost;
   private Date tmpEnd;
   private Date tmpStart;
   private String uom;
   private String upc;
   private long vndId;
   private String vndName;
   private String vndSku;
   private boolean whsOnly;
   private double weight;
   private double width;

   //
   // New fields added - need to make private post addition and process handling
   private String mruInd;
   private boolean noReturn;
   private String oldMaterialNum;
   private String newMaterialNum;
   private String velocityUnitCd;
   private String velocityDollarCd;
   private double priceUnitAmt;
   private int casePackQty;
   private int uomQty;
   private String dispositionCd;
   private String buyerCd;
   private long xrefId;
   private long itemRscId;

   private boolean notForResale;

   private String m_BulletsRawString;

   private ArrayList<String> m_Bullets;

   public int taxonomyId;

   /**
    * Default constructor.  Initializes the member variables.
    */
   public AceItemBean()
   {
      super();

      status = "";
      aceSku = "";
      aceVndId = 0;
      emerySku = null;
      emeryStocked = false;
      desc = "";
      dept = "";
      upc = "";
      vndSku = "";
      vndId = 0;
      imageUrlSm = "";
      imageUrlMd = "";
      imageUrlLg = "";
      brokenCase = false;
      dealerPack = 1;
      packOf = 1;
      cost = 0.0;
      regCost = 0.0;
      retail = 0.0;
      retailA = 0.0;
      retailB = 0.0;
      sourceData = "";
      length = 0.0;
      width = 0.0;
      height = 0.0;
      weight = 0.0;
      cube = 0.0;
      uom = "EA";
      flc = "";
      mdc = "";
      nrha = "";
      brandName = "";
      mdseClassCd = "";
      cmdtyGrp = "";
      prodGrp = "";
      rscCd = "NY01";
      rscId = 11;
      tmpCost = 0.0;
      tmpStart = null;
      tmpEnd = null;
      whsOnly = false;
      dcAllocMax = -1;
      dcAllocEnd = null;
      custAllocMax = -1;
      custAllocEnd = null;
      defPolicyCd = "";
      xrefId = 0;
      itemRscId = 0;

      m_BulletsRawString = null;

      m_Bullets = new ArrayList<>();
   }

   @Override
   public String toString() {
      return "AceItemBean{" +
              "statusExp='" + statusExp + '\'' +
              ", rscExp='" + rscExp + '\'' +
              ", mruExp='" + mruExp + '\'' +
              ", dispExp='" + dispExp + '\'' +
              ", noReturnExp='" + noReturnExp + '\'' +
              ", whsOnlyExp='" + whsOnlyExp + '\'' +
              ", notForResaleExp='" + notForResaleExp + '\'' +
              ", defPolicyExp='" + defPolicyExp + '\'' +
              ", skuExp='" + skuExp + '\'' +
              ", descExp='" + descExp + '\'' +
              ", upcExp='" + upcExp + '\'' +
              ", vndSkuExp='" + vndSkuExp + '\'' +
              ", vndIdExp='" + vndIdExp + '\'' +
              ", vndNameExp='" + vndNameExp + '\'' +
              ", imgSmExp='" + imgSmExp + '\'' +
              ", imgMdExp='" + imgMdExp + '\'' +
              ", imgLgExp='" + imgLgExp + '\'' +
              ", caseExp='" + caseExp + '\'' +
              ", dlrPackExp='" + dlrPackExp + '\'' +
              ", packOfExp='" + packOfExp + '\'' +
              ", casePackExp='" + casePackExp + '\'' +
              ", uomQtyExp='" + uomQtyExp + '\'' +
              ", priceUnitAmtExp='" + priceUnitAmtExp + '\'' +
              ", costExp='" + costExp + '\'' +
              ", retailExp='" + retailExp + '\'' +
              ", lengthExp='" + lengthExp + '\'' +
              ", widthExp='" + widthExp + '\'' +
              ", heightExp='" + heightExp + '\'' +
              ", weightExp='" + weightExp + '\'' +
              ", cubeExp='" + cubeExp + '\'' +
              ", uomExp='" + uomExp + '\'' +
              ", mdcExp='" + mdcExp + '\'' +
              ", nrhaExp='" + nrhaExp + '\'' +
              ", mdseClassExp='" + mdseClassExp + '\'' +
              ", cmdtyGrpExp='" + cmdtyGrpExp + '\'' +
              ", prodGrpExp='" + prodGrpExp + '\'' +
              ", deptExp='" + deptExp + '\'' +
              ", buyerExp='" + buyerExp + '\'' +
              ", brandNameExp='" + brandNameExp + '\'' +
              ", retailAExp='" + retailAExp + '\'' +
              ", retailBExp='" + retailBExp + '\'' +
              ", oldMaterialNumExp='" + oldMaterialNumExp + '\'' +
              ", newMaterialNumExp='" + newMaterialNumExp + '\'' +
              ", velocityUnitCdExp='" + velocityUnitCdExp + '\'' +
              ", velocityDollarCdExp='" + velocityDollarCdExp + '\'' +
              ", regCostExp='" + regCostExp + '\'' +
              ", tmpCostExp='" + tmpCostExp + '\'' +
              ", tmpCostStartExp='" + tmpCostStartExp + '\'' +
              ", tmpCostEndExp='" + tmpCostEndExp + '\'' +
              ", dcAllocMaxExp='" + dcAllocMaxExp + '\'' +
              ", dcAllocEndExp='" + dcAllocEndExp + '\'' +
              ", custAllocMaxExp='" + custAllocMaxExp + '\'' +
              ", custAllocEndExp='" + custAllocEndExp + '\'' +
              ", bulletStringRawExp='" + bulletStringRawExp + '\'' +
              ", aceSku='" + aceSku + '\'' +
              ", aceVndId=" + aceVndId +
              ", brandName='" + brandName + '\'' +
              ", brokenCase=" + brokenCase +
              ", custAllocMax=" + custAllocMax +
              ", custAllocEnd=" + custAllocEnd +
              ", cmdtyGrp='" + cmdtyGrp + '\'' +
              ", cost=" + cost +
              ", cube=" + cube +
              ", dealerPack=" + dealerPack +
              ", dcAllocMax=" + dcAllocMax +
              ", dcAllocEnd=" + dcAllocEnd +
              ", defPolicyCd='" + defPolicyCd + '\'' +
              ", desc='" + desc + '\'' +
              ", dept='" + dept + '\'' +
              ", emeryStocked=" + emeryStocked +
              ", flc='" + flc + '\'' +
              ", height=" + height +
              ", imageUrlLg='" + imageUrlLg + '\'' +
              ", imageUrlMd='" + imageUrlMd + '\'' +
              ", imageUrlSm='" + imageUrlSm + '\'' +
              ", length=" + length +
              ", mdc='" + mdc + '\'' +
              ", mdseClassCd='" + mdseClassCd + '\'' +
              ", nrha='" + nrha + '\'' +
              ", packOf=" + packOf +
              ", prodGrp='" + prodGrp + '\'' +
              ", regCost=" + regCost +
              ", retail=" + retail +
              ", retailA=" + retailA +
              ", retailB=" + retailB +
              ", rscCd='" + rscCd + '\'' +
              ", rscId=" + rscId +
              ", sourceData='" + sourceData + '\'' +
              ", status='" + status + '\'' +
              ", tmpCost=" + tmpCost +
              ", tmpEnd=" + tmpEnd +
              ", tmpStart=" + tmpStart +
              ", uom='" + uom + '\'' +
              ", upc='" + upc + '\'' +
              ", vndId=" + vndId +
              ", vndName='" + vndName + '\'' +
              ", vndSku='" + vndSku + '\'' +
              ", whsOnly=" + whsOnly +
              ", weight=" + weight +
              ", width=" + width +
              ", taxonomyId=" + taxonomyId +
              ", mruInd='" + mruInd + '\'' +
              ", noReturn=" + noReturn +
              ", oldMaterialNum='" + oldMaterialNum + '\'' +
              ", newMaterialNum='" + newMaterialNum + '\'' +
              ", velocityUnitCd='" + velocityUnitCd + '\'' +
              ", velocityDollarCd='" + velocityDollarCd + '\'' +
              ", priceUnitAmt=" + priceUnitAmt +
              ", casePackQty=" + casePackQty +
              ", uomQty=" + uomQty +
              ", dispositionCd='" + dispositionCd + '\'' +
              ", buyerCd='" + buyerCd + '\'' +
              ", xrefId=" + xrefId +
              ", itemRscId=" + itemRscId +
              ", notForResale=" + notForResale +
              ", m_BulletsRawString='" + m_BulletsRawString + '\'' +
              ", m_Bullets=" + m_Bullets +
              '}';
   }

   @Override
   protected void finalize() throws Throwable
   {
      m_Bullets.clear();
      m_Bullets = null;

      super.finalize();
   }

   /**
    *
    * @param sourceData
    */
   public AceItemBean(String sourceData)
   {
      this();

      setSourceData(sourceData);
   }

   public String getEmerySku() {
      return emerySku;
   }

   public void setEmerySku(String emerySku) {
      this.emerySku = emerySku;
   }

   public int getTaxonomyId() {
      return taxonomyId;
   }

   public void setTaxonomyId(int taxonomyId) {
      this.taxonomyId = taxonomyId;
   }

   public String getBulletsRawString() {
      return m_BulletsRawString;
   }

   public void setBulletsRawString(String m_BulletsRawString) {
      this.m_BulletsRawString = m_BulletsRawString;
   }

   /**
    *
    * @return the ACE vendor id
    */
   public long getAceVndId()
   {
      return aceVndId;
   }

   /**
    * @return the brandName
    */
   public String getBrandName()
   {
      return brandName;
   }

   /**
    * @return The bullet points
    */
   public ArrayList<String> getBullets()
   {
      return m_Bullets;
   }

   /**
    * @return the ACE sku
    */
   public String getAceSku()
   {
      return aceSku;
   }

   /**
    * @return the cmdtyGrp
    */
   public String getCmdtyGrp()
   {
      return cmdtyGrp;
   }

   /**
    * @return the cost
    */
   public double getCost()
   {
      return cost;
   }

   /**
    * @return the cube
    */
   public double getCube()
   {
      return cube;
   }

   /**
    * @return customer allocation end date
    */
   public Date getCustAllocEnd()
   {
      return custAllocEnd;
   }

   /**
    * @return customer max allocation
    */
   public int getCustAllocMax()
   {
      return custAllocMax;
   }

   /**
    * @return the dealerPack
    */
   public int getDealerPack()
   {
      return dealerPack;
   }

   /**
    * @return The dc allocation end date
    */
   public Date getDcAllocEnd()
   {
      return dcAllocEnd;
   }

   /**
    * @return The dc allocation max
    */
   public int getDcAllocMax()
   {
      return dcAllocMax;
   }

   /**
    * @return The item level defect policy code
    */
   public String getDefPolicyCd()
   {
      return defPolicyCd;
   }

   /**
    * @return the desc
    */
   public String getDesc()
   {
      return desc;
   }

   /**
    *
    * @return The department code
    */
   public String getDept()
   {
      return dept;
   }

   /**
    * @return the flc
    */
   public String getflc()
   {
      return flc;
   }

   /**
    * @return the height
    */
   public double getHeight()
   {
      return height;
   }

   /**
    * @return the imageUrlLg
    */
   public String getImageUrlLg()
   {
      return imageUrlLg;
   }

   /**
    * @return the imageUrlMd
    */
   public String getImageUrlMd()
   {
      return imageUrlMd;
   }

   /**
    * @return the imageUrlSm
    */
   public String getImageUrlSm()
   {
      return imageUrlSm;
   }

   /**
    *
    * @return The ace_item_rsc id.
    */
   public long getItemRscId()
   {
      return itemRscId;
   }

   /**
    * @return the length
    */
   public double getLength()
   {
      return length;
   }

   /**
    * @return the mdc
    */
   public String getMdc()
   {
      return mdc;
   }

   /**
    * @return the mdseClassCd
    */
   public String getMdseClassCd()
   {
      return mdseClassCd;
   }

   /**
    * @return the nrha
    */
   public String getNrha()
   {
      return nrha;
   }

   /**
    * @return the packOf
    */
   public int getPackOf()
   {
      return packOf;
   }

   /**
    * @return the prodGrp
    */
   public String getProdGrp()
   {
      return prodGrp;
   }

   /**
    * @return the regular cost
    */
   public double getRegCost()
   {
      return regCost;
   }

   /**
    * @return the retail
    */
   public double getRetail()
   {
      return retail;
   }

   /**
    * @return the retailA
    */
   public double getRetailA()
   {
      return retailA;
   }

   /**
    * @return the retailB
    */
   public double getRetailB()
   {
      return retailB;
   }

   /**
    * @return The RSC code
    */
   public String getRscCd()
   {
      return rscCd;
   }

   /**
    * @return the RSC id from the internal db table.
    */
   public int getRscId()
   {
      return rscId;
   }

   /**
    * @return the sourceData
    */
   public String getSourceData()
   {
      return sourceData;
   }

   /**
    * @return the status
    */
   public String getStatus()
   {
      return status;
   }

   /**
    * @return The temp cost
    */
   public double getTmpCost()
   {
      return tmpCost;
   }

   /**
    * @return The tmp cost end date
    */
   public Date getTmpEnd()
   {
      return tmpEnd;
   }

   /**
    * @return The temp cost start date.
    */
   public Date getTmpStart()
   {
      return tmpStart;
   }

   /**
    * @return the uom
    */
   public String getUom()
   {
      return uom;
   }

   /**
    * @return the upc
    */
   public String getUpc()
   {
      return upc;
   }

   /**
    * @return the vndId
    */
   public long getVndId()
   {
      return vndId;
   }


   /**
    * @return the vndName
    */
   public String getVndName()
   {
      return vndName;
   }

   /**
    * @return the vndSku
    */
   public String getVndSku()
   {
      return vndSku;
   }

   /**
    * @return the weight
    */
   public double getWeight()
   {
      return weight;
   }

   /**
    * @return the width
    */
   public double getWidth()
   {
      return width;
   }

   /**
    * @return The ace_item_xref id.
    */
   public long getXRefId()
   {
      return xrefId;
   }

   /**
    * @return the brokenCase
    */
   public boolean isBrokenCase()
   {
      return brokenCase;
   }

   /**
    * Checks the catalog data for equality.
    *
    * @param obj The object to compare data with
    * @return true if the catalog data is equal, false if not
    */
   public boolean isCatalogEqual(AceItemBean obj)
   {
      boolean isEqual = false;

      isEqual = obj.getBrandName().equals(getBrandName());

      //
      // This is probably expensive so only do it if the brand names match.
      // May need to do reciprocating checks on the bullet data.
      if ( isEqual )
         isEqual = obj.getBullets().size() == obj.getBullets().size() &&
            getBullets().containsAll(obj.getBullets());

      return isEqual;
   }

   /**
    * Flag for whether the item is stocked at Emery.
    * @return true if it's stocked at Emery, false if not.
    */
   public boolean isEmeryStocked()
   {
      return emeryStocked;
   }

   /**
    *
    * @param obj
    * @return
    */
   public boolean isItemEqual(AceItemBean obj)
   {
      return obj.getCmdtyGrp().equals(getCmdtyGrp()) &&
            obj.getDealerPack() == getDealerPack() &&
            obj.getDept().equals(getDept()) &&
            obj.getDesc().equals(getDesc()) &&
            obj.getMdseClassCd().equals(getMdseClassCd()) &&
            obj.getPackOf() == getPackOf() &&
            obj.getProdGrp().equals(getProdGrp()) &&
            obj.getUpc().equals(getUpc()) &&
            obj.getDefPolicyCd().equals(getDefPolicyCd()) &&
            obj.getCasePackQty() == getCasePackQty();
   }

   public boolean isNotForResale() {
      return notForResale;
   }

   /**
    *
    * @param obj A reference to the object to compare.
    * @return True if the pricing information is equal to obj pricing data
    */
   public boolean isPriceEqual(AceItemBean obj)
   {
      return obj.getCost() == getCost() &&
            obj.getTmpCost() == getTmpCost() &&
            obj.getTmpStart() == getTmpStart() &&
            obj.getTmpEnd() == getTmpEnd() &&
            obj.getRetail() == getRetail() &&
            obj.getRetailA() == getRetailA() &&
            obj.getRetailB() == getRetailB();
   }

   /**
    * Checks for equality with the vendor specific data
    * @param obj
    * @return true if the data is equal, false if not.
    */
   public boolean isVendorEqual(AceItemBean obj)
   {
      return obj.getAceVndId() == getAceVndId() &&
            obj.getVndId() == getVndId() &&
            obj.getVndName().equals(getVndName());
   }

   /**
    * Checks if the vendor item numbers are equal.
    * @param vndSku The vendor item number
    * @return true if equal, false if not.
    */
   public boolean isVndItemEqual(String vndSku)
   {
      return this.vndSku.equals(vndSku);
   }

   /**
    * The wholesale only flag
    * @return true if wholesale only, false if not.
    */
   public boolean isWhsOnly()
   {
      return this.whsOnly;
   }

   /**
    * @param sku the sku to set
    */
   public void setAceSku(String sku)
   {
      if ( sku != null )
         aceSku = sku.trim();
      else
         aceSku = "";
   }

   /**
    *
    * @param aceVndId
    */
   public void setAceVndId(long aceVndId)
   {
      this.aceVndId = aceVndId;
   }

   /**
    * @param brandName the brandName to set
    */
   public void setBrandName(String brandName)
   {
      if ( brandName != null )
         this.brandName = brandName.trim();
      else
         this.brandName = "";
   }

   /**
    * @param brokenCase the brokenCase to set
    */
   public void setBrokenCase(boolean brokenCase)
   {
      this.brokenCase = brokenCase;
   }

   /**
    *
    * @param buyerCd
    */
   public void setBuyerCd(String buyerCd)
   {
      if ( buyerCd != null )
         this.buyerCd = buyerCd.trim();
      else
         buyerCd = "";
   }

   /**
    *
    * @param casePackQty
    */
   public void setCasePackQty(int casePackQty)
   {
      this.casePackQty = casePackQty;
   }

   /**
    * @param cmdtyGrp the cmdtyGrp to set
    */
   public void setCmdtyGrp(String cmdtyGrp)
   {
      this.cmdtyGrp = cmdtyGrp;
   }

   /**
    * @param cost the cost to set
    */
   public void setCost(double cost)
   {
      this.cost = cost;
   }

   /**
    * @param cube the cube to set
    */
   public void setCube(double cube)
   {
      this.cube = cube;
   }

   /**
    * Set the end date for customer allocations
    * @param custAllocEnd
    */
   public void setCustAllocEnd(Date custAllocEnd)
   {
      this.custAllocEnd = custAllocEnd;
   }

   /**
    * Set the max allocation for a cusstomer
    * @param custAllocMax - the allocation amount.
    */
   public void setCustAllocMax(int custAllocMax)
   {
      this.custAllocMax = custAllocMax;
   }

   /**
    * Set the end date for dc allocation
    * @param dcAllocEnd
    */
   public void setDcAllocEnd(Date dcAllocEnd)
   {
      this.dcAllocEnd = dcAllocEnd;
   }

   /**
    * Set the max allocation for a DC
    * @param dcAllocMax - the allocation amount.
    */
   public void setDcAllocMax(int dcAllocMax)
   {
      this.dcAllocMax = dcAllocMax;
   }

   /**
    * @param dealerPack the dealerPack to set
    */
   public void setDealerPack(int dealerPack)
   {
      this.dealerPack = dealerPack;
   }

   /**
    * Set the item level defect policy
    * @param defPolicyCd
    */
   public void setDefPolicyCd(String defPolicyCd)
   {
      if ( defPolicyCd != null )
         this.defPolicyCd = defPolicyCd.trim();
      else
         this.defPolicyCd = "";
   }

   /**
    * Sets the department
    * @param dept
    */
   public void setDept(String dept)
   {
      if ( dept != null )
         this.dept = dept.trim();
      else
         this.dept = "";
   }

   /**
    * @param desc the desc to set
    */
   public void setDesc(String desc)
   {
      String[] parts = null;

      if ( desc != null ) {
         this.desc = desc.trim();
         parts = this.desc.split("[|]");

         try {
            if ( parts.length > 0 ) {
               for ( int i = 0; i < parts.length; i++ ) {
                  if ( i == 0 )
                     this.desc = parts[i];
                  else
                     m_Bullets.add(parts[i]);
               }
            }
         }

         finally {
            for ( int i = 0; i < parts.length; i++ )
               parts[i] = null;

            parts = null;
         }
      }
   }

   /**
    *
    * @param dispositionCd
    */
   public void setDispositionCd(String dispositionCd)
   {
      if ( dispositionCd != null )
         this.dispositionCd = dispositionCd.trim();
      else
         this.dispositionCd = "";
   }

   /**
    * @param emeryStocked
    */
   public void setEmeryStocked(boolean emeryStocked)
   {
      this.emeryStocked = emeryStocked;
   }

   /**
    * @param flc The flc to set
    */
   public void setFlc(String flc)
   {
      if ( flc != null )
         this.flc = flc;
   }

   /**
    * @param height the height to set
    */
   public void setHeight(double height)
   {
      this.height = height;
   }

   /**
    * @param url the imageUrlLg to set
    */
   public void setImageUrlLg(String url)
   {
      if ( url != null )
         imageUrlLg = url.trim();
   }

   /**
    * @param url the imageUrlMd to set
    */
   public void setImageUrlMd(String url)
   {
      if ( url != null )
         imageUrlMd = url.trim();
   }

   /**
    * @param url the imageUrlSm to set
    */
   public void setImageUrlSm(String url)
   {
      if ( url != null )
         imageUrlSm = url.trim();
   }

   /**
    * Sets the ace_item_rsc id.
    * @param itemRscId
    */
   public void setItemRscId(long itemRscId)
   {
      this.itemRscId = itemRscId;
   }

   /**
    * @param length the length to set
    */
   public void setLength(double length)
   {
      this.length = length;
   }

   /**
    * @param mdc the mdc to set
    */
   public void setMdc(String mdc)
   {
      if ( mdc != null )
         this.mdc = mdc.trim();
   }

   /**
    * @param mdseClassCd the mdseClassCd to set
    */
   public void setMdseClassCd(String mdseClassCd)
   {
      if ( mdseClassCd != null )
         this.mdseClassCd = mdseClassCd;
   }

   /**
    *
    * @param mruInd
    */
   public void setMruInd(String mruInd)
   {
      if ( mruInd != null )
         this.mruInd = mruInd;
      else
         this.mruInd = "";
   }

   public String getMruInd() {
      return mruInd;
   }

   /**
    *
    * @param newMaterialNum
    */
   public void setNewMaterialNum(String newMaterialNum)
   {
      if ( newMaterialNum != null )
         this.newMaterialNum = newMaterialNum.trim();
      else
         this.newMaterialNum = "";
   }

   /**
    *
    * @param noReturn
    */
   public void setNoReturn(boolean noReturn)
   {
      this.noReturn = noReturn;
   }

   public boolean getNoReturn() {
      return noReturn;
   }

   /**
    * @param nrha the nrha to set
    */
   public void setNrha(String nrha)
   {
      this.nrha = nrha;
   }

   /**
    *
    * @param oldMaterialNum
    */
   public void setOldMaterialNum(String oldMaterialNum)
   {
      if ( oldMaterialNum != null )
         this.oldMaterialNum = oldMaterialNum.trim();
      else
         this.oldMaterialNum = "";
   }

   public void setNotForResale(boolean notForResale) {
      this.notForResale = notForResale;
   }

   /**
    * @param packOf the packOf to set
    */
   public void setPackOf(int packOf)
   {
      this.packOf = packOf;
   }

   /**
    *
    * @param priceUnitAmt
    */
   public void setPriceUnitAmt(double priceUnitAmt)
   {
      this.priceUnitAmt = priceUnitAmt;
   }
   /**
    * @param prodGrp the prodGrp to set
    */
   public void setProdGrp(String prodGrp)
   {
      if ( prodGrp != null )
         this.prodGrp = prodGrp;
   }

   /**
    * @param cost the cost to set
    */
   public void setRegCost(double cost)
   {
      this.regCost = cost;
   }

   /**
    * @param retail the retail to set
    */
   public void setRetail(double retail)
   {
      this.retail = retail;
   }

   /**
    * @param retailA the retailA to set
    */
   public void setRetailA(double retailA)
   {
      this.retailA = retailA;
   }

   /**
    * @param retailB the retailB to set
    */
   public void setRetailB(double retailB)
   {
      this.retailB = retailB;
   }

   /**
    *
    * @param rscCd The RSC (SAP code) to set
    */
   public void setRscCd(String rscCd)
   {
      if ( rscCd != null ) {
         if ( rscCd.trim().length() > 0 )
            this.rscCd = rscCd.trim();
      }
   }

   /**
    * Sets the internal id for the RSC code
    * @param rscId, the internal rsc id from the db.
    */
   public void setRscId(int rscId)
   {
      this.rscId = rscId;
   }

   /**
    * @param sourceData the sourceData to set
    */
   public void setSourceData(String sourceData)
   {
      DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder;
      Document document;
      XPath xPath = XPathFactory.newInstance().newXPath();
      String tmp;
      DateFormat df = DateFormat.getDateTimeInstance();

      try {
         if ( sourceData != null && sourceData.length() > 0 ) {
            //
            // Remove the namespace if it happens to be in the XML. It didn't need to be there and it's jsut
            // a snippet.
            this.sourceData = sourceData.replace("eme:", "");

            builder = builderFactory.newDocumentBuilder();
            document = builder.parse(new ByteArrayInputStream(this.sourceData.getBytes()));
            setStatus(xPath.compile(statusExp).evaluate(document));
            setMruInd(xPath.compile(mruExp).evaluate(document));
            setNoReturn(!xPath.compile(noReturnExp).evaluate(document).equalsIgnoreCase("N"));
            setDispositionCd(xPath.compile(dispExp).evaluate(document));
            setRscCd(xPath.compile(rscExp).evaluate(document));
            setDefPolicyCd(xPath.compile(defPolicyExp).evaluate(document));
            setWhsOnly(xPath.compile(whsOnlyExp).evaluate(document).equalsIgnoreCase("Y"));

            setAceSku(xPath.compile(skuExp).evaluate(document));

            //ebrownewell (08-03-2015): added to accommodate the new notForResale field.
            setNotForResale(xPath.compile(notForResaleExp).evaluate(document).equalsIgnoreCase("Y"));

            //ebrownewell (11/15/2016): Added to accomodate the new bullets desc value from ace.
            setBulletsRawString(xPath.compile(bulletStringRawExp).evaluate(document));

            //
            // Ace only sends the ACE sku, rsc and the status on a delete.
            if ( ! status.equalsIgnoreCase("D") ) {
               setDesc(xPath.compile(descExp).evaluate(document));
               setUpc(xPath.compile(upcExp).evaluate(document));
               setVndSku(xPath.compile(vndSkuExp).evaluate(document));

               tmp = xPath.compile(vndIdExp).evaluate(document);

               if ( tmp != null && tmp.length() > 0 )
                  setAceVndId(Integer.parseInt(tmp));
               else
                  log.error(String.format("[AceItemBean] %s missing ace vendor ID", aceSku));

               setVndName(xPath.compile(vndNameExp).evaluate(document));
               setImageUrlSm(xPath.compile(imgSmExp).evaluate(document));
               setImageUrlMd(xPath.compile(imgMdExp).evaluate(document));
               setImageUrlLg(xPath.compile(imgLgExp).evaluate(document));
               setBrokenCase(!xPath.compile(caseExp).evaluate(document).equalsIgnoreCase("N"));

               tmp = xPath.compile(dlrPackExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setDealerPack(Integer.parseInt(tmp));

               tmp = xPath.compile(packOfExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setPackOf(Integer.parseInt(tmp));

               tmp = xPath.compile(casePackExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setCasePackQty(Integer.parseInt(tmp));

               tmp = xPath.compile(uomQtyExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setUomQty(Integer.parseInt(tmp));

               tmp = xPath.compile(priceUnitAmtExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setPriceUnitAmt(Double.parseDouble(tmp));

               tmp = xPath.compile(costExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setCost(Double.parseDouble(tmp));

               tmp = xPath.compile(retailExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setRetail(Double.parseDouble(tmp));

               tmp = xPath.compile(lengthExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setLength(Double.parseDouble(tmp));

               tmp = xPath.compile(widthExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setWidth(Double.parseDouble(tmp));

               tmp = xPath.compile(heightExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setHeight(Double.parseDouble(tmp));

               tmp = xPath.compile(weightExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setWeight(Double.parseDouble(tmp));

               tmp = xPath.compile(cubeExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setCube(Double.parseDouble(tmp));

               setUom(xPath.compile(uomExp).evaluate(document));
               setMdc(xPath.compile(mdcExp).evaluate(document));
               setNrha(xPath.compile(nrhaExp).evaluate(document));
               setMdseClassCd(xPath.compile(mdseClassExp).evaluate(document));
               setCmdtyGrp(xPath.compile(cmdtyGrpExp).evaluate(document));
               setProdGrp(xPath.compile(prodGrpExp).evaluate(document));
               setDept(xPath.compile(deptExp).evaluate(document));
               setBuyerCd(xPath.compile(buyerExp).evaluate(document));
               setBrandName(xPath.compile(brandNameExp).evaluate(document));

               //
               // Some items are part of a package and won't have retails.
               tmp = xPath.compile(retailAExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setRetailA(Double.parseDouble(tmp));

               tmp = xPath.compile(retailBExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setRetailB(Double.parseDouble(tmp));

               tmp = xPath.compile(regCostExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setRegCost(Double.parseDouble(tmp));

               tmp = xPath.compile(tmpCostExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setTmpCost(Double.parseDouble(tmp));

               tmp = xPath.compile(tmpCostStartExp).evaluate(document);

               if ( tmp != null && tmp.length() > 0 ) {
                  try {
                     setTmpCostStart(new Date(df.parse(tmp).getTime()));
                  }

                  catch ( ParseException ex ) {
                     log.error(String.format("[AceItemBean] %s", aceSku), ex);
                  }
               }

               tmp = xPath.compile(tmpCostEndExp).evaluate(document);

               if ( tmp != null && tmp.length() > 0 ) {
                  try {
                     setTmpCostEnd(new Date(df.parse(tmp).getTime()));
                  }

                  catch ( ParseException ex ) {
                     log.error(String.format("[AceItemBean] %s", aceSku), ex);
                  }
               }

               setOldMaterialNum(xPath.compile(oldMaterialNumExp).evaluate(document));
               setNewMaterialNum(xPath.compile(newMaterialNumExp).evaluate(document));
               setVelocityUnitCd(xPath.compile(velocityUnitCdExp).evaluate(document));
               setVelocityDollarCd(xPath.compile(velocityDollarCdExp).evaluate(document));

               tmp = xPath.compile(dcAllocMaxExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setDcAllocMax(Integer.parseInt(tmp));

               tmp = xPath.compile(dcAllocEndExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 ) {
                  try {
                     setDcAllocEnd(new Date(df.parse(tmp).getTime()));
                  }

                  catch ( ParseException ex ) {
                     log.error(String.format("[AceItemBean] %s", aceSku), ex);
                  }
               }

               tmp = xPath.compile(custAllocMaxExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 )
                  setCustAllocMax(Integer.parseInt(tmp));

               tmp = xPath.compile(custAllocEndExp).evaluate(document);
               if ( tmp != null && tmp.length() > 0 ) {
                  try {
                     setCustAllocEnd(new Date(df.parse(tmp).getTime()));
                  }

                  catch ( ParseException ex ) {
                     setCustAllocEnd(null);
                  }
               }
            }
         }
      }

      catch ( SAXException | IOException | ParserConfigurationException | XPathExpressionException ex ) {
         log.error(String.format("[AceItemBean] %s", aceSku), ex);
         log.error("Source: " + sourceData);
      }

      finally {
         xPath = null;
         document = null;
         builder = null;
         builderFactory = null;
      }
   }

   /**
    * @param status the status to set
    */
   public void setStatus(String status)
   {
      if ( status != null )
         this.status = status.trim();
   }

   /**
    * @param cost the cost to set
    */
   public void setTmpCost(double cost)
   {
      this.tmpCost = cost;
   }

   public void setTmpCostEnd(Date date)
   {
      this.tmpEnd = date;
   }

   public void setTmpCostStart(Date date)
   {
      this.tmpStart = date;
   }

   /**
    * @param uom the uom to set
    */
   public void setUom(String uom)
   {
      if ( uom != null )
         this.uom = uom.trim();
   }

   /**
    *
    * @param uomQty
    */
   public void setUomQty(int uomQty)
   {
      this.uomQty = uomQty;
   }

   /**
    * @param upc the upc to set
    */
   public void setUpc(String upc)
   {
      if ( upc != null ) {
         upc = upc.trim();

         //
         // The UPCs were missing leading 0s.  Not sure how many, but we'll start with four
         // missing digits.
         switch ( upc.length() ) {
            case 8: {
               upc = "0000" + upc;
               break;
            }

            case 9: {
               upc = "000" + upc;
               break;
            }

            case 10: {
               upc = "00" + upc;
               break;
            }

            case 11: {
               upc = "0" + upc;
               break;
            }
         }

         this.upc = upc;
      }
      else
         this.upc = "";
   }

   /**
    *
    * @param code The velocity dollar code
    */
   public void setVelocityDollarCd(String code)
   {
      if ( code != null )
         velocityDollarCd = code.trim();
      else
         velocityDollarCd = "";
   }

   /**
    *
    * @param code
    */
   public void setVelocityUnitCd(String code)
   {
      if ( code != null )
         velocityUnitCd = code.trim();
      else
         velocityUnitCd = "";
   }

   /**
    * @param vndId the vndId to set
    */
   public void setVndId(long vndId)
   {
      this.vndId = vndId;
   }

   /**
    * @param vndName the name to set
    */
   public void setVndName(String vndName)
   {
      if ( vndName != null )
         this.vndName = vndName.trim();
      else
         vndName = "";
   }

   /**
    * @param vndSku the vndSku to set
    */
   public void setVndSku(String vndSku)
   {
      if ( vndSku != null )
         this.vndSku = vndSku.trim();
      else
         vndSku = "";
   }

   /**
    * @param weight the weight to set
    */
   public void setWeight(double weight)
   {
      this.weight = weight;
   }

   /**
    * @param whsOnly
    */
   public void setWhsOnly(boolean whsOnly)
   {
      this.whsOnly = whsOnly;
   }

   /**
    * @param width the width to set
    */
   public void setWidth(double width)
   {
      this.width = width;
   }

   /**
    * Sets the ace item xref id.
    * @param xrefId
    */
   public void setXRefId(long xrefId)
   {
      this.xrefId = xrefId;
   }

   public int getItemEaId() {
      return ItemEaId;
   }

   public void setItemEaId(int itemEaId) {
      ItemEaId = itemEaId;
   }

   public int getEjdItemWhsId() {
      return EjdItemWhsId;
   }

   public void setEjdItemWhsId(int ejdItemWhsId) {
      EjdItemWhsId = ejdItemWhsId;
   }

   public int getEjdItemUpcId() {
      return ejdItemUpcId;
   }

   public void setEjdItemUpcId(int ejdItemUpcId) {
      this.ejdItemUpcId = ejdItemUpcId;
   }

   public String getFlc() {
      return flc;
   }

   public boolean isNoReturn() {
      return noReturn;
   }

   public String getOldMaterialNum() {
      return oldMaterialNum;
   }

   public String getNewMaterialNum() {
      return newMaterialNum;
   }

   public String getVelocityUnitCd() {
      return velocityUnitCd;
   }

   public String getVelocityDollarCd() {
      return velocityDollarCd;
   }

   public double getPriceUnitAmt() {
      return priceUnitAmt;
   }

   public int getCasePackQty() {
      return casePackQty;
   }

   public int getUomQty() {
      return uomQty;
   }

   public String getDispositionCd() {
      return dispositionCd;
   }

   public String getBuyerCd() {
      return buyerCd;
   }

   public long getXrefId() {
      return xrefId;
   }

   public String getM_BulletsRawString() {
      return m_BulletsRawString;
   }

   public ArrayList<String> getM_Bullets() {
      return m_Bullets;
   }

   public int getEjdItemId() {
      return ejdItemId;
   }

   public void setEjdItemId(int ejdItemId) {
      this.ejdItemId = ejdItemId;
   }
}
