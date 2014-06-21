package com.github.toxuin.griswold;

import com.github.toxuin.griswold.Metrics.Graph;
import net.milkbowl.vault.economy.Economy;
import net.minecraft.server.v1_7_R3.*;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_7_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_7_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_7_R3.entity.CraftVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;

// VERSION DEPENDANT

public class Griswold extends JavaPlugin implements Listener {
	public static File directory;
	public static String prefix = null;
	
	public boolean debug = false;
	
	public static int timeout = 5000;
	
	private static FileConfiguration config = null;
	private static File configFile = null;
	static Logger log = Logger.getLogger("Minecraft");
    private Map<Repairer, Pair> npcChunks = new HashMap<Repairer, Pair>();

	//public static Permission permission = null;
    public static Economy economy = null;
    
    public static double version;
    static String lang = "en_US";
    public boolean namesVisible = true;

	public void onEnable(){
		directory = this.getDataFolder();
		PluginDescriptionFile pdfFile = this.getDescription();
		version = Double.parseDouble(pdfFile.getVersion());
		prefix = "[" + pdfFile.getName()+ "]: ";

        // CHECK IF USING THE WRONG PLUGIN VERSION
        try {
            Object test = org.bukkit.craftbukkit.v1_7_R3.entity.CraftVillager.class.getName();
        } catch (NoClassDefFoundError ex) {
            log.severe(prefix + " PLUGIN NOT LOADED!!!");
            log.severe(prefix + " ERROR: YOU ARE USING THE WRONG VERSION OF THIS PLUGIN.");
            log.severe(prefix + " GO TO http://dev.bukkit.org/bukkit-plugins/griswold/");
            log.severe(prefix + " YOUR SERVER VERSION IS " + this.getServer().getBukkitVersion());
            log.severe(prefix + " PLUGIN NOT LOADED!!!");
            this.getPluginLoader().disablePlugin(this);
            return;
        }

		this.getServer().getPluginManager().registerEvents(new EventListener(this), this);

        getCommand("blacksmith").setExecutor(new CommandListener(this));

		this.getServer().getScheduler().scheduleSyncDelayedTask(this, new Starter(), 20);

		try {
		    Metrics metrics = new Metrics(this);
		    Graph graph = metrics.createGraph("Number of NPCs");
		    graph.addPlotter(new Metrics.Plotter("Total") {
		        @Override
		        public int getValue() {
		            return npcChunks.keySet().size();
		        }
		    });
		    metrics.start();
		} catch (IOException e) {
		    if (debug) log.info("ERROR: failed to submit stats to MCStats");
		}
		
		log.info( prefix + "Enabled! Version: " + version);
	}

	public void onDisable(){
        despawnAll();
		log.info( prefix + "Disabled.");
	}



	void reloadPlugin() {
		despawnAll();
		readConfig();
	}
	
	void createRepairman(String name, Location loc) {
        createRepairman(name, loc, "all", "1");
	}
	
	void createRepairman(String name, Location loc, String type, String cost) {
		boolean found = false;
        Set<Repairer> npcs = npcChunks.keySet();
		for (Repairer rep : npcs) {
			if (rep.name.equalsIgnoreCase(name)) found = true;
		}
		if (found) {
			log.info(prefix+String.format(Lang.repairman_exists, name));
			return;
		}
			
		config.set("repairmen."+name+".world", loc.getWorld().getName());
		config.set("repairmen."+name+".X", loc.getX());
		config.set("repairmen."+name+".Y", loc.getY());
		config.set("repairmen."+name+".Z", loc.getZ());
        config.set("repairmen."+name+".sound", "mob.villager.haggle");
		config.set("repairmen."+name+".type", type);
		config.set("repairmen."+name+".cost", Double.parseDouble(cost));
    	
    	try {
    		config.save(configFile);
    	} catch (Exception e) {
    		log.info(prefix+Lang.error_config);
    		e.printStackTrace();
    	}
		
    	Repairer meGusta = new Repairer();
    	meGusta.name = name;
    	meGusta.loc = loc;
    	meGusta.type = type;
    	meGusta.cost = Double.parseDouble(cost);
		spawnRepairman(meGusta);
	}
	
	void removeRepairman(String name) {
		if (config.isConfigurationSection("repairmen."+name)){
			config.set("repairmen."+name, null);
			try {
				config.save(configFile); 
        	} catch (Exception e) {
        		e.printStackTrace();
        	}
		} else {
			log.info(prefix+Lang.error_remove);
			return;
		}
		reloadPlugin();
	}
	
	void listRepairmen(CommandSender sender) {
		String result = "";
        Set<Repairer> npcs = npcChunks.keySet();
		for (Repairer rep : npcs) {
			result = result + rep.name + ", ";
		}
		if (!result.equals("")) {
			sender.sendMessage(ChatColor.GREEN+Lang.repairman_list);
			sender.sendMessage(result);
		}
	}
	
	void despawnAll() {
        Set<Repairer> npcs = npcChunks.keySet();
		for (Repairer rep : npcs) {
			rep.entity.remove();
		}
        npcChunks.clear();
	}

    void toggleNames() {
        namesVisible = !namesVisible;
        Set<Repairer> npcs = npcChunks.keySet();
        for (Repairer rep : npcs) {
            LivingEntity entity = (LivingEntity) rep.entity;
            entity.setCustomNameVisible(namesVisible);
        }

        config.set("ShowNames", namesVisible);
        try {
            config.save(configFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void setSound(String name, String sound) {
        Set<Repairer> npcs = npcChunks.keySet();
        for (Repairer rep : npcs) {
            if (rep.name.equals(name)) {
                rep.sound = sound;
                return;
            }
        }
    }
	
	public void spawnRepairman(Repairer squidward) {
		Location loc = squidward.loc;
		if (loc == null) {
			log.info(prefix+"ERROR: LOCATION IS NULL");
			return;
		}
		if (squidward.type.equals("enchant") && !Interactor.enableEnchants) {
			log.info(prefix+String.format(Lang.error_enchanter_not_spawned, loc.getX(), loc.getY(), loc.getZ()));
			return;
		}
		LivingEntity repairman = (LivingEntity) loc.getWorld().spawn(loc, EntityType.VILLAGER.getEntityClass());
        repairman.setCustomNameVisible(namesVisible);
        repairman.setCustomName(squidward.name);
		if (squidward.type.equals("enchant")) {
			((Villager) repairman).setProfession(Profession.LIBRARIAN);
		} else {
			((Villager) repairman).setProfession(Profession.BLACKSMITH);
		}

		squidward.entity = repairman;

		if (!npcChunks.containsKey(squidward)) npcChunks.put(squidward, new Pair(loc.getChunk().getX(), loc.getChunk().getZ()));

		//loc.getWorld().loadChunk(loc.getChunk());

		squidward.overwriteAI();

		if (debug) {
			log.info(prefix+String.format(Lang.repairman_spawn, squidward.entity.getEntityId(), loc.getX(), loc.getY(), loc.getZ()));
		}
	}
	
	private void readConfig() {

    	Lang.createLangFile();
		
		configFile = new File(directory, "config.yml");
        config = YamlConfiguration.loadConfiguration(configFile);
        
        npcChunks.clear();
        
        if (configFile.exists()) {
        	debug = config.getBoolean("Debug");
        	timeout = config.getInt("Timeout");
        	lang = config.getString("Language");
            namesVisible = config.getBoolean("ShowNames");
        	
        	if (Double.parseDouble(config.getString("Version")) < version) {
        		updateConfig(config.getString("Version"));
        	} else if (Double.parseDouble(config.getString("Version")) == 0) {
        		log.info(prefix+"ERROR! ERROR! ERROR! ERROR! ERROR! ERROR! ERROR!");
        		log.info(prefix+"ERROR! YOUR CONFIG FILE IS CORRUPT!!! ERROR!");
        		log.info(prefix+"ERROR! ERROR! ERROR! ERROR! ERROR! ERROR! ERROR!");
        	}

        	Lang.checkLangVersion(lang);
			Lang.init();

	        Interactor.basicArmorPrice = config.getDouble("BasicArmorPrice");
	        Interactor.basicToolsPrice = config.getDouble("BasicToolPrice");
	        Interactor.enchantmentPrice = config.getDouble("BasicEnchantmentPrice");
	        Interactor.addEnchantmentPrice = config.getDouble("PriceToAddEnchantment");
	        Interactor.clearEnchantments = config.getBoolean("ClearOldEnchantments");
	        Interactor.maxEnchantBonus = config.getInt("EnchantmentBonus");

	        Interactor.enableEnchants = config.getBoolean("UseEnchantmentSystem");

	        if (config.isConfigurationSection("repairmen")) {
        		Set<String> repairmen = config.getConfigurationSection("repairmen").getKeys(false);
	        	for (String repairman : repairmen) {
	        		Repairer squidward = new Repairer();
	        		squidward.name = repairman;
	        		squidward.loc = new Location(this.getServer().getWorld(config.getString("repairmen."+repairman+".world")),
	        									config.getDouble("repairmen."+repairman+".X"),
	        									config.getDouble("repairmen."+repairman+".Y"),
	        									config.getDouble("repairmen."+repairman+".Z"));
                    squidward.sound = config.getString("repairmen." + repairman + ".sound");
	        		squidward.type = config.getString("repairmen."+repairman+".type");
	        		squidward.cost = config.getDouble("repairmen."+repairman+".cost");
	        		
	        		squidward.loc.getWorld().loadChunk(squidward.loc.getChunk());
	        		
	        		spawnRepairman(squidward);
	        	}
        	}
	        log.info(prefix+Lang.config_loaded);

        	if(debug) {
        		log.info(prefix+String.format(Lang.debug_loaded, npcChunks.keySet().size()));
        	}
        } else {
        	config.set("Timeout", 5000);
        	config.set("Language", "en_US");
            config.set("ShowNames", true);
        	config.set("BasicArmorPrice", 10.0);
        	config.set("BasicToolPrice", 10.0);
        	config.set("BasicEnchantmentPrice", 30.0);
	        config.set("UseEnchantmentSystem", true);
        	config.set("PriceToAddEnchantment", 50.0);
        	config.set("ClearOldEnchantments", true);
        	config.set("EnchantmentBonus", 5);
        	config.set("Debug", false);
        	config.set("Version", this.getDescription().getVersion());
        	try {
        		config.save(configFile);
        		log.info(prefix+Lang.default_config);
        	} catch (Exception e) {
        		log.info(prefix+Lang.error_create_config);
        		e.printStackTrace();
        	}
        }
	}
	
	private void updateConfig(String oldVersion) {
		if (Double.parseDouble(oldVersion) < 0.05d) {
			// ADDED IN 0.05
			log.info(prefix+"UPDATING CONFIG "+config.getName()+" FROM VERSION OLDER THAN 0.5");

			config.set("PriceToAddEnchantment", 50.0);
	        config.set("ClearOldEnchantments", true);
	        config.set("EnchantmentBonus", 5);

	        config.set("Version", 0.05d);
	        try {
	            config.save(configFile);
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
		}

		if (Double.parseDouble(oldVersion) == 0.05d) {
			log.info(prefix+"UPDATING CONFIG "+config.getName()+" FROM VERSION 0.5");
			// ADDED IN 0.051
			config.set("UseEnchantmentSystem", true);

			config.set("Version", 0.051d);
			try {
				config.save(configFile);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

        if (Double.parseDouble(oldVersion) == 0.06d || Double.parseDouble(oldVersion) == 0.051d) {
            log.info(prefix+"UPDATING CONFIG "+config.getName()+" FROM VERSION 0.51/0.6");
            // ADDED IN 0.07
            config.set("ShowNames", true);

            config.set("Version", 0.07d);
            try {
                config.save(configFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
	}

	public Map<Repairer, Pair> getNpcChunks() {
		return this.npcChunks;
	}

	private class Starter implements Runnable {
		@Override
		public void run() {
			reloadPlugin();

			if (!setupEconomy()) log.info(prefix+Lang.economy_not_found);
			//if (!setupPermissions()) log.info(prefix+Lang.permissions_not_found);
			
		}
		
	}

    private boolean setupEconomy() {
    	if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }

        return (economy != null);
    }
}

class Repairer {
	public Entity entity;
	public String name = "Repairman";
	public Location loc;
	public String type = "all";
	public double cost = 1;
    public String sound = "mob.villager.haggle";
    private Random rnd = new Random();

    public void overwriteAI() {
        try {
            EntityVillager villager = ((CraftVillager)entity).getHandle();
            Field goalsField = EntityInsentient.class.getDeclaredField("goalSelector");
            goalsField.setAccessible(true);
            PathfinderGoalSelector goals = (PathfinderGoalSelector) goalsField.get(villager);
            Field listField = PathfinderGoalSelector.class.getDeclaredField("b");
            listField.setAccessible(true);
            @SuppressWarnings("rawtypes")
            List list = (List)listField.get(goals);
            list.clear();
            goals.a(1, new PathfinderGoalLookAtPlayer(villager, EntityHuman.class, 12.0F, 1.0F));
            goals.a(2, new PathfinderGoalRandomLookaround(villager));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void haggle() {
        if (this.sound != null && !this.sound.isEmpty() && !this.sound.equals("mute") && this.entity instanceof CraftEntity) {
            ((CraftWorld) this.entity.getLocation().getWorld()).getHandle().makeSound(((CraftEntity) this.entity).getHandle(), this.sound, 100f, 1.6F + (this.rnd.nextFloat() - this.rnd.nextFloat()) * 0.4F);
        }
    }
}

class Pair {
    public int x = 0;
    public int z = 0;
    public Pair (int x, int z) {
        this.x = x;
        this.z = z;
    }

    public boolean equals(Pair pair) {
        return this.x == pair.x && this.z == pair.z;
    }
    public String toString() {
        return "Pair{x="+this.x+"z="+this.z+"}";
    }
}
