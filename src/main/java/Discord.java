import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Discord {
	static public JDA jda = null;
	static public String privilegedRole = null;
	static public String cmdPrefix = null;

	//persistant between warm inits.
	static public MessageChannel boundChannel = null;
	static List<Message> autocmds = new ArrayList ();

	static public void initDiscord (Map<String, String> config, Exception lastExcept) {
		String botToken = config.get ("creds");
		cmdPrefix = config.get ("cmdPrefix");
		privilegedRole = config.get ("privilegedRole");
		try {
			jda = new JDABuilder(AccountType.BOT)
					.setToken(botToken)
					.addEventListener(new CommandListener())
					.build();

			if (lastExcept != null) {
				boundChannel.sendMessage ("DoubleKill has encounted an error! A warm restart is being attempted...").queue();
				boundChannel.sendMessage ("Here's some info about what was happening, so Murfy can fix me.\n" + lastExcept).queue();
			}
		}
		catch (LoginException e) {
			System.out.println ("DoubleKill was unable to log you in! This is really bad!");
			e.printStackTrace();
			System.exit(-1);
		}
	}

	static void shutdown () {
		jda.shutdown ();
	}
}

class CommandListener extends ListenerAdapter {
	@Override
	public void onReady (ReadyEvent e) {
		if (!Discord.autocmds.isEmpty ()) {
			for (Message m : Discord.autocmds) {
				onMessageReceived (new MessageReceivedEvent (Discord.jda, -1, m));
			}
		}
	}

	static boolean isRecordingAutocmds = false;

	@Override
	public void onMessageReceived (MessageReceivedEvent e) {
		JDA jda = e.getJDA();

		Member user = e.getMember ();
		List<Role> roles = user.getRoles ();
		Message msg = e.getMessage ();
		MessageChannel ch = e.getChannel ();

		String plainMsg = msg.getContentDisplay();
		boolean isPrivileged = false;

		for (Role r : roles) {
			if (r.getName().equals(Discord.privilegedRole)) {
				isPrivileged = true;
			}
		}

		//Some commands we don't mind if people use them without the proper role
		if (plainMsg.startsWith (Discord.cmdPrefix + "getPlayerId")) { //messages DoubleKill's player id
			ch.sendMessage ("DoubleKill's player ID is currently " + Prog.server.playerId).queue();
		}
		else if (plainMsg.startsWith (Discord.cmdPrefix + "getPlayers")) { //messages a list of ini-swapped characters, relating them to something seeable in the client.
			//TODO: make this
		}

		//TODO: make these commands look a little nicer?
		if (isPrivileged) {
			boolean isRecordable = true;
			if (plainMsg.startsWith(Discord.cmdPrefix + "bind")) { //start logging in a channel
				try {
					Discord.boundChannel = msg.getMentionedChannels().get(0);
					ch.sendMessage ("bound to channel " + Discord.boundChannel).queue();
				}
				catch (IndexOutOfBoundsException ex) {
					Discord.boundChannel = msg.getChannel();
					ch.sendMessage ("bound to channel " + Discord.boundChannel).queue();
				}
			}
			
			else if (plainMsg.startsWith(Discord.cmdPrefix + "unbind")) { //stop logging in a channel
				Discord.boundChannel = null;
			}
			
			else if (plainMsg.startsWith (Discord.cmdPrefix + "send")) { //send a packet to the AO server
				String payload = plainMsg.substring (new String(Discord.cmdPrefix + "send").length() + 1);
				try {
					Prog.server.sendPacket(new AOPacket(payload, Prog.server.encryption));
				}
				catch (IOException ex) {}
			}
			
			else if (plainMsg.startsWith (Discord.cmdPrefix + "area")) { //change area that is being logged
				String areaName = plainMsg.substring (new String(Discord.cmdPrefix + "area").length() + 1);
				try {
					Prog.server.sendPacket (new AOPacket("MC#" + areaName + "#" + Prog.server.playerId + "#%", Prog.server.encryption));
				}
				catch (IOException ex) {}
			}

			else if (plainMsg.startsWith (Discord.cmdPrefix + "recordAutocmds")) { //start recording cmds to be executed on startup.
				isRecordable = false;
				if (!isRecordingAutocmds) {
					isRecordingAutocmds = true;
					Discord.autocmds.clear ();
					ch.sendMessage ("Started recording autocmds").queue();
				}
				else {
					isRecordingAutocmds = false;
					ch.sendMessage ("Aborted recording autocmds").queue();
				}
			}

			else if (plainMsg.startsWith (Discord.cmdPrefix + "saveAutocmds")) { //save recorded cmds to file
				isRecordable = false;
				isRecordingAutocmds = false;
				ch.sendMessage ("Autocmds saved; They will be excuted if something bad happens.").queue();
			}

			if (isRecordingAutocmds && isRecordable) {
				Discord.autocmds.add (msg);
			}
		}
	}
}
