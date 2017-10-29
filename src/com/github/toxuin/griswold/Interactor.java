package com.github.toxuin.griswold;

import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

class Interactor {
	public static double basicToolsPrice = 10.0;
	public static double basicArmorPrice = 10.0;
	public static double enchantmentPrice = 30.0;
	
	public static boolean enableEnchants = true;
	public static double addEnchantmentPrice = 50.0;
	public static int maxEnchantBonus = 5;
	public static boolean clearEnchantments = false;

	private final List<Material> repairableTools = new LinkedList<Material>();
	private final List<Material> repairableArmor = new LinkedList<Material>();
	private final List<Material> notEnchantable = new LinkedList<Material>();

    final Class craftItemStack = ClassProxy.getClass("inventory.CraftItemStack");
    final Class enchantmentInstance = ClassProxy.getClass("EnchantmentInstance");
    final Class enchantmentManager = ClassProxy.getClass("EnchantmentManager");

	public Interactor() {
        repairableTools.add(Material.IRON_AXE);
        repairableTools.add(Material.IRON_PICKAXE);
        repairableTools.add(Material.IRON_SWORD);
        repairableTools.add(Material.IRON_HOE);
        repairableTools.add(Material.IRON_SPADE);              // IRON TOOLS

        repairableTools.add(Material.WOOD_AXE);
        repairableTools.add(Material.WOOD_PICKAXE);
        repairableTools.add(Material.WOOD_SWORD);
        repairableTools.add(Material.WOOD_HOE);
        repairableTools.add(Material.WOOD_SPADE);              // WOODEN TOOLS

        repairableTools.add(Material.STONE_AXE);
        repairableTools.add(Material.STONE_PICKAXE);
        repairableTools.add(Material.STONE_SWORD);
        repairableTools.add(Material.STONE_HOE);
        repairableTools.add(Material.STONE_SPADE);             // STONE TOOLS

        repairableTools.add(Material.DIAMOND_AXE);
        repairableTools.add(Material.DIAMOND_PICKAXE);
        repairableTools.add(Material.DIAMOND_SWORD);
        repairableTools.add(Material.DIAMOND_HOE);
        repairableTools.add(Material.DIAMOND_SPADE);           // DIAMOND TOOLS

        repairableTools.add(Material.GOLD_AXE);
        repairableTools.add(Material.GOLD_PICKAXE);
        repairableTools.add(Material.GOLD_SWORD);
        repairableTools.add(Material.GOLD_HOE);
        repairableTools.add(Material.GOLD_SPADE);               // GOLDEN TOOLS

        repairableTools.add(Material.BOW);                      // BOW

        repairableTools.add(Material.FLINT_AND_STEEL);          // ZIPPO
        repairableTools.add(Material.SHEARS);                   // SCISSORS
        repairableTools.add(Material.FISHING_ROD);              // FISHING ROD
        repairableTools.add(Material.BOOK);                     // BOOK
        repairableTools.add(Material.ENCHANTED_BOOK);

        notEnchantable.add(Material.FLINT_AND_STEEL);
        notEnchantable.add(Material.SHEARS);
        notEnchantable.add(Material.FISHING_ROD);

        // ARMORZ!
        repairableArmor.add(Material.LEATHER_BOOTS);
        repairableArmor.add(Material.LEATHER_CHESTPLATE);
        repairableArmor.add(Material.LEATHER_HELMET);
        repairableArmor.add(Material.LEATHER_LEGGINGS);

        repairableArmor.add(Material.CHAINMAIL_BOOTS);
        repairableArmor.add(Material.CHAINMAIL_CHESTPLATE);
        repairableArmor.add(Material.CHAINMAIL_HELMET);
        repairableArmor.add(Material.CHAINMAIL_LEGGINGS);

        repairableArmor.add(Material.IRON_BOOTS);
        repairableArmor.add(Material.IRON_CHESTPLATE);
        repairableArmor.add(Material.IRON_HELMET);
        repairableArmor.add(Material.IRON_LEGGINGS);

        repairableArmor.add(Material.GOLD_BOOTS);
        repairableArmor.add(Material.GOLD_CHESTPLATE);
        repairableArmor.add(Material.GOLD_HELMET);
        repairableArmor.add(Material.GOLD_LEGGINGS);

        repairableArmor.add(Material.DIAMOND_BOOTS);
        repairableArmor.add(Material.DIAMOND_CHESTPLATE);
        repairableArmor.add(Material.DIAMOND_HELMET);
        repairableArmor.add(Material.DIAMOND_LEGGINGS);

        File configFile = new File(Griswold.directory, "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        if (configFile.exists()) {
            if (config.isConfigurationSection("CustomItems.Tools")) {
                Set<String> tools = config.getConfigurationSection("CustomItems.Tools").getKeys(false);
                for (String itemId : tools) repairableTools.add(Material.getMaterial(Integer.parseInt(itemId)));
                Griswold.log.info("Added "+tools.size()+" custom tools from config file");
            }

            if (config.isConfigurationSection("CustomItems.Armor")) {
                Set<String> armor = config.getConfigurationSection("CustomItems.Armor").getKeys(false);
                for (String itemId : armor) repairableArmor.add(Material.getMaterial(Integer.parseInt(itemId)));
                Griswold.log.info("Added " + armor.size() + " custom armors from config file");
            }
        }
	}
	
	private final Set<Interaction> interactions = new HashSet<Interaction>();

	@SuppressWarnings("unchecked")
    public void interact(Player player, Repairer repairman) {
		ItemStack item = player.getItemInHand();

        repairman.haggle();
		
		double price = Math.round(getPrice(repairman, item));

		if (item.getType() == Material.AIR) {
			player.sendMessage(String.format(Lang.name_format, repairman.name)+Lang.chat_noitem);
			return;
		}

		if (checkCanRepair(player, repairman, item)) {
			Interaction interaction = new Interaction(player.getUniqueId(), repairman.entity, item, item.getDurability(), System.currentTimeMillis());
			
			// INTERACTS SECOND TIME
			
			for (Interaction inter : interactions) {
				if (interaction.equals(inter)) {
					
					if (item.getDurability() != 0 && (
							repairman.type.equalsIgnoreCase("armor") || 
							repairman.type.equalsIgnoreCase("tools") ||
							repairman.type.equalsIgnoreCase("both") ||
							repairman.type.equalsIgnoreCase("all")
						)) {
						 EconomyResponse r = null;
						if (Griswold.economy == null || Griswold.economy.getBalance(player) >= price) {
							if (Griswold.economy != null) r = Griswold.economy.withdrawPlayer(player, price);
				            if (Griswold.economy == null || r.transactionSuccess()) {
								item.setDurability((short) 0);
					            inter.valid = false; // INVALIDATE INTERACTION
								player.sendMessage(String.format(Lang.name_format, repairman.name)+Lang.chat_done);
				            } else {
					            inter.valid = false; // INVALIDATE INTERACTION
				            	player.sendMessage(String.format(Lang.name_format, repairman.name)+ChatColor.RED+Lang.chat_error);
				            }
							return;
						} else {
							inter.valid = false; // INVALIDATE INTERACTION
							player.sendMessage(String.format(Lang.name_format, repairman.name)+Lang.chat_poor);
							return;
						}
					} else if (enableEnchants && item.getDurability() == 0 && (repairman.type.equalsIgnoreCase("enchant") || repairman.type.equalsIgnoreCase("all"))) {
							price = addEnchantmentPrice;
							EconomyResponse r = null;
							if (Griswold.economy == null || Griswold.economy.getBalance(player) >= price) {
								if (Griswold.economy != null) r = Griswold.economy.withdrawPlayer(player, price);
					            if (Griswold.economy == null || r.transactionSuccess()) {
						            if (clearEnchantments) {
							            for (Enchantment enchantToDel : item.getEnchantments().keySet()) {
								            item.removeEnchantment(enchantToDel);
							            }
						            }

                                    try {
                                        Method asNMSCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
                                        Object vanillaItem = asNMSCopy.invoke(null, (item.getType().equals(Material.ENCHANTED_BOOK)) ? new ItemStack(Material.BOOK) : item);
                                        int bonus = (new Random()).nextInt(maxEnchantBonus);
                                        Method b;
                                        List<?> list;
                                        if (Griswold.apiVersion.getMajor() >=1 && Griswold.apiVersion.getMajor() >= 9) {
                                            b =  enchantmentManager.getDeclaredMethod("b", Random.class, vanillaItem.getClass(), int.class, boolean.class);
                                            list = (List) b.invoke(null, new Random(), vanillaItem, bonus, false);
                                        } else {
                                            b =  enchantmentManager.getDeclaredMethod("b", Random.class, vanillaItem.getClass(), int.class);
                                            list = (List) b.invoke(null, new Random(), vanillaItem, bonus);
                                        }

                                        EnchantmentStorageMeta bookmeta = null;
                                        ItemStack bookLeftovers = null;
                                        if (item.getType().equals(Material.BOOK)) {
                                            if (item.getAmount() > 1) bookLeftovers = new ItemStack(Material.BOOK, item.getAmount()-1);
                                            player.getInventory().remove(item);
                                            item = new ItemStack(Material.ENCHANTED_BOOK);
                                            bookmeta = (EnchantmentStorageMeta) item.getItemMeta();
                                        } else if (item.getType().equals(Material.ENCHANTED_BOOK)) {
                                            bookmeta = (EnchantmentStorageMeta) item.getItemMeta();
                                        }

                                        if (list != null) {
                                            for (Object obj : list) {
                                                Object instance = enchantmentInstance.cast(obj);
                                                Field enchantmentField = enchantmentInstance.getField("enchantment");
                                                Field levelField = enchantmentInstance.getField("level");
                                                Object enchantment = enchantmentField.get(instance);
                                                Field idField = enchantment.getClass().getField("id");
                                                if (item.getType().equals(Material.ENCHANTED_BOOK) && bookmeta != null) {
                                                    bookmeta.addStoredEnchant(Enchantment.getById(Integer.parseInt(idField.get(enchantment).toString())), Integer.parseInt(levelField.get(instance).toString()), true);
                                                } else {
                                                    item.addEnchantment(Enchantment.getById(Integer.parseInt(idField.get(enchantment).toString())), Integer.parseInt(levelField.get(instance).toString()));
                                                }
                                            }

                                            if (item.getType().equals(Material.ENCHANTED_BOOK)) {
                                                item.setItemMeta(bookmeta);
                                                player.getInventory().setItemInHand(item);
                                                if (bookLeftovers!=null) {
                                                    if (player.getInventory().firstEmpty() == -1) { // INVENTORY FULL, DROP TO PLAYER
                                                        player.getWorld().dropItemNaturally(player.getLocation(), bookLeftovers);
                                                    } else player.getInventory().addItem(bookLeftovers);
                                                }
                                            }

                                            inter.valid = false; // INVALIDATE INTERACTION
                                            player.sendMessage(String.format(Lang.name_format, repairman.name) + Lang.chat_enchant_success);
                                        } else {
                                            inter.valid = false; // INVALIDATE INTERACTION
                                            player.sendMessage(String.format(Lang.name_format, repairman.name) + Lang.chat_enchant_failed);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
									return;
							
					            } else {
						            inter.valid = false; // INVALIDATE INTERACTION
									player.sendMessage(String.format(Lang.name_format, repairman.name)+Lang.chat_poor);
									return;
								}
							}
					} else {
						inter.valid = false; // INVALIDATE INTERACTION
		            	player.sendMessage(String.format(Lang.name_format, repairman.name)+ChatColor.RED+Lang.chat_error);
					}
				}
			}
			
			// INTERACTS FIRST TIME
			
			if (interactions.size() > 10) interactions.clear(); // THIS SUCKS, I KNOW

			if (item.getDurability() != 0) {
				// NEEDS REPAIR
				if (!repairman.type.equalsIgnoreCase("enchant")) {
					// CAN REPAIR
					interactions.add(interaction);
					if (Griswold.economy != null) player.sendMessage(String.format(ChatColor.GOLD+"<"+repairman.name+"> "+ChatColor.WHITE+
							Lang.chat_cost, price, Griswold.economy.currencyNamePlural()));
					else player.sendMessage(String.format(Lang.name_format, repairman.name)+Lang.chat_free);
					player.sendMessage(String.format(Lang.name_format, repairman.name)+Lang.chat_agreed);
				} else {
					// CANNOT REPAIR
					player.sendMessage(String.format(Lang.name_format, repairman.name)+Lang.chat_needs_repair);
				}
			} else {
				// NEEDS ENCHANT
				if (enableEnchants && !notEnchantable.contains(item.getType()) && (repairableTools.contains(item.getType()) || repairableArmor.contains(item.getType()) )) { // ENCHANTS ENABLED AND THINGY IS ENCHANTABLE
					price = addEnchantmentPrice;
					if (repairman.type.equalsIgnoreCase("enchant") || repairman.type.equalsIgnoreCase("all")) { // CAN ENCHANT
						interactions.add(interaction);
						if (Griswold.economy != null) player.sendMessage(String.format(String.format(Lang.name_format, repairman.name)+
								Lang.chat_enchant_cost, price, Griswold.economy.currencyNamePlural()));
						else player.sendMessage(String.format(Lang.name_format, repairman.name)+Lang.chat_enchant_free);
						player.sendMessage(String.format(Lang.name_format, repairman.name)+Lang.chat_agreed);
					} else { // CANNOT ENCHANT
						player.sendMessage(String.format(Lang.name_format, repairman.name)+Lang.chat_norepair); // NO REPAIR NEEDED, CAN NOT ENCHANT
					}
				} else { // ENCHANTS DISABLED
					player.sendMessage(String.format(Lang.name_format, repairman.name)+Lang.chat_norepair); // NO REPAIR NEEDED, CAN NOT ENCHANT
				}
			}
		} else {
			player.sendMessage(String.format(Lang.name_format, repairman.name)+Lang.chat_cannot);
		}
	}

	private boolean checkCanRepair(Player player, Repairer repairman, ItemStack item) {
		if (repairman.type.equalsIgnoreCase("all")) {
			if (item.getDurability() != 0) {
				if (repairableArmor.contains(item.getType())) {
					// check for armor perm
					return player.hasPermission("griswold.armor");
				} else {
					return (repairableTools.contains(item.getType()) &&       // check tools perm
							player.hasPermission("griswold.tools"));
				}
			} else {
				return player.hasPermission("griswold.enchant");
			}
		} else if (repairman.type.equalsIgnoreCase("both")) {
			if (repairableArmor.contains(item.getType())) {
				return player.hasPermission("griswold.armor");
			} else {
				return (repairableTools.contains(item.getType()) &&
						player.hasPermission("griswold.tools"));
			}
		} else if (repairman.type.equalsIgnoreCase("tools")) {
			return player.hasPermission("griswold.tools");
		} else if (repairman.type.equalsIgnoreCase("armor")) {
			return player.hasPermission("griswold.armor");
		} else if (repairman.type.equalsIgnoreCase("enchant")) {
			return player.hasPermission("griswold.enchant");
		}
		return false;
	}

	private double getPrice(Repairer repairman, ItemStack item) {
		if (Griswold.economy == null) return 0.0;
		double price = 0;
		if (repairableTools.contains(item.getType())) price = basicToolsPrice;
		else if (repairableTools.contains(item.getType())) price = basicArmorPrice;

		price += item.getDurability();

		Map<Enchantment, Integer> enchantments = item.getEnchantments();

		if (!enchantments.isEmpty()) {
			for (int i = 0; i<enchantments.size(); i++) {
				Object[] enchantsLevels = enchantments.values().toArray();
				price = price + enchantmentPrice * Integer.parseInt(enchantsLevels[i].toString());
			}
		}
		return price * repairman.cost;
	}

    protected void finalize() throws Throwable {
        repairableArmor.clear();
        repairableTools.clear();
        notEnchantable.clear();
        super.finalize();
    }
}

class Interaction {
	private final UUID player;
	private final Entity repairman;
	private final ItemStack item;
	private final int damage;
	private final long time;
	boolean valid;
	public Interaction(UUID playerId, Entity repairman, ItemStack item, int dmg, long time) {
		this.item = item;
		this.damage = dmg;
		this.player = playerId;
		this.repairman = repairman;
		this.time = time;
		this.valid = true;
	}

	public boolean equals(Interaction inter) {
		int delta = (int) (time-inter.time);
		return ((inter.item.equals(item)) &&
				(inter.valid) &&
				(inter.damage == damage) &&
				(inter.player.equals(player)) &&
				(inter.repairman.equals(repairman)) &&
				(delta < Griswold.timeout));
	}
}