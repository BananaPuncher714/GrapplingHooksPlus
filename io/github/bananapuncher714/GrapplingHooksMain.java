package io.github.bananapuncher714;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class GrapplingHooksMain extends JavaPlugin implements Listener {
	private HashSet< FishHook > grapples = new HashSet< FishHook >();
	private HashMap< FishHook, UUID > hooks = new HashMap< FishHook, UUID >();

	private boolean usePermissions, allowGrapple, allowHook, allowLead, reduceFallDamage;
	private double maxDistanceSquared, grappleVelocityPercent, hookVelocityPercent, pullVelocityPercent, pullVelocityYCompensation, grappleVelocityYCompensation, damageReduction;
	
	@Override
	public void onEnable() {
		saveDefaultConfig();
		loadGHConfig();
		Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			public void run() {
				launch();
				pull();
			}
		}, 1, 1 );
	    Bukkit.getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public void onDisable() {
		grapples.clear();
		hooks.clear();
	}
	
	public void loadGHConfig() {
		FileConfiguration c = getConfig();
		try {
			allowGrapple = c.getBoolean( "allow-grapple" );
			allowHook = c.getBoolean( "allow-hook" );
			allowLead = c.getBoolean( "allow-pull" );
			reduceFallDamage = c.getBoolean( "reduce-fall-damage" );
			usePermissions = c.getBoolean( "use-permissions" );
			maxDistanceSquared = c.getDouble( "max-distance" ) * c.getDouble( "max-distance" );
			grappleVelocityPercent = c.getDouble( "grapple-velocity-percent" );
			hookVelocityPercent = c.getDouble( "hook-velocity-percent" );
			pullVelocityPercent = c.getDouble( "pull-velocity-percent" );
			pullVelocityYCompensation = c.getDouble( "hook-velocity-Y-compensation" );
			grappleVelocityYCompensation = c.getDouble( "grapple-velocity-Y-compensation" );
			damageReduction = c.getDouble( "damage-reduction-percent" );
		} catch ( Exception e ) {
			getLogger().info( "There has been an error with the config. Assuming default values." );
			allowGrapple = true;
			allowHook = true;
			allowLead = true;
			reduceFallDamage = true;
			usePermissions = true;
			maxDistanceSquared = 1024;
			grappleVelocityPercent = .4;
			hookVelocityPercent = .25;
			pullVelocityPercent = .15;
			pullVelocityYCompensation = .7;
			grappleVelocityYCompensation = .3;
			damageReduction = .5;
		}
		
	}
	
	public void launch() {
		if ( !grapples.isEmpty() ) {
			for ( Iterator< FishHook > i = grapples.iterator(); i.hasNext(); ) {
				FishHook hook = i.next();
				Player fisher = ( Player ) hook.getShooter();
				if ( hook.isDead() ) {
					if ( !( fisher.getInventory().getItemInMainHand().getType().equals( Material.FISHING_ROD ) ) || fisher.getLocation().distanceSquared( hook.getLocation() ) > maxDistanceSquared ) {
						grapples.remove( hook );
					} else {
						fisher.setVelocity( hook.getLocation().toVector().subtract( fisher.getLocation().toVector() ).multiply( grappleVelocityPercent ) );
						fisher.setVelocity( fisher.getVelocity().setY( fisher.getVelocity().getY() * grappleVelocityYCompensation ) );
						grapples.remove( hook );
					}			
				}
			}
		}
		return;
	}
	
	public void pull() {
		if ( !hooks.isEmpty() ) {
			try {
				for ( FishHook hook : hooks.keySet() ) {
					Entity ent = Bukkit.getEntity( hooks.get( hook ) );
					if ( ent.isDead() ) hooks.remove( hook );
					if ( hook.isDead() && hooks.containsKey( hook ) ) {
						Player player = ( Player ) hook.getShooter();
						if ( !player.getInventory().getItemInMainHand().getType().equals( Material.FISHING_ROD ) || player.getLocation().distanceSquared( hook.getLocation() ) > maxDistanceSquared ) {
							hooks.remove( hook );
						} else {
							ent.setVelocity( player.getLocation().toVector().subtract( ent.getLocation().toVector() ).multiply( hookVelocityPercent ) );
							ent.setVelocity( ent.getVelocity().setY( ent.getVelocity().getY() * pullVelocityYCompensation ) );
							hooks.remove( hook );
						}
					}
				}
			} catch ( NullPointerException e ) {
				hooks.clear();
			}
		}
	}
	
	@EventHandler
	public void onPlayerDamageEvent( EntityDamageEvent e ) {
		if ( e.getEntity() instanceof Player && reduceFallDamage ) {
			Player p = ( Player ) e.getEntity();
			if ( p.hasPermission( "grapple.grapple" ) && p.getInventory().getItemInMainHand().getType().equals( Material.FISHING_ROD ) && e.getCause().equals( DamageCause.FALL ) ) {
				e.setDamage( e.getDamage() * damageReduction );
			}
		}
	}
	
	@EventHandler( ignoreCancelled = false )
	public void onPlayerInteractEvent( PlayerInteractEvent e ) {
		Action a = e.getAction();
		Player p = e.getPlayer();
		if ( allowLead && p.getInventory().getItemInMainHand().getType().equals( Material.FISHING_ROD ) && ( a.equals( Action.LEFT_CLICK_AIR ) || a.equals( Action.LEFT_CLICK_BLOCK ) ) ) {
			for ( FishHook hook : hooks.keySet() ) {
				if ( hook.getShooter().equals( p ) ) {
					Entity ent = Bukkit.getEntity( hooks.get( hook ) );
					ent.setVelocity( p.getLocation().toVector().subtract( ent.getLocation().toVector() ).multiply( pullVelocityPercent ) );
					return;
				}
			}
		}
	}
	
	@EventHandler
	public void onProjectileHitEvent( ProjectileHitEvent e ) {
		if ( !( e.getEntity() instanceof FishHook ) || !( e.getEntity().getShooter() instanceof Player ) ) {
			return;
		}
		FishHook hook = ( FishHook ) e.getEntity();
		Player fisher = ( Player ) e.getEntity().getShooter();
		if ( ( fisher.hasPermission( "grapple.hook" ) || !usePermissions ) && e.getHitEntity() != null && allowHook && !hooks.containsKey( hook ) ) {
			hooks.put( hook, e.getHitEntity().getUniqueId() );
			return;
		}
		if ( ( fisher.hasPermission( "grapple.grapple" ) || !usePermissions ) && e.getHitBlock() != null && allowGrapple && !grapples.contains( hook ) ) {
			grapples.add( hook );
		}
	}

}
