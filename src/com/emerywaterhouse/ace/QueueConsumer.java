package com.emerywaterhouse.ace;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class QueueConsumer extends DefaultConsumer 
{
   AceJobProc m_Proc;
   
   /**
    * Default constructor
    * @param channel
    */
   public QueueConsumer(Channel channel) 
   {
      super(channel);      
   }

   @Override
   public void handleDelivery(String consumerTag, Envelope env, BasicProperties props, byte[] body)
   {
      long deliveryTag = env.getDeliveryTag();
      
      try {
         if ( body != null ) {            
            getChannel().basicAck(deliveryTag, false);            
            m_Proc.setData(body);
            m_Proc.processData();            
         }
      }
      
      catch ( Exception ex ) {
         
      }
   }
   
   /**
    * Sets the instance of the owning job processor.
    * @param proc The job that this consumer is associated with.
    */
   public void setProcessor(AceJobProc proc)
   {
      m_Proc = proc;
   }
}
