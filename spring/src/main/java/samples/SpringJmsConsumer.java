/*
 * Copyright 2015 Jesse Fugitt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package samples;

import org.springframework.jms.core.JmsTemplate;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.util.Date;

public class SpringJmsConsumer implements MessageListener {

    private JmsTemplate jmsTemplate;
    public void setJmsTemplate(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void run() {
        int numMessages = 0;
        int totalMessages = 5;
        while(numMessages < totalMessages) {
            //Receives from the default destination configured in JmsTemplate
            Message message = jmsTemplate.receive();
            if(message != null) {
                onMessage(message);
                numMessages++;
            } else {
                System.out.println("Waiting to receive " + totalMessages + " messages...");
            }
        }
    }



    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                System.out.println("Received Text Message: " + textMessage.getText());
            } else {
                System.out.println("Received Non-Text Message: " + message);
            }
        } catch(JMSException ex) {
            ex.printStackTrace();
        }
    }
}
