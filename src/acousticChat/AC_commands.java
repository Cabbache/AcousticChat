package acousticChat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import net.md_5.bungee.api.ChatColor;

public class AC_commands implements CommandExecutor{
    
	private static AcousticChat plugin;
	
	public AC_commands(AcousticChat plugin) {
		AC_commands.plugin = plugin;
	}
	
	@Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(((sender instanceof Player) && ((Player)sender).isOp()) || sender instanceof ConsoleCommandSender)) {
			sender.sendMessage(ChatColor.RED + "Permission denied" + ChatColor.RESET);
			return true;
		}
		if (args[0].equals("reload")) {
			plugin.reloadConfig();
			//plugin.getLogger().info("AcousticChat reloaded");
			sender.sendMessage("AcousticChat reloaded");
			return true;
		}// else if (args[0].equals("bypass")) {
			
		//}
		return false;
    }
}
