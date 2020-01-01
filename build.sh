mvn package
rm -rf server
mkdir server
cd server
mkdir plugins
cp ../target/craftdefense-1.0-SNAPSHOT.jar plugins
cp ../lib/craftbukkit-1.15.1-R0.1-SNAPSHOT.jar .
cp ../src/run.sh .
cp ../src/run.bat .
zip -r server.zip *