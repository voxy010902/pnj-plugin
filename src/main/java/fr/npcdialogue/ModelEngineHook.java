package fr.npcdialogue;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.UUID;

/** Intégration ModelEngine (R3/R4) par réflexion : aucune dépendance à la compilation. */
public final class ModelEngineHook {
    private ModelEngineHook() {}

    public static boolean available() {
        return Bukkit.getPluginManager().getPlugin("ModelEngine") != null;
    }

    private static Class<?> api() throws ClassNotFoundException {
        return Class.forName("com.ticxo.modelengine.api.ModelEngineAPI");
    }

    public static boolean hasModel(Entity e) {
        try {
            Object o;
            try {
                o = api().getMethod("getModeledEntity", Entity.class).invoke(null, e);
            } catch (NoSuchMethodException ex) {
                o = api().getMethod("getModeledEntity", UUID.class).invoke(null, e.getUniqueId());
            }
            return o != null;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    public static void apply(Entity base, String modelId) throws ReflectiveOperationException {
        Class<?> api = api();
        Class<?> activeCls = Class.forName("com.ticxo.modelengine.api.model.ActiveModel");
        Class<?> modeledCls = Class.forName("com.ticxo.modelengine.api.model.ModeledEntity");

        Object active = api.getMethod("createActiveModel", String.class).invoke(null, modelId);
        if (active == null) throw new IllegalStateException("Modèle ModelEngine introuvable : " + modelId);
        Object modeled = api.getMethod("createModeledEntity", Entity.class).invoke(null, base);

        modeledCls.getMethod("addModel", activeCls, boolean.class).invoke(modeled, active, true);
        try {
            modeledCls.getMethod("setBaseEntityVisible", boolean.class).invoke(modeled, false);
        } catch (NoSuchMethodException ignored) {
            // selon la version de ModelEngine
        }
    }
}
