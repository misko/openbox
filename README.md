openbox project
===============
CSC2209 Computer Network

How to run the program:

openbox project
===============
CSC2209 Computer Network

How to run the program:

Step 1: Create repository folder for client and server.<br>
For example folder "Client" for client repository and "Server" for server repository.

Step 2: Prepare certificate for SSL connection.<br>
Create new certificate by using keytool or just copy existing certificate ("myKeystore") from lib folder to the client's and server's repository.

Step 3: Run the server. We provide jar file for user to run the program more easily by using this command:<br>
java -jar openbox.jar -p <port_number> -r <server_repository_path>

Step 4: Run the client. We provide jar file for user to run the program more easily  by using this command:<br>
java -jar openbox.jar -p <port_number> -s <server_ip_address> -r <client_repository_path>

********************************************************************************************************************************************************<br>
We provide jar file to run the code. So user don't have to add apache commons jar files (already included in the libs folder) to the classpath manually.<br>
But if user don't want to use the jar file, here is how to add apache commons jar files to the classpath: <br>
java -cp .;..\libs\commons-logging-1.1.1.jar\libs\commons-vfs2-2.0.jar OpenBox<br>
********************************************************************************************************************************************************

There are more options of how to run the server and client. The details are explained below:

	OpenBox
	------------------
	Using in server mode: java OpenBox -p port_to_listen_on -r repository_root
	Using in client mode: java OpenBox -p port_to_connect_on -s servername_or_ip -r repository_root
	------------------
	--port/-p	the port to connect or listen on
	--repo/-r	the path to repository root
	--server/-s	the server ip/hostname to connect to
	--up/-u	the total maximum kilo-bytes per second upload
	--down/-d	the total maximum kilo-bytes per second download
	--threads/-t	the number of threads to use, default : 3
	--trust-store-path/-tsp	the path to ssl trust/keystore - default : repo_root/myKeystore
	--trust-store-password/-tspass	the password for the trust/key store - default : "123456"
	--block-size/-bs	the block size (in kilo-bytes) to use for file segmentation , default: 16
	--file-system-poll-delay/-poll	the time (in seconds) to use for file system polling, default: 1
	--client-timeout-sync/-cts	the maximum time (in seconds) to wait before client checks in with server, default: 15
	--status-period/-sp	the period (in seconds) between network status updates, default: 5
	--no-ssl/-ns	don't use SSL, default: use SSL
	------------------
