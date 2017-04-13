#JAVA_HOME=/usr/local/jdk1.8.0_91
#export JAVA_HOME

VDIR=/home/mmusgrov/products/vertx/vertx

TX_LIB=../lib
TX_ETC=../etc

CP="$TX_ETC:$TX_LIB/stm-5.0.0.CR2-SNAPSHOT.jar:$TX_LIB/arjuna-5.0.0.CR2-SNAPSHOT.jar:$TX_LIB/txoj-5.0.0.CR2-SNAPSHOT.jar:$TX_LIB/common-5.0.0.CR2-SNAPSHOT.jar:$TX_LIB/jboss-logging.jar:$TX_LIB/*:$VDIR/lib/*:."

vertx run $1 -cp "$CP"
