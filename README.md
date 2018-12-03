# ZonedPVE
Provides a convenient way to customize PvE zones in PvP Wurm Unlimited server

## Color scheme

Create a colored image to specify PvE/PvP zones for your Wurm Unlimited server:

Color(r, g, b) | Description
------------ | -------------
Green(0, 255, 0) | Always PvE
Yellow(255, 255, 0) | PvE with scheduled PvP
Pink(255, 128, 128) | PvP with PvE at deeds
Orange(255, 128, 0) | PvP with PvE at deeds. Scheduled PvP at deeds.
Red(255, 0, 0) | Always PvP

## Map format

The map must be 24-bit bmp image named "map.bmp" and located in "%SERVER_FOLDER%/mods/ZonedPVE" directory. 
The image size must be equal to server map size. Top left corner of the image corresponds to north-west corner of the server.

Example image:

![map](https://github.com/IldarW/ZonedPVE/raw/master/map_small.bmp)

This map is created for a very small (512x512) server. 

There is a peaceful north and south with a little PVP at the center.
Yellow zone will have PvP only at certain times. Pink zone has PvP outside of any deeds. 
Orange zone is most complicated - PvE on deeds with scheduled PvP(constant PvP outside of any deeds).
Red zone has always enabled PvP.

## Schedule format

Schedule can be set in configuration file using UNIX cron format. 

Very good site for creating schedules is [here](https://crontab.guru/). Here are some examples:

UNIX cron format | Human readable format
------------ | -------------
\* 20-21 * * SAT | Every saturday between 20 and 21 hours
\*/2 * * * * | Every second minute

Put your schedule string to "%SERVER_FOLDER%/mods/ZonedPVE.properties" file with key "pvpschedule". Example:

pvpschedule=* 20-21 * * SAT
