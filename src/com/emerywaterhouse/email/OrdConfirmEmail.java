/**
 * File: OrdConfirmEmail.java Description: Class that retrieves email addresses
 * and sends the order confirmation email. This class is basically for non-web
 * orders and is similar to web order confirmation email.
 *
 * @author Naresh Pasnur
 * <p>
 * Create Date: 05/30/08
 * <p>
 * History: $Log: OrdConfirmEmail.java,v $
 * History: Revision 1.35  2015/07/14 16:10:55  ebrownewell
 * History: Fixed encoding issue.
 * History:
 * History: Revision 1.34  2015/06/12 16:30:21  ebrownewell
 * History: added check to see if tracking number is null. if it is we don't display the line.
 * History:
 * History: Revision 1.33  2015/03/16 19:05:58  ebrownewell
 * History: changed all from emails to noreply@emeryonline.com
 * History:
 * History: Revision 1.32  2015/03/09 14:52:40  ebrownewell
 * History: removed references to emery_stock flag. replaced with item type.
 * History:
 * History: Revision 1.31  2015/02/12 15:35:40  ebrownewell
 * History: verbiage changes
 * History:
 * History: Revision 1.30  2015/01/23 20:45:59  ebrownewell
 * History: Added ACE order and ACE shipment email text functions. Added send functions for new ace order and ace shipment emails.
 * History:
 * History: Revision 1.29  2015/01/19 17:33:08  ebrownewell
 * History: updated buildEmailText
 * History:
 * History: Revision 1.28  2015/01/13 16:49:23  ebrownewell
 * History: Updated buildEmaitext function
 * History:
 * History: Revision 1.27  2015/01/07 18:54:15  ebrownewell
 * History: confirmation email changes
 * History:
 * History: Revision 1.26  2014/11/05 14:16:11  smartel
 * History: Reverting the last change - it may need to only apply to UPS orders. Still waiting on a final word.
 * History:
 * History: Revision 1.25  2014/11/04 15:11:36  smartel
 * History: Received a ticket that said "Can we just remove the phrase "includes freight" from the confirmations?"
 * History:
 * History: Revision 1.24  2014/10/21 13:01:01  ebrownewell
 * History: updated to include salt restriction notice
 * History:
 * History: Revision 1.23  2014/07/21 18:13:17  tli
 * History: Added handling fee in order confirmation email if exists.
 * History:
 * History: Revision 1.22  2013/07/15 18:42:05  jfisher
 * History: Order total and wording changes
 * History: Revision 1.21 2013/01/21
 * 19:25:54 epearson changed order conf email address to
 * customerservice@emeryonline.com
 * <p>
 * Revision 1.20 2012/10/18 14:27:01 epearson added ability to manually
 * add email recipients
 * <p>
 * Revision 1.19 2011/11/06 10:59:05 npasnur Changed the sort order by
 * item id as per Stephanie's request.
 * <p>
 * Revision 1.18 2011/09/14 20:56:38 npasnur Added a missing comma in
 * the sql query.
 * <p>
 * Revision 1.17 2011/09/14 16:52:48 npasnur Getting sell price from
 * order line table instead of calling getsellprice function as broken
 * case upcharge calculation is missing in that function.
 * <p>
 * Revision 1.16 2010/07/09 16:17:07 mtopper took out for loop that was
 * used for debuggin purposes
 * <p>
 * Revision 1.15 2010/06/30 12:18:25 mtopper took out email test logging
 * <p>
 * Revision 1.14 2010/06/30 09:48:06 mtopper Added debug logging
 * statements to make sure emails are actually being sent out.
 * <p>
 * Revision 1.13 2010/06/25 02:07:48 mtopper added recipient list to the
 * call parameters of sendConfirmationEmail. added overloaded method
 * without recipient list for backwords compatibility
 * <p>
 * Revision 1.12 2009/08/26 14:11:58 npasnur Added a overloaded method
 * for sending confirmation email as custid was getting passed.
 * <p>
 * Revision 1.11 2009/08/18 14:28:29 npasnur Added sort order for order
 * lines
 * <p>
 * Revision 1.10 2009/08/12 13:40:35 prichter Added a constructor that
 * allows the connection, orderid, and best price status to be passed as
 * parameters. This allows the email to be constructed but not sent
 * until later.
 * <p>
 * Revision 1.9 2009/05/20 08:48:47 prichter Added a getCustomerId()
 * method
 * <p>
 * Revision 1.8 2009/05/14 08:18:25 npasnur Added code to display
 * information about company xref item.
 * <p>
 * Revision 1.7 2008/08/29 00:29:51 npasnur Using DataSender class
 * directly to send emails as web service call through jar file is
 * causing issue in the production environment
 * <p>
 * Revision 1.6 2008/08/22 14:23:06 npasnur Removed some unwanted spaces
 * <p>
 * Revision 1.5 2008/08/21 15:33:41 npasnur One of the Resultset was not
 * closed
 * <p>
 * Revision 1.4 2008/08/21 14:01:37 npasnur cleaned up some more code
 * <p>
 * Revision 1.3 2008/08/21 09:25:32 npasnur Fixed a issue related to
 * closing of Callable statements
 * <p>
 * Revision 1.2 2008/08/21 07:54:35 npasnur cleaned up some code
 * <p>
 * Revision 1.1 2008/07/30 21:49:43 npasnur initial commit
 */
package com.emerywaterhouse.email;

import com.emerywaterhouse.obj.beans.EmailOrderLine;
import com.emerywaterhouse.utils.DataSender;

import java.sql.*;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;

public class OrdConfirmEmail {

    private static String styleSettings = "<style>" + " body{ font-family: Calibri, verdana, helvetica, \"Times New Roman\"; font-size: 10pt; }"
            + "th { text-align: left; }" + "</style>";
    private String m_CustId = "";
    private long m_OrderId;
    private boolean m_BestPrice;
    private String m_Carrier = "";
    private String m_TrackingNum = "";
    private Connection m_EdbConn;
    private PreparedStatement m_ItemDesc; // Used by buildEmailText to get item
    // descriptions
    private PreparedStatement m_ItemUPC; // Used by buildEmailText to get item
    // UPCs
    private PreparedStatement getPackedId; // Used by buildEmailText to get line
    // packet numbers
    private PreparedStatement m_ItemXREF; // Used by buildEmailText to get item
    // xref information
    private boolean m_OrderErrLine; // Used by buildEmailText and sendEmail to
    // get error line status

    /* Pricing statement objects */
    private PreparedStatement m_GetSell;
    private PreparedStatement m_GetCustSell;
    private PreparedStatement m_GetCustSellPromo;
    private PreparedStatement m_GetCustQtyBuySell;

    private List<String> m_Recipients;

    /**
     * Constructor that initializes the connection handler reference.
     */
    public OrdConfirmEmail() {
        super();

        m_ItemDesc = null;
        m_ItemUPC = null;
        getPackedId = null;
        m_ItemXREF = null;
        m_Recipients = new LinkedList<>();
    }

    /**
     * Constructor that accepts a connection, order id , customer, id and best
     * price indicator as parameters. This allows the object to be constructed
     * when the order is created and sent at a later time.
     *     
     * @param edbConn   Connection - the EJD EnterpriseDB connection
     * @param orderId   long - the order id
     * @param bestPrice boolean - true if the customer is eligible for best price
     */
    public OrdConfirmEmail(Connection edbConn, long orderId, boolean bestPrice) {
        this();
        m_EdbConn = edbConn;
        m_OrderId = orderId;
        m_BestPrice = bestPrice;
    }

    /**
     * Constructor that accepts a connection, order id , customer, id and best
     * price indicator as parameters. This allows the object to be constructed
     * when the order is created and sent at a later time.
     *
     * @param conn      Connection - the EIS Oracle connection
     * @param edbConn   Connection - the EJD EnterpriseDB connection
     * @param orderId   long - the order id
     * @param bestPrice boolean - true if the customer is eligible for best price
     */
    public OrdConfirmEmail(Connection edbConn, long orderId, boolean bestPrice, String carrier, String trackingNum) {
        this();
        m_EdbConn = edbConn;
        m_OrderId = orderId;
        m_BestPrice = bestPrice;
        m_Carrier = carrier;
        m_TrackingNum = trackingNum;
    }

    /**
     * Cleanup when we're done.
     *
     * @throws java.lang.Throwable throw
     */
    @Override
    public void finalize() throws Throwable {
        super.finalize();
    }

    /**
     * Overloaded buildEmailText. Assumes the connection, order id, and best
     * price variables have already been set.
     *
     * @return String - the email text
     * @throws Exception exception
     */
    public String buildEmailText() throws Exception {
        return buildEmailText(m_EdbConn, m_OrderId, m_BestPrice);
    }

    public String buildEmailTextOld() throws Exception {
        return buildEmailTextOld(m_EdbConn, m_EdbConn, m_OrderId, m_BestPrice);
    }

    /**
     * Build the confirmation email text.
     *
     * @param conn         Connection - a jdbc connection reference.
     * @param orderId      long - the order through which data is pulled from the
     *                     database.
     * @param isEligBestPr boolean - whether or not the customer is eligible for Best
     *                     Price.
     * @return String - the email text containing the order details.
     * @throws Exception - if something went wrong whilst building the email text.
     */
    public String buildEmailTextOld(Connection conn, Connection edbConn, long orderId, boolean isEligBestPr) throws Exception {
        StringBuilder msg = new StringBuilder(1024);
        int LINE_LEN = 155;
        StringBuilder line = new StringBuilder(LINE_LEN);
        char[] ch = new char[LINE_LEN];
        DecimalFormat fmt = new DecimalFormat("$0.00");
        DecimalFormat costfmt = new DecimalFormat("$0.000");
        String tmp;
        double sellPrice;
        double ordTot = 0.0;
        double extCost;
        double freight = 0.0;
        double handling = 0.0;
        String frtAprvBy = null;
        String poNum = "";
        String orderDate = "";
        String itemId = "";
        StringBuilder sql = new StringBuilder();
        PreparedStatement ordStmt = null;
        PreparedStatement ordErrStmt = null;
        PreparedStatement ordHdrStmt = null;
        PreparedStatement ordHdrCountStmt = null;
        PreparedStatement handlingFeeStmt = null;
        ResultSet ordRset = null;
        ResultSet ordErrRset = null;
        ResultSet ordHdrRset = null;
        ResultSet ordHdrCountRset = null;
        ResultSet handlingFeeRset = null;
        Format formatter;
        int i = 0;
        int j = 0;

        m_OrderId = orderId;

        if (edbConn != null) {
            try {
                //
                // Prepare the statements used when building the lines
                prepareStatements(conn);
                prepareEdbStatements(edbConn);
                preparePricingStatments(conn);

                //
                // check if the order submitted has any error lines
                sql.setLength(0);
                sql.append("select order_date, po_num, customer_id, quoted_freight, frt_approved_by ");
                sql.append("from order_header ");
                sql.append("where order_id = ?");
                ordHdrStmt = conn.prepareStatement(sql.toString());
                ordHdrStmt.setLong(1, m_OrderId);
                ordHdrRset = ordHdrStmt.executeQuery();

                if (ordHdrRset.next()) {
                    formatter = new SimpleDateFormat("MM/dd/yyyy");

                    if (ordHdrRset.getString("customer_id") != null)
                        m_CustId = ordHdrRset.getString("customer_id");

                    if (ordHdrRset.getDate("order_date") != null)
                        orderDate = formatter.format(ordHdrRset.getDate("order_date"));

                    if (ordHdrRset.getString("po_num") != null)
                        poNum = ordHdrRset.getString("po_num");

                    frtAprvBy = ordHdrRset.getString("frt_approved_by");
                    freight = ordHdrRset.getDouble("quoted_freight");
                }

                //
                // Handling fee is charged if the order type = 'WEBEX'/'WEBRT'
                // and the carrier is not Emery Truck or Customer/Employee
                // Pickup.
                sql.setLength(0);
                sql.append("select count(*) as count from order_header ");
                sql.append("join order_type on order_header.order_type_id = order_type.order_type_id and order_type.description in  ( 'WEBEX', 'WEBRT') ");
                sql.append("join carrier on order_header.carrier_id = carrier.carrier_id and name not in ('EMERY TRUCK', 'CUSTOMER PICKUP', 'EMPLOYEE PICKUP') ");
                sql.append("where order_id = ?");
                ordHdrCountStmt = conn.prepareStatement(sql.toString());
                ordHdrCountStmt.setLong(1, m_OrderId);
                ordHdrCountRset = ordHdrCountStmt.executeQuery();

                if (ordHdrCountRset.next()) {
                    if (ordHdrCountRset.getInt("count") > 0) {
                        sql.setLength(0);
                        sql.append("select percent as handling ");
                        sql.append("from adder_value, adder ");
                        sql.append("where adder.description = 'HANDLING' and adder_value.adder_id = adder.adder_id and  adder_value.description = 'WEBEX' ");
                        handlingFeeStmt = conn.prepareStatement(sql.toString());
                        handlingFeeRset = handlingFeeStmt.executeQuery();

                        if (handlingFeeRset.next()) {
                            handling = handlingFeeRset.getDouble("handling");
                        }
                    }
                }

                msg.append("This is to confirm your order placed with Emery-Waterhouse.\r\n\r\n");
                msg.append("Order Date: ");
                msg.append(orderDate);
                msg.append("\r\nOrder number: ").append(m_OrderId);
                msg.append("\r\nPO: ").append(poNum);
                String CRLF = "\r\n";
                msg.append(CRLF);

                Arrays.fill(ch, 0, LINE_LEN, ' ');
                line.setLength(0);
                line.append(ch);
                line.replace(0, 14, "Order details:");
                msg.append(CRLF);
                msg.append(line);
                msg.append(CRLF);

                //
                // check if the order submitted has any error lines
                sql.setLength(0);
                sql.append("select quantity, item_id, emery_item_id, promo_id, upc, cust_sku, errormsg ");
                sql.append("from order_line_error ");
                sql.append("where order_id = ? ");
                sql.append("order by ole_id");

                ordErrStmt = conn.prepareStatement(sql.toString());
                ordErrStmt.setLong(1, m_OrderId);
                ordErrRset = ordErrStmt.executeQuery();

                while (ordErrRset.next()) {
                    if (i == 0) {
                        m_OrderErrLine = true;
                        msg.append("The order: ");
                        msg.append(m_OrderId);
                        msg.append(" contains following line errors.\r\n");
                        // 5 spaces for qty, 7 spaces for item, 10 spaces for
                        // packet,
                        // 35 spaces for desc,
                        // 10 spaces for UPC,13 spaces for cust-sku,36 spaces
                        // for
                        // error msg
                        line.setLength(0);
                        line.append(ch);
                        line.replace(0, 3, "Qty");
                        line.replace(6, 10, "Item");
                        line.replace(15, 21, "Packet");
                        line.replace(23, 34, "Description");
                        line.replace(61, 64, "UPC");
                        line.replace(76, 84, "cust-sku");
                        line.replace(91, 104, "Error Message");

                        msg.append(line);
                        msg.append(CRLF);

                        i++;
                    }

                    // First building the email lines which has errors and
                    // should be
                    // in top of the email
                    line.setLength(0);
                    line.append(ch);

                    //
                    // quantity ordered
                    if (ordErrRset.getString("quantity") != null) {
                        tmp = ordErrRset.getString("quantity");
                        line.replace(0, tmp.length(), tmp);
                    }

                    //
                    // displaying which is not null :item_id and emery_item_id
                    if (ordErrRset.getString("item_id") != null) {
                        itemId = ordErrRset.getString("item_id");
                        line.replace(6, 13, itemId);
                        tmp = getItemDesc(itemId);
                        line.replace(23, 23 + tmp.length(), tmp);
                    } else {
                        if (ordErrRset.getString("emery_item_id") != null) {
                            itemId = ordErrRset.getString("emery_item_id");
                            line.replace(6, 13, itemId);
                            tmp = getItemDesc(itemId);
                            line.replace(23, 23 + tmp.length(), tmp);
                        }
                    }

                    //
                    // Add line packet info, if any
                    if (ordErrRset.getString("promo_id") != null) {
                        tmp = getPacketId(ordErrRset.getString("promo_id"));
                        line.replace(15, 15 + tmp.length(), tmp);
                    }

                    //
                    // Add upc info, if any
                    if (ordErrRset.getString("upc") != null) {
                        tmp = ordErrRset.getString("upc");
                        line.replace(61, 61 + tmp.length(), tmp);
                    } else {
                        if (itemId != null && !itemId.equals("")) {
                            tmp = getItemUPC(itemId);
                            line.replace(61, 61 + tmp.length(), tmp);
                        }
                    }

                    //
                    // Add cust_sku info, if any
                    if (ordErrRset.getString("cust_sku") != null) {
                        tmp = ordErrRset.getString("cust_sku");
                        line.replace(76, 76 + tmp.length(), tmp);
                    }

                    //
                    // Add error message
                    if (ordErrRset.getString("errormsg") != null) {
                        tmp = ordErrRset.getString("errormsg");

                        if (tmp.indexOf("ORA", 10) != -1) {
                            if (tmp.indexOf("ORA", 10) != -1)
                                tmp = tmp.substring(10, tmp.indexOf("ORA", 10));
                            else
                                tmp = tmp.substring(10);
                        }

                        if (tmp.length() >= 100)
                            tmp = tmp.substring(0, 100);

                        line.replace(91, 91 + tmp.length(), tmp);
                    }

                    msg.append(line);
                    msg.append(CRLF);

                }

                msg.append(CRLF);

                //
                // build the successful order line(s)
                sql.setLength(0);
                sql.append("select ol_id, qty_ordered, item_entity_attr.item_id, item_ea_id, item_type_id, upc_entered, promo_id, sell_price, ");
                sql.append("(select count(*) from ol_id_bestpr where ol_id = order_line.ol_id) bestpr_olid, warehouse_id ");
                sql.append("from order_line ");
                sql.append("join order_header using (order_id) ");
                sql.append("join item_entity_attr using (item_ea_id) ");
                sql.append("where order_id = ? ");
                sql.append("order by ol_id ");
                ordStmt = conn.prepareStatement(sql.toString());
                ordStmt.setLong(1, m_OrderId);
                ordRset = ordStmt.executeQuery();

                while (ordRset.next()) {
                    if (j == 0) {
                        msg.append("Following lines for order: ");
                        msg.append(m_OrderId);
                        msg.append(" have been successfully ordered.\r\n");

                        //
                        // 5 spaces for qty, 7 spaces for item, 15 spaces for
                        // UPC, 35
                        // spaces for desc, 10 for ret, 10 for ext ret
                        line.setLength(0);
                        line.append(ch);
                        line.replace(0, 3, "Qty");
                        line.replace(6, 10, "Item");
                        line.replace(15, 18, "UPC");
                        line.replace(30, 36, "Packet");
                        line.replace(38, 49, "Description");

                        //
                        // Only show appropriate customers best price
                        // information
                        if (isEligBestPr) {
                            line.replace(76, 88, "Reg. Price");
                            line.replace(90, 94, "PPF");
                            line.replace(97, 109, "Cost");
                            line.replace(111, 125, "Ext Cost");
                        } else {
                            line.replace(97, 109, "Cost");
                            line.replace(111, 125, "Ext Cost");
                        }

                        msg.append(line);
                        msg.append(CRLF);

                        j++;
                    }

                    line.setLength(0);
                    line.append(ch);
                    //
                    // clear previous value
                    itemId = "";

                    tmp = Integer.toString(ordRset.getInt("qty_ordered"));
                    line.replace(0, tmp.length(), tmp);

                    if (ordRset.getString("item_id") != null) {
                        itemId = ordRset.getString("item_id");
                        line.replace(6, 13, itemId);
                    }

                    //
                    // Add upc info, if any
                    if (ordRset.getString("upc_entered") != null) {
                        tmp = ordRset.getString("upc_entered");
                        line.replace(15, 15 + tmp.length(), tmp);
                    } else {
                        if (itemId != null && !itemId.equals("")) {
                            tmp = getItemUPC(itemId);
                            line.replace(15, 15 + tmp.length(), tmp);
                        }
                    }

                    //
                    // Add line packet info, if any
                    if (ordRset.getString("promo_id") != null) {
                        tmp = getPacketId(ordRset.getString("promo_id"));
                        line.replace(30, 30 + tmp.length(), tmp);
                    }

                    //
                    // Add line item description
                    if (itemId != null && !itemId.equals("")) {
                        tmp = getItemDesc(itemId);
                        line.replace(38, 38 + tmp.length(), tmp);
                    }

                    //
                    // Only show appropriate customers best price information
                    if (isEligBestPr) {
                        if (ordRset.getInt("bestpr_olid") > 0) {
                            // Display regular price to differentiate it from best price.
                            sellPrice = getSellPrice(ordRset.getInt("item_ea_id"), ordRset.getInt("qty_ordered"), ordRset.getInt("warehouse_id"));
                            tmp = costfmt.format(sellPrice);
                            line.replace(76, 76 + tmp.length(), tmp);
                            tmp = "Yes";
                        } else
                            tmp = "";

                        line.replace(90, 90 + tmp.length(), tmp);

                        sellPrice = ordRset.getDouble("sell_price");
                        tmp = costfmt.format(sellPrice);
                        extCost = sellPrice * ordRset.getInt("qty_ordered");

                        line.replace(97, 97 + tmp.length(), tmp);

                        tmp = fmt.format(extCost);
                        line.replace(111, 111 + tmp.length(), tmp);
                    } else {
                        sellPrice = ordRset.getDouble("sell_price");
                        tmp = costfmt.format(sellPrice);
                        extCost = sellPrice * ordRset.getInt("qty_ordered");

                        line.replace(97, 97 + tmp.length(), tmp);

                        tmp = fmt.format(extCost);
                        line.replace(111, 111 + tmp.length(), tmp);
                    }

                    ordTot = ordTot + extCost;

                    msg.append(line);
                    msg.append(CRLF);

                    //
                    // 04/17/09
                    // item xref info
                    if (itemId != null && !itemId.equals("")) {
                        tmp = getXrefItem(m_OrderId, itemId);
                        if (tmp != null && !tmp.equals("")) {
                            line.setLength(0);
                            line.append(ch);
                            line.replace(15, 50, "This item replaces item#: " + tmp);
                            msg.append(line);
                            msg.append(CRLF);
                        }
                    }
                }

                //
                // execute this code only for successful order line(s).
                if (j > 0) {
                    msg.append(CRLF);
                    if (handling > 0) {
                        msg.append(String.format("Total Cost: %s (includes freight, handling)", fmt.format(ordTot + freight + handling)));
                    } else {
                        msg.append(String.format("Total Cost: %s (includes freight)", fmt.format(ordTot + freight)));
                    }
                    msg.append(CRLF);
                    msg.append(CRLF);
                    msg.append("PPF = Immediate Ship Promotional Price Found.\r\n");
                    msg.append(CRLF);
                    msg.append(CRLF);
                    msg.append("Order subject to vendor price at time of shipment.");
                    msg.append(CRLF);
                    msg.append(CRLF);

                    if (freight > 0) {
                        if (frtAprvBy != null && frtAprvBy.length() > 0)
                            msg.append(String.format("*A freight charge of %s, approved by %s has been added to the order.", fmt.format(freight),
                                    frtAprvBy));
                        else
                            msg.append(String.format("*A freight charge of %s has been added to the order.", fmt.format(freight)));

                        msg.append(CRLF);
                        msg.append(CRLF);
                    }

                    if (handling > 0) {
                        msg.append(String.format("*A handling charge of %s has been added to the order.", fmt.format(handling)));

                        msg.append(CRLF);
                        msg.append(CRLF);
                    }

                }

                msg.append("Notice:  All Salt melting products are on allocation to a maximum of 1 pallet until further notice.\r\n");
                msg.append("If you have any questions about your order, please call customer service:\r\n");
                msg.append("(800) 283-0236 option 1\r\n");
                msg.append(CRLF);
            } finally {
                //
                // Close the prepared and callable statements used when building
                // the
                // email lines
                closeStatements();

                if (ordRset != null) {
                    try {
                        ordRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordStmt != null) {
                    try {
                        ordStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (handlingFeeRset != null) {
                    try {
                        handlingFeeRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (handlingFeeStmt != null) {
                    try {
                        handlingFeeStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrCountRset != null) {
                    try {
                        ordHdrCountRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrCountStmt != null) {
                    try {
                        ordHdrCountStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordErrRset != null) {
                    try {
                        ordErrRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordErrStmt != null) {
                    try {
                        ordErrStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrRset != null) {
                    try {
                        ordHdrRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrStmt != null) {
                    try {
                        ordHdrStmt.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return msg.toString();
    }

    private static String capitalize(String line) {
        return Character.toUpperCase(line.charAt(0)) + line.substring(1);
    }

    public String buildEmailText(Connection conn, long orderId, boolean isEligBestPr) throws Exception {

        boolean reqPacket = false;

        String lineBreak = "<br/>";
        String tmp;

        DecimalFormat fmt = new DecimalFormat("$0.00");
        DecimalFormat costfmt = new DecimalFormat("$0.000");

        StringBuilder sql = new StringBuilder();
        StringBuilder bodyHTML = new StringBuilder();

        String orderDate = "";
        String poNum = "";
        String frtAprvBy = null;
        String warehouse;
        String ship_name = "";
        String ship_addr1 = "";
        String ship_addr2 = "";
        String ship_city = "";
        String ship_state = "";
        String ship_zip = "";

        double freight = 0.0;
        double handling = 0.0;
        double sellPrice;
        String totalCost;
        double extCost;
        double ordTot = 0.0;
        double nonAceSubTotal = 0.0;
        double AceSubTotal = 0.0;

        PreparedStatement ordHdrStmt = null;
        PreparedStatement ordHdrCountStmt = null;
        PreparedStatement handlingFeeStmt = null;
        PreparedStatement ordStmt = null;
        PreparedStatement ordErrStmt = null;
        PreparedStatement orderWarehouseStmt;
        PreparedStatement ordShipInfoStmt;

        ResultSet ordHdrRset = null;
        ResultSet ordHdrCountRset = null;
        ResultSet handlingFeeRset = null;
        ResultSet ordRset = null;
        ResultSet ordErrRset = null;
        ResultSet orderWarehouseRset;
        ResultSet ordShipInfoRset;

        Format formatter;

        int i = 0;
        int j = 0;

        boolean has_order_lines = false;
        boolean has_ace_lines = false;
        boolean hasOnlyAceLines;

        if (conn != null) {
            try {

                // Handle creating all SQL and statements first
                prepareStatements(conn);
                preparePricingStatments(conn);

                // get the basic order header data
                sql.setLength(0);
                sql.append("select order_date, po_num, customer_id, quoted_freight, frt_approved_by ");
                sql.append("from order_header ");
                sql.append("where order_id = ?");

                ordHdrStmt = conn.prepareStatement(sql.toString());
                ordHdrStmt.setLong(1, m_OrderId);
                ordHdrRset = ordHdrStmt.executeQuery();

                // if we got data from the order header we save it to the
                // correct variables.
                if (ordHdrRset.next()) {
                    formatter = new SimpleDateFormat("MM/dd/yyyy");

                    if (ordHdrRset.getString("customer_id") != null)
                        m_CustId = ordHdrRset.getString("customer_id");

                    if (ordHdrRset.getDate("order_date") != null)
                        orderDate = formatter.format(ordHdrRset.getDate("order_date"));

                    if (ordHdrRset.getString("po_num") != null)
                        poNum = ordHdrRset.getString("po_num");

                    frtAprvBy = ordHdrRset.getString("frt_approved_by");
                    freight = ordHdrRset.getDouble("quoted_freight");
                }

                // Handling fee is charged if the order type = 'WEBEX'/'WEBRT'
                // and
                // the carrier is not Emery Truck or customer/employee pickup
                sql.setLength(0);
                sql.append("select count(*) as count from order_header ");
                sql.append("join order_type on order_header.order_type_id = order_type.order_type_id and order_type.description in  ( 'WEBEX', 'WEBRT') ");
                sql.append("join carrier on order_header.carrier_id = carrier.carrier_id and name not in ('EMERY TRUCK', 'CUSTOMER PICKUP', 'EMPLOYEE PICKUP') ");
                sql.append("where order_id = ?");

                ordHdrCountStmt = conn.prepareStatement(sql.toString());
                ordHdrCountStmt.setLong(1, m_OrderId);
                ordHdrCountRset = ordHdrCountStmt.executeQuery();

                // get handling fees from the database if the order isn't webex
                // or webrt
                if (ordHdrCountRset.next()) {
                    if (ordHdrCountRset.getInt("count") > 0) {
                        sql.setLength(0);
                        sql.append("select percent as handling ");
                        sql.append("from adder_value, adder ");
                        sql.append("where adder.description = 'HANDLING' and adder_value.adder_id = adder.adder_id and  adder_value.description = 'WEBEX' ");
                        handlingFeeStmt = conn.prepareStatement(sql.toString());
                        handlingFeeRset = handlingFeeStmt.executeQuery();

                        if (handlingFeeRset.next()) {
                            handling = handlingFeeRset.getDouble("handling");
                        }
                    }
                }

                // check if there are order errors
                sql.setLength(0);
                sql.append("select quantity, item_id, emery_item_id, promo_id, upc, cust_sku, errormsg ");
                sql.append("from order_line_error ");
                sql.append("where order_id = ? ");
                sql.append("order by ole_id");

                ordErrStmt = conn.prepareStatement(sql.toString());
                ordErrStmt.setLong(1, m_OrderId);
                ordErrRset = ordErrStmt.executeQuery();

                // get order line details
                sql.setLength(0);
                sql.append("select ol_id, qty_ordered, item_entity_attr.item_id, item_ea_id, item_type_id, upc_entered, promo_id, sell_price, ");
                sql.append("(select count(*) from ol_id_bestpr where ol_id = order_line.ol_id) bestpr_olid, warehouse_id ");
                sql.append("from order_line ");
                sql.append("join order_header using (order_id) ");
                sql.append("join item_entity_attr using (item_ea_id) ");
                sql.append("where order_id = ? ");
                sql.append("order by ol_id ");
                ordStmt = conn.prepareStatement(sql.toString());
                ordStmt.setLong(1, m_OrderId);
                ordRset = ordStmt.executeQuery();

                // get the warehouse for a specific order and save it to a
                // variable so we can include it in the email
                sql.setLength(0);
                sql.append("select name from warehouse where warehouse_id in (select warehouse_id from order_header where order_id = ?)");
                orderWarehouseStmt = conn.prepareStatement(sql.toString());
                orderWarehouseStmt.setLong(1, m_OrderId);
                orderWarehouseRset = orderWarehouseStmt.executeQuery();

                if (orderWarehouseRset.next()) {
                    warehouse = orderWarehouseRset.getString("name");
                } else {
                    warehouse = null;
                }

                sql.setLength(0);
                sql.append("select ship_name,ship_addr1,ship_addr2,ship_city,ship_state,ship_zip from order_header where order_id = ?");
                ordShipInfoStmt = conn.prepareStatement(sql.toString());

                ordShipInfoStmt.setLong(1, m_OrderId);
                ordShipInfoRset = ordShipInfoStmt.executeQuery();

                if (ordShipInfoRset.next()) {
                    ship_name = ordShipInfoRset.getString("ship_name");
                    ship_addr1 = ordShipInfoRset.getString("ship_addr1");
                    ship_addr2 = ordShipInfoRset.getString("ship_addr2");
                    ship_city = ordShipInfoRset.getString("ship_city");
                    ship_state = ordShipInfoRset.getString("ship_state");
                    ship_zip = ordShipInfoRset.getString("ship_zip");
                }

                // End handling all SQL and statements

                // GET ORDER LINES
                // create the order line array list, so we can store the data
                // and look it over however many times needed

                int numAceItems = 0;
                int numOrderLines = 0;

                ArrayList<EmailOrderLine> ol_list = new ArrayList<>();
                while (ordRset.next()) {

                    has_order_lines = true;

                    EmailOrderLine line = new EmailOrderLine();

                    // set qty
                    line.setQty(ordRset.getInt("qty_ordered"));

                    // set item ID
                    if (ordRset.getString("item_id") != null) {
                        line.setItemId(ordRset.getString("item_id"));

                        if (warehouse.equalsIgnoreCase("portland") || warehouse.equalsIgnoreCase("pittston")) {
                            if (ordRset.getInt("item_type_id") == 8 || ordRset.getInt("item_type_id") == 9) {
                                line.setIs_ace_item(true);
                                numAceItems += 1;
                            } else {
                                line.setIs_ace_item(false);
                            }
                        }
                    }

                    // set upc
                    if (ordRset.getString("upc_entered") != null  && !ordRset.getString("upc_entered").trim().equals("")) {
                        line.setUpc(ordRset.getString("upc_entered"));
                    } 
                    else {
                        if (line.getItemId() != null && !line.getItemId().equals("")) {
                            line.setUpc(getItemUPC(line.getItemId()));
                        }
                    }

                    // set packet
                    if (ordRset.getString("promo_id") != null) {
                        line.setPacket(getPacketId(ordRset.getString("promo_id")));
                    }

                    // set item description
                    if (line.getItemId() != null && !line.getItemId().equals("")) {
                        line.setDescription(getItemDesc(line.getItemId()));
                    }

                    // only do the next part if they are eligable for best price
                    if (isEligBestPr) {
                        if (ordRset.getInt("bestpr_olid") > 0) {
                            // display regular price to differentiate it from
                            // best price
                            sellPrice = getSellPrice(ordRset.getInt("item_ea_id"), ordRset.getInt("qty_ordered"), ordRset.getInt("warehouse_id"));
                            line.setReg_price(costfmt.format(sellPrice));
                            tmp = "Yes";
                        } 
                        else
                            tmp = "No";

                        line.setPpf(tmp);

                        sellPrice = ordRset.getDouble("sell_price");
                        tmp = costfmt.format(sellPrice);
                        extCost = sellPrice * ordRset.getInt("qty_ordered");
                        line.setCost(tmp);

                        tmp = fmt.format(extCost);

                        line.setExt_cost(tmp);
                    } 
                    else {
                        sellPrice = ordRset.getDouble("sell_price");
                        tmp = costfmt.format(sellPrice);
                        extCost = sellPrice * ordRset.getInt("qty_ordered");

                        line.setCost(tmp);

                        tmp = fmt.format(extCost);
                        line.setExt_cost(tmp);
                    }

                    ordTot = ordTot + extCost;

                    if (ordRset.getString("item_id") != null && !ordRset.getString("item_id").equals("")) {
                        tmp = getXrefItem(m_OrderId, ordRset.getString("item_id"));

                        if (tmp != null && !tmp.equals("")) {
                            line.setUpc("This item replaces item#: " + tmp);
                        }
                    }

                    ol_list.add(line);
                    numOrderLines += 1;

                }
                // END ORDER LINES

                // check to see if the order contains only ace items
                hasOnlyAceLines = numOrderLines == numAceItems;

                // begin building the HTML Email
                bodyHTML.append("<img style=\"width:300px\" src=\"http://www.emeryonline.com/shared/images/emery-logo-blue-medium.jpg\" alt=\"Emery|Waterhouse\">");
                bodyHTML.append("<p>This is to confirm your order placed with Emery-Waterhouse.</p>");

                bodyHTML.append(ship_name).append(lineBreak);
                bodyHTML.append(ship_addr1).append(lineBreak);
                if (ship_addr2 != null && !ship_addr2.equals(""))
                    bodyHTML.append(ship_addr2).append(lineBreak);
                bodyHTML.append(String.format("%s, %s, %s", ship_city, ship_state, ship_zip)).append(lineBreak).append(lineBreak);

                bodyHTML.append("Order Date: ").append(orderDate).append(lineBreak);
                bodyHTML.append("Order Number: ").append(m_OrderId).append(lineBreak);
                if (poNum != null && !poNum.equals(""))
                    bodyHTML.append("PO Number: ").append(poNum).append(lineBreak);

                if (handling > 0 && has_order_lines) {
                    totalCost = fmt.format(ordTot + freight + handling);
                } 
                else {
                    totalCost = fmt.format(ordTot + freight);
                }

                bodyHTML.append("Total Cost: ").append(totalCost).append(lineBreak).append(lineBreak);
                bodyHTML.append("<a target=_blank href=\"http://www.emeryonline.com/emerywh/subscriber/my_account/order_detail.jsp?order=")
                        .append(m_OrderId).append("&cust=").append(m_CustId).append(                       
						 "\">View your order on our website</a>").append(lineBreak).append(lineBreak);

                // ERROR LINES
                while (ordErrRset.next()) {
                    // only print the header once, otherwise it'll repeat for
                    // every row
                    if (i == 0) {
                        m_OrderErrLine = true;
                        bodyHTML.append("The order: ").append(m_OrderId).append(" contains the following line errors: ").append(lineBreak);
                        bodyHTML.append("<table border = 0 cellpadding = 2 style=\"text-align: left\">");
                        bodyHTML.append("<tr>");
                        bodyHTML.append("<th>Qty</th>");
                        bodyHTML.append("<th>Item</th>");
                        bodyHTML.append("<th>Packet</th>");
                        bodyHTML.append("<th>Description</th>");
                        bodyHTML.append("<th>UPC</th>");
                        bodyHTML.append("<th>cust-sku</th>");
                        bodyHTML.append("<th>Error Message</th>");
                        bodyHTML.append("</tr>");
                        i++;
                    }

                    bodyHTML.append("<tr>");

                    if (ordErrRset.getString("quantity") != null) {
                        bodyHTML.append("<td>").append(ordErrRset.getString("quantity")).append("</td>");
                    }
                    String itemId = null;
                    if (ordErrRset.getString("item_id") != null) {
                        itemId = ordErrRset.getString("item_id");
                        bodyHTML.append("<td>").append(itemId).append("</td>");
                        if (ordErrRset.getString("promo_id") != null) {
                            bodyHTML.append("<td>").append(ordErrRset.getString("promo_id")).append("</td>");
                        } else {
                            bodyHTML.append("<th></th>");
                        }
                        bodyHTML.append("<td>").append(getItemDesc(itemId)).append("</td>");
                    } else {
                        if (ordErrRset.getString("emery_item_id") != null) {
                            itemId = ordErrRset.getString("emery_item_id");
                            bodyHTML.append("<td>").append(itemId).append("</td>");
                            if (ordErrRset.getString("promo_id") != null) {
                                bodyHTML.append("<td>").append(ordErrRset.getString("promo_id")).append("</td>");
                            } else {
                                bodyHTML.append("<th></th>");
                            }
                            bodyHTML.append("<td>").append(getItemDesc(itemId)).append("</td>");
                        }
                    }

                    if (ordErrRset.getString("upc") != null) {
                        bodyHTML.append("<td>").append(ordErrRset.getString("upc")).append("</td>");

                    } else {
                        if (itemId != null && !itemId.equals("")) {
                            tmp = getItemUPC(itemId);
                            bodyHTML.append("<td>").append(tmp).append("</td>");
                        }
                    }

                    if (ordErrRset.getString("cust_sku") != null) {
                        bodyHTML.append("<td>").append(ordErrRset.getString("cust_sku")).append("</td>");
                    } else {
                        bodyHTML.append("<th></th>");
                    }

                    if (ordErrRset.getString("errormsg") != null) {
                        tmp = ordErrRset.getString("errormsg");

                        if (tmp.indexOf("ORA", 10) != -1) {
                            if (tmp.indexOf("ORA", 10) != -1)
                                tmp = tmp.substring(10, tmp.indexOf("ORA", 10));
                            else
                                tmp = tmp.substring(10);
                        }

                        if (tmp.length() >= 100)
                            tmp = tmp.substring(0, 100);

                        bodyHTML.append("<td>").append(tmp).append("</td>");
                    } else {
                        bodyHTML.append("<th></th>");
                    }

                    bodyHTML.append("</tr>");

                }
                bodyHTML.append("</table>");
                // END ERROR LINES

                // START ORDER LINES

                if (!hasOnlyAceLines) {

                    // only print the header info once, and only if we have
                    // order
                    // lines
                    if (j == 0 && has_order_lines) {

                        // if we have a warehouse we want to let the customer
                        // know
                        // where it will be shipping from, otherwise give a
                        // generic
                        // message
                        if (warehouse != null && !warehouse.equals(""))
                            bodyHTML.append("The following lines will be shipped from the Emery warehouse in <b>")
                                    .append(capitalize(warehouse.toLowerCase())).append("</b>:");
                        else
                            bodyHTML.append("The following lines for order: ").append(m_OrderId).append(" have been sucessfully ordered");

                        bodyHTML.append(lineBreak).append(lineBreak);

                        bodyHTML.append("<table border = 0 cellpadding = 2 style=\"text-align: left\">");
                        bodyHTML.append("<tr>");
                        bodyHTML.append("<th>Qty</th>");
                        bodyHTML.append("<th>Item</th>");
                        bodyHTML.append("<th>UPC</th>");
                        bodyHTML.append("<th>Packet</th>");
                        bodyHTML.append("<th>Description</th>");

                        if (isEligBestPr) {
                            bodyHTML.append("<th>Reg. Price</th>");
                            bodyHTML.append("<th>PPF</th>");
                            bodyHTML.append("<th>Cost</th>");
                            bodyHTML.append("<th>Ext Cost</th>");
                        } else {
                            bodyHTML.append("<th>Cost</th>");
                            bodyHTML.append("<th>Ext Cost</th>");
                        }

                        bodyHTML.append("</tr>");

                        for (EmailOrderLine ol : ol_list) {
                            if (!ol.isIs_ace_item()) {
                                // we need to add up the subtotal for each
                                // section,
                                // but to do this we need to remove all extra
                                // characters besides numbers and decimals
                                nonAceSubTotal += Double.parseDouble(ol.getExt_cost().replaceAll("[^\\d.]", ""));

                                bodyHTML.append("<tr>");
                                bodyHTML.append("<td>").append(ol.getQty()).append("</td>");
                                bodyHTML.append("<td>").append(ol.getItemId()).append("</td>");
                                bodyHTML.append("<td>").append(ol.getUpc()).append("</td>");
                                // avoid printing out "null" for packet
                                if (ol.getPacket() != null)
                                    bodyHTML.append("<td>").append(ol.getPacket()).append("</td>");
                                else
                                    bodyHTML.append("<td></td>");

                                bodyHTML.append("<td>").append(ol.getDescription()).append("</td>");

                                if (isEligBestPr) {
                                    String reg_price;
                                    if (ol.getReg_price() != null)
                                        reg_price = ol.getReg_price();
                                    else
                                        reg_price = "";

                                    String ppf;
                                    if (ol.getPpf() != null)
                                        ppf = ol.getPpf();
                                    else
                                        ppf = "";

                                    bodyHTML.append("<td>").append(reg_price).append("</td>");
                                    bodyHTML.append("<td>").append(ppf).append("</td>");
                                    bodyHTML.append("<td>").append(ol.getCost()).append("</td>");
                                    bodyHTML.append("<td>").append(ol.getExt_cost()).append("</td>");

                                    // System.out.printf("Reg price: %s | PPF: %s | cost: %s | ext cost: %s ",ol.getReg_price(),ol.getPpf(),ol.getCost(),ol.getExt_cost());
                                } else {
                                    bodyHTML.append("<td>").append(ol.getCost()).append("</td>");
                                    bodyHTML.append("<td>").append(ol.getExt_cost()).append("</td>");
                                }
                                bodyHTML.append("</tr>");
                            }

                        }

                        j++;
                        bodyHTML.append("</table>");
                    }

                    if (j > 0 && has_order_lines) {
                        if (handling > 0) {
                            bodyHTML.append("<b>Cost: ").append(fmt.format(nonAceSubTotal + freight + handling)).append(" (includes handling)</b>").append(lineBreak);
                            bodyHTML.append("Additional Freight charges may apply to UPS and LTL deliveries.");
                            bodyHTML.append(lineBreak).append(lineBreak);
                        } else {
                            bodyHTML.append("<b>Cost: ").append(fmt.format(nonAceSubTotal + freight)).append("</b>").append(lineBreak);
                            bodyHTML.append("Additional Freight charges may apply to UPS and LTL deliveries.");
                            bodyHTML.append(lineBreak).append(lineBreak);
                        }
                    }
                }
                // END ORDER LINES

                // check to see if any of the order lines in this order have
                // ace items
                for (EmailOrderLine order_line : ol_list) {
                    if (order_line.isIs_ace_item()) {
                        has_ace_lines = true;
                        break;
                    }
                }

                if (has_ace_lines) {
                    // START ACE LINES
                    j = 0;
                    bodyHTML.append("<p>The following lines will be given a unique order ID and will be shipped directly from the Wilton, NY warehouse. The shipment will be combined with "
                            + "any other Wilton, NY items ordered the same day.</p>");

                    bodyHTML.append("<table border = 0 cellpadding = 2 style=\"text-align: left\">");
                    bodyHTML.append("<tr>");
                    bodyHTML.append("<th>Qty</th>");
                    bodyHTML.append("<th>Item</th>");
                    bodyHTML.append("<th>UPC</th>");

                    if (reqPacket)
                        bodyHTML.append("<th>Packet</th>");
                    bodyHTML.append("<th>Description</th>");
                    if (isEligBestPr) {
                        bodyHTML.append("<th>Reg. Price</th>");
                        bodyHTML.append("<th>PPF</th>");
                        bodyHTML.append("<th>Cost</th>");
                        bodyHTML.append("<th>Ext Cost</th>");
                    } else {
                        bodyHTML.append("<th>Cost</th>");
                        bodyHTML.append("<th>Ext Cost</th>");
                    }
                    bodyHTML.append("</tr>");

                    for (EmailOrderLine ol : ol_list) {
                        if (ol.isIs_ace_item()) {
                            AceSubTotal += Double.valueOf(ol.getExt_cost().replaceAll("[^\\d.]", ""));

                            bodyHTML.append("<tr>");
                            bodyHTML.append("<td>").append(ol.getQty()).append("</td>");
                            bodyHTML.append("<td>").append(ol.getItemId()).append("</td>");
                            bodyHTML.append("<td>").append(ol.getUpc()).append("</td>");

                            if (reqPacket) {
                                if (ol.getPacket() != null)
                                    bodyHTML.append("<td>").append(ol.getPacket()).append("</td>");
                                else
                                    bodyHTML.append("<th></th>");
                            }
                            bodyHTML.append("<td>").append(ol.getDescription()).append("</td>");

                            if (isEligBestPr) {

                                String reg_price;
                                if (ol.getReg_price() != null)
                                    reg_price = ol.getReg_price();
                                else
                                    reg_price = "";

                                bodyHTML.append("<td>").append(reg_price).append("</td>");
                                bodyHTML.append("<td>").append(ol.getPpf()).append("</td>");
                                bodyHTML.append("<td>").append(ol.getCost()).append("</td>");
                                bodyHTML.append("<td>").append(ol.getExt_cost()).append("</td>");
                            } else {
                                bodyHTML.append("<td>").append(ol.getCost()).append("</td>");
                                bodyHTML.append("<td>").append(ol.getExt_cost()).append("</td>");
                            }
                            bodyHTML.append("</tr>");
                        }
                    }
                    j++;
                    bodyHTML.append("</table>");

                    if (j > 0) {
                        bodyHTML.append("<b>Cost: ").append(fmt.format(AceSubTotal)).append("</b>").append(lineBreak);
                        bodyHTML.append("Additional Freight charges may apply to UPS and LTL deliveries.");
                        bodyHTML.append(lineBreak).append(lineBreak);
                    }

                    // END ACE LINES
                }
                // START FINAL EMAIL LINES

                if (isEligBestPr)
                    bodyHTML.append("PPF = Immediate Ship Promotional Price Found.").append(lineBreak).append(lineBreak);

                bodyHTML.append("Order subject to vendor price at time of shipment.").append(lineBreak).append(lineBreak);

                if (freight > 0) {
                    if (frtAprvBy != null && frtAprvBy.length() > 0)
                        bodyHTML.append("<p>*A freight charge of ").append(fmt.format(freight)).append(", approved by ").append(frtAprvBy).append(" has been added to the order. </p>");
                    else
                        bodyHTML.append("<p>*A freight charge of ").append(fmt.format(freight)).append(" has been added to the order.</p>");
                }

                if (handling > 0) {
                    bodyHTML.append("<p>*A handling charge of ").append(fmt.format(handling)).append(" has been added to the order.</p>");
                }

                // bodyHTML.append("<p style=\"color:red\"><b>Notice: All salt melting products are on allocation to a maximum of 1 pallet until further notice.</b></p>");
                bodyHTML.append("If you have any questions about your order, please call customer service: ").append(lineBreak);
                bodyHTML.append("(800) 283-0236 option 1").append(lineBreak).append(lineBreak);

                // END FINAL EMAIL LINES
            } finally {
                // Close the prepared and callable statements used when building
                // the email lines
                closeStatements();

                if (ordRset != null) {
                    try {
                        ordRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordStmt != null) {
                    try {
                        ordStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (handlingFeeRset != null) {
                    try {
                        handlingFeeRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (handlingFeeStmt != null) {
                    try {
                        handlingFeeStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrCountRset != null) {
                    try {
                        ordHdrCountRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrCountStmt != null) {
                    try {
                        ordHdrCountStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordErrRset != null) {
                    try {
                        ordErrRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordErrStmt != null) {
                    try {
                        ordErrStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrRset != null) {
                    try {
                        ordHdrRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrStmt != null) {
                    try {
                        ordHdrStmt.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return bodyHTML.toString();

    }

    /**
     * Overloaded buildAceOrderText. Assumes the connection, order id, and best
     * price variables have already been set.
     *
     * @return String - the email text
     * @throws Exception exceptipon
     */
    public String buildAceOrderText() throws Exception {
        return buildAceOrderText(m_EdbConn, m_OrderId, m_BestPrice);
    }

    /**
     * @param conn connection
     * @param orderId order id
     * @param isEligBestPr eligible for best price
     * @return string
     * @throws Exception when unable to build ace order text
     */
    private String buildAceOrderText(Connection conn, long orderId, boolean isEligBestPr) throws Exception {

        boolean reqPacket = false; // if we want to display packet for this
        // email set this to true

        String lineBreak = "<br/>";
        String tmp;

        DecimalFormat fmt = new DecimalFormat("$0.00");
        DecimalFormat costfmt = new DecimalFormat("$0.000");

        StringBuilder sql = new StringBuilder();
        StringBuilder bodyHTML = new StringBuilder();

        String orderDate = "";
        String poNum = "";
        String frtAprvBy = null;
        String ship_name = "";
        String ship_addr1 = "";
        String ship_addr2 = "";
        String ship_city = "";
        String ship_state = "";
        String ship_zip = "";

        double freight = 0.0;
        double handling = 0.0;
        double sellPrice;
        String totalCost;
        double extCost;
        double ordTot = 0.0;
        double AceSubTotal = 0.0;

        PreparedStatement ordHdrStmt = null;
        PreparedStatement ordHdrCountStmt = null;
        PreparedStatement handlingFeeStmt = null;
        PreparedStatement ordStmt = null;
        PreparedStatement ordErrStmt = null;
        PreparedStatement ordShipInfoStmt;

        ResultSet ordHdrRset = null;
        ResultSet ordHdrCountRset = null;
        ResultSet handlingFeeRset = null;
        ResultSet ordRset = null;
        ResultSet ordErrRset = null;
        ResultSet ordShipInfoRset;

        Format formatter;

        int i = 0;

        boolean has_order_lines = false;

        if (conn != null) {
            try {

                // Handle creating all SQL and statements first
                prepareStatements(conn);
                preparePricingStatments(conn);

                // get the basic order header data
                sql.setLength(0);
                sql.append("select order_date, po_num, customer_id, quoted_freight, frt_approved_by ");
                sql.append("from order_header ");
                sql.append("where order_id = ?");

                ordHdrStmt = conn.prepareStatement(sql.toString());
                ordHdrStmt.setLong(1, m_OrderId);
                ordHdrRset = ordHdrStmt.executeQuery();

                // if we got data from the order header we save it to the
                // correct variables.
                if (ordHdrRset.next()) {
                    formatter = new SimpleDateFormat("MM/dd/yyyy");

                    if (ordHdrRset.getString("customer_id") != null)
                        m_CustId = ordHdrRset.getString("customer_id");

                    if (ordHdrRset.getDate("order_date") != null)
                        orderDate = formatter.format(ordHdrRset.getDate("order_date"));

                    if (ordHdrRset.getString("po_num") != null)
                        poNum = ordHdrRset.getString("po_num");

                    frtAprvBy = ordHdrRset.getString("frt_approved_by");
                    freight = ordHdrRset.getDouble("quoted_freight");
                }

                // Handling fee is charged if the order type = 'WEBEX'/'WEBRT'
                // and
                // the carrier is not Emery Truck or customer/employee pickup
                sql.setLength(0);
                sql.append("select count(*) as count from order_header ");
                sql.append("join order_type on order_header.order_type_id = order_type.order_type_id and order_type.description in  ( 'WEBEX', 'WEBRT') ");
                sql.append("join carrier on order_header.carrier_id = carrier.carrier_id and name not in ('EMERY TRUCK', 'CUSTOMER PICKUP', 'EMPLOYEE PICKUP') ");
                sql.append("where order_id = ?");

                ordHdrCountStmt = conn.prepareStatement(sql.toString());
                ordHdrCountStmt.setLong(1, m_OrderId);
                ordHdrCountRset = ordHdrCountStmt.executeQuery();

                // get handling fees from the database if the order isn't webex
                // or webrt
                if (ordHdrCountRset.next()) {
                    if (ordHdrCountRset.getInt("count") > 0) {
                        sql.setLength(0);
                        sql.append("select percent as handling ");
                        sql.append("from adder_value, adder ");
                        sql.append("where adder.description = 'HANDLING' and adder_value.adder_id = adder.adder_id and  adder_value.description = 'WEBEX' ");
                        handlingFeeStmt = conn.prepareStatement(sql.toString());
                        handlingFeeRset = handlingFeeStmt.executeQuery();

                        if (handlingFeeRset.next()) {
                            handling = handlingFeeRset.getDouble("handling");
                        }
                    }
                }

                // check if there are order errors
                sql.setLength(0);
                sql.append("select quantity, item_id, emery_item_id, promo_id, upc, cust_sku, errormsg ");
                sql.append("from order_line_error ");
                sql.append("where order_id = ? ");
                sql.append("order by ole_id");

                ordErrStmt = conn.prepareStatement(sql.toString());
                ordErrStmt.setLong(1, m_OrderId);
                ordErrRset = ordErrStmt.executeQuery();

                // get order line details
                sql.setLength(0);
                sql.append("select ol_id, qty_ordered, item_entity_attr.item_id, item_ea_id, item_type_id, upc_entered, promo_id, sell_price, ");
                sql.append("(select count(*) from ol_id_bestpr where ol_id = order_line.ol_id) bestpr_olid, warehouse_id ");
                sql.append("from order_line ");
                sql.append("join order_header using (order_id) ");
                sql.append("join item_entity_attr using (item_ea_id) ");
                sql.append("where order_id = ? ");
                sql.append("order by ol_id ");
                ordStmt = conn.prepareStatement(sql.toString());
                ordStmt.setLong(1, m_OrderId);
                ordRset = ordStmt.executeQuery();

                sql.setLength(0);
                sql.append("select ship_name,ship_addr1,ship_addr2,ship_city,ship_state,ship_zip from order_header " + "where order_id = ?");
                ordShipInfoStmt = conn.prepareStatement(sql.toString());

                ordShipInfoStmt.setLong(1, m_OrderId);
                ordShipInfoRset = ordShipInfoStmt.executeQuery();

                if (ordShipInfoRset.next()) {
                    ship_name = ordShipInfoRset.getString("ship_name");
                    ship_addr1 = ordShipInfoRset.getString("ship_addr1");
                    ship_addr2 = ordShipInfoRset.getString("ship_addr2");
                    ship_city = ordShipInfoRset.getString("ship_city");
                    ship_state = ordShipInfoRset.getString("ship_state");
                    ship_zip = ordShipInfoRset.getString("ship_zip");
                }

                // End handling all SQL and statements

                // GET ORDER LINES
                // create the order line array list, so we can store the data
                // and look it over however many times needed
                ArrayList<EmailOrderLine> ol_list = new ArrayList<>();
                while (ordRset.next()) {

                    has_order_lines = true;

                    EmailOrderLine line = new EmailOrderLine();

                    // set qty
                    line.setQty(ordRset.getInt("qty_ordered"));

                    // set item ID
                    if (ordRset.getString("item_id") != null) {
                        line.setItemId(ordRset.getString("item_id"));
                        if (ordRset.getInt("item_type_id") == 8 || ordRset.getInt("item_type_id") == 9) {
                            line.setIs_ace_item(true);
                        } else {
                            line.setIs_ace_item(false);
                        }
                    }

                    // set upc
                    if (ordRset.getString("upc_entered") != null) {
                        line.setUpc(ordRset.getString("upc_entered"));
                    } else {
                        if (line.getItemId() != null && !line.getItemId().equals("")) {
                            line.setUpc(getItemUPC(line.getItemId()));
                        }
                    }

                    // set packet
                    if (ordRset.getString("promo_id") != null) {
                        line.setPacket(getPacketId(ordRset.getString("promo_id")));
                    }

                    // set item description
                    if (line.getItemId() != null && !line.getItemId().equals("")) {
                        line.setDescription(getItemDesc(line.getItemId()));
                    }

                    // only do the next part if they are eligable for best price
                    if (isEligBestPr) {
                        if (ordRset.getInt("bestpr_olid") > 0) {
                            // display regular price to differentiate it from
                            // best price
                            sellPrice = getSellPrice(ordRset.getInt("item_ea_id"), ordRset.getInt("qty_ordered"), ordRset.getInt("warehouse_id"));
                            line.setReg_price(costfmt.format(sellPrice));
                            tmp = "Yes";
                        } else
                            tmp = "No";

                        line.setPpf(tmp);

                        sellPrice = ordRset.getDouble("sell_price");
                        tmp = costfmt.format(sellPrice);
                        extCost = sellPrice * ordRset.getInt("qty_ordered");
                        line.setCost(tmp);

                        tmp = fmt.format(extCost);

                        line.setExt_cost(tmp);
                    } else {
                        sellPrice = ordRset.getDouble("sell_price");
                        tmp = costfmt.format(sellPrice);
                        extCost = sellPrice * ordRset.getInt("qty_ordered");

                        line.setCost(tmp);

                        tmp = fmt.format(extCost);
                        line.setExt_cost(tmp);
                    }

                    ordTot = ordTot + extCost;
                    ol_list.add(line);

                    if (ordRset.getString("item_id") != null && !ordRset.getString("item_id").equals("")) {
                        tmp = getXrefItem(m_OrderId, ordRset.getString("item_id"));

                        if (tmp != null && !tmp.equals("")) {
                            line.setUpc("This item replaces item#: " + tmp);
                            ol_list.add(line);
                        }
                    }
                }
                // END ORDER LINES

                // begin building the HTML Email
                bodyHTML.append("<img style=\"width:300px\" src=\"http://www.emeryonline.com/shared/images/emery-logo-blue-medium.jpg\" alt=\"Emery|Waterhouse\">");
                bodyHTML.append("<p>This is to confirm your order placed with Emery-Waterhouse.</p>");

                bodyHTML.append(ship_name).append(lineBreak);
                bodyHTML.append(ship_addr1).append(lineBreak);
                if (ship_addr2 != null && !ship_addr2.equals(""))
                    bodyHTML.append(ship_addr2).append(lineBreak);
                bodyHTML.append(String.format("%s, %s, %s", ship_city, ship_state, ship_zip)).append(lineBreak).append(lineBreak);

                bodyHTML.append("Order Date: ").append(orderDate).append(lineBreak);
                bodyHTML.append("Order Number: ").append(m_OrderId).append(lineBreak);
                if (poNum != null && !Objects.equals(poNum, ""))
                    bodyHTML.append("PO Number: ").append(poNum).append(lineBreak);

                if (handling > 0 && has_order_lines) {
                    totalCost = fmt.format(ordTot + freight + handling);
                } else {
                    totalCost = fmt.format(ordTot + freight);
                }

                bodyHTML.append("Total Cost: ").append(totalCost).append(lineBreak).append(lineBreak);
                bodyHTML.append("<a target=_blank href=\"http://www.emeryonline.com/emerywh/subscriber/my_account/order_detail.jsp?order=")
                        .append(m_OrderId).append("&cust=").append(m_CustId).append(
                        /*
                         * "&inpdt=11%2F19%2F2014 -- Find out if this is really
						 * needed, seems to work fine without it
						 */"\">View your order on our website</a>").append(lineBreak).append(lineBreak);

                // ERROR LINES
                while (ordErrRset.next()) {
                    // only print the header once, otherwise it'll repeat for
                    // every row
                    if (i == 0) {
                        m_OrderErrLine = true;
                        bodyHTML.append("The order: ").append(m_OrderId).append(" contains the following line errors: ").append(lineBreak);
                        bodyHTML.append("<table border = 0 cellpadding = 2 style=\"text-align: left\">");
                        bodyHTML.append("<tr>");
                        bodyHTML.append("<th>Qty</th>");
                        bodyHTML.append("<th>Item</th>");
                        bodyHTML.append("<th>Packet</th>");
                        bodyHTML.append("<th>Description</th>");
                        bodyHTML.append("<th>UPC</th>");
                        bodyHTML.append("<th>cust-sku</th>");
                        bodyHTML.append("<th>Error Message</th>");
                        bodyHTML.append("</tr>");
                        i++;
                    }

                    bodyHTML.append("<tr>");

                    if (ordErrRset.getString("quantity") != null) {
                        bodyHTML.append("<td>").append(ordErrRset.getString("quantity")).append("</td>");
                    }
                    String itemId = null;
                    if (ordErrRset.getString("item_id") != null) {
                        itemId = ordErrRset.getString("item_id");
                        bodyHTML.append("<td>").append(itemId).append("</td>");
                        if (ordErrRset.getString("promo_id") != null) {
                            bodyHTML.append("<td>").append(ordErrRset.getString("promo_id")).append("</td>");
                        } else {
                            bodyHTML.append("<th></th>");
                        }
                        bodyHTML.append("<td>").append(getItemDesc(itemId)).append("</td>");
                    } else {
                        if (ordErrRset.getString("emery_item_id") != null) {
                            itemId = ordErrRset.getString("emery_item_id");
                            bodyHTML.append("<td>").append(itemId).append("</td>");
                            if (ordErrRset.getString("promo_id") != null) {
                                bodyHTML.append("<td>").append(ordErrRset.getString("promo_id")).append("</td>");
                            } else {
                                bodyHTML.append("<th></th>");
                            }
                            bodyHTML.append("<td>").append(getItemDesc(itemId)).append("</td>");
                        }
                    }

                    if (ordErrRset.getString("upc") != null) {
                        bodyHTML.append("<td>").append(ordErrRset.getString("upc")).append("</td>");

                    } else {
                        if (itemId != null && !itemId.equals("")) {
                            tmp = getItemUPC(itemId);
                            bodyHTML.append("<td>").append(tmp).append("</td>");
                        }
                    }

                    if (ordErrRset.getString("cust_sku") != null) {
                        bodyHTML.append("<td>").append(ordErrRset.getString("cust_sku")).append("</td>");
                    } else {
                        bodyHTML.append("<th></th>");
                    }

                    if (ordErrRset.getString("errormsg") != null) {
                        tmp = ordErrRset.getString("errormsg");

                        if (tmp.indexOf("ORA", 10) != -1) {
                            if (tmp.indexOf("ORA", 10) != -1)
                                tmp = tmp.substring(10, tmp.indexOf("ORA", 10));
                            else
                                tmp = tmp.substring(10);
                        }

                        if (tmp.length() >= 100)
                            tmp = tmp.substring(0, 100);

                        bodyHTML.append("<td>").append(tmp).append("</td>");
                    } else {
                        bodyHTML.append("<th></th>");
                    }

                    bodyHTML.append("</tr>");

                }
                bodyHTML.append("</table>");
                // END ERROR LINES

                bodyHTML.append("<p>The following lines will be shipped directly from the Wilton, NY warehouse. The shipment will be combined with "
                        + "any other Wilton, NY items ordered the same day.</p>");

                bodyHTML.append("<table border = 0 cellpadding = 2 style=\"text-align: left\">");
                bodyHTML.append("<tr>");
                bodyHTML.append("<th>Qty</th>");
                bodyHTML.append("<th>Item</th>");
                bodyHTML.append("<th>UPC</th>");
                if (reqPacket)
                    bodyHTML.append("<th>Packet</th>");
                bodyHTML.append("<th>Description</th>");
                if (isEligBestPr) {
                    bodyHTML.append("<th>Reg. Price</th>");
                    bodyHTML.append("<th>PPF</th>");
                    bodyHTML.append("<th>Cost</th>");
                    bodyHTML.append("<th>Ext Cost</th>");
                } else {
                    bodyHTML.append("<th>Cost</th>");
                    bodyHTML.append("<th>Ext Cost</th>");
                }
                bodyHTML.append("</tr>");

                for (EmailOrderLine ol : ol_list) {
                    if (ol.isIs_ace_item()) {
                        AceSubTotal += Double.valueOf(ol.getExt_cost().replaceAll("[^\\d.]", ""));

                        bodyHTML.append("<tr>");
                        bodyHTML.append("<td>").append(ol.getQty()).append("</td>");
                        bodyHTML.append("<td>").append(ol.getItemId()).append("</td>");
                        bodyHTML.append("<td>").append(ol.getUpc()).append("</td>");
                        if (reqPacket) {
                            if (ol.getPacket() != null)
                                bodyHTML.append("<td>").append(ol.getPacket()).append("</td>");
                            else
                                bodyHTML.append("<th></th>");
                        }
                        bodyHTML.append("<td>").append(ol.getDescription()).append("</td>");

                        if (isEligBestPr) {

                            String reg_price;
                            if (ol.getReg_price() != null)
                                reg_price = ol.getReg_price();
                            else
                                reg_price = "";

                            bodyHTML.append("<td>").append(reg_price).append("</td>");
                            bodyHTML.append("<td>").append(ol.getPpf()).append("</td>");
                            bodyHTML.append("<td>").append(ol.getCost()).append("</td>");
                            bodyHTML.append("<td>").append(ol.getExt_cost()).append("</td>");
                        } else {
                            bodyHTML.append("<td>").append(ol.getCost()).append("</td>");
                            bodyHTML.append("<td>").append(ol.getExt_cost()).append("</td>");
                        }
                        bodyHTML.append("</tr>");
                    }
                }
                bodyHTML.append("</table>");

                bodyHTML.append("<b>Cost: ").append(fmt.format(AceSubTotal)).append("</b>").append(lineBreak);
                bodyHTML.append("Additional Freight charges may apply to UPS and LTL deliveries.");
                bodyHTML.append(lineBreak).append(lineBreak);

                // END ACE LINES

                // START FINAL EMAIL LINES

                if (isEligBestPr)
                    bodyHTML.append("PPF = Immediate Ship Promotional Price Found.").append(lineBreak).append(lineBreak);

                bodyHTML.append("Order subject to vendor price at time of shipment.").append(lineBreak).append(lineBreak);

                if (freight > 0) {
                    if (frtAprvBy != null && frtAprvBy.length() > 0)
                        bodyHTML.append("<p>*A freight charge of ").append(fmt.format(freight)).append(", approved by ").append(frtAprvBy).append(" has been added to the order. </p>");
                    else
                        bodyHTML.append("<p>*A freight charge of ").append(fmt.format(freight)).append(" has been added to the order.</p>");
                }

                if (handling > 0) {
                    bodyHTML.append("<p>*A handling charge of ").append(fmt.format(handling)).append(" has been added to the order.</p>");
                }

                // bodyHTML.append("<p style=\"color:red\"><b>Notice: All salt melting products are on allocation to a maximum of 1 pallet until further notice.</b></p>");
                bodyHTML.append("If you have any questions about your order, please call customer service: ").append(lineBreak);
                bodyHTML.append("(800) 283-0236 option 1").append(lineBreak).append(lineBreak);

                // END FINAL EMAIL LINES
            } finally {
                // Close the prepared and callable statements used when building
                // the email lines
                closeStatements();

                if (ordRset != null) {
                    try {
                        ordRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordStmt != null) {
                    try {
                        ordStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (handlingFeeRset != null) {
                    try {
                        handlingFeeRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (handlingFeeStmt != null) {
                    try {
                        handlingFeeStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrCountRset != null) {
                    try {
                        ordHdrCountRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrCountStmt != null) {
                    try {
                        ordHdrCountStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordErrRset != null) {
                    try {
                        ordErrRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordErrStmt != null) {
                    try {
                        ordErrStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrRset != null) {
                    try {
                        ordHdrRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrStmt != null) {
                    try {
                        ordHdrStmt.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return bodyHTML.toString();

    }

    /**
     * Overloaded buildShipmentText. Assumes the connection, order id, and best
     * price variables have already been set.
     *
     * @return String - the email text
     * @throws Exception exception
     */
    public String buildShipmentText() throws Exception {
        return buildShipmentText(m_EdbConn, m_OrderId, m_BestPrice, m_Carrier, m_TrackingNum);
    }

    public String buildShipmentText(Connection conn, long orderId, boolean isEligBestPr, String carrier, String trackingNum) throws Exception {

        boolean reqPacket = false; // if we want to display packet for this
        // email set this to true

        String lineBreak = "<br/>";
        String tmp;

        DecimalFormat fmt = new DecimalFormat("$0.00");
        DecimalFormat costfmt = new DecimalFormat("$0.000");

        StringBuilder sql = new StringBuilder();
        StringBuilder bodyHTML = new StringBuilder();

        String orderDate = "";
        String poNum = "";
        String totalCost;

        double freight = 0.0;
        double handling = 0.0;
        double sellPrice;
        double extCost;
        double ordTot = 0.0;
        double AceSubTotal = 0.0;

        PreparedStatement ordHdrStmt = null;
        PreparedStatement ordHdrCountStmt = null;
        PreparedStatement handlingFeeStmt = null;
        PreparedStatement ordStmt = null;
        PreparedStatement ordErrStmt = null;

        ResultSet ordHdrRset = null;
        ResultSet ordHdrCountRset = null;
        ResultSet handlingFeeRset = null;
        ResultSet ordRset = null;
        ResultSet ordErrRset = null;

        Format formatter;

        int i = 0;
        int j;

        boolean has_order_lines = false;
        boolean has_ace_lines = false;

        // hardcoded test tracking info
        // carrier = "UPS";
        // trackingNum = "204385234085";

        if (conn != null) {
            try {

                // Handle creating all SQL and statements first
                prepareStatements(conn);
                preparePricingStatments(conn);

                // get the basic order header data
                sql.setLength(0);
                sql.append("select order_date, po_num, customer_id, quoted_freight, frt_approved_by ");
                sql.append("from order_header ");
                sql.append("where order_id = ?");

                ordHdrStmt = conn.prepareStatement(sql.toString());
                ordHdrStmt.setLong(1, m_OrderId);
                ordHdrRset = ordHdrStmt.executeQuery();

                // if we got data from the order header we save it to the
                // correct variables.
                if (ordHdrRset.next()) {
                    formatter = new SimpleDateFormat("MM/dd/yyyy");

                    if (ordHdrRset.getString("customer_id") != null)
                        m_CustId = ordHdrRset.getString("customer_id");

                    if (ordHdrRset.getDate("order_date") != null)
                        orderDate = formatter.format(ordHdrRset.getDate("order_date"));

                    if (ordHdrRset.getString("po_num") != null)
                        poNum = ordHdrRset.getString("po_num");

                    freight = ordHdrRset.getDouble("quoted_freight");
                }

                // Handling fee is charged if the order type = 'WEBEX'/'WEBRT'
                // and
                // the carrier is not Emery Truck or customer/employee pickup
                sql.setLength(0);
                sql.append("select count(*) as count from order_header ");
                sql.append("join order_type on order_header.order_type_id = order_type.order_type_id and order_type.description in  ( 'WEBEX', 'WEBRT') ");
                sql.append("join carrier on order_header.carrier_id = carrier.carrier_id and name not in ('EMERY TRUCK', 'CUSTOMER PICKUP', 'EMPLOYEE PICKUP') ");
                sql.append("where order_id = ?");

                ordHdrCountStmt = conn.prepareStatement(sql.toString());
                ordHdrCountStmt.setLong(1, m_OrderId);
                ordHdrCountRset = ordHdrCountStmt.executeQuery();

                // get handling fees from the database if the order isn't webex
                // or webrt
                if (ordHdrCountRset.next()) {
                    if (ordHdrCountRset.getInt("count") > 0) {
                        sql.setLength(0);
                        sql.append("select percent as handling ");
                        sql.append("from adder_value, adder ");
                        sql.append("where adder.description = 'HANDLING' and adder_value.adder_id = adder.adder_id and  adder_value.description = 'WEBEX' ");
                        handlingFeeStmt = conn.prepareStatement(sql.toString());
                        handlingFeeRset = handlingFeeStmt.executeQuery();

                        if (handlingFeeRset.next()) {
                            handling = handlingFeeRset.getDouble("handling");
                        }
                    }
                }

                // check if there are order errors
                sql.setLength(0);
                sql.append("select quantity, item_id, emery_item_id, promo_id, upc, cust_sku, errormsg ");
                sql.append("from order_line_error ");
                sql.append("where order_id = ? ");
                sql.append("order by ole_id");

                ordErrStmt = conn.prepareStatement(sql.toString());
                ordErrStmt.setLong(1, m_OrderId);
                ordErrRset = ordErrStmt.executeQuery();

                // get order line details
                sql.setLength(0);
                sql.append("select ol_id, qty_ordered, item_entity_attr.item_id, item_ea_id, item_type_id, upc_entered, promo_id, sell_price, ");
                sql.append("(select count(*) from ol_id_bestpr where ol_id = order_line.ol_id) bestpr_olid, warehouse_id ");
                sql.append("from order_line ");
                sql.append("join order_header using (order_id) ");
                sql.append("join item_entity_attr using (item_ea_id) ");
                sql.append("where order_id = ? ");
                sql.append("order by ol_id ");
                ordStmt = conn.prepareStatement(sql.toString());
                ordStmt.setLong(1, m_OrderId);
                ordRset = ordStmt.executeQuery();

                // End handling all SQL and statements

                // GET ORDER LINES
                // create the order line array list, so we can store the data
                // and look it over however many times needed
                ArrayList<EmailOrderLine> ol_list = new ArrayList<>();
                while (ordRset.next()) {

                    has_order_lines = true;

                    EmailOrderLine line = new EmailOrderLine();

                    // set qty
                    line.setQty(ordRset.getInt("qty_shipped"));

                    // set item ID
                    if (ordRset.getString("item_id") != null) {
                        line.setItemId(ordRset.getString("item_id"));

                        if (ordRset.getInt("item_type_id") == 8 || ordRset.getInt("item_type_id") == 9) {
                            line.setIs_ace_item(true);
                        } else {
                            line.setIs_ace_item(false);
                        }
                    }

                    // set upc
                    if (ordRset.getString("upc_entered") != null) {
                        line.setUpc(ordRset.getString("upc_entered"));
                    } else {
                        if (line.getItemId() != null && !line.getItemId().equals("")) {
                            line.setUpc(getItemUPC(line.getItemId()));
                        }
                    }

                    // set packet
                    if (ordRset.getString("promo_id") != null) {
                        line.setPacket(getPacketId(ordRset.getString("promo_id")));
                    }

                    // set item description
                    if (line.getItemId() != null && !line.getItemId().equals("")) {
                        line.setDescription(getItemDesc(line.getItemId()));
                    }

                    // only do the next part if they are eligable for best price
                    if (isEligBestPr) {
                        if (ordRset.getInt("bestpr_olid") > 0) {
                            // display regular price to differentiate it from
                            // best price
                            sellPrice = getSellPrice(ordRset.getInt("item_ea_id"), ordRset.getInt("qty_shipped"), ordRset.getInt("warehouse_id"));
                            line.setReg_price(costfmt.format(sellPrice));
                            tmp = "Yes";
                        } else
                            tmp = "No";

                        line.setPpf(tmp);

                        sellPrice = ordRset.getDouble("sell_price");
                        tmp = costfmt.format(sellPrice);
                        extCost = sellPrice * ordRset.getInt("qty_shipped");
                        line.setCost(tmp);

                        tmp = fmt.format(extCost);

                        line.setExt_cost(tmp);
                    } else {
                        sellPrice = ordRset.getDouble("sell_price");
                        tmp = costfmt.format(sellPrice);
                        extCost = sellPrice * ordRset.getInt("qty_shipped");

                        line.setCost(tmp);

                        tmp = fmt.format(extCost);
                        line.setExt_cost(tmp);
                    }
                    if (ordRset.getInt("item_type_id") == 8 || ordRset.getInt("item_type_id") == 9) {
                        ordTot = ordTot + extCost;
                    }

                    ol_list.add(line);

                    if (ordRset.getString("item_id") != null && !ordRset.getString("item_id").equals("")) {
                        tmp = getXrefItem(m_OrderId, ordRset.getString("item_id"));

                        if (tmp != null && !tmp.equals("")) {
                            line.setUpc("This item replaces item#: " + tmp);
                            ol_list.add(line);
                        }
                    }
                }
                // END ORDER LINES

                // begin building the HTML Email
                bodyHTML.append("<img style=\"width:300px\" src=\"http://www.emeryonline.com/shared/images/emery-logo-blue-medium.jpg\" alt=\"Emery|Waterhouse\">");
                bodyHTML.append("<p>Order subject to vendor price at time of shipment.</p>");

                bodyHTML.append("If you have any questions about your order, please call customer service: ").append(lineBreak);
                bodyHTML.append("(800) 283-0236 option 1").append(lineBreak);
                bodyHTML.append("This is to confirm your order shipping.").append(lineBreak).append(lineBreak);

                bodyHTML.append("Order Date: ").append(orderDate).append(lineBreak);
                bodyHTML.append("Order Number: ").append(m_OrderId).append(lineBreak);
                if (poNum != null && !Objects.equals(poNum, ""))
                    bodyHTML.append("PO Number: ").append(poNum).append(lineBreak);

                if (handling > 0 && has_order_lines) {
                    totalCost = fmt.format(ordTot + freight + handling);
                } else {
                    totalCost = fmt.format(ordTot + freight);
                }

                bodyHTML.append("Total Cost: ").append(totalCost).append(lineBreak).append(lineBreak);

                if (carrier != null && !carrier.equals("")) {
                    bodyHTML.append("Carrier: ").append(carrier).append(lineBreak);

                    if (trackingNum != null && !trackingNum.equals("")) {
                        bodyHTML.append("Tracking #: ").append(trackingNum).append(lineBreak).append(lineBreak);
                        if (carrier.equals("UPS")) {
                            bodyHTML.append("<a target=_blank href=\"http://wwwapps.ups.com/WebTracking/track?track=yes&trackNums=")
                                    .append(trackingNum).append("\">Track your shipment on the web</a>").append(lineBreak).append(lineBreak);
                        }
                    }
                }

                bodyHTML.append("<a target=_blank href=\"http://www.emeryonline.com/emerywh/subscriber/my_account/order_detail.jsp?order=").append(m_OrderId)
                        .append("&cust=").append(m_CustId).append("\">View your order on our website</a>").append(lineBreak).append(lineBreak);

                // ERROR LINES
                while (ordErrRset.next()) {
                    // only print the header once, otherwise it'll repeat for
                    // every row
                    if (i == 0) {
                        m_OrderErrLine = true;
                        bodyHTML.append("The order: ").append(m_OrderId).append(" contains the following line errors: ").append(lineBreak);
                        bodyHTML.append("<table border = 0 cellpadding = 2 style=\"text-align: left\">");
                        bodyHTML.append("<tr>");
                        bodyHTML.append("<th>Qty</th>");
                        bodyHTML.append("<th>Item</th>");
                        bodyHTML.append("<th>Packet</th>");
                        bodyHTML.append("<th>Description</th>");
                        bodyHTML.append("<th>UPC</th>");
                        bodyHTML.append("<th>cust-sku</th>");
                        bodyHTML.append("<th>Error Message</th>");
                        bodyHTML.append("</tr>");
                        i++;
                    }

                    bodyHTML.append("<tr>");

                    if (ordErrRset.getString("quantity") != null) {
                        bodyHTML.append("<td>").append(ordErrRset.getString("quantity")).append("</td>");
                    }
                    String itemId = null;
                    if (ordErrRset.getString("item_id") != null) {
                        itemId = ordErrRset.getString("item_id");
                        bodyHTML.append("<td>").append(itemId).append("</td>");
                        if (ordErrRset.getString("promo_id") != null) {
                            bodyHTML.append("<td>").append(ordErrRset.getString("promo_id")).append("</td>");
                        } else {
                            bodyHTML.append("<th></th>");
                        }
                        bodyHTML.append("<td>").append(getItemDesc(itemId)).append("</td>");
                    } else {
                        if (ordErrRset.getString("emery_item_id") != null) {
                            itemId = ordErrRset.getString("emery_item_id");
                            bodyHTML.append("<td>").append(itemId).append("</td>");
                            if (ordErrRset.getString("promo_id") != null) {
                                bodyHTML.append("<td>").append(ordErrRset.getString("promo_id")).append("</td>");
                            } else {
                                bodyHTML.append("<th></th>");
                            }
                            bodyHTML.append("<td>").append(getItemDesc(itemId)).append("</td>");
                        }
                    }

                    if (ordErrRset.getString("upc") != null) {
                        bodyHTML.append("<td>").append(ordErrRset.getString("upc")).append("</td>");

                    } else {
                        if (itemId != null && !itemId.equals("")) {
                            tmp = getItemUPC(itemId);
                            bodyHTML.append("<td>").append(tmp).append("</td>");
                        }
                    }

                    if (ordErrRset.getString("cust_sku") != null) {
                        bodyHTML.append("<td>").append(ordErrRset.getString("cust_sku")).append("</td>");
                    } else {
                        bodyHTML.append("<th></th>");
                    }

                    if (ordErrRset.getString("errormsg") != null) {
                        tmp = ordErrRset.getString("errormsg");

                        if (tmp.indexOf("ORA", 10) != -1) {
                            if (tmp.indexOf("ORA", 10) != -1)
                                tmp = tmp.substring(10, tmp.indexOf("ORA", 10));
                            else
                                tmp = tmp.substring(10);
                        }

                        if (tmp.length() >= 100)
                            tmp = tmp.substring(0, 100);

                        bodyHTML.append("<td>").append(tmp).append("</td>");
                    } else {
                        bodyHTML.append("<th></th>");
                    }

                    bodyHTML.append("</tr>");

                }
                bodyHTML.append("</table>");
                // END ERROR LINES

                if (has_order_lines) {

                    // END ORDER LINES

                    // check to see if any of the order lines in this order have
                    // ace items
                    for (EmailOrderLine order_line : ol_list) {
                        if (order_line.isIs_ace_item()) {
                            has_ace_lines = true;
                            break;
                        }
                    }

                    if (has_ace_lines) {
                        // START ACE LINES
                        j = 0;
                        bodyHTML.append("<p>The following lines have been shipped Wilton, NY.</p>");

                        bodyHTML.append("<table border = 0 cellpadding = 2 style=\"text-align: left\">");
                        bodyHTML.append("<tr>");
                        bodyHTML.append("<th>Qty</th>");
                        bodyHTML.append("<th>Item</th>");
                        bodyHTML.append("<th>UPC</th>");
                        if (reqPacket)
                            bodyHTML.append("<th>Packet</th>");
                        bodyHTML.append("<th>Description</th>");
                        if (isEligBestPr) {
                            bodyHTML.append("<th>Reg. Price</th>");
                            bodyHTML.append("<th>PPF</th>");
                            bodyHTML.append("<th>Cost</th>");
                            bodyHTML.append("<th>Ext Cost</th>");
                        } else {
                            bodyHTML.append("<th>Cost</th>");
                            bodyHTML.append("<th>Ext Cost</th>");
                        }
                        bodyHTML.append("</tr>");

                        for (EmailOrderLine ol : ol_list) {
                            if (ol.isIs_ace_item()) {
                                AceSubTotal += Double.valueOf(ol.getExt_cost().replaceAll("[^\\d.]", ""));

                                bodyHTML.append("<tr>");
                                bodyHTML.append("<td>").append(ol.getQty()).append("</td>");
                                bodyHTML.append("<td>").append(ol.getItemId()).append("</td>");
                                bodyHTML.append("<td>").append(ol.getUpc()).append("</td>");
                                if (reqPacket) {
                                    if (ol.getPacket() != null)
                                        bodyHTML.append("<td>").append(ol.getPacket()).append("</td>");
                                    else
                                        bodyHTML.append("<th></th>");
                                }
                                bodyHTML.append("<td>").append(ol.getDescription()).append("</td>");

                                if (isEligBestPr) {

                                    String reg_price;
                                    if (ol.getReg_price() != null)
                                        reg_price = ol.getReg_price();
                                    else
                                        reg_price = "";

                                    bodyHTML.append("<td>").append(reg_price).append("</td>");
                                    bodyHTML.append("<td>").append(ol.getPpf()).append("</td>");
                                    bodyHTML.append("<td>").append(ol.getCost()).append("</td>");
                                    bodyHTML.append("<td>").append(ol.getExt_cost()).append("</td>");
                                } else {
                                    bodyHTML.append("<td>").append(ol.getCost()).append("</td>");
                                    bodyHTML.append("<td>").append(ol.getExt_cost()).append("</td>");
                                }
                                bodyHTML.append("</tr>");
                            }
                        }
                        j++;
                        bodyHTML.append("</table>");

                        if (j > 0) {
                            bodyHTML.append("<b>Cost: ").append(fmt.format(AceSubTotal)).append("</b>").append(lineBreak);
                            bodyHTML.append("Additional Freight charges may apply to UPS and LTL deliveries.");
                            bodyHTML.append(lineBreak).append(lineBreak);
                        }

                        // END ACE LINES
                    }

                }

                // START FINAL EMAIL LINES

                bodyHTML.append("If you have any questions about your order, please call customer service: ").append(lineBreak);
                bodyHTML.append("(800) 283-0236 option 1").append(lineBreak).append(lineBreak);

                // END FINAL EMAIL LINES
            } finally {
                // Close the prepared and callable statements used when building
                // the email lines
                closeStatements();

                if (ordRset != null) {
                    try {
                        ordRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordStmt != null) {
                    try {
                        ordStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (handlingFeeRset != null) {
                    try {
                        handlingFeeRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (handlingFeeStmt != null) {
                    try {
                        handlingFeeStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrCountRset != null) {
                    try {
                        ordHdrCountRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrCountStmt != null) {
                    try {
                        ordHdrCountStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordErrRset != null) {
                    try {
                        ordErrRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordErrStmt != null) {
                    try {
                        ordErrStmt.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrRset != null) {
                    try {
                        ordHdrRset.close();
                    } catch (Exception ignored) {
                    }
                }

                if (ordHdrStmt != null) {
                    try {
                        ordHdrStmt.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return bodyHTML.toString();

    }

    /**
     * Closes the prepared statements used by the buildEmailText() method.
     */
    private void closeStatements() {
        if (m_ItemDesc != null) {
            try {
                m_ItemDesc.close();
            } catch (SQLException ignored) {
            }

            m_ItemDesc = null;
        }

        if (m_ItemUPC != null) {
            try {
                m_ItemUPC.close();
            } catch (SQLException ignored) {
            }

            m_ItemUPC = null;
        }

        if (getPackedId != null) {
            try {
                getPackedId.close();
            } catch (SQLException ignored) {
            }

            getPackedId = null;
        }

        if (m_GetSell != null) {
            try {
                m_GetSell.close();
            } catch (Exception ignored) {
            }

            m_GetSell = null;
        }

        if (m_GetCustSell != null) {
            try {
                m_GetCustSell.close();
            } catch (Exception ignored) {
            }

            m_GetCustSell = null;
        }

        if (m_GetCustSellPromo != null) {
            try {
                m_GetCustSellPromo.close();
            } catch (Exception ignored) {
            }

            m_GetCustSellPromo = null;
        }

        if (m_GetCustQtyBuySell != null) {
            try {
                m_GetCustQtyBuySell.close();
            } catch (Exception ignored) {
            }

            m_GetCustQtyBuySell = null;
        }

        if (m_ItemXREF != null) {
            try {
                m_ItemXREF.close();
            } catch (Exception ignored) {
            }

            m_ItemXREF = null;
        }
    }

    /**
     * Returns the oracle connection
     *
     * @return Connection - the oracle connection
     */
    protected Connection getConnection() {
        return m_EdbConn;
    }

    /**
     * Returns the customer id this email is to go to
     *
     * @return String - the customer id
     */
    protected String getCustId() {
        return m_CustId;
    }

    /**
     * Returns the order id
     *
     * @return long - the order id
     */
    protected long getOrderid() {
        return m_OrderId;
    }

    /**
     * Returns the item description for a specific item. Used by the
     * buildEmailText() method.
     *
     * @param itemId String - the id number of the item to retrieve the description
     *               for.
     * @return String the description of the item in itemId if the item is
     * found. If the item is not found, then an empty string is
     * returned.
     */
    private String getItemDesc(String itemId) {
        String desc = "";
        ResultSet rset = null;

        try {
            m_ItemDesc.setString(1, itemId);
            rset = m_ItemDesc.executeQuery();

            if (rset.next()) {
                desc = rset.getString(1);
                if (desc.length() >= 35)
                    desc = desc.substring(0, 35);
            }

        } catch (SQLException e) {
            desc = Integer.toString(e.getErrorCode());
        } catch (Exception ex) {
            desc = ex.getMessage();

            if (desc != null) {
                if (desc.length() > 35)
                    desc = desc.substring(0, 35);
            } else
                desc = "exception";
        } finally {
            if (rset != null) {
                try {
                    rset.close();
                } catch (Exception ignored) {
                }
            }
        }

        return desc;
    }

    /**
     * Returns the item description for a specific item. Used by the
     * buildEmailText() method.
     *
     * @param itemId String - the id number of the item to retrieve the description
     *               for.
     * @return String the description of the item in itemId if the item is
     * found. If the item is not found, then an empty string is
     * returned.
     */
    private String getItemUPC(String itemId) {
        String upc = "";
        ResultSet rset = null;

        try {
            m_ItemUPC.setString(1, itemId);
            rset = m_ItemUPC.executeQuery();

            if (rset.next())
                upc = rset.getString(1);
        } catch (Exception ex) {
            upc = "";
        } finally {
            //
            // Close result set
            if (rset != null) {
                try {
                    rset.close();
                } catch (SQLException ignored) {
                }
            }
        }
        return upc;
    }

    /**
     * Returns the replacement item in emery item xref. Used by the
     * buildEmailText() method.
     *
     * @param orderId long - the id number of the order.
     * @param itemId  String - the substitute item id.
     * @return String the old item id. If the item is not found, then an empty
     * string is returned.
     */
    private String getXrefItem(long orderId, String itemId) {
        String origItemId = ""; // original item id entered by the customer
        ResultSet rset = null;

        try {
            m_ItemXREF.setLong(1, orderId);
            m_ItemXREF.setString(2, itemId);
            rset = m_ItemXREF.executeQuery();

            if (rset.next())
                origItemId = rset.getString(1);
        } catch (Exception ex) {
            origItemId = "";
        } finally {
            //
            // Close result set
            if (rset != null) {
                try {
                    rset.close();
                } catch (SQLException ignored) {
                }
            }
        }

        return origItemId;
    }

    /**
     * Returns the packet_id for a specified promo_id. Used by the
     * buildEmailText() method.
     *
     * @param promoId String - the id number of the promotion
     * @return String the packet number which contains the supplied promotion
     */
    private String getPacketId(String promoId) {
        String id = "";
        ResultSet rset = null;

        try {
            getPackedId.setString(1, promoId);
            rset = getPackedId.executeQuery();

            if (rset.next())
                id = rset.getString(1);
        } catch (SQLException e) {
            id = Integer.toString(e.getErrorCode());
        } catch (Exception ex) {
            id = ex.getMessage();

            if (id != null) {
                if (id.length() > 3)
                    id = "";
            } else
                id = "";
        } finally {
            if (rset != null) {
                try {
                    rset.close();
                } catch (Exception ignored) {
                }
            }
        }

        return id;
    }

    /**
     * Returns true if the customer is eligible for best price
     *
     * @return boolean - true is the customer is eligible for best price
     */
    protected boolean isEligibleForBestPrice() {
        return m_BestPrice;
    }

    /**
     * Prepare the line item description and packet statements required by the
     * buildEmailText() method.
     *
     * @param conn Connection - a jdbc connection reference.
     * @throws SQLException - if an error occurred whilst preparing the statements.
     */
    private void prepareStatements(Connection conn) throws SQLException {
        //
        // This is being called (exclusively) for the email text building
        // method.
        // Since we have limited space in the email, I'm taking a reasonable,
        // limited
        // chunk of the description.
        m_ItemDesc = conn.prepareStatement("SELECT description FROM item_entity_attr WHERE item_id = ?");

        m_ItemUPC = conn.prepareStatement("SELECT upc_code FROM item_upc WHERE item_id = ? AND primary_upc = 1");

        m_ItemXREF = conn.prepareStatement("SELECT original_item_id FROM order_line WHERE order_id = ?  AND item_id = ? AND item_id_source = 'EMERY CROSS'");

    }

    private void prepareEdbStatements(Connection edbConn) throws SQLException {

        getPackedId = edbConn.prepareStatement("SELECT packet_id FROM promotion WHERE promo_id = ?");

    }

    /**
     * Prepare the pricing statements for item price calculation required by the
     * buildEmailText() method.
     *
     * @param conn Connection - a jdbc connection reference.
     * @throws SQLException - if an error occurred whilst preparing the statements.
     */
    private void preparePricingStatments(Connection conn) throws SQLException {

        StringBuffer sql = new StringBuffer();
        //
        // Prepare statement that gets today's sell price.
        m_GetSell = conn.prepareStatement("SELECT sell FROM ejd_item_price WHERE ejd_item_id IN " +
                "(SELECT DISTINCT ejd_item_id FROM item_entity_attr WHERE item_ea_id = ?) AND warehouse_id = ?");


        //
        // Prepare statement that gets customer-specific sell price.
        m_GetCustSell = conn.prepareStatement("SELECT ejd_cust_procs.get_sell_price(?, ?) ");


        //
        // Prepare statement that gets customer-specific promo sell price.
        m_GetCustSellPromo = conn.prepareStatement("SELECT cust_procs.getsellprice(?, ?, ?) ");


        //
        // Prepare statement that gets qty buy pricing (with promo, if included)
        sql.setLength(0);
        sql.append("select ejd_cust_procs.get_sell_price(?, ?, ?, null, ?) ");
        m_GetCustQtyBuySell = conn.prepareStatement(sql.toString());

    }

    /**
     * Overloaded method that uses the previously set connection and customer id
     * and builds the text on the fly
     *
     * @throws Exception
     */
    public void sendConfirmationEmail() throws Exception {
        sendConfirmationEmail(m_EdbConn, buildEmailText());
    }

    /**
     * Private overloaded method whhich sends an email notification to the
     * appropriate recipients
     *
     * @param edbConn   Connection - a jdbc connection reference.
     * @param emailText String - email text containing order details.
     * @throws Exception - if something went wrong whilst building the email text.
     */
    private void sendConfirmationEmail(Connection edbConn, String emailText) throws Exception {
        String from = "noreply@emeryonline.com";
        String subject;

        // HtmlEmail email = new HtmlEmail();

        // email.setDebug(false);
        // email.setHostName(mailhost);

        //
        // Include ERROR notation in the subject of the email if in fact there
        // is
        // an error.
        if (m_OrderErrLine) {
            subject = "This order " + m_OrderId + " placed with Emery-Waterhouse by " + m_CustId + " contains line errors ";
        } else {
            subject = "This is to confirm order " + m_OrderId + " placed with Emery-Waterhouse by " + m_CustId + "";
        }

        if (edbConn != null) {
            try {
                m_Recipients.addAll(getEisRecipientList(edbConn, m_CustId));
                if (emailText != null && !m_Recipients.isEmpty()) {

                    emailText = "<html><body>" + emailText + "</body></html>";

                    //
                    // Web service call through jar file is causing issue in the
                    // production environment, and not in test.
                    // So, use instead DataSender class
                    // String fromAddr, String[] recipList, String subject,
                    // String body, String htmlBody

                    emailText = "<html>" + styleSettings + "<body>" + emailText + "</body></html>";

                    DataSender.smtpMP(from, m_Recipients.toArray(new String[m_Recipients.size()]), subject, buildEmailTextOld(), emailText);
					/*
					 * email.addTo(m_Recipients.toString());
					 * email.setFrom(from);
					 * 
					 * email.setSubject(subject);
					 * 
					 * email.setHtmlMsg("<html><body>" + emailText +
					 * "</body></html>"); email.setSubject(subject);
					 * 
					 * email.send();
					 */

                }
            } finally {
                edbConn = null;
            }
        }
    }
    
    /**
     * Overloaded method for backwards compatibility with new
     * sendConfirmationEmail method. gathers the recipList and
     *
     * @param edbConn   Connection - a jdbc connection reference.
     * @param custId    String - customer to whom the email is to be sent.
     * @param emailText String - email text containing order details.
     * @throws Exception - if something went wrong whilst building the email text.
     */

    public void sendConfirmationEmail(Connection edbConn, String custId, String emailText) throws Exception {
        m_Recipients.addAll(getEisRecipientList(edbConn, custId));

        if (!m_Recipients.isEmpty()) {
            sendConfirmationEmail(edbConn, custId, m_Recipients.toArray(new String[m_Recipients.size()]), emailText);
        }
    }

    /**
     * Overloaded method for backwards compatibility with new
     * sendConfirmationEmail method. gathers the recipList and
     *
     * @param edbConn   Connection - a jdbc connection reference.
     * @param custId    String - customer to whom the email is to be sent.
     * @param emailText String - email text containing order details.
     * @param HTMLText  String - email text containing order details, formatted with
     *                  HTML
     * @throws Exception - if something went wrong whilst building the email text.
     */

    public void sendConfirmationEmail(Connection edbConn, String custId, String emailText, String HTMLText) throws Exception {
        m_Recipients.addAll(getEisRecipientList(edbConn, custId));

        if (!m_Recipients.isEmpty()) {
            sendConfirmationEmail(edbConn, custId, m_Recipients.toArray(new String[m_Recipients.size()]), emailText);
        }
    }

    /**
     * Sends an email notification to the appropriate recipients
     *
     * @param conn      Connection - a jdbc connection reference.
     * @param custId    String - customer to whom the email is to be sent.
     * @param emailText String - email text containing order details.
     * @param
     * @throws Exception - if something went wrong whilst building the email text.
     */
    public void sendConfirmationEmail(Connection conn, String custId, String[] recipList, String emailText) throws Exception {
        String from = "noreply@emeryonline.com";
        String subject = "";

        //
        // Include ERROR notation in the subject of the email if in fact there
        // is
        // an error.
        if (m_OrderErrLine) {
            subject = "This order " + m_OrderId + " placed with Emery-Waterhouse by " + custId + " contains line errors ";
        } else {
            subject = "This is to confirm order " + m_OrderId + " placed with Emery-Waterhouse by " + custId + "";
        }

        if (conn != null) {
            try {
                if (emailText != null && recipList != null) {
                    //
                    // Web service call through jar file is causing issue in the
                    // production environment, and not in test.
                    // So, use instead DataSender class
                    emailText = "<html>" + styleSettings + "<body>" + emailText + "</body></html>";
                    DataSender.smtpMP(from, recipList, subject, buildEmailTextOld(), emailText);
                }
            } finally {
                conn = null;
            }
        }
    }

    /**
     * Sends an email notification to the appropriate recipients
     *
     * @param conn      Connection - a jdbc connection reference.
     * @param custId    String - customer to whom the email is to be sent.
     * @param emailText String - email text containing order details.
     * @param HTMLText  String - email text containing order details, formatted with
     *                  HTML
     * @param
     * @throws Exception - if something went wrong whilst building the email text.
     */
    public void sendConfirmationEmail(Connection conn, String custId, String[] recipList, String emailText, String HTMLText) throws Exception {
        String from = "noreply@emeryonline.com";
        String subject = "";

        //
        // Include ERROR notation in the subject of the email if in fact there
        // is
        // an error.
        if (m_OrderErrLine) {
            subject = "This order " + m_OrderId + " placed with Emery-Waterhouse by " + custId + " contains line errors ";
        } else {
            subject = "This is to confirm order " + m_OrderId + " placed with Emery-Waterhouse by " + custId + "";
        }

        if (conn != null) {
            try {
                if (emailText != null && recipList != null) {
                    //
                    // Web service call through jar file is causing issue in the
                    // production environment, and not in test.
                    // So, use instead DataSender class
                    HTMLText = "<html>" + styleSettings + "<body>" + HTMLText + "</body></html>";
                    DataSender.smtpMP(from, recipList, subject, emailText, HTMLText);
                }
            } finally {
                conn = null;
            }
        }
    }

    /**
     * Sends an email notification to the appropriate recipients
     *
     * @param conn      Connection - a jdbc connection reference.
     * @param custId    String - customer to whom the email is to be sent.
     * @param emailText String - email text containing order details.
     * @param
     * @throws Exception - if something went wrong whilst building the email text.
     */
    public void sendAceOrderConfirmationEmail(Connection conn, String custId, String[] recipList, String emailText) throws Exception {
        String from = "noreply@emeryonline.com";
        String subject = "";

        //
        // Include ERROR notation in the subject of the email if in fact there
        // is
        // an error.
        if (m_OrderErrLine) {
            subject = "This order " + m_OrderId + " placed with Emery-Waterhouse by " + custId + " contains line errors ";
        } else {
            subject = "This is to confirm order " + m_OrderId + " placed with Emery-Waterhouse by " + custId + "";
        }

        if (conn != null) {
            try {
                if (emailText != null && recipList != null) {
                    //
                    // Web service call through jar file is causing issue in the
                    // production environment, and not in test.
                    // So, use instead DataSender class
                    // TODO: create a plaintext version of the ace order
                    // confirmation like the regular order confirmation has.
                    // Until this is done we just send an empty string for the
                    // plaintext part
                    emailText = "<html>" + styleSettings + "<body>" + emailText + "</body></html>";
                    DataSender.smtpMP(from, recipList, subject, "", emailText);
                }
            } finally {
                conn = null;
            }
        }
    }

    /**
     * Sends an email notification to the appropriate recipients
     *
     * @param conn      Connection - a jdbc connection reference.
     * @param custId    String - customer to whom the email is to be sent.
     * @param emailText String - email text containing order details.
     * @param
     * @throws Exception - if something went wrong whilst building the email text.
     */
    public void sendAceShipConfirmationEmail(Connection conn, String custId, String[] recipList, String emailText) throws Exception {
        String from = "noreply@emeryonline.com";
        String subject = "";

        //
        // Include ERROR notation in the subject of the email if in fact there
        // is
        // an error.
        if (m_OrderErrLine) {
            subject = "This order " + m_OrderId + " placed with Emery-Waterhouse by " + custId + " contains line errors ";
        } else {
            subject = "Order " + m_OrderId + ", placed with Emery-Waterhouse by " + custId + ", has shipped";
        }

        if (conn != null) {
            try {
                if (emailText != null && recipList != null) {
                    //
                    // Web service call through jar file is causing issue in the
                    // production environment, and not in test.
                    // So, use instead DataSender class
                    // TODO: create a plaintext version of the ace shipment
                    // confirmation like the regular order confirmation has.
                    // Until this is done we just send an empty string for the
                    // plaintext part
                    emailText = "<html>" + styleSettings + "<body>" + emailText + "</body></html>";
                    DataSender.smtpMP(from, recipList, subject, "", emailText);
                }
            } finally {
                conn = null;
            }
        }
    }

    /**
     * Returns a String array of email recipients for the given system,
     * component, and notification level
     *
     * @param edbConn Connection - a jdbc connection reference.
     * @param custId  String - customer to whom the email is to be sent.
     * @return String[] - email recipient list for the customer.
     */
    private List<String> getEisRecipientList(Connection edbConn, String custId) {
        ResultSet rset = null;
        List<String> recipients = new LinkedList<String>();
        StringBuffer sql = new StringBuffer();
        PreparedStatement m_EMailList = null; // gets the email recipient list

        try {
            sql.setLength(0);
            sql.append("select email1, email2 ");
            sql.append("from emery_contact ");
            sql.append("join cust_contact on emery_contact.ec_id = cust_contact.ec_id ");
            sql.append("join cust_contact_type on cust_contact.cct_id = cust_contact_type.cct_id ");
            sql.append("where cust_contact_type.description = 'ORDER CONFIRMATION' and ");
            sql.append("   cust_contact.CUSTOMER_ID = ? ");
            m_EMailList = edbConn.prepareStatement(sql.toString());
            m_EMailList.setString(1, custId);

            rset = m_EMailList.executeQuery();
            edbConn.commit();

            while (rset.next()) {
                if (rset.getString("email1") != null && !rset.getString("email1").equals(""))
                    recipients.add(rset.getString("email1"));
                if (rset.getString("email2") != null && !rset.getString("email2").equals(""))
                    recipients.add(rset.getString("email2"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            //
            // Close result set
            if (rset != null) {
                try {
                    rset.close();
                } catch (SQLException se) {
                }

                rset = null;
            }

            //
            // Close prepared statement
            if (m_EMailList != null) {
                try {
                    m_EMailList.close();
                } catch (SQLException se) {
                }

                m_EMailList = null;
            }

            sql = null;
        }

        return recipients;
    }

    /**
     * Gets the sell price for the current item and customer.
     *
     * @param itemEaId   int - the item ea id
     * @param m_Quantity int - qty of the item ordered.
     * @return double - the sell price of the item
     */
    private double getSellPrice(int itemEaId, int m_Quantity, int warehouseId) {
        double price;
        ResultSet rs;

        //
        // Get customer specific sell price.
        try {
            if (m_Quantity > 0) { // Qty buy pricing change
                m_GetCustQtyBuySell.setString(1, m_CustId);
                m_GetCustQtyBuySell.setInt(2, itemEaId);
                m_GetCustQtyBuySell.setNull(3, Types.VARCHAR);
                m_GetCustQtyBuySell.setInt(4, m_Quantity);
                rs = m_GetCustQtyBuySell.executeQuery();

                rs.next();
                price = rs.getDouble(1);
                rs.close();
            } else {
                m_GetCustSell.setString(1, m_CustId);
                m_GetCustSell.setInt(2, itemEaId);
                rs = m_GetCustSell.executeQuery();

                rs.next();
                price = rs.getDouble(1);
                rs.close();
            }
        } catch (Exception e) {
            //
            // If any exception occurred, get the regular base cost
            try {
                m_GetSell.setInt(1, itemEaId);
                m_GetSell.setInt(2, warehouseId);
                rs = m_GetSell.executeQuery();
                rs.next();
                price = rs.getDouble(1);
                rs.close();
            } catch (Exception e2) {
                price = 0.0;
            }
        }

        return price;
    }

    /**
     * Sets the EIS oracle connection
     *
     * @param conn Connection - the EIS Oracle connection
     */
    public void setConnection(Connection conn) {
        m_EdbConn = conn;
    }

    /**
     * Sets the customer id
     *
     * @param custId String - the customer id
     */
    public void setCustomerId(String custId) {
        m_CustId = custId;
    }

    /**
     * Sets the order id
     *
     * @param orderId long - the order id
     */
    public void setOrderId(long orderId) {
        m_OrderId = orderId;
    }

    /**
     * @return the m_Recipients
     */
    public List<String> getRecipients() {
        return m_Recipients;
    }

    /**
     * @param m_Recipients the m_Recipients to set
     */
    public void setRecipients(List<String> m_Recipients) {
        this.m_Recipients = m_Recipients;
    }

}