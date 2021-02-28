package za.co.tari.util;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
public class Utilities {
	public static int writeFullyClient(SocketChannel socket, ByteBuffer buf) throws IOException {
		int len = -1;
		do {
			len += socket.write(buf);
		} while (len < buf.capacity());
		return len;
	}
}
