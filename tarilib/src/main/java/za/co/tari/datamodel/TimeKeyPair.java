package za.co.tari.datamodel;

import java.security.PublicKey;

public class TimeKeyPair {
	private long time;
	private PublicKey publicKey;
	
	TimeKeyPair() {}
	public TimeKeyPair(long time, PublicKey publicKey) {
		this.time = time;
		this.publicKey = publicKey;
	}
	public long getTime() {
		return time;
	}
	public PublicKey getPublicKey() {
		return publicKey;
	}
	public void setPublicKey(PublicKey publicKey) {
		this.publicKey = publicKey;
	}
	public void setTime(long time) {
		this.time = time;
	}
}
