package za.co.tari.client;

import java.io.IOException;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Date;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.io.ByteArrayOutputStream;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import za.co.tari.crypto.GenerateKeys;
import za.co.tari.crypto.Encrypt;
import za.co.tari.datamodel.Packet;
import za.co.tari.util.Utilities;

import org.apache.log4j.Logger;

public class Client {
	final static Logger logger = Logger.getLogger(Client.class);
	private Selector selector;
	private Queue<byte[]> queue;
	private GenerateKeys cryptoKeys;
	private Encrypt cypherGenerator;
	private PublicKey serverKey;
	private boolean running;
	
	public Client() throws IOException {
		this.selector = Selector.open();
		this.queue = new LinkedBlockingDeque<byte[]>();
		try {
			this.cryptoKeys = new GenerateKeys(1024);
			this.cypherGenerator = new Encrypt();
		} catch (NoSuchAlgorithmException e) {
			logger.debug(e.getMessage());
		} catch (NoSuchPaddingException e) {
			logger.debug(e.getMessage());
		}
		serverKey =  null;
		this.running = false;
	}
	
	public static void main(String[] args) throws Exception {
		Client client = new Client();
		client.startClient();
	}

	private int readTimeout(SocketChannel socket, long timeout, byte[] buf) throws IOException {
		int len = 0;

		this.selector.select();

		Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
		while (keys.hasNext()) {
			SelectionKey key = keys.next();

			if (!key.isValid()) {
				keys.remove();
				continue;
			}

			if (key.isReadable() && (SocketChannel)key.channel() == socket) {
				keys.remove();
				ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
				len = socket.read(byteBuffer);
				break;
			}
		}
		
		return len;
	}
	
	private int writeWhenWriteable(SocketChannel socket, byte[] buf) throws IOException {
		int len = 0;

		this.selector.select();

		Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
		while (keys.hasNext()) {
			SelectionKey key = keys.next();

			if (!key.isValid()) {
				keys.remove();
				continue;
			}

			if (key.isWritable() && (SocketChannel)key.channel() == socket) {
				keys.remove();
				ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
				len = socket.write(byteBuffer);
				break;
			}
		}
		
		return len;
	}
	
	public boolean setupConnection(SocketChannel client) throws IOException, InterruptedException {
		StringBuilder stringBuf = new StringBuilder();
		byte buf[] =  new byte[1024];
		int len = 0;
		String response = null;
		
		while ((len = readTimeout(client, 1000, buf)) > 0) {
			stringBuf.append(new String(buf, 0, len));
		}
		
		if (stringBuf.length() > 0) {
			response = stringBuf.toString();
			logger.debug("Connected to server: " + response);
		} else {
			running = false;
			logger.debug("No data received: " + stringBuf.length());
			return false;
		}

		return true;
	}
	
	public boolean setupEndToEndEncryption(SocketChannel client) throws IOException, InterruptedException {
		ObjectMapper mapper = new ObjectMapper();
		
		Packet packet = new Packet("CONN", Base64.encodeBase64String(cryptoKeys.getPublicKey().getEncoded()));
		byte msg[] = mapper.writeValueAsString(packet).getBytes();
		logger.debug("Client public key " + mapper.writeValueAsString(packet)); 
		ByteBuffer buffer = ByteBuffer.wrap(msg);
		//Utilities.writeFullyClient(client, buffer);
		writeWhenWriteable(client, msg);
		buffer.clear();

		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		byte buf[] = new byte[1024];
		int len = -1;
		while ((len = readTimeout(client, 1000, buf)) > 0) {
			byteStream.write(buf, 0, len);
		}
		
		msg = byteStream.toByteArray();
		if (msg.length < 1) {
			logger.debug("No data received: " + msg.length);
			return false;
		}
		
		String response = new String(msg);
		logger.debug("Server response: " + response);
		
		packet = mapper.readValue(msg, Packet.class);
		if (!packet.getCommand().equals("OK")) {
			logger.debug("Error: Handshake failed!");
			return false;
		} else {
			try {
				msg = Base64.decodeBase64(packet.getData());
				logger.debug("Server public key " + msg.length + " " + (new String(msg))); 
				serverKey = cypherGenerator.getPublic(msg);
				
				logger.debug("Server response: " + packet.getCommand());
			} catch (InvalidKeyException e) {
				logger.debug(e.getMessage());
			} catch (GeneralSecurityException e) {
				logger.debug(e.getMessage());
			} catch (Exception e) {
				logger.debug(e.getMessage());
			}
		}
		return true;
	}
	
	public void dispatcher() throws IOException, InterruptedException {
		InetSocketAddress hostAddress = new InetSocketAddress("localhost", 8090);
		SocketChannel client = SocketChannel.open(hostAddress);
		client.configureBlocking(false);

		client.register(this.selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		
		logger.debug("Client... started");
		
		if (setupConnection(client) == false) {
			return;
		}
		
		if (setupEndToEndEncryption(client) == false) {
			return;
		}
		
		Packet packet = new Packet();
		ObjectMapper mapper = new ObjectMapper();
		
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		
		ByteBuffer buffer = null;
		byte buf[] =  new byte[1024];
		byte msg[] = null;
		
		int len = 0;
		
		Date date = new Date();
		long timestamp = date.getTime();
		for (;;) {
			if (running == false && queue.size() == 0) {
				logger.debug("Dispatcher exited");
				break;
			}
			
			logger.debug("Client is not longer connected: " + client.isConnected() + " " + client.socket().isConnected());
			if (!client.isConnected()) {
				logger.debug("Client is not longer connected");
				break;
			}

			long current_ts = (new Date()).getTime();
			logger.debug(timestamp + "  |  " + current_ts + " " + (current_ts - timestamp));
			if ((current_ts - timestamp) > 10000) {
				packet.setCommand("ALIVE");
				packet.setData(null);
				buffer = ByteBuffer.wrap(mapper.writeValueAsString(packet).getBytes());
				logger.debug("Sending heart beat packet" + mapper.writeValueAsString(packet));
				//Utilities.writeFullyClient(client, buffer);
				writeWhenWriteable(client, mapper.writeValueAsString(packet).getBytes());

				buffer.clear();
				
				timestamp = current_ts;
				logger.debug("ALIVE send");
			}
			
			msg = queue.poll();
			if (msg != null) {
				packet.setCommand("CMD");
				try {
					packet.setData(cypherGenerator.encryptText(new String(msg), cryptoKeys.getPrivateKey()));
					buffer = ByteBuffer.wrap(mapper.writeValueAsString(packet).getBytes());
					logger.debug("Sending command to be run on the server: " + mapper.writeValueAsString(packet));
					//Utilities.writeFullyClient(client, buffer);
					writeWhenWriteable(client, mapper.writeValueAsString(packet).getBytes());

					buffer.clear();
				} catch (InvalidKeyException e) {
					logger.debug(e.getMessage());
				} catch (BadPaddingException e) {
					logger.debug(e.getMessage());
				} catch (IllegalBlockSizeException e) {
					logger.debug(e.getMessage());
				} catch (NoSuchPaddingException e) {
					logger.debug(e.getMessage());
				} catch (Exception e) {
					logger.debug(e.getMessage());
				}
			}
			
			while ((len = readTimeout(client, 3000, buf)) > 0) {
				byteStream.write(buf, 0, len);
			}
			
			msg = byteStream.toByteArray();
			if (msg == null || msg.length == 0) {
				continue;
			}
			
			packet = mapper.readValue(msg, Packet.class);
			logger.debug("Received the following response from the server: " + (new String(msg)));

			if ((packet.getCommand().equals("ACK") || packet.getCommand().equals("OK") || packet.getCommand().equals("ANS")) && packet.getData() != null) {
				try {
					String response = cypherGenerator.decryptText(packet.getData(), serverKey);
					logger.debug("Server response: " + response);
					if (packet.getCommand().equals("OK")) {
						System.out.println("Server response: " + response);
					}
				} catch (InvalidKeyException e) {
					logger.debug(e.getMessage());
				} catch (GeneralSecurityException e) {
					logger.debug(e.getMessage());
				} catch (Exception e) {
					logger.debug(e.getMessage());
				}
			}
		}
		client.close();            
	}
	
	void startClient() {
		running = true;
		Thread comms = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					dispatcher();
				} catch(IOException e) {
					//System.out.println(e.)
				} catch(InterruptedException e) {
					
				}
			}});
		comms.start();
		
		Scanner scanner = new Scanner(System.in);
		for (;;) {
			String line = null;
			if ((line = scanner.nextLine()) == null) {
				continue;
			}
			if (running == false) {
				System.out.println("Bye.");
				break;
			}

			if (line.startsWith("EXIT")) {
				queue.add(line.getBytes());
				running = false;
				System.out.println("Bye.");
				break;
			} else if (line.startsWith("CMD")) {
				queue.add(line.substring(3).getBytes());
			} else {
				System.out.println("unknown command: " + line);
			}
		}
		scanner.close();
		try {
			comms.join();
		} catch(InterruptedException e) {
			
		}
	}
}
