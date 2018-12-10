# DoubleKill
### A roleplay archiving bot for AO2

This is a bot which pretty prints conversations from a AO2 courtroom
out to a Discord channel.

-------

## Use

To use DoubleKill, there must be a text file named `config.txt` with
your discord bot token stored on the first line, the desired command
prefix on the second line and the name of the privileged role in the
thrid. The recommended prefix is `;` and will be used as an example in
the command list below. Once the bot has started up privileged users
may use the following commands:

| Command          | Description
|------------------|------------------------------------------------------|
|`;bind <channel>` | Specifies where the bot will archive to              |
|`;unbind`         | Tells the bot to stop outputting messages            |
|`;area <areaName>`| Changes the courtroom that the bot is archiving      |
|`;send <payload>` | Sends a arbitrary packet to the connected AO2 server |
|`;recordAutocmds` | Starts recording commands to run on a warm reboot    |
|`;saveAutocmds`   | Save commands to run on a warm reboot                |

Any user can use these commands though:

| Command       | Description
|---------------|----------------------------------------------|
|`;getPlayerId` | Sends DoubleKill's Attorney Online player id |

-------

## Compilation

I think IDEA takes care of the heavy work for you. Just load the
project file! Build the jar from the artifacts menu.  if you're
developing with this, create an `env/` folder at project root. this is
where the program will seek external files while debugging.