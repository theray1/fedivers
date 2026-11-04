echo "Downloading JDK 27"
curl https://download.oracle.com/java/27/latest/jdk-27_linux-x64_bin.tar.gz -o jdk-27_linux-x64_bin.tar.gz

echo "Decompressing JDK"
tar -xf jdk-27_linux-x64_bin.tar.gz

echo "Removing archive"
rm jdk-27_linux-x64_bin.tar.gz

echo "Using jdk 27"
import JAVA_HOME="jdk-27.0.1.0.3/"

echo "Building with Maven"
mvn clean package