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

import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.jms.listener.AbstractJmsListeningContainer;

public class ConsumerApplication {
    public static void main(String[] args) {
        AbstractApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
        context.registerShutdownHook();


        boolean sync = true;
        if(args != null && args.length > 0) {
            sync = Boolean.valueOf(args[0]);
        }

        if(sync == true) {
            //Example of a sync consumer with Spring JMS
            SpringJmsConsumer consumer = (SpringJmsConsumer) context.getBean("springJmsConsumer");
            consumer.run();
            ((org.springframework.jms.connection.CachingConnectionFactory) context.getBean("connectionFactory")).resetConnection();
        } else {
            //Example of an async consumer with Spring JMS (autoStartup is normally set to true)
            AbstractJmsListeningContainer listenerContainer = (AbstractJmsListeningContainer) context.getBean("listenerContainer");
            listenerContainer.start();
        }
    }
}
