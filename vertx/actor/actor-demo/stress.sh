#!/bin/bash


java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005  -cp target/stm-actor-demo-5.6.0.Final-SNAPSHOT-fat.jar demo.verticle.TripNonVolatileVerticle port=8080 count=10 taxi.port=8082
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006 -cp target/stm-actor-demo-5.6.0.Final-SNAPSHOT-fat.jar demo.verticle.TaxiVolatileVerticle port=8082 count=4

curl -X POST http://localhost:8080/api/theatre/Odeon/ABC
curl -X GET http://localhost:8082/api/taxi 


# NonVolatile demo:
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005  -cp target/stm-actor-demo-5.6.0.Final-SNAPSHOT-fat.jar demo.verticle.TripNonVolatileVerticle 8080 10
curl -X POST http://localhost:8080/api/activity/task1
curl -X GET http://localhost:8081/api/activity


# java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar target/stm-actor-olddemo-5.6.0.Final-SNAPSHOT-fat.jar -Dthread.count=10000

curl -X POST http://localhost:8080/api/trip/Evita/4/ABC | prettyjson
curl -X POST http://localhost:8081/api/theatre/Cats/3 
curl -X POST http://localhost:8082/api/taxi/Brown/2 | prettyjson

curl -X GET http://localhost:8081/api/theatre | prettyjson
curl -X GET http://localhost:8083/api/taxi | prettyjson
curl -X GET http://localhost:8082/api/taxi | prettyjson

curl -X POST  http://localhost:8081/api/trip/Cats/2[1-1000] &
curl -X POST  http://localhost:8082/api/theatre/Cats/2[1-1000] &
curl -X POST  http://localhost:8083/api/taxi/favorite/2[1-1000] &
curl -X POST  http://localhost:8084/api/taxi/alt/2[1-1000] &

sleep 4

curl -X GET http://localhost:8081/api/trip
curl -X GET http://localhost:8082/api/theatre
curl -X GET http://localhost:8083/api/taxi/favorite
curl -X GET http://localhost:8084/api/taxi/alt
