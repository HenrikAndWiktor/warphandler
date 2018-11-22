package se.wiktoreriksson.server.warphandler;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.messaging.PluginMessageRecipient;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("ALL")
public final class WarpHandlerSpigot extends JavaPlugin implements PluginMessageListener, Listener {
    @EventHandler
    public void lagOnMove(PlayerMoveEvent pme) {
        portalsender:
        for (String s:
                portals.getKeys(false)) {
            int x = pme.getTo().getBlockX();
            int y = pme.getTo().getBlockY();
            int z = pme.getTo().getBlockZ();
            if (x == portals.getInt(s + ".x") && y == portals.getInt(s + ".y") && z == portals.getInt(s + ".z")) {
                sendPlayer(portals.getString(s + ".server"), pme.getPlayer(), portals.getInt(s + ".tox"), portals.getInt(s + ".toy"), portals.getInt(s + ".toz"));
                break portalsender;
            }
        }
    }



    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();
        if (subchannel.equals("GetServers")) {
            // Use the code sample in the 'Response' sections below to read
            // the data.
            servers = in.readUTF().replaceAll("mini.,","");
        } else if (subchannel.equals("Teleport")) {
            String splayer = in.readUTF();
            int x = Integer.parseInt(in.readUTF());
            int y = Integer.parseInt(in.readUTF());
            int z = Integer.parseInt(in.readUTF());
            Bukkit.getPlayerExact(splayer).teleport(new Location(Bukkit.getPlayerExact(splayer).getWorld(),x,y,z),PlayerTeleportEvent.TeleportCause.COMMAND);
        }
    }
    Scanner scan;
    String servers;
    YamlConfiguration portals;
    Map<String,Map<Character,Integer>> playertp = new HashMap<>();

    public void forwardMsg(String server,String subchannel,String... msg) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Forward"); // So BungeeCord knows to forward it
        out.writeUTF(server);
        out.writeUTF(subchannel); // The channel name to check if this your data

        ByteArrayOutputStream msgbytes = new ByteArrayOutputStream();
        DataOutputStream msgout = new DataOutputStream(msgbytes);
        try {
            for (String m :
                    msg) {
                msgout.writeUTF(m);
            }
        } catch (IOException exception){
            exception.printStackTrace();
        }

        out.writeShort(msgbytes.toByteArray().length);
        out.write(msgbytes.toByteArray());
        getServer().sendPluginMessage(WarpHandlerSpigot.this, "BungeeCord", out.toByteArray());
    }

    public void sendPluginMsg(PluginMessageRecipient pmr,String... sendarg) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        for (String arg:sendarg) out.writeUTF(arg);
        pmr.sendPluginMessage(WarpHandlerSpigot.this, "BungeeCord", out.toByteArray());
    }

    @Override
    public void onEnable() {
        try {
            // Plugin startup logic
            new File("wmsg.txt").createNewFile();
            scan = new Scanner(
                    new File("wmsg.txt")
            );
            new File(getDataFolder().getAbsolutePath()+"portals.yml").createNewFile();
            portals=YamlConfiguration.loadConfiguration(
                    new File(
                            getDataFolder()+"portals.yml"
                    )
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
        getServer().getPluginManager().registerEvents(this,this);
        WarpHandlerSpigot.this.getServer().getMessenger().registerOutgoingPluginChannel(WarpHandlerSpigot.this, "BungeeCord");
        WarpHandlerSpigot.this.getServer().getMessenger().registerIncomingPluginChannel(WarpHandlerSpigot.this, "BungeeCord", WarpHandlerSpigot.this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent pje) {
        scan.forEachRemaining(pje.getPlayer()::sendMessage);
        sendPluginMsg(pje.getPlayer(),"GetServers");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public void sendPlayer(String server,Player p,int x,int y,int z) {
        String s = p.getName();
        sendPlayer(server, p);
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                forwardMsg(server,"Teleport",s,String.valueOf(x),String.valueOf(y),String.valueOf(z));
            }
        },3000);
    }

    public void sendPlayer(String server,Player p) {
        sendPluginMsg(p,"Connect",server);
    }

    /**
     * {@inheritDoc}
     *
     * @param sender sender
     * @param command command obj
     * @param label command name
     * @param args arguments
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("warp")) {
            switch (args.length) {
                case 0:
                    sender.sendMessage(ChatColor.GREEN+"Servers: "+servers);
                    break;
                case 1:
                    if (!(sender instanceof Player))return false;
                    sendPlayer(args[0],(Player)sender);
                    break;
                case 2:
                    if (!sender.hasPermission("warps.send.other")) return false;
                    sendPlayer(args[0],Bukkit.getPlayerExact(args[1]));
                    break;
                default:
                    return false;
            }
        } else if (label.equalsIgnoreCase("createportal")) {
            if (args.length!=5) return false;


            String world = args[0];
            String server = args[1];
            Map<String,Integer> xyz = new HashMap<>();
            xyz.put("x",Integer.parseInt(args[2]));
            xyz.put("y",Integer.parseInt(args[3]));
            xyz.put("z",Integer.parseInt(args[4]));
            xyz.put("tox",Integer.parseInt(args[5]));
            xyz.put("toy",Integer.parseInt(args[6]));
            xyz.put("toz",Integer.parseInt(args[7]));
            String id = args[8].replaceAll(",-:;_'¨~´`§½ ","").replace(".","");

            if (!portals.get(id+".exist","false").equals("false")) {
                sender.sendMessage(ChatColor.RED +"Sorry! Portal already exists! Choose another id or /removeportal");
                return false;
            }

            portals.set(id+".server",sender);
            portals.set(id+".world",world);
            portals.set(id+".x",xyz.get("x"));
            portals.set(id+".y",xyz.get("y"));
            portals.set(id+".z",xyz.get("z"));
            portals.set(id+".tox",xyz.get("tox"));
            portals.set(id+".toy",xyz.get("toy"));
            portals.set(id+".toz",xyz.get("toz"));
            portals.set(id+".exist","true");
            sender.sendMessage(ChatColor.GREEN+"Portal "+id+" created");
        } else if (label.equalsIgnoreCase("removeportal")) {
            if (args.length!=1) return false;
            if (portals.get(args[0]+".exist","false").equals("false")) {
                sender.sendMessage("Portal does not exist!");
                return false;
            }

            portals.set(args[0]+".exist","false");
            sender.sendMessage(ChatColor.RED +"Portal "+args[0]+" removed");
        }
        return true;
    }
}
