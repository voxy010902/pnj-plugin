package fr.npcdialogue;

import org.bukkit.entity.Player;

/** Vérification de permissions (compatible LuckPerms : LuckPerms alimente les permissions Bukkit). */
public final class Perm {
    private Perm() {}

    /**
     * @param expr null/vide/"-" = toujours vrai ; "perm.x" = doit avoir ; "!perm.x" = ne doit PAS avoir.
     */
    public static boolean has(Player p, String expr) {
        if (expr == null) return true;
        String e = expr.trim();
        if (e.isEmpty() || e.equals("-")) return true;
        if (e.startsWith("!")) return !check(p, e.substring(1).trim());
        return check(p, e);
    }

    private static boolean check(Player p, String perm) {
        boolean ignoreOp = NpcDialoguePlugin.get().getConfig().getBoolean("permissions.ignore-op", true);
        if (ignoreOp && p.isOp() && !p.isPermissionSet(perm)) return false;
        return p.hasPermission(perm);
    }
}
