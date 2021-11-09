package io.github.seggan.slimefunwarfare;

import com.google.common.collect.Sets;
import io.github.mooy1.infinitylib.common.Events;
import io.github.mooy1.infinitylib.common.Scheduler;
import io.github.mooy1.infinitylib.core.AbstractAddon;
import io.github.mooy1.infinitylib.metrics.bukkit.Metrics;
import io.github.seggan.slimefunwarfare.items.guns.Gun;
import io.github.seggan.slimefunwarfare.items.powersuits.ArmorPiece;
import io.github.seggan.slimefunwarfare.items.powersuits.Module;
import io.github.seggan.slimefunwarfare.items.powersuits.PowerSuit;
import io.github.seggan.slimefunwarfare.listeners.BreakListener;
import io.github.seggan.slimefunwarfare.listeners.BulletListener;
import io.github.seggan.slimefunwarfare.listeners.ChatListener;
import io.github.seggan.slimefunwarfare.listeners.ConcreteListener;
import io.github.seggan.slimefunwarfare.listeners.GrenadeListener;
import io.github.seggan.slimefunwarfare.listeners.HitListener;
import io.github.seggan.slimefunwarfare.listeners.ModuleListener;
import io.github.seggan.slimefunwarfare.listeners.NukeListener;
import io.github.seggan.slimefunwarfare.listeners.PyroListener;
import io.github.seggan.slimefunwarfare.lists.Categories;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;

public class SlimefunWarfare extends AbstractAddon implements Listener {

    private static SlimefunWarfare instance = null;

    private static final Set<UUID> flying = new HashSet<>();

    public SlimefunWarfare() {
        super("ybw0014", "SlimefunWarfare-CN", "master", "auto-update");
    }

    @Override
    public void enable() {
        instance = this;

        new Metrics(this, 9227);

        Events.registerListener(new BulletListener());
        Events.registerListener(new PyroListener());
        Events.registerListener(new GrenadeListener());
        Events.registerListener(new ConcreteListener());
        Events.registerListener(new NukeListener());
        Events.registerListener(new HitListener());
        Events.registerListener(new ModuleListener());
        Events.registerListener(new BreakListener());
        Events.registerListener(new ChatListener());

        Categories.setup(this);

        Setup.setupItems(this);
        Setup.setupMelee(this);
        Setup.setupBullets(this);
        Setup.setupGuns(this);
        Setup.setupExplosives(this);
        Setup.setupSpace(this);
        Setup.setupSuits(this);
        Setup.setupResearches();

        Module.setup(this);

        if (getJavaVersion() < 16) {
            log(Level.WARNING, "你正在使用Java16以下的版本,请尽快使用Java16");
        }

        if (getConfig().getBoolean("guns.autoshoot", true)) {
            // Gun autoshoot task
            Scheduler.repeat(1, () -> {
                for (Player p : getServer().getOnlinePlayers()) {
                    if (p.isSneaking() && !p.isFlying()) {
                        ItemStack stack = p.getInventory().getItemInMainHand();
                        SlimefunItem item = SlimefunItem.getByItem(stack);
                        if (!(item instanceof Gun)) {
                            continue;
                        }
                        Gun gun = (Gun) item;
                        ItemMeta meta = stack.getItemMeta();
                        PersistentDataContainer container = meta.getPersistentDataContainer();
                        long lastUse = container.getOrDefault(Gun.LAST_USE, PersistentDataType.LONG, 0L);
                        long currentTime = System.currentTimeMillis();
                        if ((currentTime - lastUse) < gun.getCooldown()) {
                            continue;
                        }
                        container.set(Gun.LAST_USE, PersistentDataType.LONG, currentTime);
                        stack.setItemMeta(meta);
                        gun.shoot(p, stack);
                    }
                }
            });
        }

        Scheduler.repeat(20, () -> {
            for (Player p : getServer().getOnlinePlayers()) {
                PlayerInventory inv = p.getInventory();

                ItemStack head = inv.getHelmet();
                Util.ifPowerSuit(head, suit -> process(head, PowerSuit.getModules(head), suit, p));

                ItemStack chest = inv.getChestplate();
                Util.ifPowerSuit(chest, suit -> process(chest, PowerSuit.getModules(chest), suit, p));

                ItemStack legs = inv.getLeggings();
                Util.ifPowerSuit(legs, suit -> process(legs, PowerSuit.getModules(legs), suit, p));

                ItemStack boots = inv.getBoots();
                Util.ifPowerSuit(boots, suit -> process(boots, PowerSuit.getModules(boots), suit, p), () -> {
                    UUID uuid = p.getUniqueId();
                    if (flying.contains(uuid)) {
                        flying.remove(uuid);
                        p.setAllowFlight(false);
                    }
                });
            }
        });

        if (getConfig().getBoolean("suits.flight-particles", true)) {
            Scheduler.repeat(4, () -> {
                for (UUID uuid : flying) {
                    Player p = getServer().getPlayer(uuid);
                    if (p == null) {
                        flying.remove(uuid);
                        return;
                    }
                    if (p.isFlying()) {
                        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().subtract(0, 1, 0), 20, 0.5, 0.5, 0.5);
                    }
                }
            });
        }
    }

    @Override
    protected void disable() {
        instance = null;
    }

    @EventHandler
    public void onPlayerJoin(@Nonnull PlayerJoinEvent e) {
        Player p = e.getPlayer();
        ItemStack boots = p.getInventory().getBoots();
        if (p.getAllowFlight() && SlimefunItem.getByItem(boots) instanceof PowerSuit &&
            Sets.newHashSet(PowerSuit.getModules(boots)).contains(Module.MINI_JETS)) {
            flying.add(p.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerLeave(@Nonnull PlayerQuitEvent e) {
        flying.remove(e.getPlayer().getUniqueId());
    }

    private static void process(ItemStack stack, Module[] modules, PowerSuit suit, Player p) {
        UUID uuid = p.getUniqueId();

        for (Module module : modules) {
            PotionEffect effect = module.getEffect();
            if (effect != null && suit.getItemCharge(stack) >= module.getPower()) {
                p.addPotionEffect(effect);
                suit.removeItemCharge(stack, module.getPower());
            }

            switch (module) {
                case MINI_JETS:
                    if (!p.getAllowFlight()) {
                        p.setAllowFlight(true);
                        flying.add(uuid);
                    }
                    if (p.isFlying()) {
                        if (suit.getItemCharge(stack) < module.getPower()) {
                            p.setAllowFlight(false);
                            flying.remove(uuid);
                        } else {
                            suit.removeItemCharge(stack, module.getPower());
                        }
                    }
                    break;
                case AUXILIARY_GENERATOR:
                    suit.addItemCharge(stack, module.getPower());
                    break;
            }
        }

        if (suit.getType() == ArmorPiece.FEET && flying.contains(p.getUniqueId()) && !Sets.newHashSet(modules).contains(Module.MINI_JETS)) {
            p.setAllowFlight(false);
            flying.remove(uuid);
        }

        suit.addItemCharge(stack, 5);
    }

    private static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if( dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }

    public static SlimefunWarfare inst() {
        return instance;
    }
}
