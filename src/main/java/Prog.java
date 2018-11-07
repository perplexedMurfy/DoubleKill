import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

//ao master server 104.131.93.82:27016
//Offical Unoffical server 104.131.93.82:27017

//all packets end with a '%'. if they don't, the packet is split up
//and we should wait for the packet to fully send.

public class Prog {
	/** Applies Fanta Encryption or Decryption on the input
	 * applying this method to a encrypted sequence with the same key will decrypt it,
	 * so this function allows both dircetions.
	 */
	static public List<Byte> fantaCrypt (byte[] bytes_in, int key) {
		int k1 = 53761;
		int k2 = 32618;
		
		byte out = 0;
		List<Byte> result = new ArrayList();
			for (int i = 0; i < bytes_in.length; i++) {
				int in = (int)bytes_in[i];
				//encryption / decryption
				out = (byte)(in ^ Integer.remainderUnsigned((key >>> 8), 256));
				key = (Byte.toUnsignedInt(out) + key) * k1 + k2;
				
				result.add (out);
			}
		
		return result;
	}

	static public AOServer server = null;

	static public void main (String[] args) throws IOException {
		server = new AOServer ();
		server.setConnection ("104.131.93.82", 27017);
		Discord.initDiscord ("null");

		Date next = new Date (new Date().getTime() + 45000);
		while (true) {
			if (new Date().after(next)) {
				server.sendKeepAlive ();
				next = new Date (new Date().getTime() + 45000);
			}
			server.pollPackets ();
		}
	}
}
