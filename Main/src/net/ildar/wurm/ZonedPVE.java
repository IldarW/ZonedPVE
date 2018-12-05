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
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZonedPVE implements WurmServerMod, Initable, Configurable, ServerStartedListener {
    private static final String VERSION = "1.0";
    static boolean debugMode;
    static final Logger logger = Logger.getLogger(ZonedPVE.class.getSimpleName());
    private static ZoneMap map;

    public static ZoneMap getMap() {
        return map;
    }

    @Override
    public void configure(Properties properties) {
        debugMode = Boolean.parseBoolean(properties.getProperty("debug", "false"));
        map = new ZoneMap();
        map.setPvpSchedule(properties.getProperty("pvpschedule"));
        if (debugMode) {
            for(Zone zone : Zone.values())
                logger.info("The color of zone " + zone.name() + " is " + zone.getColor());
        }
    }


    @Override
    public void init() {
        try {
            BufferedImage img = ImageIO.read(new File("./mods/" + ZonedPVE.class.getSimpleName() + "/map.bmp"));
            map.setMap(img);
            HookManager.getInstance().getClassPool().insertClassPath(new ClassClassPath(ZonedPVE.class));
            AddCombatEngineHook();
            AddArcheryHook();
            AddSpellHooks();
            AddRiteOfDeathHook();
            AddScornOfLibilaHook();
            AddCatapultHook();
            logger.info("Initialization is complete");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't initialize the mod", e);
        }
    }

    private void alertPve(Creature creature, boolean performer) {
        if (performer)
            creature.getCommunicator().sendAlertServerMessage("You are in PvE zone");
        else
            creature.getCommunicator().sendAlertServerMessage("The target is in PvE zone");
    }

    private void AddCatapultHook() {
        HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.WarmachineBehaviour",
                "fireCatapult",
                "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)V",
                () -> ((proxy, method, args) -> {
                    Creature performer = (Creature) args[1];
                    if (map.isPve(performer)) {
                        alertPve(performer, true);
                        return null;
                    }
                    if (map.isPve((Item) args[2])) {
                        alertPve(performer, false);
                        return null;
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
                    m.replace("{ if(!net.ildar.wurm.ZonedPVE.getMap().isPve($0)) $_ = $proceed($$); }");
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
                    m.replace("{ if(!net.ildar.wurm.ZonedPVE.getMap().isPve($0)) $_ = $proceed($$); }");
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
                            if (map.isPve(performer)) {
                                alertPve(performer, true);
                                return false;
                            }
                            if (args[2] instanceof Creature) {
                                if (map.isPve((Creature) args[2])) {
                                    alertPve(performer, false);
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
                            if (map.isPve(performer)) {
                                alertPve(performer, true);
                                return null;
                            }
                            if (args[3] instanceof Creature) {
                                if (map.isPve((Creature) args[3])) {
                                    alertPve(performer, false);
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
                    Creature performer = (Creature) args[0];
                    Creature defender = (Creature) args[1];
                    if (map.isPve(performer)) {
                        alertPve(performer, true);
                        return true;
                    }
                    if (map.isPve(defender)) {
                        alertPve(performer, false);
                        return true;
                    }
                    return method.invoke(proxy, args);
                }));
    }

    private void AddCombatEngineHook() {
        HookManager.getInstance().registerHook("com.wurmonline.server.combat.CombatEngine",
                "attack",
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Creature;IILcom/wurmonline/server/behaviours/Action;)Z",
                () -> ((proxy, method, args) -> {
                    Creature performer = (Creature) args[0];
                    Creature defender = (Creature) args[1];
                    boolean isPve = false;
                    if (map.isPve(performer)) {
                        alertPve(performer, true);
                        isPve = true;
                    }
                    if (map.isPve(defender)) {
                        alertPve(performer, false);
                        isPve = true;
                    }
                    if (isPve) {
                        performer.setOpponent(null);
                        return true;
                    }
                    return method.invoke(proxy, args);
                }));
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public void onServerStarted() {
        try {
            map.validate();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Invalid map", e);
        }
    }

    @SuppressWarnings("unused")
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
