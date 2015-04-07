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

import java.util.Date;

public class SpringJmsProducer {

    private JmsTemplate jmsTemplate;
    public void setJmsTemplate(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    public void run() {
        for(int i = 0; i < 5; i++) {
            String messageText = "Message " + i + " at " + new Date();
            System.out.println("Sending Text Message: " + messageText);
            //Sends to the default destination configured in JmsTemplate (other send methods also take a destination param)
            jmsTemplate.convertAndSend(messageText);
        }
    }
}
