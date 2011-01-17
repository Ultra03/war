package bukkit.tommytony.war;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;

import com.tommytony.war.Monument;
import com.tommytony.war.Team;
import com.tommytony.war.TeamMaterials;
import com.tommytony.war.WarHub;
import com.tommytony.war.Warzone;
import com.tommytony.war.ZoneLobby;
import com.tommytony.war.mappers.WarMapper;
import com.tommytony.war.mappers.WarzoneMapper;


/**
 * 
 * @author tommytony
 *
 */
public class WarPlayerListener extends PlayerListener {

	private final War war;
	private Random random = null;

	public WarPlayerListener(War war) {
		this.war = war;
		random = new Random();
	}
	
	public void onPlayerJoin(PlayerEvent event) {
		event.getPlayer().sendMessage(war.str("War is on! Pick your battle (try /warzones)."));
    }
	
	public void onPlayerQuit(PlayerEvent event) {
		Player player = event.getPlayer();
		Team team = war.getPlayerTeam(player.getName());
		if(team != null) {
			team.removePlayer(player.getName());
		}
	}

	
	public void onPlayerMove(PlayerMoveEvent event) {
		Player player = event.getPlayer();
		Location from = event.getFrom();
		Location to = event.getTo();
		
		// Zone walls
		if(to != null && !war.isZoneMaker(player.getName())) { // zone makers don't get bothered with guard walls
			Warzone nearbyZone = war.zoneOfZoneWallAtProximity(to);
			if(nearbyZone != null) {	
				nearbyZone.protectZoneWallAgainstPlayer(player);
			} else {
				// make sure to delete any wall guards as you leave
				for(Warzone zone : war.getWarzones()) {
					zone.dropZoneWallGuardIfAny(player);
				}
			}
		}
		
		Warzone playerWarzone = war.getPlayerWarzone(player.getName());	// this uses the teams, so it asks: get the player's team's warzone, to be clearer
		if(playerWarzone != null) {
			Team team = war.getPlayerTeam(player.getName());
			
			// Player belongs to a warzone team but is outside: he just died! Handle death! Exempt the zone maker.
			if(from != null && war.warzone(player.getLocation()) == null && team != null && !war.isZoneMaker(player.getName())) {
				// teleport to team spawn upon death
				
				boolean roundOver = false;
				synchronized(playerWarzone) {
					int remaining = team.getRemainingTickets();
					if(remaining == 0) { // your death caused your team to lose
						List<Team> teams = playerWarzone.getTeams();
						for(Team t : teams) {
							t.teamcast(war.str("The battle is over. Team " + team.getName() + " lost: " 
									+ player.getName() + " hit the bottom of their life pool." ));
							t.teamcast(war.str("A new battle begins. The warzone is being reset..."));
							if(!t.getName().equals(team.getName())) {
								// all other teams get a point
								t.addPoint();
								t.resetSign();
							}
						}
						playerWarzone.getVolume().resetBlocks();
						playerWarzone.initializeZone();
						roundOver = true;
					} else {
						team.setRemainingTickets(remaining - 1);
					}
				}
				synchronized(player) {
					if(!roundOver && !war.inAnyWarzone(player.getLocation())) {	// only respawn him if he isnt back at zone yet
						playerWarzone.respawnPlayer(event, team, player);
						team.resetSign();
						war.getLogger().log(Level.INFO, player.getName() + " died and was tp'd back to team " + team.getName() + "'s spawn");
					} else {
						war.getLogger().log(Level.INFO, player.getName() + " died and battle ended in team " + team.getName() + "'s disfavor");
					}
				}
			}
			
			// Monuments
			if(to != null && team != null
					&& playerWarzone.nearAnyOwnedMonument(to, team) 
					&& player.getHealth() < 20
					&& random.nextInt(42) == 3 ) {	// one chance out of many of getting healed
				player.setHealth(20);
				player.sendMessage(war.str("Your dance pleases the monument's voodoo. You gain full health!"));
			}
		} else if (war.inAnyWarzone(player.getLocation()) && !war.isZoneMaker(player.getName())) { // player is not in any team, but inside warzone boundaries, get him out
			Warzone zone = war.warzone(player.getLocation());
			event.setTo(zone.getTeleport());
			player.sendMessage(war.str("You can't be inside a warzone without a team."));
		}
		
		if(to != null) {
			// Warzone lobby gates
			for(Warzone zone : war.getWarzones()){
				if(zone.getLobby() != null) {
					synchronized(player) {
						Team oldTeam = war.getPlayerTeam(player.getName());
						if(oldTeam == null) { // trying to counter spammy player move
							if(zone.getLobby().isAutoAssignGate(to)) {
								dropFromOldTeamIfAny(player);
								zone.autoAssign(event, player);
							} else if (zone.getLobby().isInTeamGate(TeamMaterials.TEAMDIAMOND, to)){
								dropFromOldTeamIfAny(player);
								Team diamondTeam = zone.getTeamByMaterial(TeamMaterials.TEAMDIAMOND);
								diamondTeam.addPlayer(player);
								zone.keepPlayerInventory(player);
								player.sendMessage(war.str("Your inventory is is storage until you /leave."));
								zone.respawnPlayer(event, diamondTeam, player);
								for(Team team : zone.getTeams()){
									team.teamcast(war.str("" + player.getName() + " joined team diamond."));
								}
							} else if (zone.getLobby().isInTeamGate(TeamMaterials.TEAMIRON, to)){
								dropFromOldTeamIfAny(player);
								Team ironTeam = zone.getTeamByMaterial(TeamMaterials.TEAMIRON);
								ironTeam.addPlayer(player);
								zone.keepPlayerInventory(player);
								player.sendMessage(war.str("Your inventory is is storage until you /leave."));
								zone.respawnPlayer(event, ironTeam, player);
								for(Team team : zone.getTeams()){
									team.teamcast(war.str("" + player.getName() + " joined team iron."));
								}
							} else if (zone.getLobby().isInTeamGate(TeamMaterials.TEAMGOLD, to)){
								dropFromOldTeamIfAny(player);
								Team goldTeam = zone.getTeamByMaterial(TeamMaterials.TEAMGOLD);
								goldTeam.addPlayer(player);
								zone.keepPlayerInventory(player);
								player.sendMessage(war.str("Your inventory is is storage until you /leave."));
								zone.respawnPlayer(event, goldTeam, player);
								for(Team team : zone.getTeams()){
									team.teamcast(war.str("" + player.getName() + " joined team gold."));
								}
							} else if (zone.getLobby().isInWarHubLinkGate(to)){
								dropFromOldTeamIfAny(player);
								event.setTo(war.getWarHub().getLocation());
								player.sendMessage(war.str("Welcome to the War hub."));
							}
						} else if(war.inAnyWarzone(event.getFrom())) { // already in a team and in warzone, leaving
							if(zone.getLobby().isAutoAssignGate(to)
									|| zone.getLobby().isInTeamGate(TeamMaterials.TEAMDIAMOND, to)
									|| zone.getLobby().isInTeamGate(TeamMaterials.TEAMIRON, to)
									|| zone.getLobby().isInTeamGate(TeamMaterials.TEAMGOLD, to)) {
								// same as leave, except event.setTo
								Team playerTeam = war.getPlayerTeam(player.getName());
								playerTeam.removePlayer(player.getName());
								event.setTo(playerWarzone.getTeleport());
								player.sendMessage(war.str("Left the zone."));
								playerWarzone.restorePlayerInventory(player);
								player.sendMessage(war.str("Your inventory has (hopefully) been restored."));
							}
						}
					}
				}
			}
			
			// Warhub zone gates
			WarHub hub = war.getWarHub();
			if(hub != null) {
				Warzone zone = hub.getDestinationWarzoneForLocation(player.getLocation());
				synchronized(player) {
					if(zone != null) {
						event.setTo(zone.getTeleport());
						//player.teleportTo(zone.getTeleport());
						player.sendMessage(war.str("Welcome to warzone " + zone.getName() + "."));
					}
				}
			}
		}
    }
	
	private void dropFromOldTeamIfAny(Player player) {
		// drop from old team if any
		Team previousTeam = war.getPlayerTeam(player.getName());
		if(previousTeam != null) {
			if(!previousTeam.removePlayer(player.getName())){
				war.getLogger().log(Level.WARNING, "Could not remove player " + player.getName() + " from team " + previousTeam.getName());
			}
		}
	}

	public String getAllTeamsMsg(Player player){
		String teamsMessage = "Teams: ";
		Warzone warzone = war.warzone(player.getLocation());
		ZoneLobby lobby = war.lobby(player.getLocation());
		if(warzone == null && lobby != null) {
			warzone = lobby.getZone();
		} else {
			lobby = warzone.getLobby();
		}
		if(warzone.getTeams().isEmpty()){
			teamsMessage += "none.";
		}
		for(Team team :warzone.getTeams()) {
			teamsMessage += team.getName() + " (" + team.getPoints() + " points, "+ team.getRemainingTickets() + "/" + warzone.getLifePool() + " lives left. ";
			for(Player member : team.getPlayers()) {
				teamsMessage += member.getName() + " ";
			}
			teamsMessage += ")  ";
		}
		return teamsMessage;
	}

	
	
}
