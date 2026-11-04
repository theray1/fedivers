curl https://download.oracle.com/java/27/latest/jdk-27_linux-x64_bin.tar.gz -o jdk-27_linux-x64_bin.tar.gz
tar -xf jdk-27_linux-x64_bin.tar.gz
rm jdk-27_linux-x64_bin.tar.gz
sdk install java jdk-27 $(realpath jdk-27.0.1.0.3/)
sdk default java jdk-27
mvn clean package