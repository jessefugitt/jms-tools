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
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProducerTool implements Runnable {
    static Logger LOGGER = LoggerFactory.getLogger(ProducerTool.class);

    private int acknowledgeMode = Session.AUTO_ACKNOWLEDGE; //a
    private String clientId = null; //c
    private boolean durable = false; //d
    private int perMessageSleepMS = 0; //e
    private String connectionFactoryName = "connectionFactory"; //f
    private int numThreads = 1; //g
    private boolean jndiLookupDestinations = false; //j
    private int bytesLength = -1; //l
    private int numMessages = 1; //m
    private String destinationName = "DESTINATION.123"; //n
    boolean useFinalControlMessage = false; //o
    private boolean useTemporaryDestinations = false; //p
    private boolean useQueueDestinations = true; //q
    private boolean transacted = false; //t
    private String messageGroupId = null; //x
    private int batchSize = 1; //z

    private Context context;
    private ConnectionFactory connectionFactory;

    public static void main(String[] args) throws NamingException, InterruptedException {
        LOGGER.info("Starting ProducerTool");
        ProducerTool producerTool = new ProducerTool();
        producerTool.parseCommandLine(args);
        producerTool.setupContextAndConnectionFactory();

        if(producerTool.numThreads > 1) {
            ExecutorService executor = Executors.newFixedThreadPool(producerTool.numThreads);
            for(int t = 0; t < producerTool.numThreads; t++) {
                executor.submit(producerTool);
            }
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        } else {
            producerTool.run();
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


            MessageProducer producer = session.createProducer(destination);
            if(durable) {
                producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            } else {
                producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            }

            int numMessagesToSend = useFinalControlMessage ? numMessages - 1 : numMessages;

            for (int i = 0; i < numMessagesToSend; i++) {
                String messageText = "Message " + i + " at " + new Date();
                if(bytesLength > -1) {
                    byte[] messageTextBytes = messageText.getBytes(StandardCharsets.UTF_8);
                    BytesMessage bytesMessage = session.createBytesMessage();
                    bytesMessage.writeBytes(messageTextBytes);
                    if(messageTextBytes.length < bytesLength) {
                        byte[] paddingBytes = new byte[bytesLength - messageTextBytes.length];
                        bytesMessage.writeBytes(paddingBytes);
                    }
                    producer.send(bytesMessage);
                } else {
                    LOGGER.info("Sending message: " + messageText);
                    TextMessage textMessage = session.createTextMessage(messageText);
                    if (messageGroupId != null) {
                        textMessage.setStringProperty("JMSXGroupID", messageGroupId);
                    }
                    producer.send(textMessage);
                }

                if (perMessageSleepMS > 0) {
                    Thread.sleep(perMessageSleepMS);
                }
                if (transacted == true) {
                    if ((i + 1) % batchSize == 0) {
                        session.commit();
                    }
                }
            }
            if (useFinalControlMessage) {
                Message message = session.createMessage();
                if(messageGroupId != null) {
                    message.setStringProperty("JMSXGroupID", messageGroupId);
                }
                producer.send(message);
                if (transacted == true) {
                    session.commit();
                }
            }
            producer.close();
        } catch (Exception ex) {
            LOGGER.error("ProducerTool hit exception: " + ex.getMessage(), ex);
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
                    LOGGER.error("JMSException closing session", e);
                }
            }
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

        Option bytesLengthOpt = OptionBuilder.withLongOpt("bytes-message-length")
                .withArgName("length")
                .hasArg()
                .withDescription("use a bytes message of a specific length")
                .create("l");
        options.addOption(bytesLengthOpt);

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

        Option controlOpt = OptionBuilder.withLongOpt("final-control-message")
                .withDescription("use a control message as the final message")
                .create("o");
        options.addOption(controlOpt);

        Option tempOpt = OptionBuilder.withLongOpt("temporary-destination")
                .withDescription("use a temporary destination")
                .create("p");
        options.addOption(tempOpt);

        Option useQueueOpt = OptionBuilder.withLongOpt("queue-destination")
                .withDescription("use a queue destination")
                .create("q");
        options.addOption(useQueueOpt);

        Option transOpt = OptionBuilder.withLongOpt("transacted")
                .withDescription("use a transacted session")
                .create("t");
        options.addOption(transOpt);

        Option groupIdOpt = OptionBuilder.withLongOpt("message-group-id")
                .withArgName("groupId")
                .hasArg()
                .withDescription("JMSXGroupID")
                .create("x");
        options.addOption(groupIdOpt);

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
                formatter.printHelp("ProducerTool", options, true);
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

            if(line.hasOption("l")) {
                bytesLength = Integer.parseInt(line.getOptionValue("l"));
            }

            if(line.hasOption("m")) {
                numMessages = Integer.parseInt(line.getOptionValue("m"));
            }

            if(line.hasOption("n")) {
                destinationName = line.getOptionValue("n");
            }

            if(line.hasOption("o")) {
                 useFinalControlMessage = true;
            }

            if(line.hasOption("p")) {
                useTemporaryDestinations = true;
            }

            if(line.hasOption("q")) {
                useQueueDestinations = true;
            }

            if (line.hasOption("t")) {
                transacted = true;
            }

            if(line.hasOption("x")) {
                messageGroupId = line.getOptionValue("x");
            }

            if(line.hasOption("z")) {
                batchSize = Integer.parseInt(line.getOptionValue("z"));
            }

        } catch (ParseException exp) {
            LOGGER.error("Commandline parsing exception: " + exp.getMessage(), exp);
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("ProducerTool", options, true);
            System.exit(-1);
        }
    }


}