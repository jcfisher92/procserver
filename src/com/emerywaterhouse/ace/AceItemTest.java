package com.emerywaterhouse.ace;

import com.emerywaterhouse.ace.catalog.AceItemBean;
import com.emerywaterhouse.ace.catalog.DataModule;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by ebrownewell on 10/25/2018. ©Emery|Waterhouse
 */
public class AceItemTest {

    private static Logger log = Logger.getLogger(AceItemTest.class.getName());

    public static void main(String... args) throws ParserConfigurationException, SAXException, IOException {
        BasicConfigurator.configure();

        String source = loadResourceToString("test.xml");

        Connection con = getPgConnection(true);

        List<String> beans = parse(source);

        DataModule dm = new DataModule();

        dm.setConnection(con);
        dm.setLogger(log);

        for(String bean : beans){
            AceItemBean testBean = new AceItemBean(bean);

            try {
                int res = dm.addDbRecord(testBean);
                log.info("Item " + testBean.getAceSku() + ", Res: " + res);
            } catch (Exception e) {
                log.error("Caught exception for test bean " + testBean, e);
            }
        }

    }

    public static List<String> parse(String body) throws SAXException, IOException, ParserConfigurationException {

        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(new InputSource(new ByteArrayInputStream(body.getBytes("utf-8"))));
        // normalize text representation
        doc.getDocumentElement().normalize();

        DOMImplementationLS ls = (DOMImplementationLS) doc.getImplementation();
        LSSerializer ser = ls.createLSSerializer();
        ser.getDomConfig().setParameter("xml-declaration", false);
        // End document intake
        NodeList listOfItems = doc.getElementsByTagName("eme:catalogItem");			// Creates NodeList of all cata
        LinkedList<String> nodes = new LinkedList<String>();
        for (int i = 0 ; i < listOfItems.getLength(); ++i) {
            Node n = listOfItems.item(i);
            nodes.add(ser.writeToString(n));
        }
        nodes.add("end:item.xml");
        return nodes;
    }

    private static java.sql.Connection getPgConnection(boolean useProd) {
        java.sql.Connection con = null;

        java.util.Properties conProps = new java.util.Properties();
        conProps.put("user", "ejd");
        conProps.put("password", "boxer");

        try {
            if (useProd) {
                con = java.sql.DriverManager.getConnection("jdbc:edb://172.30.1.33/emery_jensen", conProps);
            } else {
                con = java.sql.DriverManager.getConnection("jdbc:edb://10.128.0.11/emery_jensen", conProps);
            }
        } catch (SQLException e) {
            log.error("Error getting connection from postgres. ", e);
        }


        return con;
    }

    private static String loadResourceToString(final String path) {
        final InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        try {
            return IOUtils.toString(stream);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

}
