package za.co.tari.server;
import java.io.IOException;


import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;

import javax.crypto.NoSuchPaddingException;

import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map.Entry;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import java.security.PrivateKey;
import java.security.PublicKey;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import org.apache.commons.codec.binary.Base64;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import java.nio.channels.CancelledKeyException;

import org.apache.log4j.Logger;

import za.co.tari.datamodel.TimeKeyPair;
import za.co.tari.crypto.GenerateKeys;
import za.co.tari.crypto.Encrypt;
import za.co.tari.datamodel.Packet;
import za.co.tari.expr.MathExprEval;
import za.co.tari.util.Utilities;

public class Server {
	final static Logger logger = Logger.getLogger(Server.class);
	private Selector selector;
	private InetSocketAddress listenAddress;
	private ConcurrentHashMap<SocketChannel, TimeKeyPair> timeTracker;
	private GenerateKeys cryptoKeys;
	private Encrypt cypherGenerator;
 
	public static void main(String[] args) throws Exception {
		MathExprEval expr = new MathExprEval("1+7*4*2+2");
		if (expr.eval() == true) {
			System.out.println("Expression evaluated");
		}
		System.out.println(expr.calculate());
		return;

//		Server server = new Server("localhost", 8090);
//		server.startServer();
	}

	public Server(String address, int port) throws IOException {
		listenAddress = new InetSocketAddress(address, port);
		timeTracker = new ConcurrentHashMap<SocketChannel, TimeKeyPair>();
		try {
			this.cryptoKeys = new GenerateKeys(1024);
			this.cypherGenerator = new Encrypt();
		} catch (NoSuchAlgorithmException e) {
			logger.debug("##########" + e.getMessage());
		} catch (NoSuchPaddingException e) {
			logger.debug("##########" + e.getMessage());
		}
	}

	private void startServer() throws IOException {
		this.selector = Selector.open();
		ServerSocketChannel serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);

		serverChannel.socket().bind(listenAddress);
		serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);

		logger.debug("Server started...");
		
		boolean timeDaemonRunning = true;
		Thread timeoutDaemon = new Thread(new Runnable () {
			@Override
			public void run() {
				for (;;) {
					if (timeDaemonRunning == false) {
						break;
					}

					for (Entry<SocketChannel, TimeKeyPair> entry : timeTracker.entrySet()) {
						long timestamp = (new Date()).getTime();
						logger.debug("TimeTracker running: " + entry.getValue() + " " + timestamp + " " + (timestamp - entry.getValue().getTime()));
						if ((timestamp - entry.getValue().getTime()) > 40000) {
							SelectionKey key = entry.getKey().keyFor(selector);
							if (!key.isReadable()) {
								try {
									key.cancel();
								} catch(CancelledKeyException e) {
									logger.debug(e.getMessage());
								}
								timeTracker.remove(entry.getKey());
								try {
									entry.getKey().close();
									logger.debug("Socket timeout: " + entry.getKey().socket().getRemoteSocketAddress());
								} catch(IOException e) {
									logger.debug(e.getMessage());
								}
							}
						}
					}
					try {
						Thread.sleep(3000);
					} catch(InterruptedException e) {
						logger.debug(e.getMessage());
					}
				}
			}
		});
		
		timeoutDaemon.start();

		ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);

		while (true) {
			this.selector.select();

			Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
			
			while (keys.hasNext()) {
				SelectionKey key = (SelectionKey) keys.next();

				keys.remove();

				if (!key.isValid()) {
					continue;
				}

				if (key.isAcceptable()) {
					logger.debug("Accepting a socket");
					this.accept(key);
					logger.debug("Finished accepting a socket");
				}
				else if (key.isReadable()) {
					logger.debug("Reading a socket ++ now readable");
					executor.submit(() -> {
						logger.debug("Reading a socket");
						TimeKeyPair track = this.timeTracker.get((SocketChannel)key.channel());
						track.setTime((new Date()).getTime());
						this.timeTracker.put((SocketChannel)key.channel(), track);
						logger.debug("Reading a socket, NOW STARTED");
						this.read(key);
						logger.debug("Reading a socket, NOW ended");
						return null;
					});
				}
			}
		}
	}

	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
		SocketChannel channel = serverChannel.accept();
		channel.configureBlocking(false);
		Socket socket = channel.socket();
		SocketAddress remoteAddr = socket.getRemoteSocketAddress();
		System.out.println("Connected to: " + remoteAddr);

		channel.register(this.selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		timeTracker.put(channel, new TimeKeyPair((new Date()).getTime(), null));
		
		byte[] msg = new String("TARI CMD V1.0").getBytes();
		ByteBuffer buffer = ByteBuffer.wrap(msg);
		//Utilities.writeFullyClient(channel, buffer);
		writeWhenWriteable(channel, msg);
	}

	private void read(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		ByteBuffer inBuffer = ByteBuffer.allocate(2048);
		int numRead = -1;
		numRead = channel.read(inBuffer);

		if (numRead < 1) {
			Socket socket = channel.socket();
			SocketAddress remoteAddr = socket.getRemoteSocketAddress();
			logger.debug("Connection closed by client: " + remoteAddr);
			channel.close();
			key.cancel();
			return;
		}

		byte[] data = new byte[numRead];
		System.arraycopy(inBuffer.array(), 0, data, 0, numRead);
		
		logger.debug("Got packet: " + (new String(data)));
		
		ObjectMapper mapper = new ObjectMapper();
		Packet packet = mapper.readValue(data, Packet.class);
		
		if (packet.getCommand().equals("CONN")) {
			handShake((SocketChannel)key.channel(), packet);
		}
		else if (packet.getCommand().equals("ALIVE")) {
			logger.debug("Heart beat");
			packet.setCommand("ACK");
			packet.setData(null);
			data = mapper.writeValueAsString(packet).getBytes();
			ByteBuffer outBuffer = ByteBuffer.wrap(data);
			//Utilities.writeFullyClient(channel, outBuffer);
			writeWhenWriteable(channel, data);
		} else  if (packet.getCommand().equals("CMD")) {
			handleCMD((SocketChannel)key.channel(), packet);
		} else {
			logger.debug("Uknown command");
		}
		
	}

	private void handShake(SocketChannel channel, Packet packet) throws IOException {
		byte[] data = null;
		try {
			data = Base64.decodeBase64(packet.getData());
			logger.debug("Client public key: " + data.length + " " + (new String(data))); 
			TimeKeyPair track = this.timeTracker.get(channel);
			track.setTime((new Date()).getTime());
			track.setPublicKey(cypherGenerator.getPublic(data));
			this.timeTracker.put(channel, track);
		} catch (Exception e) {
			logger.debug(e.getMessage());
		}
		
		packet.setCommand("OK");
		packet.setData(new String(Base64.encodeBase64String(cryptoKeys.getPublicKey().getEncoded())));
		
		ObjectMapper mapper = new ObjectMapper();
		data = mapper.writeValueAsString(packet).getBytes();
		logger.debug("Server public key: " + mapper.writeValueAsString(packet)); 
		
		ByteBuffer outBuffer = ByteBuffer.wrap(data);
		//Utilities.writeFullyClient(channel, outBuffer);
		writeWhenWriteable(channel, data);
	}
	
	private void handleCMD(SocketChannel channel, Packet packet) throws IOException {
		String expr = null;
		try {
			TimeKeyPair track = this.timeTracker.get(channel);
			expr = cypherGenerator.decryptText(packet.getData(), track.getPublicKey());
			logger.debug("Got command: " + expr);
		} catch (InvalidKeyException e) {
			logger.debug(e.getMessage());
		} catch (GeneralSecurityException e) {
			logger.debug(e.getMessage());
		} catch (Exception e) {
			logger.debug(e.getMessage());
		}
		String value = null;
		try {
			MathExprEval eval = new MathExprEval(expr);
			if (eval.eval() == false) {
				value = new String("Could not evaluate expression");
				logger.debug(value);
			}
			double d = eval.calculate();
			logger.debug("Expression: " +  expr + " = " + d);
			value = Double.toString(d);
			
		} catch (Exception e) {
			value = "Parsing error: " + e.getMessage();
			logger.debug(value);
		}
		
		packet.setCommand("ANS");
		try {
			packet.setData(cypherGenerator.encryptText(value, cryptoKeys.getPrivateKey()));
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
		
		try {
			ObjectMapper mapper = new ObjectMapper();
			byte data[] = mapper.writeValueAsString(packet).getBytes();
			
			logger.debug("Sending ANS packet " + mapper.writeValueAsString(packet));
			ByteBuffer outBuffer = ByteBuffer.wrap(data);
			//Utilities.writeFullyClient(channel, outBuffer);
			writeWhenWriteable(channel, data);
		} catch(Exception e) {
			logger.debug(e.getMessage());
		}
	}
	
	private int writeWhenWriteable(SocketChannel socket, byte[] buf) throws IOException {
		int len = 0;

		this.selector.select();

		Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
		while (keys.hasNext()) {
			SelectionKey key = keys.next();

			keys.remove();
			
			if (!key.isValid()) {
				continue;
			}

			if (key.isWritable() && (SocketChannel)key.channel() == socket) {
				ByteBuffer byteBuffer = ByteBuffer.wrap(buf);
				len = socket.write(byteBuffer);
				break;
			}
		}
		
		return len;
	}
}
