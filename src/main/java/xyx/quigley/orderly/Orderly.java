package xyx.quigley.orderly;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import xyx.quigley.orderly.api.UIManager;
import xyx.quigley.orderly.config.OrderlyConfigManager;
import xyx.quigley.orderly.ui.DefaultUIStyle;
import xyx.quigley.orderly.ui.SaoUIStyle;

public class Orderly implements ClientModInitializer {

    public static final String MODID = "orderly";
    private static KeyBinding toggleKey;
    private static final Logger log = LogManager.getLogger(MODID);

    public static Logger getLogger() {
        return log;
    }

    static {
        // configure debug logging if certain flags are set. this also ensures compatibility with
        // mainline Mesh-Library debug behaviour, without directly depending on the library
        if (Boolean.getBoolean("fabric.development")
                || Boolean.getBoolean("orderly.debug")
                || Boolean.getBoolean("mesh.debug")
                || Boolean.getBoolean("mesh.debug.logging")) {
            Configurator.setLevel(MODID, Level.ALL);
        }
    }

    @Override
    public void onInitializeClient() {
        // TODO find a good place for registering those
        final Identifier defaultStyle = new Identifier(MODID, "default");
        final Identifier saoStyle = new Identifier(MODID, "sao_like");
        UIManager.registerStyle(defaultStyle, DefaultUIStyle::getInstance);
        UIManager.registerStyle(saoStyle, SaoUIStyle::new);
        UIManager.setCurrentStyle(defaultStyle);
        OrderlyConfigManager.init();
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.orderly.toggle",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                "category.orderly"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(event -> {
            if (event.isWindowFocused() && toggleKey.wasPressed()) {
                OrderlyConfigManager.getConfig().toggleDraw();
                OrderlyConfigManager.save();
            }
        });
    }
}
