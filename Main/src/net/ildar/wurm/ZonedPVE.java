package net.ildar.wurm;

import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZonedPVE implements WurmServerMod, Initable, Configurable, ServerStartedListener {
    public static boolean debug;
    public static final Logger logger = Logger.getLogger(ZonedPVE.class.getSimpleName());
    private static final String VERSION = "1.0";
    private static ZoneMap map;
    private static boolean initialized;
    private String pvpSchedule;


    @Override
    public void configure(Properties properties) {
        debug = Boolean.parseBoolean(properties.getProperty("debug", "false"));
        pvpSchedule = properties.getProperty("pvpschedule");
        if (debug) {
            for(Zone zone : Zone.values())
                logger.info("The color of zone " + zone.name() + " is " + zone.getColor());
        }
    }


    @Override
    public void init() {
        try {
            BufferedImage img = ImageIO.read(new File("./mods/" + ZonedPVE.class.getSimpleName() + "/map.bmp"));
            map = new ZoneMap(img);
            HookManager.getInstance().getClassPool().insertClassPath(new ClassClassPath(ZonedPVE.class));
            AddCombatEngineHook();
            AddArcheryHook();
            AddSpellHooks();
            AddRiteOfDeathHook();
            AddScornOfLibilaHook();
            AddCatapultHook();
            if (pvpSchedule != null)
                map.setPvpSchedule(pvpSchedule);
            initialized = true;
            logger.info("Initialization is complete");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't initialize the mod", e);
            initialized = false;
        }
    }

    private void AddCatapultHook() {
        HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.WarmachineBehaviour",
                "fireCatapult",
                "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)V",
                () -> ((proxy, method, args) -> {
                    Creature performer = (Creature) args[1];
                    if (creatureInPve(performer, true))
                        return null;
                    Item item = (Item) args[2];
                    if (itemInPve(item)) {
                        performer.getCommunicator().sendAlertServerMessage("The target is in PVE zone");
                    }
                    return method.invoke(proxy, args);
                }));
    }

    private void AddScornOfLibilaHook() throws Exception{
        CtMethod doEffectMethod = HookManager.getInstance().getClassPool().get("com.wurmonline.server.spells.ScornOfLibila")
                .getMethod("doEffect", "(Lcom/wurmonline/server/skills/Skill;DLcom/wurmonline/server/creatures/Creature;IIII)V");
        doEffectMethod.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getMethodName().equals("addWoundOfType")) {
                    m.replace("{ if(!net.ildar.wurm.ZonedPVE#creatureInPve($0, false)) $_ = $proceed($$); }");
                }
            }
        });
    }

    private void AddRiteOfDeathHook() throws Exception{
        CtMethod doEffectMethod = HookManager.getInstance().getClassPool().get("com.wurmonline.server.spells.RiteDeath")
                .getMethod("doEffect", "(Lcom/wurmonline/server/skills/Skill;DLcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)V");
        doEffectMethod.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getMethodName().equals("addWoundOfType")) {
                    m.replace("{ if(!net.ildar.wurm.ZonedPVE#creatureInPve($0, false)) $_ = $proceed($$); }");
                }
            }
        });
    }

    private void AddSpellHooks() {
        for(Spell spell : Spell.values()) {
            String className = "com.wurmonline.server.spells." + spell.name();
            if (spell.withPrecondition)
                HookManager.getInstance().registerHook(className,
                        "precondition",
                        spell.methodSignature,
                        () -> ((proxy, method, args) -> {
                            Creature performer = (Creature) args[1];
                            if (creatureInPve(performer, true))
                                return false;
                            if (args[2] instanceof Creature) {
                                Creature target = (Creature) args[2];
                                if (creatureInPve(target, false)) {
                                    performer.getCommunicator().sendNormalServerMessage("The target is in PVE zone");
                                    return false;
                                }
                            }
                            return method.invoke(proxy, args);
                        }));
            else
                HookManager.getInstance().registerHook(className,
                        "doEffect",
                        spell.methodSignature,
                        () -> ((proxy, method, args) -> {
                            Creature performer = (Creature) args[2];
                            if (creatureInPve(performer, true))
                                return null;
                            if (args[3] instanceof Creature) {
                                Creature target = (Creature) args[3];
                                if (creatureInPve(target, false)) {
                                    performer.getCommunicator().sendAlertServerMessage("The target is in PVE zone");
                                    return null;
                                }
                            }
                            method.invoke(proxy, args);
                            return null;
                        }));
        }
    }

    private void AddArcheryHook() {
        HookManager.getInstance().registerHook("com.wurmonline.server.combat.Archery",
                "attack",
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;FLcom/wurmonline/server/behaviours/Action;)Z",
                () -> ((proxy, method, args) -> {
                    if (initialized) {
                        Creature performer = (Creature) args[0];
                        Creature defender = (Creature) args[1];
                        if (creatureInPve(performer, true)) {
                            return true;
                        }
                        if (creatureInPve(defender, false)) {
                            performer.getCommunicator().sendAlertServerMessage("The target is in PVE zone");
                            return true;
                        }
                    }
                    return method.invoke(proxy, args);
                }));
    }

    private void AddCombatEngineHook() {
        HookManager.getInstance().registerHook("com.wurmonline.server.combat.CombatEngine",
                "attack",
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;IILcom/wurmonline/server/behaviours/Action;)Z",
                () -> ((proxy, method, args) -> {
                    if (initialized) {
                        Creature performer = (Creature) args[0];
                        Creature defender = (Creature) args[1];
                        boolean isPve = creatureInPve(performer, true) || creatureInPve(defender, true);
                        if (isPve) {
                            performer.setOpponent(null);
                            return true;
                        }
                    }
                    return method.invoke(proxy, args);
                }));
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean creatureInPve(Creature creature, boolean alert) {
        if (!initialized)
            return false;
        Zone creatureZone = map.getZone(creature);
        if (creatureZone != Zone.PVP)
            if (map.isPVE(creature, creatureZone)) {
                if (alert)
                    creature.getCommunicator().sendAlertServerMessage(creatureZone.getDescription());
                return true;
            }
        return false;
    }

    @SuppressWarnings("WeakerAccess")
    public static boolean itemInPve(Item item) {
        if (!initialized)
            return false;
        Zone itemZone = map.getZone(item);
        if (itemZone != Zone.PVP)
            return map.isPVE(item, itemZone);
        return false;
    }



    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public void onServerStarted() {
        if (initialized && map != null) {
            try {
                map.validate();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Invalid map", e);
                initialized = false;
            }
        }
    }

    public enum Zone {
        /**
         * Всегда ПВЕ
         */
        PVE(new Color(0, 255, 0), "You are in PVE zone"),

        /**
         * Всегда ПВП
         */
        PVP(new Color(255, 0, 0), "You are in PVP zone"),

        /**
         * ПВП включается в судную ночь, но обычно ПВЕ
         */
        TimedPvp(new Color(255, 255, 0), "It is PVP time in the deed"),

        /**
         * ПВЕ в поселениях, но обычно ПВП
         */
        PveOnDeeds(new Color(255, 128, 128), "You are in PVE zone on deed"),

        /**
         * Обычно ПВП, кроме поселений. ПВП в поселениях по расписанию
         */
        TimedPveOnDeeds(new Color(255, 128, 0), "You are in PVE zone on deed");

        private int color;
        private String description;

        Zone(Color color, String description) {
            this.color = color.getRGB();
            this.description = description;
        }

        public int getColor() {
            return this.color;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum Spell{
        DrainHealth(false, "(Lcom/wurmonline/server/skills/Skill;DLcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;)V"),
        DrainStamina(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;)Z"),
        FirePillar(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;III)Z"),
        FireHeart(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;)Z"),
        FungusTrap(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;III)Z"),
        IcePillar(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;III)Z"),
        PainRain(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;III)Z"),
        Phantasms(false, "(Lcom/wurmonline/server/skills/Skill;DLcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;)V"),
        RottingGut(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;)Z"),
        ShardOfIce(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;)Z"),
        DeepTentacles(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;III)Z"),
        Tornado(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;III)Z"),
        Weakness(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;)Z"),
        WormBrains(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;)Z"),
        WrathMagranon(false, "(Lcom/wurmonline/server/skills/Skill;DLcom/wurmonline/server/creatures/Creature;IIII)V"),
        ScornOfLibila(false, "(Lcom/wurmonline/server/skills/Skill;DLcom/wurmonline/server/creatures/Creature;IIII)V"),
        RiteDeath(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)Z");

        public boolean withPrecondition;
        public String methodSignature;

        Spell(boolean withPrecondition, String methodSignature) {
            this.withPrecondition = withPrecondition;
            this.methodSignature = methodSignature;
        }
    }
}
