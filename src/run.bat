rd /s /q data
mkdir data
cd data
echo eula=true> eula.txt
mkdir plugins
copy ..\plugins\* plugins
java -Xmx1024M -DIReallyKnowWhatIAmDoingISwear -jar ..\craftbukkit-1.15.1-R0.1-SNAPSHOT.jar
cd ..