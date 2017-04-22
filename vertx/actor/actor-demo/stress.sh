#!/bin/bash

# java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -jar target/stm-actor-olddemo-5.6.0.Final-SNAPSHOT-fat.jar -Dthread.count=10000

curl -X POST  http://localhost:8081/api/trip/Cats/2[1-1000] &
curl -X POST  http://localhost:8082/api/theatre/Cats/2[1-1000] &
curl -X POST  http://localhost:8083/api/taxi/favorite/2[1-1000] &
curl -X POST  http://localhost:8084/api/taxi/alt/2[1-1000] &

sleep 4

curl -X GET http://localhost:8081/api/trip
curl -X GET http://localhost:8082/api/theatre
curl -X GET http://localhost:8083/api/taxi/favorite
curl -X GET http://localhost:8084/api/taxi/alt
