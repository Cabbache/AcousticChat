package acousticChat;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.Random;
import java.util.Set;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/*
			Credits to
	https://github.com/cs-au-dk/dk.brics.automaton
	https://github.com/mifmif/Generex
*/
import com.mifmif.common.regex.Generex;

public class AcousticChat extends JavaPlugin implements Listener {
	public static Random r;
	public HashMap<Player, Long> cooldown;
	
	private static Logger log = null;
	
	@Override
	public void onEnable() {
		r = new Random();
		log = getLogger();

		cooldown = new HashMap<Player, Long>();//make sure this is before registerEvents
		getServer().getPluginManager().registerEvents(this, this);
		getCommand("ac").setExecutor(new AC_commands(this));
		saveResource("config.yml", false);
		
		log.info("Max distance set to " + getConfig().getInt("maxDistance"));
	}
	
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        cooldown.remove(player);
    }
	
	private String applyFormat(String format, String username, String message) {
		return format.replaceFirst(Pattern.quote("%1$s"), Matcher.quoteReplacement(username))
				.replaceFirst(Pattern.quote("%2$s"), Matcher.quoteReplacement(message));
	}
	
	@EventHandler (priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
		event.setCancelled(true);//from now on it will be handled here
		
		Player sender = event.getPlayer();
		String mformat = event.getFormat();
		String senderName = sender.getDisplayName();
		String messageText = event.getMessage();
		
		String message = applyFormat(mformat, senderName, messageText);
		
		getServer().getConsoleSender().sendMessage(message);
		sender.sendMessage(message);
		//TODO: colour this in a way to indicate who heard it
		
		@SuppressWarnings("unchecked")
		List<Player> players = (List<Player>) Bukkit.getOnlinePlayers();
		
		boolean sentOne = false;
		for (int i = 0;i < players.size();i++) {
			Player p = players.get(i);
			if (p.getUniqueId().equals(sender.getUniqueId())) continue; //message already sent to themselves
			if ((sender.isOp() && getConfig().getBoolean("opsAlwaysHeard"))) {
				p.sendMessage(message);
				sentOne = true;
				continue;
			} else if (p.isOp() && getConfig().getBoolean("opsHearEverything")) {
				p.sendMessage(message);
				sentOne = sentOne || !getConfig().getBoolean("messageWhenNoListeners.notifyIfOpHears");
				continue;
			}
			if (p.getWorld() != sender.getWorld()) continue;
			
			double entropy = calcEntropy(sender, p);
			if (entropy > 1.0) continue;
			if (getConfig().getBoolean("hideSender.enabled") && getConfig().getDouble("hideSender.minEntropy") < entropy)
				senderName = fixColor(getConfig().getString("hideSender.senderName"));
			p.sendMessage(applyFormat(mformat, senderName, addNoise(messageText, entropy)));
			sentOne = true;
		}
		if (!getConfig().getBoolean("messageWhenNoListeners.enabled") || sentOne) return;
		
		int cooltime = getConfig().getInt("messageWhenNoListeners.cooldown") * 1000;
		if (cooltime != 0 &&
				cooldown.containsKey(sender) &&
				System.currentTimeMillis() - cooldown.get(sender) < cooltime)
			return;

		sender.sendMessage(fixColor(getConfig().getString("messageWhenNoListeners.message")));
		
		if (cooltime == 0) return;
		cooldown.put(sender, System.currentTimeMillis());
	}
	
	private String fixColor(String text) {
		return ChatColor.translateAlternateColorCodes('&', text);
	}
	
	private double calcEntropy(Player sender, Player receiver) {
		double distance = sender.getLocation().distance(receiver.getLocation());
		double percent = distance / getConfig().getInt("maxDistance");
		double fluctuation = getConfig().getDouble("entropyRandomFluctuation");
		double entropy = percent + (fluctuation*2*r.nextDouble() - fluctuation);
		entropy = Math.min(Math.max(0, entropy), 1.0); //to make sure it's still in range
		entropy = getConfig().getBoolean("obeyInverseSquareLaw") ? Math.pow(entropy, 2):entropy;
		
		//increase entropy if there are blocks in the way
		if (getConfig().getBoolean("lineOfSight.enabled") && !sender.hasLineOfSight(receiver))
			entropy += getConfig().getDouble("lineOfSight.weighting");
		
		if (!getConfig().getBoolean("senderFacing.enabled")) return entropy;
		
		//increase entropy if players are not facing each other
		Vector senderFacing = sender.getEyeLocation().getDirection().normalize();
		Vector receiverDirection = receiver.getLocation().toVector().subtract(sender.getLocation().toVector()).normalize();
		
		double anglenoise = senderFacing.angle(receiverDirection) / Math.PI;
		entropy += anglenoise * getConfig().getDouble("senderFacing.weighting");
		//Pi radians is 180 degrees which should be max angle between vectors
		return entropy;
	}
	
	private int sumWeights(ArrayList<Rule> rules) {
		int weightSum = 0;
		for (Rule r : rules)
			weightSum += (r.getWeight() > 0) ? r.getWeight():0;
		return weightSum;
	}

	public String addNoise(String message, double entropy) {
		ConfigurationSection effects = getConfig().getConfigurationSection("noiseEffects");
		Set<String> ruleKeys = effects.getKeys(false);
		
		ArrayList<Rule> rules = new ArrayList<Rule>();
		for (String ruleKey : ruleKeys) {
			if (effects.contains(ruleKey+".enabled") && !effects.getBoolean(ruleKey+".enabled"))
				continue;
			rules.add(new Rule(
					effects.contains(ruleKey+".weighting", false) ? effects.getInt(ruleKey+".weighting"):-1,
					effects.contains(ruleKey+".match", false) ? effects.getString(ruleKey+".match"):"",
					fixColor(effects.contains(ruleKey+".before", false) ? effects.getString(ruleKey+".before"):""),
					fixColor(effects.contains(ruleKey+".after", false) ? effects.getString(ruleKey+".after"):""),
					effects.contains(ruleKey+".remove", false) ? effects.getBoolean(ruleKey+".remove"):false
				)
			);
		}
		
		int weightSum = sumWeights(rules);
		String noiseMessage = message;
		
		for (Rule rule : rules) {
			Pattern rulePattern = Pattern.compile(rule.getMatch());
			Matcher m = rulePattern.matcher(noiseMessage);
			
			int num_matches = 0;
			while (m.find()) num_matches++;
			m.reset();
			
			//TODO: don't count num_matches if rule weight <= 0
			int num_insertions = (rule.getWeight() < 0) ? num_matches:(int) Math.round(num_matches * ((double)(rule.getWeight()) / weightSum) * entropy);
			
			Generex before = new Generex(rule.getBefore());
			Generex after = new Generex(rule.getAfter());
			
			StringBuilder sb = new StringBuilder();
			int pos = 0;
			
			while (m.find()) {
				sb.append(noiseMessage, pos, m.start());
				if (r.nextInt(num_matches) < num_insertions) {
					sb.append(before.random());
					if (!rule.doRemove())
						sb.append(noiseMessage, m.start(), m.end());
					sb.append(after.random());
					pos = m.end();
					num_insertions--;
				} else {
					sb.append(noiseMessage, m.start(), m.end());
					pos = m.end();
				}
				num_matches--;
			}
			sb.append(noiseMessage, pos, noiseMessage.length());//in case pos still = 0
			noiseMessage = sb.toString();
		}
		
		return noiseMessage;
	}
}