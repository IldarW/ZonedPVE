package net.ildar.wurm;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.zones.Zones;

import java.awt.image.BufferedImage;
import java.time.ZonedDateTime;

import static com.cronutils.model.CronType.UNIX;

public class ZoneMap {
    private BufferedImage map;
    private ExecutionTime pvpSchedule;
    private String pvpScheduleDescription;

    ZoneMap(BufferedImage map) {
        this.map = map;
    }
    public void validate() throws Exception{
        if (map == null)
            throw new Exception("Map is not initialized");
        if (map.getHeight() != Zones.worldTileSizeY || map.getWidth() != Zones.worldTileSizeX)
            throw new Exception(String.format("Map size should be [%d, %d] but it is [%d, %d] now", Zones.worldTileSizeX, Zones.worldTileSizeY, map.getWidth(), map.getHeight()));
    }
    public ZonedPVE.Zone getZone(Creature creature) {
        if (creature == null || !creature.isPlayer())
            return ZonedPVE.Zone.PVP;
        float x = creature.getStatus().getPositionX();
        float y = creature.getStatus().getPositionY();
        return getZone(x, y);
    }

    public ZonedPVE.Zone getZone(Item item){
        if (item == null)
            return ZonedPVE.Zone.PVP;
        return getZone(item.getPosX(), item.getPosY());
    }

    private ZonedPVE.Zone getZone(float x, float y){
        int tileX = (int)(x/4);
        int tileY = (int)(y/4);
        int color = map.getRGB(tileX, tileY);
        if (ZonedPVE.debug)
            ZonedPVE.logger.info(String.format("The color of tile [%d, %d] is %d", tileX, tileY, color));
        for(ZonedPVE.Zone zone : ZonedPVE.Zone.values())
            if(zone.getColor() == color)
                return zone;
        return ZonedPVE.Zone.PVP;
    }

    public void setPvpSchedule(String cronExpression) {
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(UNIX);
        CronParser parser = new CronParser(cronDefinition);
        Cron cron = parser.parse(cronExpression);
        this.pvpScheduleDescription = cronExpression;
        this.pvpSchedule = ExecutionTime.forCron(cron);
    }

    public boolean isPVE(Creature creature, ZonedPVE.Zone zone) {
        if (creature == null)
            return false;
        return isPVE(creature.getName(), creature.getCurrentVillage(), zone);
    }

    public boolean isPVE(Item item, ZonedPVE.Zone zone) {
        if (item == null)
            return false;
        return isPVE(item.getName(), Zones.getVillage((int)(item.getPosX()/4), (int)(item.getPosY()/4), item.isOnSurface()), zone);
    }

    private boolean isPVE(String name, Village village, ZonedPVE.Zone zone) {
        if (zone == ZonedPVE.Zone.PVE) {
            if (ZonedPVE.debug)
                ZonedPVE.logger.info(name + " is in PvE zone");
            return true;
        }
        if (zone == ZonedPVE.Zone.PveOnDeeds) {
            if (village != null && ZonedPVE.debug)
                ZonedPVE.logger.info(name + " is in PvE zone inside " + village.getName());
            return village != null;
        }
        if (zone == ZonedPVE.Zone.TimedPvp){
            boolean pvpTime = pvpSchedule != null && pvpSchedule.isMatch(ZonedDateTime.now());
            if (pvpTime && ZonedPVE.debug)
                ZonedPVE.logger.info(name + " is in PvE zone, but it is PvP based on the schedule - " + pvpScheduleDescription);
            return pvpTime;
        }
        if (zone == ZonedPVE.Zone.TimedPveOnDeeds) {
            if (village != null) {
                boolean pvpTime = pvpSchedule != null && pvpSchedule.isMatch(ZonedDateTime.now());
                if (pvpTime && ZonedPVE.debug)
                    ZonedPVE.logger.info(name + " is inside " + village.getName() + " but it is PvP based on the schedule - " + pvpScheduleDescription);
                if (!pvpTime && ZonedPVE.debug)
                    ZonedPVE.logger.info(name + " is in PvE zone inside " + village.getName());
                return !pvpTime;
            }
        }
        return false;
    }

}
