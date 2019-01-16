
# Quick Overview

The project is a mapping of the CORBA Object Transaction Service (https://www.omg.org/spec/TRANS/1.3)
to the Protocol Buffer format (https://grpc.io/).

Services are defined in proto files which can then be ran through a compiler to produce client and
server stubs for the various supported languages. These stubs may then form a basis for creating
high performance, open-source universal RPC style services.

The Protocol Buffer proto files for the project are located in a single maven artefact:

```
        <dependency>
            <groupId>${project.groupId}</groupId>
            <artifactId>ots-grpc-proto</artifactId>
            <version>${project.version}</version>
        </dependency>
```

There are three proto files that specify:

- [common message structures](https://github.com/mmusgrov/narayana/blob/grpc/ArjunaJTS/grpc/proto/src/main/proto/OTSCommonTypes.proto)
  and describe message structures that are used by both clients and by transaction management services
- [service definition of client resources and synchronizations](https://github.com/mmusgrov/narayana/blob/grpc/ArjunaJTS/grpc/proto/src/main/proto/OTSResourceServices.proto)
  which are suded by clients in order to receive calls (synchronizations and resource callbacks)
  from the transaction manager
- [service definitions used by an OTS Transaction Manager](https://github.com/mmusgrov/narayana/blob/grpc/ArjunaJTS/grpc/proto/src/main/proto/OTSServices.proto)
  which clients use to make calls on a transaction manager in order to start, enlist with and end transactions.

The maven artefact contains the data access and service stub classes generated from these proto
files. By depending on the artefact, other projects are then able to make gRPC calls to the
services defined in the proto files.

## Benefits

The mapping means that:

- existing implementations of CORBA OTS (including Java EE servers) are able to quickly integrate
  with a gRPC based environment
- gRPC services may access transaction functionality

The mapping and supporting Java implementation are split across 4 maven artefacts:

- [ots-grpc-proto](proto/pom.xml)
  contains service and data definitions and Java data types and stubs for accessing transaction
  services over gRPC. Clients depend on the artefact in order to invoke services over gRPC
- [ots-grpc-resource](resource/pom.xml)
  Provides a service implementation of a generic resource (including subtransaction aware resources)
  and synchronizations. It provides a wrapper around resource services that simplifies the way
  in which Java clients can register with transactions and to receive transaction callbacks
- [ots-grpc-server](server/pom.xml)
  provides service implementation of services that clients use to start and end transactions
  and to enlist with a transaction.
- [ots-grpc-client](client/pom.xml)
  a Java client for starting, ending and enlisting callback handlers (for notifications about
  the lifecycle of a transaction)

Caveat: this version of the project is a prototype and not all interface methods have been
implemented but enough of it (the most important bits) have been implemented to get a feel
for the API. Certainly it is possible to start and end transactions and to register resources
such that the transaction manager will callback to client to prepare/commit/rollback etc his
resources when the transaction is committed/rolled back.

To build everything:

```
mvn clean install -DskipTests
```

To run the test suite (runs everything in a single JVM):

```
mvn test -f client/pom.xml
```

The tests are basic:

- start and end a transaction
- start a transaction, enlist a resource, commit the transaction and verify that the resource was called back correctly
- start a transaction, enlist two resources, commit the transaction and verify that the resources were called back correctly

To run the server and client in different JVMs:

`mvn exec:java -Dexec.mainClass=org.jboss.narayana.grpc.server.OTSGrpcServer -f server/pom.xml`

`mvn exec:java -Dexec.mainClass=org.jboss.narayana.grpc.client.OTSGrpcClient -f client/pom.xml`

The client will connect to the server and start a transaction, enlist a resource with it and then commit the transaction.

# Motivation for the project
 
JTS was the Java EE standard for different JTA implementations to interoperate. Since CORBA (and
therefore JTS) is now an optional component of Jakarta EE there is a need for an alternative
specification for how services can propagate a transaction context. This current work
acknowledges that: 1) gRPC is a good technology choice for service to service interactions;
2) is polyglot or, as stated in the documentation, "Automatically generates idiomatic client and
server stubs for your service in a variety of languages and platforms"; and 3) provides excellent
performance.

The reason JTS was chosen as the Java EE interop standard is that 1) it provides language level
bindings that enable interoperability; and 2) it builds on a pre-existing industry standard, namely OTS.

Consequently, it is reasonable to conclude that a mapping from CosTransactions.idl (the IDL
definition for OTS) to Protocol Buffers and gRPC services will:

- Enable existing OTS/JTS implementations to provide service in these new environments.
- Enable new and existing gRPC services to be able to access transactional functionality in an interoperable manner.
