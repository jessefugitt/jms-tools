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
package tools;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsumerTool implements Runnable {
    static Logger LOGGER = LoggerFactory.getLogger(ConsumerTool.class);

    private int acknowledgeMode = Session.AUTO_ACKNOWLEDGE; //a
    private boolean useQueueBrowser = false;
    private String clientId = null; //c
    private boolean durable = false; //d
    private int perMessageSleepMS = 0; //e
    private String connectionFactoryName = "connectionFactory"; //f
    private int numThreads = 1; //g
    private boolean jndiLookupDestinations = false; //j
    private String selector = null; //k
    private int numMessages = 1; //m
    private String destinationName = "DESTINATION.123"; //n
    private boolean useTemporaryDestinations = false; //p
    private boolean useQueueDestinations = false; //q
    private int receiveTimeoutMS = 5000; //r
    private String subscriptionName = "Subscription123"; //s
    private boolean transacted = false; //t
    private boolean useAsyncListener = false; //y
    private int batchSize = 1; //z

    private Context context;
    private ConnectionFactory connectionFactory;

    public static void main(String[] args) throws NamingException, InterruptedException {
        LOGGER.info("Starting ConsumerTool");
        ConsumerTool consumerTool = new ConsumerTool();
        consumerTool.parseCommandLine(args);
        consumerTool.setupContextAndConnectionFactory();

        if(consumerTool.numThreads > 1) {
            ExecutorService executor = Executors.newFixedThreadPool(consumerTool.numThreads);
            for(int t = 0; t < consumerTool.numThreads; t++) {
                executor.submit(consumerTool);
            }
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } else {
            consumerTool.run();
        }
    }

    public void setupContextAndConnectionFactory() throws NamingException {
        context = new InitialContext();
        connectionFactory = (ConnectionFactory) context.lookup(connectionFactoryName);

    }

    @Override
    public void run() {
        Connection connection = null;
        Session session = null;
        try {
            connection = connectionFactory.createConnection();
            if (clientId != null) {
                connection.setClientID(clientId);
            }
            connection.start();
            session = connection.createSession(transacted, acknowledgeMode);
            Destination destination = null;
            if (jndiLookupDestinations) {
                destination = (Destination) context.lookup(destinationName);
            } else {
                if (useQueueDestinations) {
                    if (useTemporaryDestinations) {
                        destination = session.createTemporaryQueue();
                    } else {
                        destination = session.createQueue(destinationName);
                    }
                } else {
                    if (useTemporaryDestinations) {
                        destination = session.createTemporaryTopic();
                    } else {
                        destination = session.createTopic(destinationName);
                    }
                }
            }

            MessageConsumer consumer = null;
            QueueBrowser browser = null;
            if(useQueueDestinations == false) {
                if(durable == true) {
                    if (selector != null) {
                        consumer = session.createDurableSubscriber((Topic) destination, subscriptionName, selector, false);
                    } else {
                        consumer = session.createDurableSubscriber((Topic) destination, subscriptionName);
                    }
                } else {
                    if (selector != null) {
                        consumer = session.createConsumer(destination, selector);
                    } else {
                        consumer = session.createConsumer(destination);
                    }
                }
            } else {
                if(useQueueBrowser) {
                    if (selector != null) {
                        browser = session.createBrowser((Queue) destination, selector);
                    } else {
                        browser = session.createBrowser((Queue) destination);
                    }
                } else {
                    if (selector != null) {
                        consumer = session.createConsumer(destination, selector);
                    } else {
                        consumer = session.createConsumer(destination);
                    }
                }
            }

            if (useAsyncListener) {
                final Session consumerSession = session;

                final AtomicInteger perConsumerReceivedMessages = new AtomicInteger(0);
                consumer.setMessageListener(new MessageListener() {

                    @Override
                    public void onMessage(Message message) {
                        perConsumerReceivedMessages.incrementAndGet();
                        handleMessage(consumerSession, message, perConsumerReceivedMessages.get());
                    }
                });
                while (perConsumerReceivedMessages.get() < numMessages) {
                    Thread.sleep(100);
                }
            } else if(useQueueBrowser) {
                Enumeration messages = browser.getEnumeration();
                int perConsumerReceivedMessages = 0;
                while (perConsumerReceivedMessages < numMessages) {
                    if(messages != null) {
                        while (messages.hasMoreElements()){
                            Message message = (Message) messages.nextElement();
                            if (message != null) {
                                perConsumerReceivedMessages++;
                                handleMessage(session, message, perConsumerReceivedMessages);
                            }
                        }
                    }
                    Thread.sleep(receiveTimeoutMS);
                    messages = browser.getEnumeration();
                }
            } else {
                int perConsumerReceivedMessages = 0;
                while (perConsumerReceivedMessages < numMessages) {

                    Message message = null;
                    if (receiveTimeoutMS > -1) {
                        message = consumer.receive(receiveTimeoutMS);
                    } else {
                        message = consumer.receive();
                    }

                    if (message != null) {
                        perConsumerReceivedMessages++;
                        handleMessage(session, message, perConsumerReceivedMessages);
                    }
                }
            }
            consumer.close();
        } catch (Exception ex) {
            LOGGER.error("ConsumerTool hit exception:" + ex.getMessage(), ex);
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (JMSException e) {
                    LOGGER.error("JMSException closing session", e);
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException e) {
                    LOGGER.error("JMSException closing connection", e);
                }
            }
        }
    }

    public void handleMessage(Session session, Message message, int perConsumerReceivedMessages) {
        try {
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                String text = textMessage.getText();
                LOGGER.info("Received text message: " + text);
            } else {
                LOGGER.info("Received message: " + message);
            }
            if (perMessageSleepMS > 0) {
                try {
                    Thread.sleep(perMessageSleepMS);
                } catch (InterruptedException e) {
                    LOGGER.debug("Interrupted while sleeping", e);
                }
            }
            if (acknowledgeMode == Session.CLIENT_ACKNOWLEDGE) {
                if (perConsumerReceivedMessages % batchSize == 0) {
                    message.acknowledge();
                }
            }
            if (transacted == true) {
                if (perConsumerReceivedMessages % batchSize == 0) {
                    session.commit();
                }
            }
        } catch (JMSException e) {
            LOGGER.error("JMSException handling message" + e.getMessage(), e);
        }
    }

    public void parseCommandLine(String[] args) {
        CommandLineParser parser = new PosixParser();

        Options options = new Options();

        Option ackModeOpt = OptionBuilder.withLongOpt("acknowledgement-mode")
                .withArgName("ackMode")
                .hasArg()
                .withDescription("session acknowledgement mode: AUTO_ACKNOWLEDGE, CLIENT_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE")
                .create("a");
        options.addOption(ackModeOpt);

        Option browserOpt = OptionBuilder.withLongOpt("queue-browser")
                .withDescription("create a queue browser")
                .create("b");
        options.addOption(browserOpt);

        Option clientIdOpt = OptionBuilder.withLongOpt("client-id")
                .withArgName("id")
                .hasArg()
                .withDescription("client id string that can optionally be set on a connection")
                .create("c");
        options.addOption(clientIdOpt);

        Option durableOpt = OptionBuilder.withLongOpt("durable")
                .withDescription("create a durable subscriber")
                .create("d");
        options.addOption(durableOpt);

        Option perMessageSleepOpt = OptionBuilder.withLongOpt("per-message-sleep")
                .withArgName("millis")
                .hasArg()
                .withDescription("amount of time (in ms) to sleep after receiving each message")
                .create("e");
        options.addOption(perMessageSleepOpt);

        Option connFactOpt = OptionBuilder.withLongOpt("connection-factory-name")
                .withArgName("name")
                .hasArg()
                .withDescription("name of the connection factory to lookup")
                .create("f");
        options.addOption(connFactOpt);

        Option numThreadsOpt = OptionBuilder.withLongOpt("num-threads")
                .withArgName("num")
                .hasArg()
                .withDescription("number of threads to run in parallel each with a connection")
                .create("g");
        options.addOption(numThreadsOpt);

        Option helpOpt = OptionBuilder.withLongOpt("help")
                .withDescription("show help")
                .create("h");
        options.addOption(helpOpt);

        Option jndiDestOpt = OptionBuilder.withLongOpt("jndi-lookup-destination")
                .withDescription("lookup destinations with jndi")
                .create("j");
        options.addOption(jndiDestOpt);

        Option selectorOpt = OptionBuilder.withLongOpt("message-selector")
                .withArgName("selector")
                .hasArg()
                .withDescription("message selector to use when creating consumer")
                .create("k");
        options.addOption(selectorOpt);

        Option numMessagesOpt = OptionBuilder.withLongOpt("num-messages")
                .withArgName("num")
                .hasArg()
                .withDescription("number of messages to receive before stopping")
                .create("m");
        options.addOption(numMessagesOpt);

        Option destNameOpt = OptionBuilder.withLongOpt("destination-name")
                .withArgName("name")
                .hasArg()
                .withDescription("name of the destination to receive from")
                .create("n");
        options.addOption(destNameOpt);

        Option tempOpt = OptionBuilder.withLongOpt("temporary-destination")
                .withDescription("use a temporary destination")
                .create("p");
        options.addOption(tempOpt);

        Option useQueueOpt = OptionBuilder.withLongOpt("queue-destination")
                .withDescription("use a queue destination")
                .create("q");
        options.addOption(useQueueOpt);

        Option receiveTimeoutOpt = OptionBuilder.withLongOpt("receive-timeout")
                .withArgName("millis")
                .hasArg()
                .withDescription("blocking receive timeout (-1 indicates blocking receive call with no timeout)")
                .create("r");
        options.addOption(receiveTimeoutOpt);

        Option subscriptionOpt = OptionBuilder.withLongOpt("subscription-name")
                .withArgName("subName")
                .hasArg()
                .withDescription("subscription name to use when creating a durable subscriber")
                .create("s");
        options.addOption(subscriptionOpt);

        Option transOpt = OptionBuilder.withLongOpt("transacted")
                .withDescription("use a transacted session")
                .create("t");
        options.addOption(transOpt);

        Option asyncOpt = OptionBuilder.withLongOpt("async-listener")
                .withDescription("use an async message listener instead of consumer receive calls")
                .create("y");
        options.addOption(asyncOpt);

        Option batchSizeOpt = OptionBuilder.withLongOpt("batch-size")
                .withArgName("size")
                .hasArg()
                .withDescription("size of the batch to ack or commit when using client ack mode or transacted sessions")
                .create("z");
        options.addOption(batchSizeOpt);




        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);

            if (line.hasOption("h")) {
                // automatically generate the help statement
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("ConsumerTool", options, true);
                System.exit(0);
            }

            if(line.hasOption("a")) {
                String ackModeStr = line.getOptionValue("a");
                if(ackModeStr.equalsIgnoreCase("AUTO_ACKNOWLEDGE")) {
                    acknowledgeMode = Session.AUTO_ACKNOWLEDGE;
                } else if(ackModeStr.equalsIgnoreCase("CLIENT_ACKNOWLEDGE")) {
                    acknowledgeMode = Session.CLIENT_ACKNOWLEDGE;
                } else if(ackModeStr.equalsIgnoreCase("DUPS_OK_ACKNOWLEDGE")) {
                    acknowledgeMode = Session.DUPS_OK_ACKNOWLEDGE;
                } else if(ackModeStr.equalsIgnoreCase("SESSION_TRANSACTED")) {
                    acknowledgeMode = Session.SESSION_TRANSACTED;
                } else {
                    throw new ParseException("Invalid value for acknowledge mode: " + ackModeStr);
                }
            }

            if(line.hasOption("b")) {
                useQueueBrowser = true;
            }

            if(line.hasOption("c")) {
                clientId = line.getOptionValue("c");
            }

            if(line.hasOption("d")) {
                durable = true;
            }

            if(line.hasOption("e")) {
                perMessageSleepMS = Integer.parseInt(line.getOptionValue("e"));
            }

            if(line.hasOption("f")) {
                connectionFactoryName = line.getOptionValue("f");
            }

            if(line.hasOption("g")) {
                numThreads = Integer.parseInt(line.getOptionValue("g"));
            }

            if (line.hasOption("j")) {
                jndiLookupDestinations = true;
            }

            if(line.hasOption("k")) {
                selector = line.getOptionValue("k");
            }

            if(line.hasOption("m")) {
                numMessages = Integer.parseInt(line.getOptionValue("m"));
            }

            if(line.hasOption("n")) {
                destinationName = line.getOptionValue("n");
            }

            if(line.hasOption("p")) {
                useTemporaryDestinations = true;
            }

            if(line.hasOption("q")) {
                useQueueDestinations = true;
            }

            if(line.hasOption("r")) {
                receiveTimeoutMS = Integer.parseInt(line.getOptionValue("r"));
            }

            if(line.hasOption("s")) {
                subscriptionName = line.getOptionValue("s");
            }

            if (line.hasOption("t")) {
                transacted = true;
            }

            if(line.hasOption("y")) {
                useAsyncListener = true;
            }

            if(line.hasOption("z")) {
                batchSize = Integer.parseInt(line.getOptionValue("z"));
            }

        } catch (ParseException exp) {
            LOGGER.error("Commandline parsing exception: " + exp.getMessage(), exp);
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("ConsumerTool", options, true);
            System.exit(-1);
        }
    }
}