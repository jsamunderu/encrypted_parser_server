package za.co.tari.datamodel;

public class Packet {
	private String command;
	private String data;

	public Packet() {}
	public Packet(String command, String data) {
		this.command = command;
		this.data = data;
	}
	
	public String getCommand() {
		return command;
	}

	public String getData() {
		return data;
	}

	public void setCommand(String command) {
		this.command = command;
	}

	public void setData(String data) {
		this.data = data;
	}
}
