package su.nexmedia.engine.api.menu.impl;

import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nexmedia.engine.NexPlugin;
import su.nexmedia.engine.api.config.JOption;
import su.nexmedia.engine.api.config.JYML;
import su.nexmedia.engine.api.menu.MenuItemType;
import su.nexmedia.engine.api.menu.click.ClickHandler;
import su.nexmedia.engine.api.menu.click.ClickType;
import su.nexmedia.engine.api.menu.click.ItemClick;
import su.nexmedia.engine.api.menu.item.MenuItem;
import su.nexmedia.engine.utils.PlayerUtil;
import su.nexmedia.engine.utils.StringUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ConfigMenu<P extends NexPlugin<P>> extends Menu<P> {

    protected final JYML cfg;
    protected final Map<Class<? extends Enum<?>>, ClickHandler<?>> handlers;

    public ConfigMenu(@NotNull P plugin, @NotNull JYML cfg) {
        super(plugin, "", 27);
        this.cfg = cfg;
        this.handlers = new HashMap<>();
    }

    public void load() {
        String title = JOption.create("Title", "", "Sets the GUI title.").read(this.cfg);

        int size = JOption.create("Size", 27, "Sets the GUI size. Must be multiply of 9.").read(this.cfg);

        InventoryType type = JOption.create("Inventory_Type", InventoryType.class, InventoryType.CHEST,
            "Sets the GUI type.",
            "https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/event/inventory/InventoryType.html").read(this.cfg);

        this.getOptions().setTitle(title);
        this.getOptions().setSize(size);
        this.getOptions().setType(type);

        this.cfg.getSection("Content").forEach(sId -> {
            MenuItem menuItem = this.readItem("Content." + sId);
            this.addItem(menuItem);
        });
    }

    @Override
    public void clear() {
        super.clear();
        this.handlers.clear();
    }

    public @NotNull <E extends Enum<E>> ClickHandler<E> registerHandler(@NotNull Class<E> clazz) {
        ClickHandler<E> handler = new ClickHandler<>();
        this.handlers.put(clazz, handler);
        return handler;
    }

    public @NotNull Set<Class<? extends Enum<?>>> getHandlerTypes() {
        return this.handlers.keySet();
    }

    protected @NotNull MenuItem readItem(@NotNull String path) {
        Enum<?> type = MenuItemType.NONE;
        ItemClick clickOrigin = null;

        String typeRaw = this.cfg.getString(path + ".Type", "");
        Label_Search:
        for (Class<? extends Enum<?>> clazz : this.getHandlerTypes()) {
            for (Enum<?> eType : clazz.getEnumConstants()) {
                if (eType.name().equalsIgnoreCase(typeRaw)) {
                    type = eType;
                    ClickHandler<?> handler = this.handlers.get(clazz);
                    if (handler != null) {
                        clickOrigin = handler.getClick(type);
                    }
                    break Label_Search;
                }
            }
        }

        ItemStack item = this.cfg.getItem(path + ".Item");
        int[] slots = this.cfg.getIntArray(path + ".Slots");
        int priority = this.cfg.getInt(path + ".Priority");

        ItemClick clickCommands = null;
        Map<ClickType, List<String>> commandMap = new HashMap<>();

        for (String sType : this.cfg.getSection(path + ".Click_Actions")) {
            ClickType clickType = StringUtil.getEnum(sType, ClickType.class).orElse(null);
            if (clickType == null) continue;

            List<String> commands = this.cfg.getStringList(path + ".Click_Actions." + sType);
            commandMap.put(clickType, commands);
        }

        if (!commandMap.isEmpty()) {
            clickCommands = ((viewer, event) -> {
                List<String> commands = commandMap.get(ClickType.from(event));
                if (commands == null || commands.isEmpty()) return;

                commands.forEach(command -> PlayerUtil.dispatchCommand(viewer.getPlayer(), command));
            });
        }

        MenuItem menuItem = new MenuItem(type, item, priority, slots);

        ItemClick finalClickOrigin = clickOrigin;
        ItemClick finalClickCommands = clickCommands;
        menuItem.setClick(((viewer, event) -> {
            if (finalClickOrigin != null) finalClickOrigin.click(viewer, event);
            if (finalClickCommands != null) finalClickCommands.click(viewer, event);
        }));

        return menuItem;
    }
}