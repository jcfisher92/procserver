/*
  File: DataModule.java
  Description: Module for handling database access and data manipulation

  @author: Jeff Fisher
  Create Date: 06/13/2014
 */
package com.emerywaterhouse.ace.catalog;

import com.emerywaterhouse.utils.DbUtils;
import org.apache.log4j.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;

public class DataModule implements AutoCloseable {
    public static final int noUpdate = 0;
    public static final int itemAdd = 1;
    public static final int itemChange = 2;
    public static final int itemDelete = 3;

    /**
     * Item disposition enumeration
     */
    public enum ItemDisp {
        Unknown, Review, Ignore, Approved, BuySell, Delete, NoBuy, NoBuyNoSell
    }

    private Connection m_Conn;
    private boolean m_Prepared;

    private PreparedStatement m_AddBullet;
    private PreparedStatement m_AddEx;
    private PreparedStatement m_AddRaw;
    private PreparedStatement m_AddVndChange;
    private CallableStatement m_DelItem;
    private PreparedStatement m_DelXRef;
    private PreparedStatement m_GetBCId;
    private PreparedStatement m_GetCurItemData;
    private PreparedStatement m_GetDeptId;
    private PreparedStatement m_GetDispId;
    private PreparedStatement m_GetFlcId;
    private PreparedStatement m_GetItemDisp;
    private PreparedStatement m_GetItemIdFromXref;
    private PreparedStatement m_GetItemTypeId;
    private PreparedStatement m_GetItemType;
    private PreparedStatement m_GetNewItem;
    private PreparedStatement m_GetRetUnitId;
    private PreparedStatement m_GetRscId;
    private PreparedStatement m_GetSell;
    private PreparedStatement m_GetShipUnitId;
    private PreparedStatement m_GetTaxonomyId;
    private PreparedStatement m_GetVelCodeId;
    private PreparedStatement m_GetVdhId;
    private PreparedStatement m_GetVndId;
    private PreparedStatement m_GetWarehouseId;
    private PreparedStatement m_GetXref;
    private PreparedStatement m_GetXrefDelta;
    private PreparedStatement m_LogMsg;
    private PreparedStatement m_UpdCatBullet;
    private PreparedStatement m_UpdItemVnd;
    private PreparedStatement m_UpdPricing;
    private PreparedStatement m_UpdVndSku;
    private PreparedStatement m_updateUpc;
    private PreparedStatement m_DelBullet;
    private PreparedStatement m_AddXRef;
    private PreparedStatement m_GetXRefId;
    private PreparedStatement m_CheckVndSku;
    private PreparedStatement m_UpdCatItem;
    private PreparedStatement m_UpdTaxonomy;

    private PreparedStatement m_GetItemEaIdIfExists;
    private PreparedStatement m_CheckItemEaWhsExists;
    private PreparedStatement m_AddItemEa;
    private PreparedStatement m_AddEjdItemWhs;
    private PreparedStatement m_AddEjdItemUpc;
    private PreparedStatement m_CheckEjdItemUpcExists;
    private PreparedStatement m_AddWebItemEa;
    private PreparedStatement m_CheckWebItemEa;
    private PreparedStatement m_CheckVendorEaSkuCrossExists;
    private PreparedStatement m_AddVendorEaSkuCross;
    private PreparedStatement m_AddEjdItem;
    private PreparedStatement m_GetItemIdByItemEaId;
    private PreparedStatement m_updItemEa;
    private PreparedStatement m_updEjdItem;
    private PreparedStatement m_getWeight;
    private PreparedStatement m_updItemWhs;
    private PreparedStatement m_CheckEjdItemIdExists;
    private PreparedStatement m_GetAceMerchTaxonomy;
    private PreparedStatement m_GetAceProductTaxonomy;
    private PreparedStatement m_CheckItemHasStock;

    private Logger m_Log;
    private String m_CurProc;

    /**
     * Default constructor.
     */
    public DataModule() {
        super();

        m_Conn = null;
        m_Prepared = false;
        m_CurProc = "";
    }

    /**
     * Adds the item bullet data sent from ACE. The data is a single * delimeted string. Note - There is a lot of
     * internal white space in the strings.
     *
     * @param itemEaId item ea id
     * @param desc     bullet description
     */
    private void addBullets(int itemEaId, String desc) {
        if (itemEaId == 0) {
            m_Log.error("Unable to add bullets, item ea id is null or empty. Bullet raw string: " + desc);
            return;
        }

        String bullet;
        String[] list = desc.split("[*]");
        int seqNbr = 10;

        // first we want to remove all the existing bullets for this item
        deleteBullets(itemEaId);

        for (String listItem : list)
            if (!listItem.trim().equals("?")) {
                bullet = listItem.replaceAll("\\W+", " ").trim();

                if (bullet.length() > 0)
                    addBullet(itemEaId, bullet, seqNbr);

                seqNbr += 10;
            }
    }

    /**
     * @param itemEaId item ea id
     */
    private void deleteBullets(int itemEaId) {
        try {
            m_DelBullet.setInt(1, itemEaId);
            m_DelBullet.executeUpdate();
        } catch (SQLException ex) {
            m_Log.error("[delBullets]", ex);
        }
    }

    /**
     * @param itemEaId item ea id
     * @param bullet   bullet
     * @param seqNbr   seq nbr
     */
    private void addBullet(int itemEaId, String bullet, int seqNbr) {
        try {
            m_AddBullet.setInt(1, itemEaId);
            m_AddBullet.setString(2, bullet);
            m_AddBullet.setInt(3, seqNbr);

            m_AddBullet.executeUpdate();
        } catch (SQLException ex) {
            m_Log.error(String.format("[addBullet] %s %s %d %s", itemEaId, bullet, seqNbr, ex.getMessage()));
        }
    }

    /**
     * Adds an Ace item record to the database. The main controlling method for adding data to all the tables needed.
     *
     * @param newRec The item data record.
     * @return 0 if no record added or updated, 1 = add, 2 = change, 3 = delete.
     */
    public int addDbRecord(AceItemBean newRec) {
        int whsId;
        int result = 0;
        int ejdItemId = 0;

        long vndId;
        long id = 0;

        Savepoint sp = null;

        AceItemBean curRec;

        if (newRec != null) {
            try {
                vndId = getVendorId(newRec.getAceVndId());
                newRec.setVndId(vndId);
                id = addRawData(newRec);

                //
                // Don't continue unless the raw data was added.
                // Note that the addRawData method does the rollback which aborts the
                // current transaction.
                if (id > 0) {
                    sp = m_Conn.setSavepoint();
                    newRec.setRscId(getInternalRscId(newRec.getRscCd()));
                    whsId = getWarehouseId(newRec.getRscId());
                    newRec.setXRefId(getXRefId(newRec.getAceSku()));

                    if (newRec.getXrefId() == 0)
                        ejdItemId = getEjdItemIdIfUpcExists(newRec.getUpc());

                    newRec.setItemEaId(getItemEaIdIfExists(newRec.getXrefId()));
                    newRec.setEmerySku(getEmerySku(newRec.getItemEaId()));

                    if (ejdItemId > 0)
                        newRec.setEjdItemId(ejdItemId);
                    else
                        newRec.setEjdItemId(getEjdItemIdIfExists(newRec.getXrefId()));

                    newRec.setEjdItemWhsId(getEjdItemWhsIdIfExists(newRec.getEjdItemId(), whsId));
                    newRec.setEjdItemUpcId(getEjdItemUpcIdIfExists(newRec.getEjdItemId(), whsId));

                    // On a delete, ACE only sends the ACE sku and the status of "D"
                    // We still need to be able to process even though the vendor id will be 0
                    if (vndId > 0 || (vndId == 0 && newRec.getStatus().equalsIgnoreCase("D"))) {
                        if (!newRec.getStatus().equalsIgnoreCase("D")) {
                            newRec.setVndId(vndId);

                            curRec = getCurItemData(newRec.getAceSku(), whsId);

                            newRec.setFlc(getFlcId(newRec.getMdseClassCd(), newRec.getCmdtyGrp(), newRec.getProdGrp()));

                            if (newRec.getEjdItemId() == 0)
                                newRec.setEjdItemId(insertEjdItem(newRec));
                            else if (curRec != null && !curRec.isItemEqual(newRec))
                                updateEjdItem(id, newRec);

                            if (newRec.getItemEaId() == 0)
                                newRec.setItemEaId(addEmeryItemEa(vndId, newRec));
                            else if (curRec != null && !curRec.isItemEqual(newRec))
                                updateItemEa(id, newRec);

                            newRec.setEmerySku(getEmerySku(newRec.getItemEaId()));

                            if (newRec.getXrefId() == 0) {
                                newRec.setXRefId(addItemXRef(newRec));

                                result = itemAdd;
                            } else
                                result = itemChange;

                            if (newRec.getEjdItemWhsId() == 0)
                                newRec.setEjdItemWhsId(addEjdItemWhs(newRec));
                            else if (curRec != null && !curRec.isItemEqual(newRec))
                                updateEjdItemWhs(id, newRec);

                            if (newRec.getEjdItemUpcId() == 0) {
                                if (newRec.getUpc() != null && newRec.getUpc().length() > 0)
                                    newRec.setEjdItemUpcId(addEjdItemUpc(newRec));
                                else {
                                    logWarn("item add", "missing UPC ", newRec.getAceSku(), newRec.getAceSku());
                                    addException(id, newRec, "[addUpc] missing UPC");
                                }
                            } else if (curRec != null && !newRec.getUpc().equalsIgnoreCase(curRec.getUpc()))
                                updateUpc(newRec.getEjdItemId(), whsId, newRec.getUpc());

                            if (!CheckCatItemEa(newRec.getItemEaId()))
                                // Add the catalog data
                                addCatItemEa(newRec);
                            else if (curRec != null && !curRec.isCatalogEqual(newRec))
                                updateCatalog(id, newRec);

                            if (curRec != null && !curRec.isVendorEqual(newRec))
                                updateVendor(id, newRec);

                            // Add the vendor part number to the vendor/item cross reference
                            if (!vndEaSkuExists(newRec))
                                addVndEaSku(vndId, newRec);
                            else if (curRec != null && !curRec.isVndItemEqual(newRec.getVndSku()))
                                updateVendorSku(id, newRec);

                            updateTaxonomy(newRec);

                            // ebrownewell (12/02/2016): we always want to process bullets from ace. so do these every time.
                            addBullets(newRec.getItemEaId(), newRec.getBulletsRawString());

                            if (!doesPriceRecordExistForWarehouse(newRec)) {
                                updatePricing(id, newRec); // Add the item price record
                            }
                        } else {
                            if (newRec.getStatus().equalsIgnoreCase("D")) {
                                deleteItem(newRec);
                                result = itemDelete;

                                // Handle deleting items
                                logInfo("item delete", newRec.getRscCd(), newRec.getAceSku(), newRec.getAceSku());
                            } else
                                logWarn("unknown status", newRec.getStatus());
                        }
                    } else {
                        m_Conn.rollback(sp);
                        addException(id, newRec, "Missing Emery vendor; ace vendor = " + newRec.getAceVndId());
                    }
                }
            } catch (Exception ex) {
                result = 0;

                try {
                    m_Conn.rollback(sp);
                } catch (SQLException ex1) {
                    m_Log.error("[AceItems]", ex1);
                }

                m_Log.error("[EXCEPTION]ace sku: " + newRec.getAceSku(), ex);
                addException(id, newRec, m_CurProc + " DB exception: " + ex.getMessage());
            }

            try {
                m_Conn.commit();
            } catch (SQLException ex2) {
                m_Log.error("[AceItems]", ex2);
            }
        } else
            m_Log.error("[AceItems] null item record");

        return result;
    }

    private boolean doesPriceRecordExistForWarehouse(AceItemBean item) {
        boolean res = false;

        String sql = "select * from ejd_item_price where warehouse_id = ? and ejd_item_id = ?";

        try (PreparedStatement stmt = m_Conn.prepareStatement(sql)) {
            stmt.setInt(1, getWarehouseId(item.getRscId()));
            stmt.setInt(2, item.getEjdItemId());

            try (ResultSet rs = stmt.executeQuery()) {
                if(rs.next()){
                    res = true;
                }
            }
        } catch (SQLException e) {
            m_Log.error("Failed to check if price record exists for item " + item);
        }

        return res;
    }

    private int getEjdItemIdIfUpcExists(String upc) throws SQLException {
        int res = 0;

        String sql = "SELECT DISTINCT ejd_item_id FROM ejd_item_whs_upc WHERE upc_code = ? AND warehouse_id IN (1,2)";

        try (PreparedStatement stmt = m_Conn.prepareStatement(sql)) {
            stmt.setString(1, upc);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    res = rs.getInt("ejd_item_id");
            }
        }

        return res;
    }

    private String getEmerySku(int itemEaId) throws SQLException {
        String res = null;

        String sql = "SELECT DISTINCT item_id FROM item_entity_attr WHERE item_ea_id = ?";

        try (PreparedStatement stmt = m_Conn.prepareStatement(sql)) {
            stmt.setInt(1, itemEaId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    res = rs.getString("item_id");
            }
        }

        return res;
    }

    /**
     * Adds the item to the ace/emery cross reference table.
     *
     * @param rec The item record from the queue
     * @return The db identifier of the record.
     * @throws SQLException
     */
    private long addItemXRef(AceItemBean rec) throws SQLException {
        long xrefId = 0;
        m_CurProc = "[addItemXref]";

        m_AddXRef.setString(1, rec.getAceSku());
        m_AddXRef.setString(2, rec.getEmerySku());
        m_AddXRef.setString(3, rec.getDept());
        m_AddXRef.setString(4, rec.getMdseClassCd());
        m_AddXRef.setString(5, rec.getProdGrp());
        m_AddXRef.setString(6, rec.getCmdtyGrp());
        m_AddXRef.setString(7, rec.getBuyerCd());
        m_AddXRef.setString(8, rec.getMruInd());
        m_AddXRef.setInt(9, (rec.getNoReturn() ? 1 : 0));
        m_AddXRef.setInt(10, rec.isWhsOnly() ? 1 : 0);
        m_AddXRef.setInt(11, rec.isNotForResale() ? 1 : 0);

        m_AddXRef.execute();

        try (ResultSet rs = m_AddXRef.getResultSet()) {
            if (rs.next())
                xrefId = rs.getLong(1);
        }

        return xrefId;
    }

    /**
     * Gets the ace_xref_id from the ace_item_ref record.
     *
     * @param aceSku The ace sku
     * @return The db record id for the ace sku in the xref table
     * @throws SQLException
     */
    private long getXRefId(String aceSku) throws SQLException {
        long id = 0;
        ResultSet rs = null;

        try {
            m_GetXRefId.setString(1, aceSku);
            rs = m_GetXRefId.executeQuery();

            if (rs.next())
                id = rs.getInt(1);
        } finally {
            closeRs(rs);
        }

        return id;
    }

    /**
     * @param vndId
     * @param newRec
     * @throws SQLException
     */
    private void addVndEaSku(long vndId, AceItemBean newRec) throws SQLException {
        m_CurProc = "[addVndEaSku]";
        Savepoint sp = m_Conn.setSavepoint();
        int res = 0;

        try {
            m_AddVendorEaSkuCross.setLong(1, vndId);
            m_AddVendorEaSkuCross.setInt(2, newRec.getItemEaId());
            m_AddVendorEaSkuCross.setString(3, newRec.getVndSku());

            res = m_AddVendorEaSkuCross.executeUpdate();

            if (res == 0)
                m_Log.error(
                        String.format(
                                "[addVndEaSku] Unable to add vendor_item_ea_cross entry for item_ea_id %s, vendor sku %s",
                                newRec.getItemEaId(), newRec.getVndSku())
                );
        } catch (Exception ex) {
            m_Conn.rollback(sp);
        } finally {
            sp = null;
        }
    }

    /**
     * @param newRec
     * @return
     * @throws SQLException
     */
    private boolean vndEaSkuExists(AceItemBean newRec) {
        boolean res = false;
        long vndId = newRec.getVndId();
        String vndSku = newRec.getVndSku();
        ResultSet rs = null;

        m_CurProc = "[vndEaSkuExists]";

        try {
            m_CheckVendorEaSkuCrossExists.setLong(1, vndId);
            m_CheckVendorEaSkuCrossExists.setString(2, vndSku);

            rs = m_CheckVendorEaSkuCrossExists.executeQuery();
            res = rs.next();
        } catch (SQLException e) {
            ;
        } finally {
            DbUtils.closeDbConn(null, null, rs);
        }

        return res;
    }

    private void addCatItemEa(AceItemBean newRec) throws SQLException {
        m_CurProc = "[addCatItemEa]";

        String desc = newRec.getDesc();

        if(desc.length() > 80){
            desc = desc.substring(0, 80);
        }

        m_AddWebItemEa.setInt(1, newRec.getItemEaId());
        m_AddWebItemEa.setString(2, newRec.getVndSku());
        m_AddWebItemEa.setString(3, newRec.getBrandName());
        m_AddWebItemEa.setString(4, newRec.getImageUrlSm());
        m_AddWebItemEa.setString(5, newRec.getImageUrlMd());
        m_AddWebItemEa.setString(6, newRec.getImageUrlLg());
        m_AddWebItemEa.setString(7, desc);

        int res = m_AddWebItemEa.executeUpdate();

        if (res == 0)
            m_Log.error("[addCatItemEa]Unable to add web_item_ea entry for item_ea_id " + newRec.getItemEaId());
    }

    private boolean CheckCatItemEa(int itemEaId) throws SQLException {
        boolean res;

        m_CheckWebItemEa.setInt(1, itemEaId);

        try (ResultSet rs = m_CheckWebItemEa.executeQuery()) {
            res = rs.next();
        }

        return res;
    }

    private int addEjdItemUpc(AceItemBean newRec) throws SQLException {
        int res = 0;

        m_AddEjdItemUpc.setString(1, newRec.getUpc());
        m_AddEjdItemUpc.setInt(2, newRec.getEjdItemId());
        m_AddEjdItemUpc.setInt(3, getWarehouseId(newRec.getRscId()));

        m_AddEjdItemUpc.execute();

        try (ResultSet rs = m_AddEjdItemUpc.getResultSet()) {
            if (rs.next())
                res = rs.getInt(1);
        }

        return res;
    }

    private int getEjdItemUpcIdIfExists(int ejdItemId, int whsId) throws SQLException {
        int res = 0;

        m_CheckEjdItemUpcExists.setInt(1, ejdItemId);
        m_CheckEjdItemUpcExists.setInt(2, whsId);

        try (ResultSet rs = m_CheckEjdItemUpcExists.executeQuery()) {
            if (rs.next())
                res = rs.getInt("ejd_upc_id");
        }

        return res;
    }

    private int addEjdItemWhs(AceItemBean newRec) throws SQLException {
        int res = 0;

        int whsId = getWarehouseId(newRec.getRscId());
        int velocityId = getVelocityCodeId();

        m_AddEjdItemWhs.setInt(1, newRec.getEjdItemId());
        m_AddEjdItemWhs.setInt(2, whsId);
        m_AddEjdItemWhs.setInt(3, newRec.getDealerPack()); // stock pack
        m_AddEjdItemWhs.setInt(4, velocityId);
        m_AddEjdItemWhs.setDouble(5, newRec.getLength());
        m_AddEjdItemWhs.setDouble(6, newRec.getWidth());
        m_AddEjdItemWhs.setDouble(7, newRec.getHeight());
        m_AddEjdItemWhs.setDouble(8, newRec.getCube());

        m_AddEjdItemWhs.execute();

        try (ResultSet rs = m_AddEjdItemWhs.getResultSet()) {
            if (rs.next())
                res = rs.getInt(1);
        }

        return res;
    }

    private int getEjdItemWhsIdIfExists(int ejdItemId, int warehouseId) throws SQLException {
        int res = 0;

        m_CheckItemEaWhsExists.setInt(1, ejdItemId);
        m_CheckItemEaWhsExists.setInt(2, warehouseId);
        try (ResultSet rs = m_CheckItemEaWhsExists.executeQuery()) {
            if (rs.next())
                res = rs.getInt("ejd_item_whs_id");
        }

        return res;
    }

    private int addEmeryItemEa(long vndId, AceItemBean newRec) throws SQLException {
        int res = 0;

        String flcId;
        int retPack = 1;

        int stockPack = newRec.getDealerPack();

        int shipUnitId = getShipUnitId(newRec.getUom());
        int retUnitId = getRetUnitId(newRec.getUom(), retPack);

        if (newRec.getMruInd().length() > 0 || (newRec.getRetail() < newRec.getCost()))
            retPack = newRec.getPackOf();

        // ebrownewell - 11/5/15: Per ticket 4439, any item that is nbc (according to line 941 any that have
        // IPUqty > 1 is nbc) and has retPack == stockPack set retPack = 1
        if (stockPack > 1 && stockPack == retPack)
            retPack = 1;

        flcId = getFlcId(newRec.getMdseClassCd(), newRec.getCmdtyGrp(), newRec.getProdGrp());
        newRec.setFlc(flcId);

        String itemId = getNewItemId(newRec.getAceSku());

        String desc = newRec.getDesc();

        if(desc.length() > 80){
            desc = desc.substring(0, 80);
        }

        m_AddItemEa.setString(1, itemId); // item id
        m_AddItemEa.setString(2, desc); // description
        m_AddItemEa.setLong(3, vndId); // vendor id
        m_AddItemEa.setInt(4, getVdhId(vndId)); // vdh id
        m_AddItemEa.setInt(5, stockPack); // buy multiple
        m_AddItemEa.setInt(6, shipUnitId);
        m_AddItemEa.setInt(7, retUnitId);
        m_AddItemEa.setInt(8, retPack);
        m_AddItemEa.setInt(9, newRec.getEjdItemId());
        m_AddItemEa.setInt(10, getNewTaxonomyId(newRec));

        m_AddItemEa.execute();

        try (ResultSet rs = m_AddItemEa.getResultSet()) {
            if (rs.next())
                res = rs.getInt(1);
        }

        return res;
    }

    private int getEjdItemIdIfExists(long xrefId) throws SQLException {
        int res = 0;

        m_CheckEjdItemIdExists.setLong(1, xrefId);

        try (ResultSet rs = m_CheckEjdItemIdExists.executeQuery()) {
            if (rs.next())
                res = rs.getInt("ejd_item_id");
        }

        return res;
    }

    private int insertEjdItem(AceItemBean newRec) throws SQLException {
        int res = 0;

        int shipUnitId = getShipUnitId(newRec.getUom());
        int retUnitId = getRetUnitId(newRec.getUom(), 1);

        double weight = 0.0;

        if (!doesItemHaveStockEntry(newRec.getEmerySku()))
            weight = newRec.getWeight();
        else
            weight = getWeight(newRec.getItemEaId());

        m_AddEjdItem.setDouble(1, weight);
        m_AddEjdItem.setInt(2, getStickerOpt(newRec.getUom(), shipUnitId, retUnitId, newRec.getBuyerCd()));
        m_AddEjdItem.setString(3, newRec.getflc());
        m_AddEjdItem.setInt(4, getDeptId("99"));
        m_AddEjdItem.setInt(5, getBrokenCaseId(newRec.isBrokenCase(), newRec.getDealerPack()));

        m_AddEjdItem.execute();

        try (ResultSet rs = m_AddEjdItem.getResultSet()) {
            if (rs.next())
                res = rs.getInt(1);
        }
        return res;
    }

    private int getItemEaIdIfExists(long xrefId) throws SQLException {
        int res = 0;

        m_GetItemEaIdIfExists.setLong(1, xrefId);
        try (ResultSet rs = m_GetItemEaIdIfExists.executeQuery()) {
            if (rs.next())
                res = rs.getInt("item_ea_id");
        }

        return res;
    }

    private boolean updateUpc(int ejdItemId, int whsId, String newUpc) throws SQLException {
        boolean rtn;

        m_CurProc = "[updateUpc]";

        m_updateUpc.setString(1, newUpc);
        m_updateUpc.setInt(2, ejdItemId);
        m_updateUpc.setInt(3, whsId);

        m_Log.debug(String.format("[DataModule]Updating upc for ejd item id %d and warehouse id %d to %s", ejdItemId,
                whsId, newUpc));

        int res = 0;
        try {
            res = m_updateUpc.executeUpdate();
        } catch (SQLException e) {
            m_Log.error(String.format("[DataModule]Failed to update upc for ejd item id %d and warehouse id %d", ejdItemId,
                    whsId));

            throw e;
        }

        if (res > 0)
            rtn = true;
        else {
            rtn = false;
            m_Log.debug(String.format("[DataModule]Failed to update upc for ejd item id %d and warehouse id %d", ejdItemId,
                    whsId));
        }

        return rtn;
    }

    /**
     * Adds an entry in the exception table.
     */
    private void addException(long rawId, AceItemBean itemRec, String msg) {
        try {
            m_AddEx.setLong(1, rawId);
            m_AddEx.setString(2, itemRec.getAceSku());
            m_AddEx.setString(3, msg);

            //
            // really need to add this back to a queue, but for now add it to the DB record.
            m_AddEx.setString(4, itemRec.getSourceData());

            m_AddEx.executeUpdate();
        } catch (SQLException ex) {
            m_Log.error("[AceItems]", ex);
        }
    }

    /**
     * Adds the item data to the raw data table.
     *
     * @param rec
     * @return The id of the inserted record.
     */
    private long addRawData(AceItemBean rec) {
        long id = 0;
        m_CurProc = "[addRawData]";

        try {
            m_AddRaw.setString(1, rec.getStatus());
            m_AddRaw.setString(2, rec.getAceSku());
            m_AddRaw.setString(3, rec.getDesc());
            m_AddRaw.setString(4, rec.getUpc());
            m_AddRaw.setString(5, rec.getVndSku());
            m_AddRaw.setString(6, Long.toString(rec.getVndId()));
            m_AddRaw.setString(7, rec.getVndName());
            m_AddRaw.setString(8, rec.getImageUrlSm());
            m_AddRaw.setString(9, rec.getImageUrlMd());
            m_AddRaw.setString(10, rec.getImageUrlLg());
            m_AddRaw.setInt(11, rec.isBrokenCase() ? 1 : 0);
            m_AddRaw.setInt(12, rec.getDealerPack());
            m_AddRaw.setInt(13, rec.getPackOf());
            m_AddRaw.setDouble(14, rec.getCost());
            m_AddRaw.setDouble(15, rec.getRetail());
            m_AddRaw.setDouble(16, rec.getLength());
            m_AddRaw.setDouble(17, rec.getWidth());
            m_AddRaw.setDouble(18, rec.getHeight());
            m_AddRaw.setDouble(19, rec.getWeight());
            m_AddRaw.setDouble(20, rec.getCube());
            m_AddRaw.setString(21, rec.getUom());
            m_AddRaw.setString(22, rec.getMdc());
            m_AddRaw.setString(23, rec.getNrha());
            m_AddRaw.setString(24, rec.getBrandName());
            m_AddRaw.setDouble(25, rec.getRetailA());
            m_AddRaw.setDouble(26, rec.getRetailB());
            m_AddRaw.setString(27, rec.getMdseClassCd());
            m_AddRaw.setString(28, rec.getProdGrp());
            m_AddRaw.setString(29, rec.getCmdtyGrp());
            m_AddRaw.setString(30, rec.getBuyerCd());
            m_AddRaw.setString(31, rec.getMruInd());
            m_AddRaw.setInt(32, (rec.getNoReturn() ? 1 : 0));
            m_AddRaw.setInt(33, rec.getCasePackQty());
            m_AddRaw.setInt(34, rec.getUomQty());
            m_AddRaw.setDouble(35, rec.getPriceUnitAmt());
            m_AddRaw.setString(36, rec.getOldMaterialNum());
            m_AddRaw.setString(37, rec.getNewMaterialNum());
            m_AddRaw.setString(38, rec.getVelocityUnitCd());
            m_AddRaw.setString(39, rec.getVelocityDollarCd());
            m_AddRaw.setString(40, rec.getDispositionCd());
            m_AddRaw.setString(41, rec.getSourceData());
            m_AddRaw.setString(42, rec.getRscCd());
            m_AddRaw.setInt(43, rec.isWhsOnly() ? 1 : 0);
            m_AddRaw.setString(44, rec.getDefPolicyCd());
            m_AddRaw.setInt(45, rec.getDcAllocMax());
            m_AddRaw.setDate(46, rec.getDcAllocEnd());
            m_AddRaw.setInt(47, rec.getCustAllocMax());
            m_AddRaw.setDate(48, rec.getCustAllocEnd());

            m_AddRaw.execute();

            try (ResultSet rs = m_AddRaw.getResultSet()) {
                if (rs.next())
                    id = rs.getInt(1);
            }
        } catch (SQLException e) {
            // we can just catch this exception, log it, and return a zero id
            // because we don't want the main try/catch block to grab this and try to roll back to the savepoint.

            try {
                m_Conn.rollback();
            } catch (SQLException e1) {
                m_Log.debug("Failed to rollback connection after adding raw data failed.");
            }

            addException(id, rec, e.getMessage());
        }

        return id;
    }

    /**
     * Close any open and allocated objects.
     */
    @Override
    public void close() {
        m_Prepared = false;

        closeStmt(m_AddBullet);
        closeStmt(m_AddEx);
        closeStmt(m_AddRaw);
        closeStmt(m_AddVndChange);
        closeStmt(m_DelItem);
        closeStmt(m_DelXRef);
        closeStmt(m_GetBCId);
        closeStmt(m_GetCurItemData);
        closeStmt(m_GetDeptId);
        closeStmt(m_GetDispId);
        closeStmt(m_GetFlcId);
        closeStmt(m_GetItemDisp);
        closeStmt(m_GetItemIdFromXref);
        closeStmt(m_GetItemType);
        closeStmt(m_GetItemTypeId);
        closeStmt(m_GetNewItem);
        closeStmt(m_GetVelCodeId);
        closeStmt(m_GetSell);
        closeStmt(m_GetShipUnitId);
        closeStmt(m_GetTaxonomyId);
        closeStmt(m_GetRetUnitId);
        closeStmt(m_GetRscId);
        closeStmt(m_GetVdhId);
        closeStmt(m_GetVndId);
        closeStmt(m_GetXref);
        closeStmt(m_GetXrefDelta);
        closeStmt(m_LogMsg);
        closeStmt(m_UpdItemVnd);
        closeStmt(m_UpdCatBullet);
        closeStmt(m_UpdPricing);
        closeStmt(m_UpdVndSku);
        closeStmt(m_updateUpc);
        closeStmt(m_DelBullet);
        closeStmt(m_GetItemEaIdIfExists);
        closeStmt(m_AddItemEa);
        closeStmt(m_CheckItemEaWhsExists);
        closeStmt(m_AddEjdItemWhs);
        closeStmt(m_AddEjdItemUpc);
        closeStmt(m_CheckEjdItemUpcExists);
        closeStmt(m_AddWebItemEa);
        closeStmt(m_CheckWebItemEa);
        closeStmt(m_CheckVendorEaSkuCrossExists);
        closeStmt(m_AddVendorEaSkuCross);
        closeStmt(m_AddEjdItem);
        closeStmt(m_GetItemIdByItemEaId);
        closeStmt(m_AddXRef);
        closeStmt(m_CheckVndSku);
        closeStmt(m_UpdCatItem);
        closeStmt(m_updItemEa);
        closeStmt(m_updEjdItem);
        closeStmt(m_updItemWhs);
        closeStmt(m_CheckEjdItemIdExists);
        closeStmt(m_UpdTaxonomy);
        closeStmt(m_GetAceMerchTaxonomy);
        closeStmt(m_GetAceProductTaxonomy);
        closeStmt(m_CheckItemHasStock);
        closeStmt(m_getWeight);

        m_AddBullet = null;
        m_AddEx = null;
        m_AddRaw = null;
        m_AddVndChange = null;
        m_DelItem = null;
        m_DelXRef = null;
        m_GetBCId = null;
        m_GetCurItemData = null;
        m_GetDeptId = null;
        m_GetDispId = null;
        m_GetFlcId = null;
        m_GetItemDisp = null;
        m_GetItemType = null;
        m_GetItemTypeId = null;
        m_GetNewItem = null;
        m_GetShipUnitId = null;
        m_GetTaxonomyId = null;
        m_GetRetUnitId = null;
        m_GetRscId = null;
        m_GetSell = null;
        m_GetVelCodeId = null;
        m_GetVdhId = null;
        m_GetVndId = null;
        m_GetXref = null;
        m_GetXrefDelta = null;
        m_LogMsg = null;
        m_UpdCatBullet = null;
        m_UpdItemVnd = null;
        m_UpdPricing = null;
        m_UpdVndSku = null;
        m_updateUpc = null;
        m_DelBullet = null;
        m_GetItemEaIdIfExists = null;
        m_AddItemEa = null;
        m_CheckItemEaWhsExists = null;
        m_AddEjdItemWhs = null;
        m_AddEjdItemUpc = null;
        m_CheckEjdItemUpcExists = null;
        m_AddWebItemEa = null;
        m_CheckWebItemEa = null;
        m_CheckVendorEaSkuCrossExists = null;
        m_AddVendorEaSkuCross = null;
        m_AddEjdItem = null;
        m_GetItemIdByItemEaId = null;
        m_AddXRef = null;
        m_CheckVndSku = null;
        m_UpdCatItem = null;
        m_updItemEa = null;
        m_updEjdItem = null;
        m_getWeight = null;
        m_updItemWhs = null;
        m_CheckEjdItemIdExists = null;
        m_UpdTaxonomy = null;
        m_GetAceProductTaxonomy = null;
        m_GetAceMerchTaxonomy = null;
        m_CheckItemHasStock = null;

        m_Prepared = false;
    }

    /**
     * Closes a resultset.
     *
     * @param rs
     */
    private void closeRs(ResultSet rs) {
        if (rs != null)
            try {
                rs.close();
            } catch (SQLException ex) {

            }
    }

    /**
     * Closes a statement.
     *
     * @param stmt
     */
    private void closeStmt(Statement stmt) {
        if (stmt != null)
            try {
                stmt.close();
            } catch (SQLException ex) {

            }
    }

    /**
     * Deletes the emery item and the ace cross reference item.
     *
     * @param rec
     */
    private void deleteItem(AceItemBean rec) throws SQLException {
        m_CurProc = "[deleteItem]";
        m_DelItem.setString(1, rec.getAceSku());
        m_DelItem.setString(2, rec.getRscCd());
        m_DelItem.execute();
    }

    /**
     * Deletes each item out of the cross reference.
     *
     * @param list
     */
    public void delXRef(ArrayList<String> list) {
        String[] tmp;
        String aceSku;
        String itemId;
        Iterator<String> iter;

        if (list != null) {
            iter = list.listIterator();

            while (iter.hasNext())
                try {
                    tmp = iter.next().split("[,]");
                    aceSku = tmp[0];
                    itemId = tmp[1];

                    m_DelXRef.setString(1, aceSku);
                    m_DelXRef.setString(2, itemId);
                    m_DelXRef.executeUpdate();

                    m_Conn.commit();
                } catch (SQLException ex) {

                }
        }
    }

    /**
     * Gets the broken case id.
     *
     * @param canBreak
     * @param ipuQty
     * @return The item type id for type.
     * @throws SQLException
     */
    private int getBrokenCaseId(boolean canBreak, int ipuQty) throws SQLException {
        int id = 0;
        ResultSet rs = null;
        String caseDesc = "ALLOW BROKEN CASES";

        //
        // Currently, ACE says the broken case flag shouldn't be used;
        // only use the IPU quantity. IPU > 0 = no broken case.
        // jf 01/12/2015
        if (ipuQty > 1)
            caseDesc = "ROUND DOWN THEN ROUND UP";

        try {
            m_GetBCId.setString(1, caseDesc);
            rs = m_GetBCId.executeQuery();

            if (rs.next())
                id = rs.getInt(1);
        } finally {
            closeRs(rs);
            rs = null;
        }

        return id;
    }

    /**
     * @param aceSku
     * @param whsId
     * @return
     * @throws SQLException
     */
    private AceItemBean getCurItemData(String aceSku, int whsId) throws SQLException {
        AceItemBean data = null;
        ResultSet rs = null;
        int count = 1;
        m_CurProc = "[getCurItemData] [attr]";

        try {
            m_GetCurItemData.setInt(1, whsId);
            m_GetCurItemData.setString(2, aceSku);
            rs = m_GetCurItemData.executeQuery();

            //
            // There may be multiple bullet points which causes more than one
            // row to be returned. Just add the core data once and add subsequent
            // bullets as we iterate through.
            while (rs.next()) {
                if (count == 1) {
                    data = new AceItemBean();
                    data.setAceSku(aceSku);
                    data.setDesc(rs.getString("description"));

                    //
                    // Ace items that are stocked and setup by Emery may not have an ace vendor in the
                    // cross reference.
                    String aceVndId = rs.getString("ace_vnd_id");
                    data.setAceVndId(aceVndId != null ? Long.parseLong(aceVndId) : -1);

                    data.setVndId(rs.getInt("vendor_id"));
                    data.setVndName(rs.getString("name"));
                    data.setVndSku(rs.getString("vendor_item_num"));
                    data.setLength(rs.getDouble("length"));
                    data.setWidth(rs.getDouble("width"));
                    data.setHeight(rs.getDouble("height"));
                    data.setWeight(rs.getDouble("weight"));
                    data.setCube(rs.getDouble("cube"));
                    data.setDept(rs.getString("dept_num"));
                    data.setMdseClassCd(rs.getString("mdse_class_cd"));
                    data.setCmdtyGrp(rs.getString("cmdty_group"));
                    data.setProdGrp(rs.getString("product_group"));
                    data.setBrandName(rs.getString("brand_name"));
                    data.setImageUrlSm(rs.getString("img_url_sm"));
                    data.setImageUrlMd(rs.getString("img_url_md"));
                    data.setImageUrlLg(rs.getString("img_url_lg"));
                    data.setUpc(rs.getString("upc_code"));
                    data.setPackOf(rs.getInt("retail_pack"));
                    data.setDealerPack(rs.getInt("stock_pack"));
                    data.setEjdItemWhsId(rs.getInt("ejd_item_whs_id"));
                    data.setTaxonomyId(rs.getInt("taxonomy_id"));
                    data.setCasePackQty(rs.getInt("case_pack_qty"));

                    data.setEmeryStocked(getItemType(data.getAceSku()).equals("STOCK"));
                }

                data.getBullets().add(rs.getString("bullet_point"));
                count++;
            }

            closeRs(rs);

        } finally {
            closeRs(rs);
            rs = null;
        }

        return data;
    }

    /**
     * Gets the emery department. Currently department 99 but may be based on an ace/emery cross reference.
     *
     * @param deptNum - The department number to get the id for.
     * @return The department id.
     * @throws SQLException
     */
    private int getDeptId(String deptNum) throws SQLException {
        int id = 0;
        ResultSet rs = null;

        try {
            m_GetDeptId.setString(1, deptNum);
            rs = m_GetDeptId.executeQuery();

            if (rs.next())
                id = rs.getInt(1);
        } finally {
            closeRs(rs);
            rs = null;
        }

        return id;
    }

    /**
     * Gets the emery flc id based on the ace classification data.
     *
     * @param mdseClass - ACE merchandise class code
     * @param cmdtyGrp  - ACE commodity group
     * @param prodGrp   - ACE product group
     * @return The emery item number if there is one.
     * @throws SQLException
     */
    private String getFlcId(String mdseClass, String cmdtyGrp, String prodGrp) throws SQLException {
        String flcId = "9998";
        ResultSet rs = null;

        try {
            m_GetFlcId.setString(1, mdseClass);
            m_GetFlcId.setString(2, cmdtyGrp);
            m_GetFlcId.setString(3, prodGrp);
            rs = m_GetFlcId.executeQuery();

            if (rs.next())
                flcId = rs.getString(1);
        } finally {
            closeRs(rs);
            rs = null;
        }

        return flcId;
    }

    /**
     * Gets the internal DB record id for the RSC based on the SAP site code in the recs rscCode.
     *
     * @param rscCode The SAP site code sent in teh XML from ACE.
     * @return Internal pk id for the db record.
     * @throws SQLException
     */
    private int getInternalRscId(String rscCode) throws SQLException {
        int rscId = 11;
        ResultSet rs = null;

        try {
            m_GetRscId.setString(1, rscCode);
            rs = m_GetRscId.executeQuery();

            if (rs.next())
                rscId = rs.getInt(1);
        } finally {
            closeRs(rs);
            rs = null;
        }

        return rscId;
    }

    /**
     * Checks the ace_item_xref table for the existance of an item number already
     * associated with an ace sku.
     *
     * @param aceSku The current ace article number.
     * @return The item_id in ace_item_xref if it exists.
     * @throws SQLException
     */
    private String getItemIdFromXRef(String aceSku) throws SQLException {
        String itemId = null;
        ResultSet rs = null;
        m_CurProc = "[getItemIdFromXref]";

        m_GetItemIdFromXref.setString(1, aceSku);
        rs = m_GetItemIdFromXref.executeQuery();

        if (rs.next())
            itemId = rs.getString(1);

        return itemId;
    }

    /**
     * Gets the emery item type
     *
     * @param itemId the Emery item number
     * @return The item type for the item
     * @throws SQLException
     */
    private String getItemType(String itemId) throws SQLException {
        String type = "";
        ResultSet rs = null;
        m_CurProc = "[getItemType]";

        try {
            m_GetItemType.setString(1, itemId);
            rs = m_GetItemType.executeQuery();

            if (rs.next())
                type = rs.getString(1);
        } finally {
            closeRs(rs);
            rs = null;
        }

        return type;
    }

    /**
     * Gets a new item number from the system when creating new items.
     *
     * @return The new item id.
     * @throws SQLException
     */
    private String getNewItemId(String aceSku) throws SQLException {
        String itemId = null;
        ResultSet rs = null;

        itemId = getItemIdFromXRef(aceSku);

        if (itemId == null) {
            aceSku = String.format("%07d", Integer.parseInt(aceSku));

            if (checkAceSkuExistsAsItemId(aceSku))
                try {
                    rs = m_GetNewItem.executeQuery();

                    if (rs.next())
                        itemId = rs.getString(1);
                } finally {
                    closeRs(rs);
                }
            else
                itemId = aceSku;
        }

        return itemId;
    }

    private boolean checkAceSkuExistsAsItemId(String aceSku) throws SQLException {
        boolean res;

        String sql = "SELECT * FROM item_entity_attr WHERE item_id = ?";

        try (PreparedStatement stmt = m_Conn.prepareStatement(sql)) {
            stmt.setString(1, aceSku);
            try (ResultSet rs = stmt.executeQuery()) {
                res = rs.next();
            }
        }

        return res;
    }

    /**
     * Gets the retail unit id.
     *
     * @param uom the ace uom
     * @return The id for uom.
     * @throws SQLException
     */
    private int getRetUnitId(String uom, int retPack) throws SQLException {
        int id = 12; // default this to the value for EA in the system.
        ResultSet rs = null;

        try {
            if (retPack == 1)
                if (uom.equalsIgnoreCase("spool") || uom.equalsIgnoreCase("reel"))
                    uom = "FT";

            m_GetRetUnitId.setString(1, uom);
            rs = m_GetRetUnitId.executeQuery();

            if (rs.next())
                id = rs.getInt(1);
            else
                logWarn("ace uom not found", uom);
            // TODO - Add some notification or other processing to show the Ace UOM.
        } finally {
            closeRs(rs);
            rs = null;
        }

        return id;
    }

    /**
     * Gets the shipping unit id.
     *
     * @param uom the ace uom
     * @return The id for uom.
     * @throws SQLException
     */
    private int getShipUnitId(String uom) throws SQLException {
        int id = 12; // default this to the value for EA in the system.
        ResultSet rs = null;

        try {
            m_GetShipUnitId.setString(1, uom);
            rs = m_GetShipUnitId.executeQuery();

            if (rs.next())
                id = rs.getInt(1);
            else
                logWarn("ace uom not found", uom);
            // TODO - Add some notification or other processing to show the Ace UOM.
        } finally {
            closeRs(rs);
            rs = null;
        }

        return id;
    }

    /**
     * @throws SQLException
     */
    private int getStickerOpt(String uom, int shipUnitId, int retUnitId, String buyerCode) throws SQLException {
        int res = 0;
        boolean isDspOrAst = false;

        if (buyerCode == null)
            buyerCode = "";

        PreparedStatement shipUnitStmt = m_Conn.prepareStatement("SELECT unit FROM ship_unit WHERE unit_id = ?");

        shipUnitStmt.setInt(1, shipUnitId);

        try (ResultSet rs = shipUnitStmt.executeQuery()) {
            if (rs.next())
                if (rs.getString("unit").equalsIgnoreCase("AST") || rs.getString("unit").equalsIgnoreCase("DSP"))
                    isDspOrAst = true;
        }

        // If the unit of measure is feet it should always be ONE.
        if (uom.equalsIgnoreCase("FT"))
            res = 1;
        else if (buyerCode.equalsIgnoreCase("09M"))
            res = 0;
        else if (isDspOrAst) {
            PreparedStatement RetUnitStmt = m_Conn
                    .prepareStatement("SELECT unit FROM retail_unit WHERE unit_id = ?");

            RetUnitStmt.setInt(1, retUnitId);

            try (ResultSet rs = RetUnitStmt.executeQuery()) {
                if (rs.next())
                    if (rs.getString("unit").equalsIgnoreCase("AST") || rs.getString("unit").equalsIgnoreCase("DSP"))
                        res = 1;// if both ship unit and retail unit are display or assortment we want ONE.
                    else
                        res = 2; // otherwise we want MANY
            }

            RetUnitStmt.close();

        } else if (!isDspOrAst)
            res = 2; // if the item is not display or assortment then we want MANY

        shipUnitStmt.close();

        return res;
    }

    private int getNewTaxonomyId(AceItemBean rec) {
        int id = -1;

        ResultSet rs = null;

        try {
            if (rec.getProdGrp() != null && !rec.getProdGrp().trim().equals("")) {
                m_GetAceProductTaxonomy.setString(1, rec.getProdGrp());
                m_GetAceProductTaxonomy.setString(2, rec.getMdseClassCd());

                rs = m_GetAceProductTaxonomy.executeQuery();
            } else {
                m_GetAceMerchTaxonomy.setString(1, rec.getMdseClassCd());

                rs = m_GetAceMerchTaxonomy.executeQuery();
            }

            if (rs.next())
                id = rs.getInt("taxonomy_id");

            rs.close();

            if (id == -1)
                id = 2894; // misc taxonomy id
        } catch (SQLException ex) {
            m_Log.debug("Unable to get new taxonomy ID. ", ex);
        } finally {
            if (rs != null)
                try {
                    rs.close();
                } catch (SQLException ignored) {
                }
        }
        return id;
    }

    /**
     * Gets the default velocity code
     *
     * @return The velocity code id.
     * @throws SQLException
     */
    private int getVelocityCodeId() throws SQLException {
        int id = 0;
        ResultSet rs = null;

        try {
            rs = m_GetVelCodeId.executeQuery();

            if (rs.next())
                id = rs.getInt(1);
        } finally {
            closeRs(rs);
        }

        return id;
    }

    /**
     * Gets the Emery vendor ID from cross reference.
     *
     * @param aceVndId The ace vendor id.
     * @return The emery vendor id if found, 0 if not.
     * @throws SQLException ex
     */
    private long getVendorId(long aceVndId) throws SQLException {
        long id = 0;
        ResultSet rs = null;

        try {
            m_GetVndId.setLong(1, aceVndId);
            rs = m_GetVndId.executeQuery();

            if (rs.next())
                id = rs.getLong(1);
        } finally {
            closeRs(rs);
        }

        return id;
    }

    /**
     * Gets the warehouse id based on the internal ace rsc..
     *
     * @param rscId The ace rsc id.
     * @return The emery warehouse ID if found. 0 if not.
     * @throws SQLException ex
     */
    private int getWarehouseId(int rscId) throws SQLException {
        int res = 0;

        m_GetWarehouseId.setInt(1, rscId);

        try (ResultSet rs = m_GetWarehouseId.executeQuery()) {
            if (rs.next())
                res = rs.getInt("warehouse_id");
        }

        return res;
    }

    /**
     * Gets the Emery vdh_id from the vendor record.
     *
     * @param vndId The emery vendor id.
     * @return The emery vendor defect handling ID based on the Emery vendor.
     * @throws SQLException ex
     */
    private int getVdhId(long vndId) throws SQLException {
        int id = 0;
        ResultSet rs = null;

        try {
            m_GetVdhId.setLong(1, vndId);
            rs = m_GetVdhId.executeQuery();

            if (rs.next())
                id = rs.getInt(1);
        } finally {
            closeRs(rs);
        }

        return id;
    }

    /**
     * Logs an error message to the process_log table.
     *
     * @param msg1   msg1
     * @param msg2   msg2
     * @param field1 field1
     * @param field2 field2
     */
    public void logError(String msg1, String msg2, String field1, String field2) {
        try {
            m_LogMsg.setString(1, "error");
            m_LogMsg.setString(2, msg1);
            m_LogMsg.setString(3, msg2);
            m_LogMsg.setString(4, field1);
            m_LogMsg.setString(5, field2);

            m_LogMsg.executeUpdate();
        } catch (SQLException ex) {
            m_Log.error("[AceItems] logError", ex);
        }
    }

    /**
     * Logs an info message to the process_log table.
     *
     * @param msg1   msg1
     * @param field1 field1
     * @throws SQLException ex
     */
    public void logInfo(String msg1, String field1) throws SQLException {
        logInfo(msg1, "", field1, "");
    }

    /**
     * Logs an info message to the process_log table.
     *
     * @param msg1   msg1
     * @param msg2   msg1
     * @param field1 field1
     * @param field2 field2
     */
    public void logInfo(String msg1, String msg2, String field1, String field2) {
        try {
            m_LogMsg.setString(1, "info");
            m_LogMsg.setString(2, msg1);
            m_LogMsg.setString(3, msg2);
            m_LogMsg.setString(4, field1);
            m_LogMsg.setString(5, field2);

            m_LogMsg.executeUpdate();
        } catch (SQLException ex) {
            m_Log.error("[AceItems] logInfo", ex);
        }
    }

    /**
     * @param msg1   msg1
     * @param field1 field1
     */
    public void logWarn(String msg1, String field1) {
        logWarn(msg1, "", field1, "");
    }

    /**
     * Logs a warning message to the process_log table.
     *
     * @param msg1   msg1
     * @param field1 field1
     */
    public void logWarn(String msg1, String msg2, String field1, String field2) {
        try {
            m_LogMsg.setString(1, "warn");
            m_LogMsg.setString(2, msg1);
            m_LogMsg.setString(3, msg2);
            m_LogMsg.setString(4, field1);
            m_LogMsg.setString(5, field2);

            m_LogMsg.executeUpdate();
        } catch (SQLException ex) {
            m_Log.error("[AceItems] logWarn", ex);
        }
    }

    /**
     * Prepares the SQL queries.
     *
     * @throws SQLException ex
     */
    private boolean prepareStatements() {
        StringBuilder sql = new StringBuilder();

        if (m_Conn != null)
            try {
                sql.setLength(0);
                sql.append("insert into web_item_ea_bullet (item_ea_id, bullet_point, seq_nbr) ");
                sql.append("values (?, ?, ?)");
                m_AddBullet = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("insert into ace_item_raw ( ");
                sql.append("status, sku, description, upc, vendor_sku, ");
                sql.append("vendor_id, vendor_name, image_url_sm, image_url_md, image_url_lg, ");
                sql.append("broken_case, dealer_pack, pack_of, cost, retail, ");
                sql.append("length, width, height, weight, cube, ");
                sql.append("uom, mdc, nrha, brand_name, retail_a, ");
                sql.append("retail_b, mdse_class_cd, product_group, cmdty_group, buyer_cd,");
                sql.append("mru_ind, no_return, case_pack_qty, uom_qty, price_unit_amt, ");
                sql.append("old_ace_sku, new_ace_sku, velocity_unit_cd, velocity_dollar_cd, status_code, ");
                sql.append("src_data, rsc, wholesale_only, def_policy_cd, dc_alloc_max, ");
                sql.append("dc_alloc_end, cust_alloc_max, cust_alloc_end) ");
                sql.append("values (");
                sql.append("?,?,?,?,?,?,?,?,?,?,");
                sql.append("?,?,?,?,?,?,?,?,?,?,");
                sql.append("?,?,?,?,?,?,?,?,?,?,");
                sql.append("?,?,?,?,?,?,?,?,?,?,");
                sql.append("?,?,?,?,?,?,?,?) ");
                sql.append("returning air_id ");
                m_AddRaw = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("insert into ace_item_exception (air_id, sku, message, src_data) ");
                sql.append("values(?,?,?,?)");
                m_AddEx = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("insert into ace_vendor_change (ace_vnd_id, vendor_id, name, vnd_sku) ");
                sql.append("values(?,?,?,?)");
                m_AddVndChange = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select delete_ace_item(?, ?) ");
                m_DelItem = m_Conn.prepareCall(sql.toString());

                sql.setLength(0);
                sql.append("delete ace_xref_change where ace_sku = ? and item_id = ?");
                m_DelXRef = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select broken_case_id from broken_case ");
                sql.append("where description = ?");
                m_GetBCId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("SELECT ");
                sql.append("ace_item_xref.ace_sku, ");
                sql.append("ace_item_xref.item_id, ");
                sql.append("ejd_item_warehouse.ejd_item_whs_id, ");
                sql.append("ace_item_xref.dept_num, ");
                sql.append("ejd_item.weight, ");
                sql.append("ejd_item_warehouse.length, ");
                sql.append("ejd_item_warehouse.width, ");
                sql.append("ejd_item_warehouse.height, ");
                sql.append("ejd_item_warehouse.cube, ");
                sql.append("ace_item_xref.mdse_class_cd, ");
                sql.append("ace_item_xref.product_group, ");
                sql.append("ace_item_xref.cmdty_group, ");
                sql.append("ace_item_xref.buyer_cd, ");
                sql.append("ace_item_xref.mru_ind, ");
                sql.append("ace_item_xref.no_return, ");
                sql.append("ace_item_xref.wholesale_only, ");
                sql.append("item_entity_attr.description, ");
                sql.append("ace_vnd_xref.ace_vnd_id, ");
                sql.append("vendor.vendor_id, ");
                sql.append("vendor.name, ");
                sql.append("vendor_item_ea_cross.vendor_item_num, ");
                sql.append("web_item_ea.brand_name, ");
                sql.append("web_item_ea.img_url_sm, ");
                sql.append("web_item_ea.img_url_md, ");
                sql.append("web_item_ea.img_url_lg, ");
                sql.append("web_item_ea_bullet.bullet_point, ");
                sql.append("ejd_item_whs_upc.upc_code, ");
                sql.append("stock_pack, ");
                sql.append("retail_pack, ");
                sql.append("item_entity_attr.taxonomy_id, ");
                sql.append("case_pack_qty ");
                sql.append("FROM ace_item_xref ");
                sql.append("JOIN item_entity_attr ON item_entity_attr.item_id = ace_item_xref.item_id AND item_type_id = 8 ");
                sql.append("join ejd_item on ejd_item.ejd_item_id = item_entity_attr.ejd_item_id ");
                sql.append("JOIN ejd_item_warehouse ");
                sql.append("ON ejd_item_warehouse.ejd_item_id = item_entity_attr.ejd_item_id AND ejd_item_warehouse.warehouse_id = ? ");
                sql.append("JOIN vendor ON vendor.vendor_id = item_entity_attr.vendor_id ");
                sql.append("JOIN ace_vnd_xref ON ace_vnd_xref.vendor_id = item_entity_attr.vendor_id ");
                sql.append("LEFT OUTER JOIN web_item_ea ON web_item_ea.item_ea_id = item_entity_attr.item_ea_id ");
                sql.append("LEFT OUTER JOIN web_item_ea_bullet ON web_item_ea_bullet.item_ea_id = item_entity_attr.item_ea_id ");
                sql.append("JOIN ejd_item_whs_upc ON ejd_item_whs_upc.ejd_item_id = ejd_item.ejd_item_id ");
                sql.append("and ejd_item_whs_upc.warehouse_id = ejd_item_warehouse.warehouse_id AND primary_upc = 1 ");
                sql.append("JOIN vendor_item_ea_cross ON vendor_item_ea_cross.vendor_id = item_entity_attr.vendor_id AND ");
                sql.append("vendor_item_ea_cross.item_ea_id = item_entity_attr.item_ea_id ");
                sql.append("WHERE ace_sku = ? ");
                m_GetCurItemData = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("insert into ace_item_xref (ace_sku, item_id, dept_num, mdse_class_cd, product_group, cmdty_group, buyer_cd, mru_ind, no_return, wholesale_only, no_resale) ");
                sql.append("values (?,?,?,?,?,?,?,?,?,?,?) ");
                sql.append("returning ace_xref_id ");
                m_AddXRef = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select dept_id from emery_dept ");
                sql.append("where dept_num = ?");
                m_GetDeptId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select disp_id from item_disp ");
                sql.append("where disposition = ?");
                m_GetDispId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select flc_id from ace_class_xref ");
                sql.append("where mdse_class_cd = ? and cmdty_group = ? and prod_group = ?");
                m_GetFlcId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select disposition from ejd_item_warehouse ");
                sql.append("join item_entity_attr using(ejd_item_id) ");
                sql.append("join item_disp using (disp_id) ");
                sql.append("where item_id = ? and warehouse_id = ?");
                m_GetItemDisp = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select item_id ");
                sql.append("from ace_item_xref ");
                sql.append("where ace_sku = ? ");
                m_GetItemIdFromXref = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select itemtype ");
                sql.append("from item_entity_attr ");
                sql.append("join item_type on item_type.item_type_id = item_entity_attr.item_type_id ");
                sql.append("where item_entity_attr.item_id = ? ");
                m_GetItemType = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select item_type_id from item_type ");
                sql.append("where itemtype = ?");
                m_GetItemTypeId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select getNextItemId()");
                m_GetNewItem = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select unit_id from retail_unit where unit = ?");
                m_GetRetUnitId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select ace_rsc_id from ace_rsc where sap_site_cd = ?");
                m_GetRscId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select warehouse_id from warehouse where ace_rsc_id = ?");
                m_GetWarehouseId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select ace_margin_price.get_base_sell(?, ?) from dual");
                m_GetSell = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select unit_id from ship_unit where unit = ?");
                m_GetShipUnitId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select velocity_id from item_velocity where velocity = 'E'");
                m_GetVelCodeId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select vdh_id from vendor where vendor_id = ?");
                m_GetVdhId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select vendor_id from ace_vnd_xref where ace_vnd_id = ?");
                m_GetVndId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select item_id, ace_sku from ace_xref_change");
                m_GetXref = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select item_id, ace_sku from ace_xref_change where change_date >= trunc(?)");
                m_GetXrefDelta = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select ace_xref_id from ace_item_xref where ace_sku = ?");
                m_GetXRefId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("insert into process_log(proc_name, msg_type, msg1, msg2, field1, field2)");
                sql.append("values('aceitems', ?, ?, ?, ?, ?)");
                m_LogMsg = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("delete bmi_item_bullet ");
                sql.append("where item_id = ?");
                m_UpdCatBullet = m_Conn.prepareCall(sql.toString());

                sql.setLength(0);
                sql.append("update web_item_ea ");
                sql.append("set brand_name = ?, img_url_sm = ?, img_url_md = ?, img_url_lg = ? ");
                sql.append("where item_ea_id = ?");
                m_UpdCatItem = m_Conn.prepareCall(sql.toString());

                sql.setLength(0);
                sql.append("update item_entity_attr set description = ?, retail_pack = ? ");
                sql.append("where item_ea_id = ? ");
                m_updItemEa = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("update ejd_item set weight = ? ");
                sql.append("where ejd_item_id in (select ejd_item_id from item_entity_attr where item_ea_id = ?)");
                m_updEjdItem = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append(
                        "select weight from ejd_item where ejd_item_id in (select ejd_item_id from item_entity_attr where item_ea_id = ?)");
                m_getWeight = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("update ejd_item_warehouse set stock_pack = ?, case_pack_qty = ? ");
                sql.append("where ejd_item_id = ? and warehouse_id = ? ");
                m_updItemWhs = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("   update item_entity_attr ");
                sql.append("   set vendor_id = ? ");
                sql.append("   where item_ea_id = ?");
                m_UpdItemVnd = m_Conn.prepareCall(sql.toString());

                sql.setLength(0);
                sql.append("select vendor_item_num from vendor_item_cross ");
                sql.append("where vendor_id = ? and vendor_item_num = ?");
                m_CheckVndSku = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("update vendor_item_ea_cross ");
                sql.append("set vendor_item_num = ? ");
                sql.append("where item_ea_id = ? and vendor_id = ?");
                m_UpdVndSku = m_Conn.prepareCall(sql.toString());

                //
                // itemId varchar2, aceSku varchar2, rscId number, regCost number, tmpCost number,
                // tmpStart date, tmpEnd date, retailA number, retailB number, retail number
                sql.setLength(0);
                sql.append(
                        "select * from ace_margin_price.update_price(?,?,?,?::numeric,?::numeric,?,?,?::numeric,?::numeric,?::numeric) ");
                m_UpdPricing = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append(
                        "update ejd_item_whs_upc set upc_code = ? where ejd_item_id = ? and warehouse_id = ? and primary_upc = 1");
                m_updateUpc = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("delete web_item_ea_bullet where item_ea_id = ?");
                m_DelBullet = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append(
                        "select * from item_entity_attr where item_type_id = 8 and item_id in (select distinct item_id from ace_item_xref where ace_xref_id = ?)");
                m_GetItemEaIdIfExists = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append(
                        "insert into item_entity_attr (item_id, description, item_type_id, vendor_id, vdh_id, buy_mult, ship_unit_id,");
                sql.append("ret_unit_id, retail_pack, vendor_guaranteed, ejd_item_id, taxonomy_id) ");
                sql.append("values (?, ?, 8, ?, ?, ?, ?, ?, ?, 0, ?, ?) ");
                sql.append("returning item_ea_id");
                m_AddItemEa = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select * from ejd_item_warehouse where ejd_item_id = ? and warehouse_id = ?");
                m_CheckItemEaWhsExists = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append(
                        "insert into ejd_item_warehouse (ejd_item_id, warehouse_id, can_stock, stock_pack, can_plan, active, ");
                sql.append("active_begin, in_catalog, disp_id, velocity_id, update_user, length, width, height, cube) ");
                sql.append("VALUES (?,?,0,?,0,1, trunc(now()), 0, 1, ?, 'ACE LOAD', ?, ?, ?, ?) "); // disp ids: 1 -
                // buy/sell, 5 - review
                sql.append("returning ejd_item_whs_id");
                m_AddEjdItemWhs = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("insert into ejd_item_whs_upc (upc_code, primary_upc, ejd_item_id, warehouse_id) ");
                sql.append("VALUES (?, 1, ?, ?) ");
                sql.append("returning ejd_upc_id");
                m_AddEjdItemUpc = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select * from ejd_item_whs_upc where ejd_item_id = ? and warehouse_id = ?");
                m_CheckEjdItemUpcExists = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append(
                        "insert into web_item_ea(item_ea_id, vendor_sku, brand_name, noun, modifier, img_url_sm, img_url_md, img_url_lg, web_descr) ");
                sql.append("VALUES (?, ?, ?, '', '', ?, ?, ?, ?)");
                m_AddWebItemEa = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select * from web_item_ea where item_ea_id = ?");
                m_CheckWebItemEa = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select * from vendor_item_ea_cross where vendor_id = ? and vendor_item_num = ?");
                m_CheckVendorEaSkuCrossExists = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("insert into vendor_item_ea_cross (vendor_id, item_ea_id, vendor_item_num) ");
                sql.append("VALUES (?, ?, ?)");
                m_AddVendorEaSkuCross = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("insert into ejd_item (weight, pallet_qty, stickers, flc_id, dept_id, broken_case_id) ");
                sql.append("VALUES (?, 0, ?, ?, ?, ?) ");
                sql.append("returning ejd_item_id");
                m_AddEjdItem = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select item_id from item_entity_attr where item_ea_id = ?");
                m_GetItemIdByItemEaId = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select distinct ejd_item_id from ejd_item ");
                sql.append("join item_entity_attr using (ejd_item_id) ");
                sql.append("where item_id in (select distinct item_id from ace_item_xref where ace_xref_id = ?) ");
                m_CheckEjdItemIdExists = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("update item_entity_attr set taxonomy_id = ? where item_ea_id = ?");
                m_UpdTaxonomy = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append(
                        "select taxonomy_id from ace_product_taxonomy where ace_product_id = ? and ace_merchandise_id = ?");
                m_GetAceProductTaxonomy = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select taxonomy_id from ace_merchandise_taxonomy where ace_merchandise_id = ?");
                m_GetAceMerchTaxonomy = m_Conn.prepareStatement(sql.toString());

                sql.setLength(0);
                sql.append("select * from item_entity_attr where item_type_id = 1 and item_id = ?");
                m_CheckItemHasStock = m_Conn.prepareStatement(sql.toString());

                m_Prepared = true;
            } catch (Exception ex) {
                m_Prepared = false;
                m_Log.error("[AceItems]", ex);
            }

        return m_Prepared;
    }

    private boolean doesItemHaveStockEntry(String itemId) throws SQLException {
        boolean res;

        m_CheckItemHasStock.setString(1, itemId);

        try (ResultSet rs = m_CheckItemHasStock.executeQuery()) {
            res = rs.next();
        }

        return res;
    }

    /**
     * Sets the database connection and prepares the statements. Need to close and prepare again if the connection has
     * changed.
     *
     * @param conn connection
     */
    public void setConnection(Connection conn) {
        m_Conn = conn;

        //
        // Have to turn auto commit off, it's on by default.
        try {
            m_Conn.setAutoCommit(false);
        } catch (Exception ignored) {

        }

        if (m_Prepared)
            close();

        prepareStatements();
    }

    /**
     * Set the logger.
     *
     * @param log log
     */
    public void setLogger(Logger log) {
        m_Log = log;
    }

    /**
     * @param id  raw data record id
     * @param rec rec
     */
    private void updateItemEa(long id, AceItemBean rec) {
        String errMsg = "[updateItemEa] item id = %s, ace item id = %s error is %s";
        int retPack = 1;
        int stockPack = rec.getDealerPack();

        if (rec.getMruInd().length() > 0 || (rec.getRetail() < rec.getCost()))
            retPack = rec.getPackOf();

        // ebrownewell - 11/5/15: Per ticket 4439, any item that is nbc (according to line 941 any that have
        // IPUqty > 1 is nbc) and has retPack == stockPack set retPack = 1
        if (stockPack > 1 && stockPack == retPack)
            retPack = 1;

        String desc = rec.getDesc();

        if(desc.length() > 80){
            desc = desc.substring(0, 80);
        }

        try {
            m_updItemEa.setString(1, desc);
            m_updItemEa.setInt(2, retPack);
            m_updItemEa.setInt(3, rec.getItemEaId());
            m_updItemEa.executeUpdate();
        } catch (SQLException ex) {
            addException(id, rec, String.format(errMsg, rec.getAceSku(), rec.getAceSku(), ex.getMessage()));
        }
    }

    /**
     * @param id  raw data record id
     * @param rec item bean
     */
    private void updateEjdItem(long id, AceItemBean rec) {
        String errMsg = "[updateEjdItem] item id = %s, ace item id = %s error is %s";

        try {
            double weight;

            if (!doesItemHaveStockEntry(rec.getEmerySku()))
                weight = rec.getWeight();
            else
                weight = getWeight(rec.getItemEaId());

            m_updEjdItem.setDouble(1, weight);
            m_updEjdItem.setInt(2, rec.getItemEaId());
            m_updEjdItem.executeUpdate();
        } catch (SQLException ex) {
            addException(id, rec, String.format(errMsg, rec.getAceSku(), rec.getAceSku(), ex.getMessage()));
        }
    }

    /**
     * @param id  raw data record id
     * @param rec rec
     */
    private void updateEjdItemWhs(long id, AceItemBean rec) {
        String errMsg = "[updateEjdItemWhs] item id = %s, ace item id = %s error is %s";
        int stockPack = rec.getDealerPack();

        try {
            m_updItemWhs.setInt(1, stockPack);
            m_updItemWhs.setInt(2, rec.getCasePackQty());
            m_updItemWhs.setInt(3, rec.getEjdItemId());
            m_updItemWhs.setInt(4, getWarehouseId(rec.getRscId()));
            m_updItemWhs.executeUpdate();
        } catch (SQLException ex) {
            addException(id, rec, String.format(errMsg, rec.getAceSku(), rec.getAceSku(), ex.getMessage()));
        }
    }

    /**
     * Updates the catalog information
     *
     * @param id  raw data table record id
     * @param rec The item data
     */
    private void updateCatalog(long id, AceItemBean rec) {
        String errMsg = "[updateCatalog] item id = %s, ace item id = %s error is %s";

        try {
            m_UpdCatItem.setString(1, rec.getBrandName());
            m_UpdCatItem.setString(2, rec.getImageUrlSm());
            m_UpdCatItem.setString(3, rec.getImageUrlMd());
            m_UpdCatItem.setString(4, rec.getImageUrlLg());
            m_UpdCatItem.setString(5, rec.getAceSku());
            m_UpdCatItem.executeUpdate();

            //
            // At this point, just delete what's there and re-add.
            m_UpdCatBullet.setString(1, rec.getAceSku());
            m_UpdCatBullet.executeUpdate();

        } catch (SQLException ex) {
            addException(id, rec, String.format(errMsg, rec.getAceSku(), rec.getAceSku(), ex.getMessage()));
        }
    }

    private void updateTaxonomy(AceItemBean newRec) throws SQLException {
        int newTaxId = getNewTaxonomyId(newRec);

        m_UpdTaxonomy.setInt(1, newTaxId);
        m_UpdTaxonomy.setInt(2, newRec.getItemEaId());

        int res = m_UpdTaxonomy.executeUpdate();

        if (res > 0)
            m_Log.debug("Successfully updated item entity attribute row with id " + newRec.getItemEaId()
                    + " with new tax id " + newTaxId);
        else
            m_Log.debug("Failed to update item entity attribute row with id " + newRec.getItemEaId() + " with new tax id "
                    + newTaxId);
    }

    /**
     * Updates item pricing. Note - ACE Retails don't line up with Emery's retails.
     *
     * @param id  id
     * @param rec rec
     */
    private double updatePricing(long id, AceItemBean rec) {
        m_CurProc = "[udpatePricing]";
        String errMsg = "[updatePricing] item id = %s, ace sku %s, error is %s";
        double sell = -1;

        try {
            m_UpdPricing.setString(1, getItemId(rec.getEjdItemId()));
            m_UpdPricing.setInt(2, rec.getEjdItemId());
            m_UpdPricing.setInt(3, getWarehouseId(rec.getRscId()));
            m_UpdPricing.setDouble(4, rec.getCost());
            m_UpdPricing.setDouble(5, rec.getTmpCost());
            m_UpdPricing.setDate(6, rec.getTmpStart());
            m_UpdPricing.setDate(7, rec.getTmpEnd());
            m_UpdPricing.setDouble(8, rec.getRetail()); // retail a
            m_UpdPricing.setDouble(9, rec.getRetailA()); // retail b
            m_UpdPricing.setDouble(10, rec.getRetailB()); // retail c
            m_UpdPricing.execute();

            try (ResultSet rs = m_UpdPricing.getResultSet()) {
                if (rs.next())
                    sell = rs.getDouble(1);
            }

            if (sell < 0)
                logError("item change", "Unable to set item price", "item id: ", rec.getAceSku());
        } catch (SQLException ex) {
            addException(id, rec, String.format(errMsg, rec.getAceSku(), rec.getAceSku(), ex.getMessage()));
        }

        return sell;
    }

    private String getItemId(int ejdItemId) throws SQLException {
        String res = null;

        String sql = "SELECT DISTINCT item_id FROM item_entity_attr WHERE ejd_item_id = ?";

        try (PreparedStatement stmt = m_Conn.prepareStatement(sql)) {
            stmt.setInt(1, ejdItemId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next())
                    res = rs.getString("item_id");
            }
        }

        return res;
    }

    /**
     * Updates vendor specific information for an item and updates the ace_item_rsc table. Eventually the vendor should
     * come out of the item table.
     *
     * @param id  The id of the ace_item_raw table.
     * @param rec AceItemBean reference.
     */
    private void updateVendor(long id, AceItemBean rec) {
        m_CurProc = "[updateVendor]";
        String errMsg = "[updateVendor] vendor id = %d, ace vendor id %d, item id = %s, error is %s";

        try {
            m_UpdItemVnd.setLong(1, rec.getVndId());
            m_UpdItemVnd.setInt(2, rec.getItemEaId());
            m_UpdItemVnd.executeUpdate();

            logInfo("item change", "vendor updated", "item id: " + rec.getAceSku(),
                    "emery vnd id: " + rec.getVndId() + " ace vnd id: " + rec.getAceVndId());
        } catch (SQLException ex) {
            addException(id, rec,
                    String.format(errMsg, rec.getVndId(), rec.getAceVndId(), rec.getAceSku(), ex.getMessage()));
        }
    }

    /**
     * Updates the vendor item number
     *
     * @param id  id
     * @param rec rec
     */
    private void updateVendorSku(long id, AceItemBean rec) {
        String errMsg = "[updateVndorSku] vendor id = %d, vendor sku = %s, item ea id = %d, error is %s";

        //
        // Update vendor part number
        try {
            m_UpdVndSku.setString(1, rec.getVndSku());
            m_UpdVndSku.setInt(2, rec.getItemEaId());
            m_UpdVndSku.setLong(3, rec.getVndId());
            m_UpdVndSku.executeUpdate();

            logInfo("item change", "vendor sku updated", "item ea id: " + rec.getItemEaId(),
                    "vendor: " + rec.getVndId() + " sku: " + rec.getVndSku());
        } catch (SQLException ex) {
            addException(id, rec,
                    String.format(errMsg, rec.getVndId(), rec.getVndSku(), rec.getItemEaId(), ex.getMessage()));
        }
    }

    private double getWeight(int itemEaId) throws SQLException {
        double weight = 0.0;

        m_getWeight.setInt(1, itemEaId);

        try (ResultSet rs = m_getWeight.executeQuery()) {
            if (rs.next())
                weight = rs.getDouble("weight");
        }

        return weight;
    }
}
