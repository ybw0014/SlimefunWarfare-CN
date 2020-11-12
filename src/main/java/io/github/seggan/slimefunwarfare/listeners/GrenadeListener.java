package io.github.seggan.slimefunwarfare.listeners;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class GrenadeListener implements Listener {

    @EventHandler
    public void onGrenadeHit(ProjectileHitEvent e) {
        Projectile projectile = e.getEntity();
        if (projectile.getType() != EntityType.SNOWBALL) {
            return;
        }

        if (projectile.hasMetadata("effect")) {
            String id = projectile.getMetadata("effect").get(0).asString();
            switch (id) {
                case "NITROGEN_TRIIODIDE":
                    Location loc = e.getHitBlock().getRelative(e.getHitBlockFace()).getLocation();
                    projectile.getWorld().createExplosion(loc, 2F, false, false);
                    AreaEffectCloud cloud = (AreaEffectCloud) projectile.getWorld()
                        .spawnEntity(loc, EntityType.AREA_EFFECT_CLOUD);
                    cloud.addCustomEffect(new PotionEffect(
                        PotionEffectType.BLINDNESS,
                        100,
                        2,
                        false,
                        false
                    ), true);
                    cloud.setDuration(100);
                    cloud.setDurationOnUse(0);
                    cloud.setRadiusOnUse(0);
                    cloud.setColor(Color.PURPLE);
                    cloud.setRadius(3);
            }
        }
    }
}