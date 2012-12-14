openbox project
===============
CSC2209 Computer Network

How to run the program:

Step 1: Create repository folder for client and server.<br>
For example folder "Client" for client repository and "Server" for server repository.

Step 2: Prepare certificate for SSL connection.<br>
Create new certificate by using keytool or just copy existing certificate ("mySrvKeystore") from lib folder to the client's and server's repository.

Step 3: Run the server by using this command:<br>
java  -jar OpenBox.jar OpenBox -p <port_number> -r <server_repository_path>

Step 4: Run the client by using this command:<br>
java -jar OpenBox.jar OpenBox -p <port_number> -s <server_ip_address> -r <client_repository_path>




	OpenBox<br>
	------------------<br>
	Using in server mode: java OpenBox-p port_to_listen_on -r repository_root<br>
	Using in client mode: java OpenBox-p port_to_connect_on -s servername_or_ip -r repository_root<br>
	------------------<br>
	--port/-p	the port to connect or listen on<br>
	--repo/-r	the path to repository root<br>
	--server/-s	the server ip/hostname to connect to<br>
	--up/-u	the total maximum kilo-bytes per second upload<br>
	--down/-d	the total maximum kilo-bytes per second download<br>
	--threads/-t	the number of threads to use, default : 3<br>
	--trust-store-path/-tsp	the path to ssl trust/keystore - default : repo_root/myKeystore<br>
	--trust-store-password/-tspass	the password for the trust/key store - default : "123456"<br>
	--block-size/-bs	the block size (in kilo-bytes) to use for file segmentation , default: 16<br>
	--file-system-poll-delay/-poll	the time (in seconds) to use for file system polling, default: 1<br>
	--client-timeout-sync/-cts	the maximum time (in seconds) to wait before client checks in with server, default: 15<br>
	--status-period/-sp	the period (in seconds) between network status updates, default: 5<br>
	--no-ssl/-ns	don't use SSL, default: use SSL<br>
	------------------<br>
