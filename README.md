# jms-tools
A set of standard JMS (Java Message Service) tools.

Spring Module
------
Simple Spring JMS Producer and Consumer applications that use a JMSTemplate and applicationContext.xml.

#### Build
    $ cd spring
    $ ./gradlew build
#### Run
    $ ./gradlew ConsumerApplication
    $ ./gradlew ProducerApplication
    
Utility Module
-------
Command line driven Producer and Consumer tools that can exercise many of the common JMS APIs.

#### Build
    $ cd utility
    $ ./gradlew build
#### Run
    $ ./gradlew ConsumerTool
    $ ./gradlew ProducerTool
#### Help
    $ ./gradlew ConsumerTool -PArgs="--help"
    $ ./gradlew ProducerTool -PArgs="--help"
#### Examples
###### Queue Example (100 persistent messages to/from a Queue named QueueABC)
    $ ./gradlew ConsumerTool -PArgs="-q -m 100 -n QueueABC"
    $ ./gradlew ProducerTool -PArgs="-d -q -m 100 -n QueueABC"
###### Topic Example (10 non-persistent messages to/from a Topic named TopicDEF with a durable subscriber)
    $ ./gradlew ConsumerTool -PArgs="-c MyClientId -d -m 10 -n TopicDEF -s Subscription123"
    $ ./gradlew ProducerTool -PArgs="-m 10 -n TopicDEF"

#### Reference
##### ConsumerTool Usage
```
usage: ConsumerTool [-a <ackMode>] [-b] [-c <id>] [-d] [-e <millis>] [-f
       <name>] [-g <num>] [-h] [-j] [-k <selector>] [-m <num>] [-n <name>]
       [-p] [-q] [-r <millis>] [-s <subName>] [-t] [-y] [-z <size>]
 -a,--acknowledgement-mode <ackMode>   session acknowledgement mode:
                                       AUTO_ACKNOWLEDGE,
                                       CLIENT_ACKNOWLEDGE,
                                       DUPS_OK_ACKNOWLEDGE
 -b,--queue-browser                    create a queue browser
 -c,--client-id <id>                   client id string that can
                                       optionally be set on a connection
 -d,--durable                          create a durable subscriber
 -e,--per-message-sleep <millis>       amount of time (in ms) to sleep
                                       after receiving each message
 -f,--connection-factory-name <name>   name of the connection factory to
                                       lookup
 -g,--num-threads <num>                number of threads to run in
                                       parallel each with a connection
 -h,--help                             show help
 -j,--jndi-lookup-destination          lookup destinations with jndi
 -k,--message-selector <selector>      message selector to use when
                                       creating consumer
 -m,--num-messages <num>               number of messages to receive
                                       before stopping
 -n,--destination-name <name>          name of the destination to receive
                                       from
 -p,--temporary-destination            use a temporary destination
 -q,--queue-destination                use a queue destination
 -r,--receive-timeout <millis>         blocking receive timeout (-1
                                       indicates blocking receive call
                                       with no timeout)
 -s,--subscription-name <subName>      subscription name to use when
                                       creating a durable subscriber
 -t,--transacted                       use a transacted session
 -y,--async-listener                   use an async message listener
                                       instead of consumer receive calls
 -z,--batch-size <size>                size of the batch to ack or commit
                                       when using client ack mode or
                                       transacted sessions
```

##### ProducerTool Usage
```
usage: ProducerTool [-a <ackMode>] [-c <id>] [-d] [-e <millis>] [-f
       <name>] [-g <num>] [-h] [-j] [-l <length>] [-m <num>] [-n <name>]
       [-o] [-p] [-q] [-t] [-x <groupId>] [-z <size>]
 -a,--acknowledgement-mode <ackMode>   session acknowledgement mode:
                                       AUTO_ACKNOWLEDGE,
                                       CLIENT_ACKNOWLEDGE,
                                       DUPS_OK_ACKNOWLEDGE
 -c,--client-id <id>                   client id string that can
                                       optionally be set on a connection
 -d,--durable                          create a durable subscriber
 -e,--per-message-sleep <millis>       amount of time (in ms) to sleep
                                       after receiving each message
 -f,--connection-factory-name <name>   name of the connection factory to
                                       lookup
 -g,--num-threads <num>                number of threads to run in
                                       parallel each with a connection
 -h,--help                             show help
 -j,--jndi-lookup-destination          lookup destinations with jndi
 -l,--bytes-message-length <length>    use a bytes message of a specific
                                       length
 -m,--num-messages <num>               number of messages to receive
                                       before stopping
 -n,--destination-name <name>          name of the destination to receive
                                       from
 -o,--final-control-message            use a control message as the final
                                       message
 -p,--temporary-destination            use a temporary destination
 -q,--queue-destination                use a queue destination
 -t,--transacted                       use a transacted session
 -x,--message-group-id <groupId>       JMSXGroupID
 -z,--batch-size <size>                size of the batch to ack or commit
                                       when using client ack mode or
                                       transacted sessions
```

##### JNDI Configuration
Edit src/main/resources/jndi.properties as needed to configure the specific JMS provider's client library (default is configured to use ActiveMQ).  See the ActiveMQ (jndi.properties.activemq) and QPID (jndi.properties.qpid) files in src/main/resources for basic examples of configuring those JMS clients.
