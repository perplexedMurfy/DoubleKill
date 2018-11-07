import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;
import java.io.IOException;

public class Discord {
	static public JDA jda = null;
	static public TextChannel boundChannel = null;

	static public void initDiscord (String botToken) {
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

		Member user = e.getMember();
		Message msg = e.getMessage();
		MessageChannel ch = e.getChannel();

		String plainMsg = msg.getContentDisplay();

		if (plainMsg.startsWith(";bind")) {
			try {
				Discord.boundChannel = msg.getMentionedChannels().get(0);
				ch.sendMessage ("bound to channel " + Discord.boundChannel).queue();
			}
			catch (IndexOutOfBoundsException ex) {
				ch.sendMessage ("currently bound to channel " + Discord.boundChannel).queue();
			}
		}

		else if (plainMsg.startsWith(";unbind")) {
			Discord.boundChannel = null;
		}

		else if (plainMsg.startsWith (";send")) {
			String payload = plainMsg.substring (6);
			try {
				Prog.server.sendPacket(new AOPacket(payload, Prog.server.encryption));
			}
			catch (IOException ex) {}
		}

		else if (plainMsg.startsWith (";area")) {
			String areaName = plainMsg.substring (6);
			try {
				Prog.server.sendPacket (new AOPacket("MC#" + areaName + "#" + Prog.server.playerId + "#%", Prog.server.encryption));
			}
			catch (IOException ex) {}
		}
	}
}
