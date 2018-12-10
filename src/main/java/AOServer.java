import java.net.*;
import java.io.*;
import java.util.*;

public class AOServer {
	//start server state
	//This is all the stuff that a normal client would want to keep track of

	//"a totally random string,
	//what could possibly go wrong."
	// --AO2 client's source
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
	List<String> evidenceNames = new ArrayList ();
	List<String> evidenceDesc  = new ArrayList ();
	List<String> evidenceImg   = new ArrayList ();
	
	int defHP = 0;
	int proHP = 0;
	//end server state

	Socket sock = null;
	InputStream is = null;
	OutputStream os = null;
	List<AOPacket> packetQueue = new ArrayList ();

	Map playerInfo = new HashMap<Integer, String>();

	/** Reads packets from the socket into the packetQueue
	 * If a packet is large enough that it is split into more than 1
	 * part, it is recombined here.  incomplete packets are stored in
	 * `_carryBuffer`, which behaves like a FIFO stack. It is assumed
	 * that either incomplete packets will complete themselves in this
	 * order, or that there will only be one packet that needs to be
	 * combined at a time.
	 *
	 * A packet part in the carry buffer will have it's header, while
	 * it's completing packet part will lack one. When the latter is
	 * encountered in the packetQueue loop it will remove the first
	 * element of the carry buffer and concatinate the parts into one
	 * working packet.
	 *
	 * Note: `_carryBuffer` should only be used in this function.
	 */
	List<String> _carryBuffer = new ArrayList ();
	void readPackets () throws IOException {
		if (is.available() >= 2) {
			byte[] buffer = new byte[is.available()];
			is.read (buffer);
			String str_buffer = new String (buffer);
			
			String[] packets = str_buffer.split("\\#\\%");
			if (str_buffer.charAt(str_buffer.length() - 1) != '%') {
				_carryBuffer.add(packets[packets.length - 1]);
				packets[packets.length - 1] = null;
			}
			
			for (int i = 0; i < packets.length; i++) {
				if (!((packets[i] == null) || (packets[i].isEmpty()) || (packets[i].charAt(0) == '\u0000'))) {
					if (hasValidHeader(packets[i])) {
						packetQueue.add(new AOPacket(packets[i] + "#%", false));
					}
					else {
						String buf = new String ();
						for (String s : _carryBuffer) {
							buf += s;
						}
						_carryBuffer.clear ();
						packetQueue.add (new AOPacket (buf + packets[i] + "#%", false));
					}
				}
			}
		}
	}


	boolean hasValidHeader (String packet) {
		try { packet = packet.substring (0, packet.indexOf("#")); }
		catch (IndexOutOfBoundsException e) {} //contentless packets will trip this
		finally {
			//This should be every packet header, according to https://github.com/AttorneyOnline/AO2Protocol
			if (packet.matches ("decryptor|askchaa|CharsCheck|OPPASS|DONE|CHECK|" + //long headers
								"HI|ID|PN|FL|SI|RC|SC|RM|SM|RD|CC|PV|MS|CT|MC|RT|HP|BN|PE|DE|EE|LE|CH|ZZ|IL|MU|KK|KB|DB|" + //short headers
			                    "SV|PING|ALL|SN|VC|NOSERV|SCC|PSDD|DOOM|" + //master server headers
			                    "MM")) { //undocumented headers
				return true;
			}
		}
		return false;
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

	/** Closes connection to a server
	 */
	public void close () throws IOException {
		sock.close (); //TODO: confirm that AO doesn't do anything on a clean disconnect.
	}

	/** Polls all packets in `packetQueue`
	 */
	public void pollPackets () throws IOException {
		//TODO: get good expantions for all packet headers.
		readPackets ();
		AOPacket packet = null;
		while ((packet = popPacket()) != null) {

			if (packet.contents[0].equals ("CT")) {
				packet.contents[1] = "REDACTED";
				packet.contents[2] = "REDACTED";
			}

			System.out.println("    Receiving: " + packet);

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
				sendPacket (new AOPacket ("ID#AO2#2.4.10#%", true)); //more like protocol version, rather than program version.
			}

			else if (packet.contents[0].equals("PN")) { //Player Number
				curPlayers = Integer.parseInt (packet.contents[1]);
				maxPlayers = Integer.parseInt (packet.contents[2]);
				
			}

			else if (packet.contents[0].equals("FL")) { //Feature List
				for (String s : packet.contents) {
					if (s.equals ("noencryption")) {
						encryption = false;
					}
				}
				sendPacket (new AOPacket ("askchaa#%", encryption)); //TODO: what should I do if we don't get a FL packet?
			}

			else if (packet.contents[0].equals("SI")) { //Server Info
				//TODO: we might care about this later. for now, it's info serves no purpose.
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
			 * 2 pre_emote - emote before talking
			 * 3 char_name
			 * 4 emote - emote while talking
			 * 5 message
			 * 6 side - wit, def, pro, jud, hld, hlp
			 * 7 sfx_name
			 * 8 emote_mod - 0 no pre, become 2 with object
			 *               1 pre + sfx
			 *               2 pre + obj
			 *               3 null
			 *               4 null
			 *               5 no pre, zoom
			 *               6 no pre, obj + zoom
			 * 9 char_id - index char is
			 * 10 sfx_delay
			 * 11 shout_mod
			 * 12 evidence_id
			 * 13 flip - bool
			 * 14 realization - bool
			 * 15 text_color - 0=w | 1=g | 2=r | 3=o | 4=b(think) | 5=yellow | 6=gay
			 */
			else if (packet.contents[0].equals("MS")) {
				if (Discord.boundChannel != null) {
					//boolean desk = Integer.parseInt(packet.contents[1]) == 1;
					String preEmote = packet.contents[2];
					String name = packet.contents[3];
					String emote = packet.contents[4];
					String msg = packet.contents[5];
					String pos = packet.contents[6];
					String sfxName = packet.contents[7]; //TODO: work this into the grammer?
					//don't care 8
					int playerId = Integer.parseInt(packet.contents[9]);
					//don't care 10
					int shoutMod = Integer.parseInt(packet.contents[11]);
					int evidenceIndex = Integer.parseInt(packet.contents[12]);
					//don't care 13
					boolean realize = Integer.parseInt(packet.contents[14]) == 1;
					int textColor = Integer.parseInt(packet.contents[15]);

					playerInfo.put (playerId, name);

					boolean doEvidence = evidenceIndex != 0;
					boolean doShout = shoutMod != 0;
					boolean doMessage = msg.trim().length() > 0;
					boolean doPostEmote = ((!preEmote.equals("-")) &&
					                       (!preEmote.equals(emote)));
					boolean doEmote = !emote.contains ("normal");
					boolean doSound = false;

					//TODO: combine regexs
					emote = emote.toLowerCase().replaceAll(name.toLowerCase(), "").replaceAll("[\\W^_]", "").replaceAll("(pre)+", "");
					preEmote = preEmote.toLowerCase().replaceAll(name.toLowerCase(), "").replaceAll("[\\W^_]", "").replaceAll("(pre)+", "");
					String evidenceName = "pokemon";
					String evidenceImgName = "missingno";
					if (doEvidence) { //TODO: do something about emotes being used as evidence.
						try {
							evidenceName = evidenceNames.get(evidenceIndex - 1);
							evidenceImgName = evidenceImg.get(evidenceIndex - 1);
							if (evidenceImgName.contains ("char_icon") && evidenceImgName.contains ("..")) {
								//The name should be the directory right above.
								String mugName = evidenceImgName.replaceAll ("\\.\\.\\/characters\\/", "").replaceAll ("\\/char_icon.png", "");
								evidenceImgName = "mugshot of " + mugName;
							}
							else {
								if (evidenceImgName.contains ("\\") || evidenceImgName.contains("/")) {
									evidenceImgName = evidenceImgName.replaceAll (".+(?=\\\\|\\/)", "").substring (1); //removes file extention
								}
							}
							evidenceImgName = evidenceImgName.replaceAll ("\\..+", "");; //removes path related stuff
						}
						catch (Exception e) {
						}
					}
						

					//Form when doShout && doEvidence
					//POS PERSON [Acts out EMOTE] shouts SHOUT and presents EVIDENCE, which looks like a EVIDENCEIMG. [Saying MESSAGE,] [They act out EMOTE afterwards].

					//Form when (doShout ^^ doEvidence) || !(doShout && doEvidence)
					//POS PERSON [acts out EMOTE] <and> {shouts SHOUT}{presents EVIDENCE, which looks like a EVIDENCEIMG} <while> [Saying MESSAGE] <,> [they act out EMOTE afterwards].

					//Combined form
					//POS PERSON [acts out EMOTE] 1<,> 2<and> [Shouts SHOUT] 1<and> [presents EVIDENCE, which looks like a EVIDENCE] 1<.> 2<while> [Saying MESSAGE] 3<,> [they act out EMOTE afterwards].

					StringBuilder message = new StringBuilder ();
					//TODO: refactor this

					//pos
					if (pos.equals("wit"))      { message.append ("Witness"); }
					else if (pos.equals("def")) { message.append ("Attorney"); }
					else if (pos.equals("hld")) { message.append ("Co-Council"); }
					else if (pos.equals("jud")) { message.append ("Judge"); }
					else if (pos.equals("pro")) { message.append ("Prosecutor"); }
					else if (pos.equals("hlp")) { message.append ("Prosecutor's aide"); }

					//person
					message.append (" " + name);

					//pre emote (or single emote)
					if (doPostEmote) { //make this the emote clause instead.
						message.append (" acts out " + preEmote);
						if (doShout && doEvidence)      { message.append (","); }
						else if (doShout || doEvidence) { message.append (" and"); }
					}
					else if (doEmote) {
						message.append (" acts out " + emote);
						if (doShout && doEvidence)      { message.append (","); }
						else if (doShout || doEvidence) { message.append (" and"); }
					}

					//shout
					if (doShout) {
						if (shoutMod == 1)      { message.append (" shouts \"HOLD IT!\""); }
						else if (shoutMod == 2) { message.append (" shouts \"OBJECTION!\""); }
						else if (shoutMod == 3) { message.append (" shouts \"TAKE THAT!\""); }
						else if (shoutMod == 4) { message.append (" shouts something"); }

						if (doEvidence) { message.append (" and"); }
					}

					//evidence
					if (doEvidence) { //TODO: account for evidence.
						message.append(" presents the '" + evidenceName + "', which looks like a " + evidenceImgName);
						if (doShout) { message.append ("."); }
						else { message.append (","); }
					}

					//type of message
					if (doMessage) { //if message has content.
						if (doEvidence && doShout) { //It is the beginning of a sentence
							message.append (" They");
							if (realize)             { message.append (" realize"); }
							else if (textColor == 4) { message.append (" say under their breath"); } //blue
							else if (textColor == 5) { message.append (" brood"); } //yellow 
							else if (textColor == 6) { message.append (" say in a gay manner"); } //rainbow
							else                     { message.append (" say"); }
						}
						else if ((!doEvidence && !doShout)) {
							if (doEmote || doPostEmote) { message.append (" and"); }

							if (realize)             { message.append (" realizes"); }
							else if (textColor == 4) { message.append (" says under their breath"); } //blue
							else if (textColor == 5) { message.append (" broods"); } //yellow 
							else if (textColor == 6) { message.append (" says in a gay manner"); } //rainbow
							else                     { message.append (" says"); }
						}
						else {
							message.append (" while");
							if (realize)             { message.append (" realizing"); }
							else if (textColor == 4) { message.append (" saying under their breath"); } //blue
							else if (textColor == 5) { message.append (" brooding"); } //yellow 
							else if (textColor == 6) { message.append (" saying in a gay manner"); } //rainbow
							else                     { message.append (" saying"); }
						}

						//message body
						message.append (", \"" + msg + "\"");
					}
					else if (realize) {
						if (doEvidence && doShout) {
							message.append (" They realize something");
						}
						else if ((!doEvidence && !doShout)) {
							message.append (" while realizing something");
						}
					}

					//emote
					if (doPostEmote) {
						if (!doMessage && (!doEvidence && !doShout)) {
							message.append (" and acts out " + emote + " afterwards");
						}
						else if (doMessage && (!doEvidence && !doShout)) {
							message.append (", acting out " + emote + " afterwards");
						}
						else if (!doMessage && (doEvidence && doShout)) {
							message.append (" They act out" + emote + " afterwards");
						}
						else if (doMessage && (doEvidence && doShout)) {
							message.append (" and act out" + emote + " afterwards");
						}
						else if (doMessage && (doEvidence || doShout)) { //TODO: check logic on this 
							message.append(" acting out " + emote + " afterwards");
						}
					}

					//special "empty" message text.
					if (!(doEvidence || doShout || doMessage || doPostEmote || doEmote || doSound)) {
						message.append (" just stands there, waiting");
					}

					message.append (".");

					if (message.toString().length() < 2000) {
						Discord.boundChannel.sendMessage (message.toString()).queue();
					}
				}
			}

			else if (packet.contents[0].equals("CT")) { //CommunicaTe
				//TODO: ignore this, better?
			}

			else if (packet.contents[0].equals("MC")) { //Music Change
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

			//TODO: is there a client bug with malformed evidence?
			//TODO: we could correct such automaticly, since it seems the stock client breaks on it.
			else if (packet.contents[0].equals("LE")) { //List Evidence
				evidenceNames.clear ();
				evidenceDesc.clear ();
				evidenceImg.clear ();
				boolean isDirty = false; //There's a bug where null evidence is
				for (int i = 1; i < packet.contents.length; i++) {
					String[] evi = packet.contents[i].split ("\\&");
					try {
						evidenceNames.add (evi[0]);
						evidenceDesc.add (evi[1]);
						evidenceImg.add (evi[2]);
					}
					catch (IndexOutOfBoundsException e) {
						isDirty = true;
					}
					if (isDirty) {
						//sendPacket(new AOPacket("DE#"#%", encryption));
					}
				}
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
				//No reason to want to mute someone.
			}

			else if (packet.contents[0].equals("UM")) { //UnMute user
				//Ignored...
				//No reason to want to mute someone.
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
