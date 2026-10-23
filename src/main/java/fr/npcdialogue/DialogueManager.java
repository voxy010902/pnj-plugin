package fr.npcdialogue;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class DialogueManager {
    private final NpcDialoguePlugin plugin;
    private final File folder;
    private final Map<String, Dialogue> dialogues = new TreeMap<>();

    public DialogueManager(NpcDialoguePlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "dialogues");
    }

    public void load() {
        dialogues.clear();
        if (!folder.exists()) {
            folder.mkdirs();
            plugin.saveResource("dialogues/example.yml", false);
        }
        File[] files = folder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4);
            try {
                dialogues.put(id, parse(id, YamlConfiguration.loadConfiguration(f)));
            } catch (Exception ex) {
                plugin.getLogger().warning("Dialogue invalide " + f.getName() + " : " + ex.getMessage());
            }
        }
    }

    private Dialogue parse(String id, YamlConfiguration y) {
        Dialogue d = new Dialogue(id);
        ConfigurationSection ns = y.getConfigurationSection("nodes");
        if (ns != null) {
            for (String key : ns.getKeys(false)) {
                ConfigurationSection s = ns.getConfigurationSection(key);
                if (s == null) continue;
                Dialogue.Node n = new Dialogue.Node(key);
                n.speaker = s.getString("speaker");
                n.portrait = s.getString("portrait");
                n.next = s.getString("next");
                if (s.isList("text")) n.text.addAll(s.getStringList("text"));
                else if (s.isString("text")) n.text.add(s.getString("text"));
                n.commands.addAll(s.getStringList("commands"));
                for (Map<?, ?> m : s.getMapList("branches")) {
                    n.branches.add(new Dialogue.Branch(str(m.get("permission")), str(m.get("next"))));
                }
                for (Map<?, ?> m : s.getMapList("choices")) {
                    Dialogue.Choice c = new Dialogue.Choice();
                    if (m.get("text") != null) c.text = str(m.get("text"));
                    c.next = str(m.get("next"));
                    c.permission = str(m.get("permission"));
                    c.close = Boolean.parseBoolean(String.valueOf(m.get("close")));
                    if (m.get("commands") instanceof List<?> l) {
                        for (Object o : l) c.commands.add(String.valueOf(o));
                    }
                    n.choices.add(c);
                }
                d.nodes.put(key, n);
            }
        }
        d.start = y.getString("start");
        if (d.start == null && !d.nodes.isEmpty()) d.start = d.nodes.keySet().iterator().next();
        return d;
    }

    public void save(Dialogue d) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("start", d.start);
        for (Dialogue.Node n : d.nodes.values()) {
            String p = "nodes." + n.id;
            if (n.speaker != null) y.set(p + ".speaker", n.speaker);
            if (n.portrait != null) y.set(p + ".portrait", n.portrait);
            y.set(p + ".text", new ArrayList<>(n.text));
            if (n.next != null) y.set(p + ".next", n.next);
            if (!n.commands.isEmpty()) y.set(p + ".commands", new ArrayList<>(n.commands));
            if (!n.branches.isEmpty()) {
                List<Map<String, Object>> l = new ArrayList<>();
                for (Dialogue.Branch b : n.branches) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    if (b.permission() != null) m.put("permission", b.permission());
                    m.put("next", b.next());
                    l.add(m);
                }
                y.set(p + ".branches", l);
            }
            if (!n.choices.isEmpty()) {
                List<Map<String, Object>> l = new ArrayList<>();
                for (Dialogue.Choice c : n.choices) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("text", c.text);
                    if (c.next != null) m.put("next", c.next);
                    if (c.close) m.put("close", true);
                    if (c.permission != null) m.put("permission", c.permission);
                    if (!c.commands.isEmpty()) m.put("commands", new ArrayList<>(c.commands));
                    l.add(m);
                }
                y.set(p + ".choices", l);
            }
        }
        try {
            folder.mkdirs();
            y.save(new File(folder, d.id + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().severe("Sauvegarde dialogue " + d.id + " impossible : " + e.getMessage());
        }
    }

    public Dialogue create(String id) {
        Dialogue d = new Dialogue(id);
        dialogues.put(id, d);
        save(d);
        return d;
    }

    public boolean delete(String id) {
        boolean existed = dialogues.remove(id) != null;
        File f = new File(folder, id + ".yml");
        if (f.exists() && !f.delete()) plugin.getLogger().warning("Impossible de supprimer " + f);
        return existed;
    }

    public Dialogue get(String id) { return id == null ? null : dialogues.get(id); }
    public Collection<Dialogue> all() { return dialogues.values(); }
    public Set<String> ids() { return dialogues.keySet(); }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
}
