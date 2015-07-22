# Authors

![vicaba](https://github.com/vicaba "@vicaba")

![xadobu](https://github.com/xadobu "@xadobu")


# Motivation

This is a college exercise proposed by PhD Joan Navarro Marín [1].

We must design and implement a distributed application that is able to replicate data in an epidemic way [2] in order to achieve a multi-versioned architecture similar to the following figure:

![Epidemia](https://github.com/vicobu/DistrubtedSystem/blob/master/images/ex_img1.png)

The system should be like the one in the figure X which have 7 nodes and 3 layers. The
characteristics of the system are as follows:

1. Communication between nodes should be made via sockets
2. The core layer uses update everywhere (all nodes in this partition are able to handle writes), active and eager replication options to replicate the data.
3. B1 and B2 nodes receive data every 10 writes (lazy) using passive replication and primary backup (only one node can handle writes from core layer).
4. Nodes C1 and C2 receive data every 10 seconds (lazy) using passive replication and primary backup (only one node can handle writes from B layer).
5. Each node must have a local text file that logs every version of the transaction.
6. The clients send transactions from a local file.

![Architecture](https://github.com/vicobu/DistrubtedSystem/blob/master/images/ex_img2.png)

We could have used our knowledge in Java but, as we wanted to develop a flexible and extensible system, we felt that Java was very limiting in providing enough abstractions to build the desired system. Also, we wanted to develop a system capable of being deployed in a distributed environment and, once again, Java was very limiting in this aspect.
Searching the Internet, we started to hear whispers of the Akka framework for building distributed applications. We took a look and we started to love it. Although Akka can be used from Java and/or Scala, we decided that it was a good opportunity to learn a new language (or part of it) so we engaged the Scala approach.

# Akka and its benefits
## Reactive Applications - Reactive Manifesto
The [Reactive Manifesto](http://www.reactivemanifesto.org/) describes the characteristics that reactive applications should have. Akka is a framework designed to build and model those reactive applications.

## The Actor Model
The actor model was proposed by Carl Hewitt in 1973 [3] and proposes a model for building concurrent and distributed applications.

In the main-page of Akka we can found the description of an Actor:

*“Actors are very lightweight concurrent entities. They process messages asynchronously using an event-driven receive loop. Pattern matching against messages is a convenient way to express an actor's behaviour. They raise the abstraction level and make it much easier to write, test, understand and maintain concurrent and/or distributed systems. You focus on workflow—how the messages flow in the system—instead of low level primitives like threads, locks and socket IO.”*

We can find another one looking in the Wikipedia:

*“The actor model in computer science is a mathematical model of concurrent computation that treats "actors" as the universal primitives of concurrent computation: in response to a message that it receives, an actor can make local decisions, create more actors, send more messages, and determine how to respond to the next message received.”*

After working on this project and with the gained expertise we can state that both definitions are correct.

Those actors provide an abstraction against the system’s simple objects, that fact was a first big obstacle for us when we tried to develop the whole application. Actors become the first class citizens in the application’s design and implementation
The main characteristics are:

* Send messages to (unforgeable) addresses of Actors that it has.
* Create new Actors
* Designate how to handle the next message that it receives.

# Application architecture

Given the system structure to design, we thought that every “Node” could be represented as an Actor. This “Node” could then create connections to communicate with other “Nodes” and those connections could also be Actors acting as “Ports” with a concrete behavior. The benefit of this approach was that both “Node” (called from now on “CoreNode”) and “Port” (called from now on “PortNode”) inherited from “Node” and moreover a “Node” could take the behaviours of:

* **WhenAlgorithm** (XOR)
  * **Lazy**: when a transaction is received the lazy protocol tells the sender that the transaction has been acknowledged in the hierarchy even if not all the nodes did receive the transaction.
  * **Eager**: when a transaction is received the eager protocol holds the acknowledge until all nodes in the hierarchy acknowledged the first node.

* **HowAlgorithm** (XOR)
  * **Core**: This kind of Node persists the data.
  * **Active**: This kind of node process the transaction and gives to all to the other nodes in the hierarchy.
  * **Passive**: This kind of node process the transaction and gives the digest to the other nodes.

As this protocol (Onion Protocol) distributes the replication among several layers, a transaction or a group of them are replicated to lower layers when some (one or more) conditions are satisfied. 
To satisfy this purpose we created a watchdog (represented by an actor) attached to the PortNode, this actor can handle several conditions (as you might guess every condition is an actor too) and when one or more conditions are satisfied the watchdog actor sends a message to the PortNode telling that the buffered transactions have to be delivered.

After all this number of independent actors were living in sweet harmony, we found that an external agent should take care of the messages that the external clients could send to our system. This overseer node (called from now on CDNMaster) has a global vision of the whole system and moreover it’s the responsible of asking the nodes to deploy connections between them as the system admin requests (between which nodes and with what behavior and conditions).

We developed a mini-DSL, the following code samples show its semantics:
* In order to tell to the CDNMaster about the existence of a node we will use:
```scala
    // Add Nodes to CDN Master
    cdnMasterNode ! AddNode(nodeA1, 0, AddNode.WriteMode)
```

* In order to tell to the CDNMaster that it have to create connections between two nodes we will use:
```scala
    cdnMasterNode ! RequestNodeConnection(
      (nodeA1, PortNode.portOf(new PortNode with Active with Eager), Nil),
      (nodeA2, PortNode.portOf(new PortNode with Active with Eager), Nil)
    )
 ```
 ```scala
     cdnMasterNode ! RequestNodeConnection(
      (nodeA2, PortNode.portOf(new PortNode with Passive with Lazy), List(("transBound", Condition.of(TransactionBoundary(10))))),
      (nodeB1, PortNode.portOf(new PortNode with Passive with Lazy), Nil)
    )
 ```
 
## CoreNode - PortNode Architecture
In the following diagram we can see a sketch of the architecture:
 
![Sketch](https://github.com/vicobu/DistrubtedSystem/blob/master/images/node_diag.png)

 
The circles represent a CoreNode, the smallest rectangles represent a PortNode, the green ones are port nodes in the between nodes of the same partition; the blue ones between partitions, the arrows represent connections between ports.

The small rectangles are Nodes of a partition composed of one CoreNode and one or more PortNodes.

The CDNMaster does not belong to any partition and it’s the responsible of:

* Managing reads and writes.
* Deploying Nodes.
* Deploying connections between Nodes.

# Conclusions
Thanks to using the Akka framework and the abstraction Actor Model, we have reduced the complexity and difficulty implied in dealing with threads and sockets. Moreover, the use of the Actor Model has led our thoughts towards a new model of abstraction for designing distributed and concurrent applications.

We have designed the architecture of the code and system taking into account their reusability and flexibility making it capable of represent different system topologies or behaviours among each layer.

# References

[1] Navarro Martín, Joan. "From cluster databases to cloud storage: Providing transactional support on the cloud." (2015).

[2] Arrieta-Salinas, Itziar, José Enrrique Armendáriz-Iñigo, and Joan Navarro. "Epidemia: Variable Consistency for Transactional Cloud Databases." Journal of Universal Computer Science 20.14 (2014): 1876-1902.

[3] Hewitt, Carl, Peter Bishop, and Richard Steiger. "A universal modular actor formalism for artificial intelligence." Proceedings of the 3rd international joint conference on Artificial intelligence. Morgan Kaufmann Publishers Inc., 1973.
