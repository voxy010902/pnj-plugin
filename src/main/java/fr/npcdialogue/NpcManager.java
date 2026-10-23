package fr.npcdialogue;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class NpcManager {
    private final NpcDialoguePlugin plugin;
    private final NamespacedKey key;
    private final File file;
    private final Map<String, Npc> npcs = new LinkedHashMap<>();

    public NpcManager(NpcDialoguePlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "npc_id");
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    // ───────────────────────── Persistance ─────────────────────────

    public void load() {
        npcs.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("npcs");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Npc n = new Npc(id);
            try {
                n.type = Npc.Type.valueOf(s.getString("type", "PLAYER").toUpperCase());
            } catch (IllegalArgumentException ex) {
                n.type = Npc.Type.PLAYER;
            }
            n.skin = s.getString("skin", "Steve");
            n.model = s.getString("model");
            n.displayName = s.getString("name", "<yellow>" + id);
            n.nameVisible = s.getBoolean("name-visible", true);
            n.portrait = s.getString("portrait");
            n.hitboxWidth = s.getDouble("hitbox.width", 0.8);
            n.hitboxHeight = s.getDouble("hitbox.height", 2.0);
            n.world = s.getString("location.world");
            n.x = s.getDouble("location.x");
            n.y = s.getDouble("location.y");
            n.z = s.getDouble("location.z");
            n.yaw = (float) s.getDouble("location.yaw");
            n.pitch = (float) s.getDouble("location.pitch");
            for (Map<?, ?> m : s.getMapList("rules")) {
                Npc.Rule r = new Npc.Rule();
                r.permission = str(m.get("permission"));
                if (m.get("click") != null) r.click = String.valueOf(m.get("click")).toUpperCase();
                r.dialogue = str(m.get("dialogue"));
                r.start = str(m.get("start"));
                if (m.get("commands") instanceof List<?> l) {
                    for (Object o : l) r.commands.add(String.valueOf(o));
                }
                n.rules.add(r);
            }
            npcs.put(id, n);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Npc n : npcs.values()) {
            String p = "npcs." + n.id;
            y.set(p + ".type", n.type.name());
            y.set(p + ".skin", n.skin);
            if (n.model != null) y.set(p + ".model", n.model);
            y.set(p + ".name", n.displayName);
            y.set(p + ".name-visible", n.nameVisible);
            if (n.portrait != null) y.set(p + ".portrait", n.portrait);
            y.set(p + ".hitbox.width", n.hitboxWidth);
            y.set(p + ".hitbox.height", n.hitboxHeight);
            y.set(p + ".location.world", n.world);
            y.set(p + ".location.x", n.x);
            y.set(p + ".location.y", n.y);
            y.set(p + ".location.z", n.z);
            y.set(p + ".location.yaw", (double) n.yaw);
            y.set(p + ".location.pitch", (double) n.pitch);
            List<Map<String, Object>> rules = new ArrayList<>();
            for (Npc.Rule r : n.rules) {
                Map<String, Object> m = new LinkedHashMap<>();
                if (r.permission != null) m.put("permission", r.permission);
                m.put("click", r.click);
                if (r.dialogue != null) m.put("dialogue", r.dialogue);
                if (r.start != null) m.put("start", r.start);
                if (!r.commands.isEmpty()) m.put("commands", new ArrayList<>(r.commands));
                rules.add(m);
            }
            y.set(p + ".rules", rules);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Sauvegarde npcs.yml impossible : " + e.getMessage());
        }
    }

    // ───────────────────────── API ─────────────────────────

    public Npc get(String id) { return id == null ? null : npcs.get(id.toLowerCase()); }
    public Collection<Npc> all() { return npcs.values(); }
    public Set<String> ids() { return npcs.keySet(); }

    public Npc create(String id, Npc.Type type, String value, String name, Location loc) {
        Npc n = new Npc(id);
        n.type = type;
        if (type == Npc.Type.PLAYER) n.skin = value;
        else {
            n.model = value;
            n.nameVisible = false;   // ModelEngine : le nom flottant est au sol, on le masque par défaut
        }
        n.displayName = name;
        n.setLocation(loc);
        npcs.put(id, n);
        save();
        spawn(n);
        return n;
    }

    public void remove(String id) {
        Npc n = npcs.remove(id);
        if (n == null) return;
        despawn(n);
        save();
    }

    public void saveAndRespawn(Npc n) {
        save();
        spawn(n);
    }

    public Npc npcOf(Entity e) {
        if (e == null) return null;
        String id = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id == null ? null : npcs.get(id);
    }

    public void interact(Player p, Npc npc, String click) {
        Npc.Rule r = npc.select(p, click);
        if (r == null) return;
        if (!r.commands.isEmpty()) Actions.run(plugin, p, npc, r.commands);
        if (r.dialogue != null && !r.dialogue.isBlank()) plugin.ui().open(p, npc, r.dialogue, r.start);
    }

    // ───────────────────────── Entités ─────────────────────────

    private boolean alive(UUID id) {
        return id != null && Bukkit.getEntity(id) != null;
    }

    private boolean isLoaded(Location l) {
        return l != null && l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4);
    }

    public void despawn(Npc n) {
        for (UUID u : new UUID[]{n.entityId, n.hitboxId}) {
            if (u == null) continue;
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        n.entityId = null;
        n.hitboxId = null;
    }

    /** (Re)crée les entités du PNJ à sa position. */
    public void spawn(Npc n) {
        Location loc = n.location();
        if (loc == null) {
            plugin.getLogger().warning("PNJ " + n.id + " : monde '" + n.world + "' non chargé.");
            return;
        }
        loc.getWorld().getChunkAt(loc);   // s'assure que le chunk est chargé
        despawn(n);

        if (n.type == Npc.Type.PLAYER) {
            Mannequin m = loc.getWorld().spawn(loc, Mannequin.class, e -> setupMannequin(e, n));
            n.entityId = m.getUniqueId();
            resolveSkin(n, m);
        } else {
            ArmorStand base = loc.getWorld().spawn(loc, ArmorStand.class, e -> {
                e.getPersistentDataContainer().set(key, PersistentDataType.STRING, n.id);
                e.setInvisible(true);
                e.setMarker(true);
                e.setGravity(false);
                e.setInvulnerable(true);
                e.setSilent(true);
                e.setBasePlate(false);
                e.setPersistent(true);
                e.customName(Text.component(n.displayName, null, null));
                e.setCustomNameVisible(n.nameVisible);
            });
            Interaction hitbox = loc.getWorld().spawn(loc, Interaction.class, e -> {
                e.getPersistentDataContainer().set(key, PersistentDataType.STRING, n.id);
                e.setInteractionWidth((float) n.hitboxWidth);
                e.setInteractionHeight((float) n.hitboxHeight);
                e.setResponsive(true);
                e.setInvulnerable(true);
                e.setPersistent(true);
            });
            n.entityId = base.getUniqueId();
            n.hitboxId = hitbox.getUniqueId();
            applyModelLater(n, base.getUniqueId(), 2L);
        }
    }

    private void setupMannequin(Mannequin e, Npc n) {
        e.getPersistentDataContainer().set(key, PersistentDataType.STRING, n.id);
        e.setImmovable(true);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setPersistent(true);
        e.setGravity(false);
        var pb = ResolvableProfile.resolvableProfile();
        try {
            pb = pb.uuid(UUID.fromString(n.skin));
        } catch (IllegalArgumentException ex) {
            pb = pb.name(n.skin);
        }
        e.setProfile(pb.build());
        e.customName(Text.component(n.displayName, null, null));
        e.setCustomNameVisible(n.nameVisible);
        try {
            e.setDescription(null);   // masque le texte "NPC" sous le nom
        } catch (Throwable ignored) {
        }
    }

    /** Résout le skin (textures signées) auprès de Mojang puis l'applique au mannequin. */
    private void resolveSkin(Npc n, Mannequin spawned) {
        UUID entity = spawned.getUniqueId();
        PlayerProfile pp;
        try {
            pp = Bukkit.createProfile(UUID.fromString(n.skin));
        } catch (IllegalArgumentException ex) {
            pp = Bukkit.createProfile(n.skin);
        }
        pp.update().thenAccept(res -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!(Bukkit.getEntity(entity) instanceof Mannequin m)) return;
            var b = ResolvableProfile.resolvableProfile();
            if (res.getId() != null) b = b.uuid(res.getId());
            if (res.getName() != null) b = b.name(res.getName());
            for (ProfileProperty prop : res.getProperties()) b = b.addProperty(prop);
            m.setProfile(b.build());
        })).exceptionally(t -> {
            plugin.getLogger().warning("Skin '" + n.skin + "' non résolu : " + t.getMessage());
            return null;
        });
    }

    private void applyModelLater(Npc n, UUID entity, long delay) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity base = Bukkit.getEntity(entity);
            if (base == null || n.model == null) return;
            if (!ModelEngineHook.available()) {
                plugin.getLogger().warning("PNJ " + n.id + " : ModelEngine n'est pas installé.");
                return;
            }
            if (ModelEngineHook.hasModel(base)) return;
            try {
                ModelEngineHook.apply(base, n.model);
            } catch (Exception ex) {
                plugin.getLogger().warning("PNJ " + n.id + " : modèle '" + n.model + "' impossible : "
                        + (ex.getCause() != null ? ex.getCause() : ex));
            }
        }, delay);
    }

    // ───────────────────────── Rattachement au chargement ─────────────────────────

    public void adoptAll() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) adopt(e);
        }
        for (Npc n : npcs.values()) ensureSpawned(n);
    }

    public void adopt(Entity e) {
        String id = e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (id == null) return;
        Npc n = npcs.get(id);
        if (n == null) {          // PNJ supprimé alors que son chunk était déchargé
            e.remove();
            return;
        }
        boolean hitbox = e instanceof Interaction;
        UUID current = hitbox ? n.hitboxId : n.entityId;
        if (current != null && !current.equals(e.getUniqueId()) && alive(current)) {
            e.remove();           // doublon
            return;
        }
        if (hitbox) {
            n.hitboxId = e.getUniqueId();
        } else {
            n.entityId = e.getUniqueId();
            if (n.type == Npc.Type.MODEL) applyModelLater(n, e.getUniqueId(), 20L);
        }
    }

    public void ensureSpawned(Npc n) {
        Location l = n.location();
        if (!isLoaded(l)) return;
        boolean ok = alive(n.entityId) && (n.type == Npc.Type.PLAYER || alive(n.hitboxId));
        if (!ok) spawn(n);
    }

    public void onChunkEntitiesLoaded(Chunk chunk, List<Entity> entities) {
        for (Entity e : entities) adopt(e);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Npc n : npcs.values()) {
                Location l = n.location();
                if (l == null || !l.getWorld().equals(chunk.getWorld())) continue;
                if ((l.getBlockX() >> 4) == chunk.getX() && (l.getBlockZ() >> 4) == chunk.getZ()) ensureSpawned(n);
            }
        });
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
}
