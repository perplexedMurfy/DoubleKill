import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;


public class Discord {
	static public JDA jda = null;
	static public String privilegedRole = null;
	static public String cmdPrefix = null;
	static public TextChannel boundChannel = null;

	//persistant between warm inits.
	static long channelName = 0;
	static long guildName = 0;
	static String areaName = null;
	static Exception lastExcept = null;
	static final String[] RAND_CRASH_MSGS = {"DoubleKill; 100% uptime, 90% of the time.",
	                                         "DoubleKill was in a tense battle with Bridge Bot. Thankfully one survived.",
	                                         "DoubleKill entered a battle royal with AOpy, and secured the ***EPIC VICTORY ROYAL***.",
	                                         "AOgm challenged DoubleKill to a 1v1, Final Destination, Fox only, no items money match. DoubleKill is now $5 richer.",
	                                         "DoubleKill enjoyed whip spam _a little too much_.",
	                                         "First God, and now DoubleKill has left us.",
	                                         "DoubleKill said, \"this ain't it chief.\"",
	                                         "DoubleKill died by a Headshot",
	                                         "So, now that DoubleKill's dead do we get a triple kill?",
	                                         "The bot died, and so did it's credibility.\n***D-D-DOUBLEKILL***",
	                                         "This bot is a work of fiction. It's functions have passed into fantasy.",
	                                         "Please wait warmly, Murfys are programming",
	                                         "Whoops! DoubleKill had a breakdown! Thankfully he's back now!",
	                                         "After a stern talking to, DoubleKill's ready to behave again!",
	                                         "Are you guys _TRYING_ to break the bot again?"};

	static public void initDiscord (Map<String, String> config, Exception lastExcept) {
		String botToken = config.get ("creds");
		cmdPrefix = config.get ("cmdPrefix");
		privilegedRole = config.get ("privilegedRole");
		Discord.lastExcept = lastExcept;
		try {
			jda = new JDABuilder(AccountType.BOT)
					.setToken(botToken)
					.addEventListener(new CommandListener())
					.build();
		}
		catch (LoginException e) {
			System.out.println ("DoubleKill was unable to log you in! This is really bad!");
			e.printStackTrace();
			System.exit(-1);
		}
	}

	static public boolean sendMessage (String s) {
		if (s.length() < 2000 && boundChannel != null) {
			boundChannel.sendMessage (s).queue();
			return true;
		}
		return false;
	}

	static void shutdown () {
		jda.shutdown ();
		privilegedRole = null;
		cmdPrefix = null;
		boundChannel = null;
	}
}

class CommandListener extends ListenerAdapter {
	@Override
	public void onReady (ReadyEvent event) {
			if (Discord.lastExcept != null) { //recovering from a crash...
				try {
					if (Discord.areaName != null) {
						boolean wait = true;
						while (wait) { //we'll eventually reconnect to the right area.
							try {
								if (!Prog.server.encryption) {
									Prog.server.sendPacket (new AOPacket("MC#" + Discord.areaName + "#" + Prog.server.playerId + "#%", Prog.server.encryption));
									wait = false;
								}
							} catch (Exception e) {}
						}
					}
					Guild guild = Discord.jda.getGuildById (Discord.guildName);
					Discord.boundChannel = guild.getTextChannelById (Discord.channelName);

					StringWriter sw = new StringWriter();
					Discord.lastExcept.printStackTrace(new PrintWriter(sw));
					String stackTrace = sw.toString ();
					Discord.boundChannel.sendMessage (Prog.getRandomString(Discord.RAND_CRASH_MSGS)).queue();
					Discord.boundChannel.sendMessage ("Here's the exception info for Murfy, he might like it:\n" + stackTrace).queue();
					Discord.boundChannel.sendMessage ("If you saw these messages, service should have resumed.").queue();
				}
				catch (IndexOutOfBoundsException e) {
					System.out.println ("There was an error restarting, I'm just gonna give up...");
					System.exit (-1);
				}
			}
	}

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
				}
				catch (IndexOutOfBoundsException ex) {
					Discord.boundChannel = (TextChannel)msg.getChannel();
				}
				Discord.channelName = Discord.boundChannel.getIdLong();
				Discord.guildName = Discord.boundChannel.getGuild().getIdLong();
				ch.sendMessage ("bound to channel " + Discord.boundChannel).queue();
			}
			
			else if (plainMsg.startsWith(Discord.cmdPrefix + "unbind")) { //stop logging in a channel
				Discord.channelName = 0;
				Discord.guildName = 0;
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
				Discord.areaName = plainMsg.substring (new String(Discord.cmdPrefix + "area").length() + 1);
				try {
					Prog.server.sendPacket (new AOPacket("MC#" + Discord.areaName + "#" + Prog.server.playerId + "#%", Prog.server.encryption));
				}
				catch (IOException ex) {}
			}
		}
	}
}
