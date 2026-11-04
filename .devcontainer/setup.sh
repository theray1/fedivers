#!/bin/bash

echo "curl https://download.oracle.com/java/27/latest/jdk-27_linux-x64_bin.tar.gz -o jdk-27_linux-x64_bin.tar.gz"
curl https://download.oracle.com/java/27/latest/jdk-27_linux-x64_bin.tar.gz -o jdk-27_linux-x64_bin.tar.gz

echo "tar -xf jdk-27_linux-x64_bin.tar.gz"
tar -xf jdk-27_linux-x64_bin.tar.gz

echo "rm jdk-27_linux-x64_bin.tar.gz"
rm jdk-27_linux-x64_bin.tar.gz

echo 'export JAVA_HOME="jdk-27.0.1.0.3/"'
export JAVA_HOME="jdk-27.0.1.0.3/"

echo "mvn clean package"
mvn clean package