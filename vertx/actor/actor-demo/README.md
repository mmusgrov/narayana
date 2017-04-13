
Another example of STM and Vert.x integration

This quickstart introduces two vertx verticles with the aim of demonstrating the two extremes of how
to share data and manage concurency using STM. One extreme shows a single JVM running multiple vertx
instances sharing the same STM object, whereas the other extreme shows multiple JVMs sharing the same
STM object.

The two examples illustrate the different ways in which an application can be scaled whilst maintaining
data consistency:

 - verticle scaling by using better hardware so that more threads can do be used to service the
   workload;
 - horizontal scaling by using more servers so that the workload can be distributed to multiple JVMs

First build a fat jar that contains all the classes needed to run both demos in a single jar:

  mvn clean package

Demo 1: demo.verticle.VolatileTripVerticle
===============================================

  Charactertics:

  - RECOVERABLE and EXCLUSIVE
  - creates multiple vertx instances each instance using a handle to the same STM object
  
  What it's good for:

  - vertical scaling where adding better h/w is an option in order to support more threads in one JVM

  How to run the example:

  a) Start a JVM with multiple vertx instances all listening on port 8080:

     java -cp target/stm-actor-demo-5.6.0.Final-SNAPSHOT-fat.jar demo.verticle.VolatileTripVerticle 8080 10

     the two arguments specify the port (8080) on which the verticle event loops will listen and the
     number of instances (10) of the verticles. Note that the version of the jar (5.6.0.Final-SNAPSHOT
     in this example) will change depending upon which narayana release you have.

  b) Issue an http request to create an activity:

     curl -X POST http://localhost:8080/api/activity/task1

  c) Obtain a count of the number of activities. The vertx instance that handles the request is arbitary
     but each one should return the same value:

    curl -X GET http://localhost:8080/api/activity

    The response will report the current count and the thread that serviced the request.
    Re run the http GET request and observe that the thread id changes but the value remains the same.

Demo 2: demo.verticle.NonVolatileTripVerticle
==================================================

  Charactertics:

  - PERSISTENT and SHARED
  - starts multiple vertx servers each running a single vertx instance, all sharing the a handle to the
    same STM object
  
  What it's good for:

  - horizontal scaling by using better hardware so that more threads can do be used to service the
    workload;

  How to run the example:

  a) Start a JVMs running a single vertx instance listening on port 8080:

     java -cp target/stm-actor-demo-5.6.0.Final-SNAPSHOT-fat.jar demo.verticle.NonVolatileTripVerticle 8080 1 &

  b) Obtain the object identifier (Uid) of the STM instance so that it can be cloned in other processes:

     curl -X GET http://localhost:8080/api/activity/uid 
     > 0:ffffc0a80008:9d3d:58fe56cc:3

     The output of this GET request can then be passed to other JVMs running the verticle:

  c) Start a second JVM running a single vertx instance listening on port 8081 along with the STM uid:
     java -cp target/stm-actor-demo-5.6.0.Final-SNAPSHOT-fat.jar demo.verticle.NonVolatileTripVerticle 8081 1 "0:ffffc0a80008:9d3d:58fe56cc:3" &

  d) Issue an http request to create activities on each JVM:

     curl -X POST http://localhost:8080/api/activity/task1
     curl -X POST http://localhost:8081/api/activity/task1

  e) Obtain a count of the number of activities. You should be able to issue the request to either
     JVM and obtain the same value:

    curl -X GET http://localhost:8080/api/activity
    curl -X GET http://localhost:8081/api/activity

    Each response will report the same current count illustrating that the data is shared across JVMs.

  f) Since the STM object is PERSISTENT and SHARED it will survive restarting the JVMs.
     So now try shutting down all verticles. You can pick up from where you left off by restarting
     each of them with the same Uid.
