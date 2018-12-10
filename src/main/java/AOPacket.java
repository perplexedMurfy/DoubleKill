import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Repersents one packet that can be sent to / has been read from AO.
 */
public class AOPacket {
	public String[] contents;///contents of packet, split by #'s. The first entry is the header.
	public boolean encrypted;    ///wheather the header should be encrypted or not.
	
	/** Makes a empty packet
	 */
	public AOPacket () {
		contents = null;
		encrypted = false;
	}
	
	/** Makes a packet from a formatted string.
	 * Things will be wrong if more than one packet is contained in a string.
	 */
	public AOPacket (String in, boolean isEncrypted) {
		encrypted = isEncrypted;
		contents = in.split ("#[%]?");
		for (int i = 0; i < contents.length; i++) {
			String s = contents[i];
			
			s = s.replaceAll ("\\<percent\\>", "\\%");
			s = s.replaceAll ("\\<num\\>",     "\\#");
			s = s.replaceAll ("\\<dollar\\>",  "\\$");
			s = s.replaceAll ("\\<and\\>",     "\\&");
			contents[i] = s;
		}
	}

	/** Transforms the packet into a string.
	 * This is called when we send a packet.
	 */
	@Override
	public String toString () {
		String result = new String ();
		if (encrypted) {
			result = "#"; //for debugging only.
			//TODO: this is an error, throw an expection!
		}

		for (String s : contents) {
			//Not sure if all the escapes are needed, but it won't hurt to be safe.
			s = s.replaceAll ("\\%", "\\<percent\\>");
			s = s.replaceAll ("\\#", "\\<num\\>");
			s = s.replaceAll ("\\$", "\\<dollar\\>");
			s = s.replaceAll ("\\&", "\\<and\\>");
			
			result += s + "#";
		}
		result += "%"; //'#% is the terminator for data in packets
		return result;
	}

	public String toString (int serverKey) {
		String result = new String ();
		if (encrypted) {
			result = "#";
			List<Byte> encryptedHeader = Prog.fantaCrypt (contents[0].getBytes(), serverKey);
			contents[0] = "";
			for (int i = 0; i < encryptedHeader.size(); i++) {
				contents[0] += Integer.toHexString((Byte.toUnsignedInt(encryptedHeader.get(i))));
			}
		}

		for (String s : contents) {
			//Not sure if all the escapes are needed, but it won't hurt to be safe.
			s = s.replaceAll ("\\%", "\\<percent\\>");
			s = s.replaceAll ("\\#", "\\<num\\>");
			s = s.replaceAll ("\\$", "\\<dollar\\>");
			s = s.replaceAll ("\\&", "\\<and\\>");
			
			result += s + "#";
		}
		result += "%"; //'#% is the terminator for data in packets
		return result;
	}
}
