rm -rf data
mkdir data
cd data
echo eula=true > eula.txt
java -DIReallyKnowWhatIAmDoingISwear -jar ../craftbukkit-1.15.1-R0.1-SNAPSHOT.jar
