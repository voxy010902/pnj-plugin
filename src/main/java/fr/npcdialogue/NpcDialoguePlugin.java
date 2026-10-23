package fr.npcdialogue;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class NpcDialoguePlugin extends JavaPlugin {

    private static NpcDialoguePlugin instance;
    private DialogueManager dialogues;
    private NpcManager npcs;
    private DialogueUI ui;

    public static NpcDialoguePlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        dialogues = new DialogueManager(this);
        dialogues.load();
        ui = new DialogueUI(this);
        npcs = new NpcManager(this);
        npcs.load();

        getServer().getPluginManager().registerEvents(new NpcListener(this), this);

        NpcCommand npcCommand = new NpcCommand(this);
        PluginCommand c1 = getCommand("npcd");
        if (c1 != null) {
            c1.setExecutor(npcCommand);
            c1.setTabCompleter(npcCommand);
        }
        DialogueCommand dialogueCommand = new DialogueCommand(this);
        PluginCommand c2 = getCommand("dialogue");
        if (c2 != null) {
            c2.setExecutor(dialogueCommand);
            c2.setTabCompleter(dialogueCommand);
        }

        // Les mondes sont chargés : on rattache / fait réapparaître les PNJ
        getServer().getScheduler().runTask(this, npcs::adoptAll);
        getLogger().info("NpcDialogue activé : " + npcs.all().size() + " PNJ, "
                + dialogues.all().size() + " dialogues. ModelEngine : "
                + (ModelEngineHook.available() ? "détecté" : "absent"));
    }

    @Override
    public void onDisable() {
        if (npcs != null) npcs.save();
    }

    public void reloadAll() {
        reloadConfig();
        dialogues.load();
        npcs.load();
        npcs.adoptAll();
    }

    public DialogueManager dialogues() { return dialogues; }
    public NpcManager npcs() { return npcs; }
    public DialogueUI ui() { return ui; }
}
