package net.ildar.wurm;

import com.wurmonline.math.Vector3f;
import com.wurmonline.server.combat.ServerProjectile;
import com.wurmonline.server.creatures.Creature;
import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ZonedPVE implements WurmServerMod, PreInitable, Initable, Configurable, ServerStartedListener {
    private static final String VERSION = "1.0";
    static boolean debugMode;
    static final Logger logger = Logger.getLogger(ZonedPVE.class.getSimpleName());
    private static ZoneMap map = new ZoneMap();

    @Override
    public void configure(Properties properties) {
        debugMode = Boolean.parseBoolean(properties.getProperty("debug", "false"));
        map.setPvpSchedule(properties.getProperty("pvpschedule"));
        if (debugMode) {
            for (Zone zone : Zone.values())
                logger.info("The color of zone " + zone.name() + " is " + zone.getColor());
        }
    }

    @Override
    public void init() {
        try {
            addSpellHooks();
            addCombatEngineHook();
            addArcheryHook();
            addCatapultHook();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't initialize the mod", e);
        }
    }

    @Override
    public void preInit() {
        try {
            BufferedImage img = ImageIO.read(new File("./mods/" + ZonedPVE.class.getSimpleName() + "/map.bmp"));
            map.setMap(img);
            processRiteOfDeathSpell();
            processScornOfLibilaSpell();
            logger.info("Initialization is complete");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Couldn't preinitialize the mod", e);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public void alertPve(Creature creature, boolean performer) {
        if (performer)
            creature.getCommunicator().sendAlertServerMessage("You are in PvE zone");
        else
            creature.getCommunicator().sendAlertServerMessage("The target is in PvE zone");
    }

    private void addCatapultHook() {
        HookManager.getInstance().registerHook("com.wurmonline.server.behaviours.WarmachineBehaviour",
                "fireCatapult",
                "(Lcom/wurmonline/server/behaviours/Action;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)V",
                () -> ((proxy, method, args) -> {
                    Creature performer = (Creature) args[1];
                    if (map.isPve(performer)) {
                        alertPve(performer, true);
                        return null;
                    }
                    return method.invoke(proxy, args);
                }));
        HookManager.getInstance().registerHook("com.wurmonline.server.combat.ServerProjectile",
                "fire",
                "(Z)Z",
                () -> (((proxy, method, args) -> {
                    ServerProjectile projectile = (ServerProjectile) proxy;
                    if (map.isPve((int) (projectile.getPosDownX() / 4), (int) (projectile.getPosDownY() / 4), true)) {
                        alertPve(projectile.getShooter(), false);
                        return false;
                    }
                    return method.invoke(proxy, args);
                })));
        HookManager.getInstance().registerHook("com.wurmonline.server.combat.ServerProjectile",
                "poll",
                "(J)Z",
                () -> (((proxy, method, args) -> {
                    ServerProjectile projectile = (ServerProjectile) proxy;
                    long now = (long) args[0];
                    if (now > projectile.getTimeAtLanding()) {
                        Field projectileInfoField = ServerProjectile.class.getDeclaredField("projectileInfo");
                        projectileInfoField.setAccessible(true);
                        Object projectileInfo = projectileInfoField.get(projectile);
                        Field endPositionField = projectileInfo.getClass().getField("endPosition");
                        endPositionField.setAccessible(true);
                        Vector3f endPosition = (Vector3f) endPositionField.get(projectileInfo);
                        if (map.isPve((int) (endPosition.x / 4), (int) (endPosition.y / 4), true)) {
                            alertPve(projectile.getShooter(), false);
                            return true;
                        }
                    }
                    return method.invoke(proxy, args);
                })));
    }

    private void processScornOfLibilaSpell() throws Exception {
        CtClass ctSpellClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.spells.ScornOfLibila");
        HookManager.getInstance().addCallback(ctSpellClass, "zonedMap", map);
        HookManager.getInstance().addCallback(ctSpellClass, "zonedPve", this);
        CtMethod doEffectMethod = ctSpellClass.getMethod("doEffect", "(Lcom/wurmonline/server/skills/Skill;DLcom/wurmonline/server/creatures/Creature;IIII)V");
        doEffectMethod.insertBefore("{if (zonedMap.isPve($3)) zonedPve.alertPve($3, true);}");
        doEffectMethod.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getMethodName().equals("addWoundOfType")) {
                    if (debugMode)
                        logger.info("Recompiling addWoundOfType inside Scorn of Libila");
                    m.replace("{ if(!zonedMap.isPve($0)) $_ = $proceed($$); }");
                }
            }
        });
    }

    private void processRiteOfDeathSpell() throws Exception {
        CtClass ctSpellClass = HookManager.getInstance().getClassPool().get("com.wurmonline.server.spells.RiteDeath");
        HookManager.getInstance().addCallback(ctSpellClass, "zoneMap", map);
        CtMethod doEffectMethod = ctSpellClass.getMethod("doEffect", "(Lcom/wurmonline/server/skills/Skill;DLcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)V");
        doEffectMethod.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                if (m.getMethodName().equals("addWoundOfType")) {
                    if (debugMode)
                        logger.info("Recompiling addWoundOfType inside Rite of Death");
                    m.replace("{ if(!zoneMap.isPve($0)) $_ = $proceed($$); }");
                }
            }
        });
    }

    private void addSpellHooks() {
        for (Spell spell : Spell.values()) {
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

    private void addArcheryHook() {
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

    private void addCombatEngineHook() {
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
    public enum Spell {
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
        RiteDeath(true, "(Lcom/wurmonline/server/skills/Skill;Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/items/Item;)Z");

        public boolean withPrecondition;
        public String methodSignature;

        Spell(boolean withPrecondition, String methodSignature) {
            this.withPrecondition = withPrecondition;
            this.methodSignature = methodSignature;
        }
    }
}
