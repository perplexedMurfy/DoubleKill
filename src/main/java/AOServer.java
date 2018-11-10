import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AOServer {
	//start server state

	//"a totally random string
	//what could possibly go wrong."
	// --AO2 client's source
	//TODO: actually get the hdid, or whatever it _actually_ wants.
	//TODO: wait, is this really good enough?
	//TODO: yeah, I think it's good enough. maybe we should generate it anyways, just for fun.
	String hwid = "12ASDG235GHAKl";
	int serverKey = 0;
	boolean encryption = true;

	int playerId = 0;

	String serverSoftware = null;
	String serverVersion = null;

	int curPlayers = 0;
	int maxPlayers = 0;

	int charListLen = 0;
	String[] chars = null;
	boolean[] charsTaken = null;
	int musicListLen = 0;
	String[] musics = null;

	int evidenceListLen = 0;
	String[] evidenceNames = null;
	String[] evidenceDesc = null;
	String[] evidenceImg = null;
	
	int defHP = 0;
	int proHP = 0;
	//end server state

	Socket sock = null;
	InputStream is = null;  ///to the socket
	OutputStream os = null; ///from the socket
	List<AOPacket> packetQueue = new ArrayList ();

	/** Reads packets from the socket into the packetQueue
	 */
	void readPackets () throws IOException {
		byte[] byte_buffer = new byte[4096]; //TODO: this is not good enough...
		if (is.available() >= 2) {
			is.read(byte_buffer);

			String[] str_packet = new String(byte_buffer).split ("\\#\\%");
			//TODO: Remove empty bit left over...
			for (int i = 0; i < str_packet.length; i++) {
				if (!(str_packet[i].charAt(0) == '\u0000')) {
					packetQueue.add(new AOPacket(str_packet[i] + "#%", false)); //reappends the ending.
				}
			}
		}
	}

	AOPacket popPacket () {
		try {
			return packetQueue.remove (0);
		}
		catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

	/** Sends a packet to the currently connected server
	 */
	public void sendPacket (AOPacket packet) throws IOException {
		System.out.println ("Sending: " + packet.toString()); //Debug code
		if (packet.encrypted) {
			os.write(packet.toString(serverKey).getBytes());
		}
		else {
			os.write(packet.toString().getBytes());
		}
	}

	public void sendKeepAlive () throws IOException {
		System.out.print ("[" + new Date() + "] ");
		sendPacket (new AOPacket ("CH#" + playerId + "#%", encryption));
	}

	/** Connects to a server
	 */
	public void setConnection (String ip, int port) throws IOException {
		sock = new Socket (ip, port); //TODO: Do we even need to keep track of this sock?
		
		is = sock.getInputStream ();
		os = sock.getOutputStream ();
	}

	/** Polls all packets in `packetQueue`
	 */
	public void pollPackets () throws IOException {
		//TODO: get good expantions for all packet headers.
		readPackets ();
		AOPacket packet = null;
		while ((packet = popPacket()) != null) {
			
			System.out.println ("    Receiving: " + packet);
			if (packet.contents[0].equals("decryptor")) {
				String encryptedKey = packet.contents[1];

				byte[] tmp = new byte[encryptedKey.length()/2];
				for (int i = 0; i < encryptedKey.length(); i+=2) {
					tmp[i/2] = Byte.parseByte(encryptedKey.substring(i, i+2), 16);
				}
				
				List<Byte> byte_key = Prog.fantaCrypt (tmp, 322); //gives us our numbers in char form.
				
				//Dispite being a number, the key is actually encoded as a String...
				//meaning, instead of a value of '2', we'd have '50'
				char[] char_key = new char[byte_key.size()];
				for (int i = 0; i < byte_key.size(); i++) {
					byte b = byte_key.get(i);
					char_key[i] = (char)b;
				}
				serverKey = Integer.parseInt(new String(char_key)); //this is quite silly...
				
				sendPacket (new AOPacket ("HI#" + hwid + "#%", true)); //Hardware Infomation
			}

			else if (packet.contents[0].equals("ID")) { //IDentify session and program
				playerId = Integer.parseInt(packet.contents[1]);
				serverSoftware = packet.contents[2];
				if (packet.contents.length == 4) { //optional piece of data.
					serverVersion = packet.contents[3];
				}
				sendPacket (new AOPacket ("ID#AO2#2.4.10#%", true));
			}

			else if (packet.contents[0].equals("PN")) { //Player Number
				curPlayers = Integer.parseInt (packet.contents[1]);
				maxPlayers = Integer.parseInt (packet.contents[2]);
				
			}

			else if (packet.contents[0].equals("FL")) { //Feature List
				for (String s : packet.contents) {
					if (s.equals ("noencryption")) {
						encryption = false; //TODO: use this to determine if encryption of packets is nessary
					}
				}
				//I think this is the last packet in the handshake, it's time to askchaa
				sendPacket (new AOPacket ("askchaa#%", encryption)); //TODO: what should I do if we don't get a FL packet?
			}

			else if (packet.contents[0].equals("SI")) { //Server Info
				charListLen     = Integer.parseInt (packet.contents[1]);
				chars           = new String[charListLen];
				charsTaken      = new boolean[charListLen];
				evidenceListLen = Integer.parseInt (packet.contents[2]);
				evidenceNames   = new String[evidenceListLen];
				evidenceDesc    = new String[evidenceListLen];
				evidenceImg     = new String[evidenceListLen];
				musicListLen    = Integer.parseInt (packet.contents[3]);
				musics          = new String[musicListLen];
				sendPacket (new AOPacket ("RC#%", encryption)); //Request Characters
			}

			else if (packet.contents[0].equals("SC")) { //Send Characters

				for (int i = 1; i < charListLen; i++) {
					chars[i] = packet.contents[i];
				}
				sendPacket (new AOPacket ("RM#%", encryption)); //Request Music
			}

			else if (packet.contents[0].equals("SM")) { //Send Music
				musics = new String[musicListLen];
				for (int i = 1; i < musicListLen; i++) {
					//musics[i] = packet.contents[i]; //TODO: fix?
				}
				sendPacket (new AOPacket ("RD#%", encryption));
			}
			
			else if (packet.contents[0].equals("CharsCheck")) {
				for (int i = 1; i < charListLen; i++) {
					if (packet.contents[1].equals("0")) {
						charsTaken[i] = false;
					}
					else {
						charsTaken[i] = true;
					}
				}
			}

			else if (packet.contents[0].equals("OPPASS")) {
				//Not the actual pass to become op. it's an artifact from <1.8?
				//Either way, it's ignored...
			}

			else if (packet.contents[0].equals("DONE")) {
				//System.out.println ("Server Handshake is Done!");
			}

			/** MeSsage
			 * 0 MS
			 * 1 chat - 1=no desk | 2=desk
			 * 2 char_name
			 * 3 pre_emote - emote before talking
			 * 4 emote - emote while talking
			 * 5 message
			 * 6 side - wit, def, pro, jud, hld, hlp
			 * 7 emote_mod - 0 no pre, become 2 with object
			 *               1 pre + sfx
			 *               2 pre + obj
			 *               3 null
			 *               4 null
			 *               5 no pre, zoom
			 *               6 no pre, obj + zoom
			 * 8 char_id - index char is
			 * 9 sfx_delay
			 * 10 object_mod - 0 nothing
			 *                 1 hold it
			 *                 2 obj
			 *                 3 take that
			 *                 4 shout!
			 * 11 evidence - 0=none | >1==present
			 * 12 flip - bool
			 * 13 realization - bool
			 * 14 text_color - 0=w | 1=g | 2=r | 3=o | 4=b(think) | 5=yellow | 6=gay
			 */
			else if (packet.contents[0].equals("MS")) {
				if (Discord.boundChannel != null) {
					//boolean desk = Integer.parseInt(packet.contents[1]) == 1;
					String preEmote = packet.contents[2];
					String name = packet.contents[3];
					String emote = packet.contents[4];
					String msg = packet.contents[5];
					String pos = packet.contents[6];
					//int emoteMod = Integer.parseInt(packet.contents[7]);
					//don't care 8
					//don't care 9
					int objectionMod = Integer.parseInt(packet.contents[11]);
					int evidenceIndex = Integer.parseInt(packet.contents[12]);
					boolean flip = Integer.parseInt(packet.contents[13]) == 1;
					boolean realize = Integer.parseInt(packet.contents[14]) == 1;
					int textColor = Integer.parseInt(packet.contents[15]);

					StringBuilder message = new StringBuilder ();
					//person
					message.append (name);

					//evidence
					if (evidenceIndex != 0) {
						try {
							message.append(", presenting the " + evidenceNames[evidenceIndex - 1] + ",");
						} catch (NullPointerException | ArrayIndexOutOfBoundsException e) {
							message.append (", presenting something,");
						}
					}

					//pre emote
					//TODO: do something with for *-pre and * emote pairs
					if (preEmote.equals("-")) { //make this the emote clause instead.
						message.append (" acts out " + emote + " then");
					}
					else if (!preEmote.contains("normal")) {
						message.append (" acts out " + preEmote + " then");
					}


					//type of message
					if (objectionMod != 0) {
						if (objectionMod == 1) { message.append (" shouts \"HOLD IT!\" and"); }
						if (objectionMod == 2) { message.append (" shouts \"OBJECTION!\" and"); }
						if (objectionMod == 3) { message.append (" shouts \"TAKE THAT!\" and"); }
						if (objectionMod == 4) { message.append (" shouts something and"); }
					}

					if (msg.trim().length() > 0) { //if message has content.
						if (realize) { message.append (" realizes"); } //TODO: fix spelling
						else if (textColor == 4) { message.append (" thinks"); }
						else { message.append (" says"); }
						//TODO: add clause for not saying anything.

						//pos
						message.append (" from");
						if (pos.equals("wit")) { message.append (" the witness stand"); }
						else if (pos.equals("def")) { message.append (" the attorney's desk"); }
						else if (pos.equals("pro")) { message.append (" the prosecution's desk"); }
						else if (pos.equals("jud")) { message.append (" the judge's bench"); }
						else if (pos.equals("hld")) { message.append (" the attorney's side"); }
						else if (pos.equals("hlp")) { message.append (" the prosecution's side"); }

						//message body
						message.append (", \"" + msg + "\"");
					}

					//emote
					if (!(emote.contains("normal") ||
					      emote.equals(preEmote) ||
					      preEmote.equals("-"))) { //this is so we don't emote caluse twice.
						message.append (" while acting out " + emote);
					}

					message.append (".");

					if (message.length() < 2000);
					Discord.boundChannel.sendMessage (message.toString()).queue();
				}
			}

			else if (packet.contents[0].equals("CT")) { //CommunicaTe

			}

			else if (packet.contents[0].equals("MC")) { //Music Change
				//TODO: wanna play music too?
				//System.out.println (packet.contents[2] + " Changed the song to " + packet.contents[1]);
			}

			else if (packet.contents[0].equals("PV")) {
				//TODO: care about this.
				//confirms that a CC (Character Change) packet was successful
			}

			else if (packet.contents[0].equals("RT")) { //TODO: Render Testmonies?
				//TODO: Ignored...
			}

			else if (packet.contents[0].equals("HP")) { //Health Points
				if (packet.contents[1].equals("1")) {
					defHP = Integer.parseInt (packet.contents[2]);
				}
				else {
					proHP = Integer.parseInt (packet.contents[2]);
				}
			}

			else if (packet.contents[0].equals("BN")) { //Background Name
				
			}

			//TODO: test underlying string repersnetation, &'s should be there
			else if (packet.contents[0].equals("LE")) { //List Evidence
				//Sends the entire list of evidence for every update that's made for it.
			}

			else if (packet.contents[0].equals("CHECK")) {
				//The server is alive!
				//TODO: we should actually care if the server is dead or not.
			}

			//Moderator related stuff
			else if (packet.contents[0].equals("ZZ")) { //call mod
				System.out.println ("A user is calling you, a mod...\n" + packet.contents[1]);
				//TODO: we're never gonna be a mod, right?
			}

			else if (packet.contents[0].equals("IL")) { //Ip List
				//Ignored...
				//TODO: wait, why do we have to deal with this?
			}

			else if (packet.contents[0].equals("MU")) { //Mute User
				//Ignored...
				//TODO: can we ignore a user's mute status?
			}

			else if (packet.contents[0].equals("UM")) { //UnMute user
				//Ignored...
			}

			else if (packet.contents[0].equals("KK")) { //Kick Klient
				//Ignored...
				//TODO: we should probably abort, huh?
			}

			else if (packet.contents[0].equals("KB")) { //TODO: oK, Ban?
				System.out.println ("We have just been banned!");
			}

			else if (packet.contents[0].equals("BD")) { //BanneD
				System.out.println ("We just tried to connect to a server that banned us!");
			}
			//TODO: master server stuff
		}
	}
}
