package com.emerywaterhouse.routes;


import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.jndi.JndiContext;

import com.emerywaterhouse.server.ConfLoader;
import com.emerywaterhouse.server.ProcessApplication;
import com.emerywaterhouse.server.ProcessServer;

public class CamelApp extends ProcessApplication implements ConfLoader {

    private static final String confFileName = "camelproc.xml";
    private CamelContext context;
    
    private JndiContext jndi;
            
    /**
     * Default constructor. Initialize the application.
     */
    public CamelApp() 
    {
       m_Name = "CamelApp";
       m_MaxProcCount = 1;


       loadConf();
       ProcessServer.registerConfObj(this, confFileName);
    }

    /**
     * Clean up anything we created.
     * 
     * @throws Throwable
     */
    @Override
    public void finalize() throws Throwable 
    {
       m_Thread = null;
       context.stop();
       super.finalize();
    }
    
    @Override
    public void loadConf() {
        try {
            System.setProperty("org.apache.camel.jmx.mbeanServerDefaultDomain", "ProcessServerMgmt");
            jndi = new JndiContext();
            jndi.bind("parseXML", new ParseAceItemXml());
            context = new DefaultCamelContext(jndi);
            context.addRoutes(new AceCatalogRouteBuilder());
        } catch (Exception ex) {
            ProcessServer.log.warn("[CamelApp] Ace Catalog Camel routes could not be added : ", ex);
        }
        
        
    }   
    
    /**
     * Creates the internal thread and initializes the internal db jobs Reads
     * the applications properties and check for a list of jobs that should be
     * run.
     *
     * @throws Exception
     */
    @Override
    public void start() throws Exception 
    {
       m_Status = ProcessServer.init;
       context.start();
       super.start();
    }


    /**
     * Stops the processing and closes the queue connection.
     */
    @Override
    public void stop() 
    {
        try {
            context.stop();
        } catch (Exception ex) {
            ProcessServer.log.warn("[CamelApp] Camel could not be stopped : " + ex.getMessage());
        }
    }

}
