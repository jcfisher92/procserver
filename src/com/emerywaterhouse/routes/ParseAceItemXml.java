package com.emerywaterhouse.routes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ParseAceItemXml {
	
	
	public List<String> parse(String body) throws SAXException, IOException, ParserConfigurationException {
		
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
}

