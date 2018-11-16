import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Discord {
	static public JDA jda = null;
	static public MessageChannel boundChannel = null;
	static public String privilegedRole = null;
	static public String cmdPrefix = null;

	static public void initDiscord (Map<String, String> config) {
		String botToken = config.get ("creds");
		cmdPrefix = config.get ("cmdPrefix");
		privilegedRole = config.get ("privilegedRole");
		try {
			jda = new JDABuilder(AccountType.BOT)
					.setToken(botToken)
					.addEventListener(new CommandListener())
					.build();
		}
		catch (LoginException e ) {
			e.printStackTrace();
		}
	}
}

class CommandListener extends ListenerAdapter {
	@Override
	public void onReady (ReadyEvent e) {
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

		//TODO: make these commands look a little nicer?
		if (isPrivileged) {
			if (plainMsg.startsWith(Discord.cmdPrefix + "bind")) {
				try {
					Discord.boundChannel = msg.getMentionedChannels().get(0);
					ch.sendMessage ("bound to channel " + Discord.boundChannel).queue();
				}
				catch (IndexOutOfBoundsException ex) {
					Discord.boundChannel = msg.getChannel();
					ch.sendMessage ("bound to channel " + Discord.boundChannel).queue();
				}
			}
			
			else if (plainMsg.startsWith(Discord.cmdPrefix + "unbind")) {
				Discord.boundChannel = null;
			}
			
			else if (plainMsg.startsWith (Discord.cmdPrefix + "send")) {
				String payload = plainMsg.substring (new String(Discord.cmdPrefix + "send").length() + 1);
				try {
					Prog.server.sendPacket(new AOPacket(payload, Prog.server.encryption));
				}
				catch (IOException ex) {}
			}
			
			else if (plainMsg.startsWith (Discord.cmdPrefix + "area")) {
				String areaName = plainMsg.substring (new String(Discord.cmdPrefix + "area").length() + 1);
				try {
					Prog.server.sendPacket (new AOPacket("MC#" + areaName + "#" + Prog.server.playerId + "#%", Prog.server.encryption));
				}
				catch (IOException ex) {}
			}
		}
	}
}
