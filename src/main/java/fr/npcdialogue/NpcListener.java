package fr.npcdialogue;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NpcListener implements Listener {
    private final NpcDialoguePlugin plugin;
    private final Map<UUID, Long> last = new HashMap<>();

    public NpcListener(NpcDialoguePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent e) {
        Npc npc = plugin.npcs().npcOf(e.getRightClicked());
        if (npc == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        trigger(e.getPlayer(), npc, "RIGHT");
    }

    @EventHandler
    public void onPreAttack(PrePlayerAttackEntityEvent e) {
        Npc npc = plugin.npcs().npcOf(e.getAttacked());
        if (npc == null) return;
        e.setCancelled(true);
        trigger(e.getPlayer(), npc, "LEFT");
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        Npc npc = plugin.npcs().npcOf(e.getEntity());
        if (npc == null) return;
        e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Player p) {
            trigger(p, npc, "LEFT");
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        plugin.npcs().onChunkEntitiesLoaded(e.getChunk(), e.getEntities());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        last.remove(e.getPlayer().getUniqueId());
    }

    private void trigger(Player p, Npc npc, String click) {
        if (!p.hasPermission("npcdialogue.interact")) return;
        long now = System.currentTimeMillis();
        Long prev = last.get(p.getUniqueId());
        if (prev != null && now - prev < 300) return;   // anti double-événement
        last.put(p.getUniqueId(), now);
        plugin.npcs().interact(p, npc, click);
    }
}
