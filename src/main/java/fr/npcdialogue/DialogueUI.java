package fr.npcdialogue;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Interface de dialogue : utilise les écrans "Dialog" natifs (Paper), habillés par le resource pack. */
public final class DialogueUI {
    private static final ClickCallback.Options ONE_USE = ClickCallback.Options.builder().uses(1).build();

    private final NpcDialoguePlugin plugin;

    public DialogueUI(NpcDialoguePlugin plugin) {
        this.plugin = plugin;
    }

    /** @param npc peut être null (mode test) */
    public void open(Player p, Npc npc, String dialogueId, String startNode) {
        Dialogue d = plugin.dialogues().get(dialogueId);
        if (d == null) {
            p.sendMessage(Msg.err("Dialogue introuvable : " + dialogueId));
            return;
        }
        String start = (startNode == null || startNode.isBlank()) ? d.start : startNode;
        show(p, npc, d, start);
    }

    public void show(Player p, Npc npc, Dialogue d, String nodeId) {
        Dialogue.Node n = d.nodes.get(nodeId);
        if (n == null) {
            p.closeDialog();
            p.sendMessage(Msg.err("Noeud introuvable : " + d.id + "/" + nodeId));
            return;
        }

        // Redirections selon permissions (branches)
        for (int i = 0; i < 10 && !n.branches.isEmpty(); i++) {
            Dialogue.Node target = null;
            for (Dialogue.Branch b : n.branches) {
                if (Perm.has(p, b.permission())) {
                    target = d.nodes.get(b.next());
                    break;
                }
            }
            if (target == null) break;
            n = target;
        }
        final Dialogue.Node node = n;

        if (!node.commands.isEmpty()) Actions.run(plugin, p, npc, node.commands);

        FileConfiguration cfg = plugin.getConfig();
        Key font = Key.key(cfg.getString("ui.font", "npcdialog:ui"));
        int textWidth = cfg.getInt("ui.text-width", 300);
        int buttonWidth = cfg.getInt("ui.button-width", 280);

        // ---- Titre : portrait + nom
        String portraitKey = node.portrait != null ? node.portrait
                : (npc != null && npc.portrait != null ? npc.portrait : cfg.getString("ui.default-portrait", "default"));
        String portraitGlyph = Text.unescape(cfg.getString("ui.portraits." + portraitKey, ""));
        Component title = Component.empty();
        if (!portraitGlyph.isEmpty()) {
            title = title.append(Component.text(portraitGlyph).font(font).color(NamedTextColor.WHITE))
                    .append(Component.space());
        }
        String speaker = node.speaker != null ? node.speaker : (npc != null ? npc.displayName : "<gray>???");
        title = title.append(Text.component(speaker, p, npc));

        // ---- Corps : séparateur + paragraphes
        List<DialogBody> body = new ArrayList<>();
        String divider = Text.unescape(cfg.getString("ui.divider", ""));
        if (!divider.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text(divider).font(font).color(NamedTextColor.WHITE), textWidth));
        }
        for (String para : node.text) {
            if (para.isBlank()) continue;
            body.add(DialogBody.plainMessage(Text.component(para, p, npc), textWidth));
        }

        // ---- Boutons
        String arrow = Text.unescape(cfg.getString("ui.arrow", ""));
        List<ActionButton> buttons = new ArrayList<>();
        for (Dialogue.Choice c : node.choices) {
            if (!Perm.has(p, c.permission)) continue;   // choix masqué sans la permission
            Component label = Text.component(c.text, p, npc);
            if (!arrow.isEmpty()) {
                label = Component.empty()
                        .append(Component.text(arrow).font(font).color(NamedTextColor.WHITE))
                        .append(Component.space()).append(label);
            }
            buttons.add(button(label, buttonWidth, clicker -> {
                click(clicker);
                if (c.close || c.next == null) {
                    clicker.closeDialog();
                    // 1 tick plus tard : laisse l'écran se fermer avant d'ouvrir un menu (DeluxeMenus...)
                    Bukkit.getScheduler().runTask(plugin, () -> Actions.run(plugin, clicker, npc, c.commands));
                } else {
                    Actions.run(plugin, clicker, npc, c.commands);
                    show(clicker, npc, d, c.next);
                }
            }));
        }

        String exitText = cfg.getString("ui.exit-button", "");
        ActionButton exit = null;
        if (buttons.isEmpty()) {
            // Pas de choix : "Continuer" (si next) ou "Fermer"
            if (node.next != null) {
                Component label = Text.component(cfg.getString("ui.continue-button", "<green>Continuer"), p, npc);
                buttons.add(button(label, buttonWidth, clicker -> {
                    click(clicker);
                    show(clicker, npc, d, node.next);
                }));
            } else {
                String t = exitText.isEmpty() ? "<gray>[Fermer]" : exitText;
                buttons.add(button(Text.component(t, p, npc), buttonWidth, clicker -> {
                    click(clicker);
                    clicker.closeDialog();
                }));
            }
        } else if (!exitText.isEmpty()) {
            exit = button(Text.component(exitText, p, npc), buttonWidth, clicker -> {
                click(clicker);
                clicker.closeDialog();
            });
        }

        final Component finalTitle = title;
        final ActionButton finalExit = exit;
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(finalTitle)
                        .canCloseWithEscape(cfg.getBoolean("ui.close-with-escape", true))
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(buttons, finalExit, 1)));

        p.showDialog(dialog);
        String sound = cfg.getString("ui.sounds.open", "");
        if (!sound.isEmpty()) p.playSound(p.getLocation(), sound, 1f, 1f);
    }

    private void click(Player p) {
        String sound = plugin.getConfig().getString("ui.sounds.click", "");
        if (!sound.isEmpty()) p.playSound(p.getLocation(), sound, 0.6f, 1f);
    }

    private ActionButton button(Component label, int width, Consumer<Player> handler) {
        return ActionButton.create(label, null, width, DialogAction.customClick((view, audience) -> {
            if (!(audience instanceof Player clicker)) return;
            Bukkit.getScheduler().runTask(plugin, () -> handler.accept(clicker));
        }, ONE_USE));
    }
}
