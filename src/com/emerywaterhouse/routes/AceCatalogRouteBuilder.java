package com.emerywaterhouse.routes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

public class AceCatalogRouteBuilder extends RouteBuilder {
       private String env = System.getProperty("server.env");

       public String getEnv() {
          return env;
       }

       public void setEnv(String env) {
          this.env = env;
       }

       // note 86400000 is the number of milliseconds in a day. this route fires at
       // 4am and repeats every 24 hours
       private String ftpEndpointURL = "ftp://kftp@ftp.emeryonline.com//home/acehw/in?password=S0ylentGr33n&delay=60000"
             + "&stepwise=false&ignoreFileNotFoundOrPermissionError=true&delete=true&readLock=true&binary=true";

       private String DEVftpEndpointURL = "ftp://kftp@ftp.emeryonline.com//home/acedev/in?password=S0ylentGr33n&delay=60000"
             + "&stepwise=false&ignoreFileNotFoundOrPermissionError=true&delete=true&readLock=true&binary=true";

       private final String PRODRMQEndpoint = "rabbitmq:pmb125:5672/ace_items?username=pubsub&password=Rule#1Cardio&routingKey=item";
      
       private final String DEVRMQEndpoint = "rabbitmq:tmb128:5672/ace_items?username=pubsub&password=pubsub&routingKey=item";
       
       private String RMQEndpoint; 
       
       
       /*
        * Leaving this in the file for now because of the weirdness associated with
        * the ace account.
        * "ftp://acehw@ftp.emeryonline.com/home/acehw/in?password=RAW(IqYTe&lh)&delay=60000"
        * + "&stepwise=false&ignoreFileNotFoundOrPermissionError=true&delete=true";
        * //&readLock=true&binary=true
        */

       @Override
       public void configure() throws Exception {
          //
          // Write the files to disk. Send the working copy to a temp directory
          // first so we don't have to worry about
          // record locking. The intermediate step will move them to the correct
          // working directory.
          // Run daily at 4am.
          if (env.equalsIgnoreCase("prod")) {
             RMQEndpoint =  PRODRMQEndpoint;
              
             from(ftpEndpointURL)
                   .routeId("ace.ftp.in")
                   .process(new Processor() {
                      @Override
                      public void process(Exchange ex) throws Exception {
                         // mutate file name to include directory structure sep'd by
                         // '.'
                         // files come in with same name
                         ex.getIn().setHeader(
                               "CamelFileName",
                               ((String) ex.getIn().getHeader("CamelFileName"))
                                     .replace('/', '.'));
                      }
                   }).multicast()
                   .to("file:///var/procsrv/work/temp", "direct:archive");
          } else {
             RMQEndpoint = DEVRMQEndpoint;
              
             from(DEVftpEndpointURL)
                   .routeId("ace.ftp.dev.in")
                   .noAutoStartup()
                   .process(new Processor() {
                      @Override
                      public void process(Exchange ex) throws Exception {
                         // mutate file name to include directory structure sep'd by
                         // '.'
                         // files come in with same name
                         ex.getIn().setHeader(
                               "CamelFileName",
                               ((String) ex.getIn().getHeader("CamelFileName"))
                                     .replace('/', '.'));
                      }
                   }).multicast()
                   .to("file:///var/procsrv/work/temp", "direct:archive");
          }
          //
          // Move the files from the temporary holding area to the work directory
          from("file:///var/procsrv/work/temp").routeId("ace.file.move-tmp").to(
                "file:///var/procsrv/work/ace");

          //
          //
          from("direct:archive").routeId("ace.direct.archive")
                .process(new Processor() {
                   @Override
                   public void process(Exchange ex) throws Exception {
                      // mutate file name to include date as a leading directory,
                      // allows for archiving of dup filenames
                      ex.getIn().setHeader(
                            "CamelFileName",
                            (simple("${date:now:yyyyMMdd}").evaluate(ex,
                                  String.class)
                                  + "/" + (String) ex.getIn().getHeader(
                                  "CamelFileName")));
                   }
                }).to("file:///var/procsrv/archive/ftpin");

          //
          // Process any files that show up in the Ace work directory. The XML file
          // is the catalog
          from("file:///var/procsrv/work/ace")
                .routeId("ace.file.work")
                .choice()
                .when(header("CamelFileNameOnly").isEqualTo(
                        "test.xml"))
                .convertBodyTo(String.class)
                .to("direct:acexml")
                .when(header("CamelFileNameOnly").contains(".csv"))
                .convertBodyTo(String.class)
                .to("direct:acecsv")
                .otherwise()
                .log(LoggingLevel.WARN,
                      "wrong file type "
                            + header("CamelFileNameOnly").convertToString());

          //
          // The choice "to" method from split doesn't allow for a choice so we need
          // to use a separate pipe for this.
          // We need to know when the process is complete so we can notify the
          // service in procsrv.
          from("direct:acexml").routeId("ace.direct.xml.amq").onCompletion()
                .log(LoggingLevel.INFO, "ACE file preprocessing complete").end()
                .split().method("parseXML", "parse").streaming()
                .setHeader("rabbitmq.ROUTING_KEY", constant("item"))
                .setHeader("rabbitmq.EXCHANGE_NAME", constant("ace.items"))
                .to(RMQEndpoint)
                .choice()
                .when(property("CamelSplitComplete").isEqualTo("true"))
                .log(LoggingLevel.INFO, "[AceRoutes#AceCatalogFile]Split ${property.CamelSplitSize} catalog item records from ace file.");

          //
          // Not sure what to do with this yet. Probably send it to the report
          // server so it can generate an excel spreadsheet
          // of the data and email it to merchandising.
          from("direct:acecsv").routeId("ace.direct.csv.out").to("mock:output");
       }
    }
