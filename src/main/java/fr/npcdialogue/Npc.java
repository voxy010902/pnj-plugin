package fr.npcdialogue;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Npc {

    public enum Type { PLAYER, MODEL }

    /** Une règle d'interaction : conditions (permission + clic) -> commandes et/ou dialogue. */
    public static final class Rule {
        public String permission;           // null = règle par défaut ; "!x" = ne doit pas avoir x
        public String click = "RIGHT";      // RIGHT | LEFT | ANY
        public String dialogue;             // id de dialogue (optionnel)
        public String start;                // noeud de départ (optionnel)
        public final List<String> commands = new ArrayList<>();

        public boolean matchesClick(String c) {
            return click.equalsIgnoreCase("ANY") || click.equalsIgnoreCase(c);
        }
    }

    public final String id;
    public Type type = Type.PLAYER;
    public String skin = "Steve";           // pseudo ou UUID (type PLAYER)
    public String model;                    // id ModelEngine (type MODEL)
    public String displayName;
    public boolean nameVisible = true;
    public String portrait;                 // clé ui.portraits (optionnel)
    public double hitboxWidth = 0.8;        // hitbox cliquable (type MODEL)
    public double hitboxHeight = 2.0;
    public final List<Rule> rules = new ArrayList<>();

    // position
    public String world;
    public double x, y, z;
    public float yaw, pitch;

    // entités (runtime)
    public UUID entityId;
    public UUID hitboxId;

    public Npc(String id) {
        this.id = id;
        this.displayName = "<yellow>" + id;
    }

    public Location location() {
        World w = world == null ? null : Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public void setLocation(Location l) {
        this.world = l.getWorld().getName();
        this.x = l.getX();
        this.y = l.getY();
        this.z = l.getZ();
        this.yaw = l.getYaw();
        this.pitch = l.getPitch();
    }

    /** Règles avec permission d'abord (dans l'ordre), puis la première règle par défaut. */
    public Rule select(Player p, String click) {
        Rule fallback = null;
        for (Rule r : rules) {
            if (!r.matchesClick(click)) continue;
            if (r.permission == null || r.permission.isBlank() || r.permission.equals("-")) {
                if (fallback == null) fallback = r;
                continue;
            }
            if (Perm.has(p, r.permission)) return r;
        }
        return fallback;
    }
}
