openbox project
===============
CSC2209 Computer Network

How to run the code:

Step 1: Add apache commons jar files to the classpath. 
The jar files are already included in the lib folder of the project, so just run this command:
java -cp .;..\libs\commons-logging-1.1.1.jar\libs\commons-vfs2-2.0.jar OpenBox

Step 2: Create repository folder for client and server.
For example folder "Client" for client repository and "Server" for server repository.

Step 3: Prepare certificate for SSL connection.
Create new certificate by using keytool.
Or just copy existing certificate ("mySrvKeystore") from lib folder to the client's and server's repository.

Step 4: Run the server by using this command:
java -cp "..\libs\commons-logging-1.1.1.jar;..\libs\commons-vfs2-2.0.jar" OpenBox -p <port_number> -r <server_repository_path>

Step 5: Run the client by using this command:
java -cp "..\bin;..libs\commons-logging-1.1.1.jar;..\libs\commons-vfs2-2.0.jar" OpenBox -p <port_number> -s <server_ip_address> -r <client_repository_path>

