rm -rf server
mkdir server
cd server
mkdir plugins
cp ../target/craftdefense-1.0-SNAPSHOT.jar plugins
echo eula=true > eula.txt
java -DIReallyKnowWhatIAmDoingISwear -jar ../lib/craftbukkit-1.15.1-R0.1-SNAPSHOT.jar
