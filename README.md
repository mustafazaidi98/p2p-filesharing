Client and Indexing server, both of them are java based. The environment is linux 22.04. So, in order to compile and run both the files, we have to make sure that openjdk is installed on the machine. In order to do so use the following command:
sudo apt-get install openjdk-11-jdk
Now we will compile both server and client using the javac file, using the following command:
sudo javac Server.java
sudo  javac Client.java
after running the above commands, we will have executables ready and we can run them using following commands:
sudo java Server 
sudo java Client
To ensure a smooth connection, make sure to run the server executable first. Also to register files on the client machine, make a folder named 'files', and put the desired files on that folder.