# DoubleKill
### A roleplay archiving bot for AO2

This is a bot which pretty prints conversations from a AO2 courtroom out to a Discord channel.

-------

## Use

To use DoubleKill, there must be a text file named `creds.txt` with your discord bot token stored inside.
Once the bot has started up use the following commands.

| Command          | Description
|------------------|------------------------------------------------------|
|`;bind <channel>` | Specifies where the bot will archive to              |
|`;unbind`         | Tells the bot to stop outputting messages            |
|`;area <areaName>`| changes the courtroom that the bot is archiving      |
|`;send <payload>` | sends a arbitrary packet to the connected AO2 server |

-------

## Compilation

I think IDEA takes care of the heavy work for you. Just load the project file! Build the jar from the artifacts menu.
if you're developing with this, create an `env/` folder at project root. this is where the program will seek external files while debugging.