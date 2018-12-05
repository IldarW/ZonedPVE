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
import static net.ildar.wurm.ZonedPVE.debugMode;

public class ZoneMap {
    private BufferedImage map;
    private ExecutionTime pvpSchedule;
    private String pvpScheduleDescription;
    private boolean initialized;

    public void validate() throws Exception {
        if (map == null)
            throw new Exception("Map is not initialized");
        if (map.getHeight() != Zones.worldTileSizeY || map.getWidth() != Zones.worldTileSizeX)
            throw new Exception(String.format("Map size should be [%d, %d] but it is [%d, %d] now", Zones.worldTileSizeX, Zones.worldTileSizeY, map.getWidth(), map.getHeight()));
        initialized = true;
    }

    public void setMap(BufferedImage map) {
        this.map = map;
    }

    public void setPvpSchedule(String cronExpression) {
        if (cronExpression == null) return;
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(UNIX);
        CronParser parser = new CronParser(cronDefinition);
        Cron cron = parser.parse(cronExpression);
        this.pvpScheduleDescription = cronExpression;
        this.pvpSchedule = ExecutionTime.forCron(cron);
    }

    public boolean isPve(Creature creature) {
        if (!initialized || creature == null || !creature.isPlayer())
            return false;
        float x = creature.getStatus().getPositionX();
        float y = creature.getStatus().getPositionY();
        Zone zone = getZone(x, y);
        return isPVE(creature.getName(), creature.getCurrentVillage(), zone);
    }

    public boolean isPve(Item item) {
        if (!initialized || item == null)
            return false;
        float x = item.getPosX();
        float y = item.getPosY();
        Zone zone = getZone(x, y);
        return isPVE(item.getName(), Zones.getVillage((int) (x / 4), (int) (y / 4), item.isOnSurface()), zone);
    }


    private Zone getZone(float x, float y) {
        int tileX = (int) (x / 4);
        int tileY = (int) (y / 4);
        int color = map.getRGB(tileX, tileY);
        if (debugMode)
            ZonedPVE.logger.info(String.format("The color of tile [%d, %d] is %d", tileX, tileY, color));
        for (Zone zone : Zone.values())
            if (zone.getColor() == color)
                return zone;
        return Zone.PVP;
    }

    private boolean isPVE(String name, Village village, Zone zone) {
        if (zone == Zone.PVE) {
            if (debugMode)
                ZonedPVE.logger.info(name + " is in PvE zone");
            return true;
        }
        if (zone == Zone.PveOnDeeds) {
            if (village != null && debugMode)
                ZonedPVE.logger.info(name + " is in PvE zone inside " + village.getName());
            return village != null;
        }
        if (zone == Zone.TimedPvp) {
            boolean pvpTime = pvpSchedule != null && pvpSchedule.isMatch(ZonedDateTime.now());
            if (pvpTime && debugMode)
                ZonedPVE.logger.info(name + " is in PvE zone, but it is PvP based on the schedule - " + pvpScheduleDescription);
            return pvpTime;
        }
        if (zone == Zone.TimedPveOnDeeds) {
            if (village != null) {
                boolean pvpTime = pvpSchedule != null && pvpSchedule.isMatch(ZonedDateTime.now());
                if (pvpTime && debugMode)
                    ZonedPVE.logger.info(name + " is inside " + village.getName() + " but it is PvP based on the schedule - " + pvpScheduleDescription);
                if (!pvpTime && debugMode)
                    ZonedPVE.logger.info(name + " is in PvE zone inside " + village.getName());
                return !pvpTime;
            }
        }
        return false;
    }
}
