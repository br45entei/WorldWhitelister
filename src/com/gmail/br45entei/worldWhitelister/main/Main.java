package com.gmail.br45entei.worldWhitelister.main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

/** Plugin that provides basic world whitelisting and blacklisting support.
 *
 * @author Brian_Entei */
public class Main extends JavaPlugin implements Listener {
	
	protected static final ConcurrentHashMap<String, List<UUID>> whitelistedWorlds = new ConcurrentHashMap<>();
	protected static final ConcurrentHashMap<String, List<UUID>> blacklistedWorlds = new ConcurrentHashMap<>();
	protected static final ConcurrentHashMap<String, Boolean> whitelistEnableFlags = new ConcurrentHashMap<>();
	protected static final ConcurrentHashMap<String, Boolean> blacklistEnableFlags = new ConcurrentHashMap<>();
	
	/** Returns the list of the desired type for the specified world, if it exists.
	 * 
	 * @param world The world whose list is requested
	 * @param whitelistOrBlacklist Whether or not the list requested is a whitelist or a blacklist
	 * @return The requested list for the specified world */
	public static List<UUID> getListFor(World world, boolean whitelistOrBlacklist) {
		ConcurrentHashMap<String, List<UUID>> worldList = whitelistOrBlacklist ? whitelistedWorlds : blacklistedWorlds;
		for(String worldName : worldList.keySet()) {
			if(worldName.equals(world.getName())) {
				return worldList.get(worldName);
			}
		}
		for(String worldName : worldList.keySet()) {
			if(worldName.equalsIgnoreCase(world.getName())) {//Check for and fix world names whose cases don't match in the worldList
				List<UUID> list = worldList.get(worldName);
				worldList.remove(worldName);
				List<UUID> check = worldList.get(world.getName());
				if(check != null) {
					for(UUID player : list) {
						if(!contains(check, player)) {
							check.add(player);
						}
					}
				} else {
					worldList.put(world.getName(), list);
				}
			}
		}
		List<UUID> list = worldList.get(world.getName());
		if(list == null) {
			list = new ArrayList<>();
			worldList.put(world.getName(), list);
		}
		return list;
	}
	
	/** Returns the list of the desired type for the specified world, if it exists.
	 * 
	 * @deprecated Use {@link #getListFor(World, boolean)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param worldName The name of the world whose list is requested
	 * @param whitelistOrBlacklist Whether or not the list requested is a whitelist or a blacklist
	 * @return The requested list for the specified world, or <tt><b>null</b></tt> if the world doesn't have a list of the requested type (or the given worldName is not valid) */
	@Deprecated
	public static List<UUID> getListFor(String worldName, boolean whitelistOrBlacklist) {
		World world = Main.server.getWorld(worldName);
		if(world != null && world.getName().equals(worldName)) {
			return getListFor(world, whitelistOrBlacklist);
		}
		ConcurrentHashMap<String, List<UUID>> worldList = whitelistOrBlacklist ? whitelistedWorlds : blacklistedWorlds;
		for(String check : worldList.keySet()) {
			if(check.equals(worldName)) {
				return worldList.get(check);
			}
		}
		return null;
	}
	
	@Deprecated
	protected static boolean _doesWorldHaveAList(String worldName, boolean whitelistOrBlacklist) {
		ConcurrentHashMap<String, List<UUID>> worldList = whitelistOrBlacklist ? whitelistedWorlds : blacklistedWorlds;
		for(String check : worldList.keySet()) {
			if(check.equals(worldName)) {
				return true;
			}
		}
		return false;
	}
	
	@Deprecated
	protected static boolean _doesWorldHaveAListIgnoreCase(String worldName, boolean whitelistOrBlacklist) {
		ConcurrentHashMap<String, List<UUID>> worldList = whitelistOrBlacklist ? whitelistedWorlds : blacklistedWorlds;
		for(String check : worldList.keySet()) {
			if(check.equalsIgnoreCase(worldName)) {
				return true;
			}
		}
		return false;
	}
	
	/** Goes through and updates each list's world name based on the matching existing world.<br>
	 * Empty lists are removed automatically.
	 * 
	 * @param mergeConflicts Whether or not a list named "worldname" and a list named "worldName" should be merged into one. This will only happen if a world matching one of the names is loaded, and the listname matching the world's name exactly will be kept. */
	public static void updateLists(boolean mergeConflicts) {
		for(World world : Main.server.getWorlds()) {
			getListFor(world, true);
			getListFor(world, false);
			isWorldListEnabled(world, true);
			isWorldListEnabled(world, false);
		}
		
		for(int i = 0; i < 2; i++) {
			boolean whitelistOrBlacklist = i == 0;
			ConcurrentHashMap<String, List<UUID>> worldList = whitelistOrBlacklist ? whitelistedWorlds : blacklistedWorlds;
			ConcurrentHashMap<String, Boolean> enableFlags = whitelistOrBlacklist ? whitelistEnableFlags: blacklistEnableFlags;
			for(String worldName : worldList.keySet()) {
				List<UUID> list = worldList.get(worldName);
				if(list.isEmpty()) {
					worldList.remove(worldName);
					continue;
				}
				World check = Main.server.getWorld(worldName);
				if(check != null && !check.getName().equals(worldName)) {
					List<UUID> existingList = worldList.get(check.getName());
					worldList.remove(worldName);
					if(existingList != null) {
						if(mergeConflicts) {
							for(UUID uuid : list) {
								if(!contains(existingList, uuid)) {
									existingList.add(uuid);
								}
							}
						} else {
							worldList.put(check.getName(), list);
						}
					} else {
						worldList.put(check.getName(), list);
					}
				}
			}
			for(String worldName : worldList.keySet()) {
				if(enableFlags.get(worldName) == null) {
					enableFlags.put(worldName, Boolean.FALSE);
				}
				
				List<UUID> list = worldList.get(worldName);
				List<UUID> cleanList = new ArrayList<>();
				for(UUID player : list) {
					if(player != null) {
						cleanList.add(player);
					}
				}
				if(cleanList.size() != list.size()) {
					worldList.put(worldName, cleanList);
				}
			}
			for(String worldName : enableFlags.keySet()) {
				if(worldList.get(worldName) == null) {
					worldList.put(worldName, new ArrayList<>());
				}
			}
		}
	}
	
	/** Since there can exist two or more UUIDs with the same string but different internal states, this method checks through a given collection of them in search for one that matches the given UUID's string output, rather than by using the collection's default implementation. (which probably uses equals() and hashCode())
	 * 
	 * @param list The list of UUIDs to check
	 * @param uuid The UUID to check
	 * @return True if the list contains the specified UUID, or another UUID with the same {@link UUID#toString() toString()} output */
	public static boolean contains(Collection<UUID> list, UUID uuid) {
		if(list != null && uuid != null) {
			for(UUID check : list) {
				if(check != null && check.toString().equals(uuid.toString())) {
					return true;
				}
			}
		}
		return false;
	}
	
	/** Since there can exist two or more UUIDs with the same string but different internal states, this method checks through a given collection of them in search for one that matches the given UUID's string output, rather than by using the collection's default implementation. (which probably uses equals() and hashCode())
	 * 
	 * @param list The list of UUIDs to remove from
	 * @param uuid The UUID to remove
	 * @return True if the list contained the specified UUID (or another UUID with the same {@link UUID#toString() toString()} output) and no longer contains it */
	public static boolean remove(Collection<UUID> list, UUID uuid) {
		boolean contained = false;
		if(list != null && uuid != null) {
			for(UUID check : new ArrayList<>(list)) {
				if(check != null && check.toString().equals(uuid.toString())) {
					list.remove(check);
					contained = true;
				}
			}
		}
		return contained;
	}
	
	/** Concatenates the strings in the given array using the given separator string and returns the result.<br>
	 * The separator is only added between the elements, and is not added to the beginning or end of the string.
	 * 
	 * @param args The array of strings that will be combined
	 * @param start The index to start at in the given array (inclusive)
	 * @param end The index to stop short at in the given array (exclusive)
	 * @param separator The separator string to use
	 * @return The resulting string */
	public static String stringArrayToString(String[] args, int start, int end, String separator) {
		StringBuilder sb = new StringBuilder();
		for(int i = start; i < end; i++) {
			sb.append(args[i]).append(i + 1 == end ? "" : separator);
		}
		return sb.toString();
	}
	
	/** Concatenates the strings in the given array using the given separator character and returns the result.<br>
	 * The separator is only added between the elements, and is not added to the beginning or end of the string.
	 * 
	 * @param args The array of strings that will be combined
	 * @param start The index to start at in the given array (inclusive)
	 * @param end The index to stop short at in the given array (exclusive)
	 * @param c The separator character to use
	 * @return The resulting string */
	public static String stringArrayToString(String[] args, int start, int end, char c) {
		return stringArrayToString(args, start, end, new String(new char[] {c}));
	}
	
	/** Concatenates the strings in the given array using the given separator character and returns the result.<br>
	 * The separator is only added between the elements, and is not added to the beginning or end of the string.
	 * 
	 * @param args The array of strings that will be combined
	 * @param start The index to start at in the given array (inclusive)
	 * @param c The separator character to use
	 * @return The resulting string */
	public static String stringArrayToString(String[] args, int start, char c) {
		return stringArrayToString(args, start, args.length, c);
	}
	
	/** Concatenates the strings in the given array using the given separator character and returns the result.<br>
	 * The separator is only added between the elements, and is not added to the beginning or end of the string.
	 * 
	 * @param args The array of strings that will be combined
	 * @param c The separator character to use
	 * @return The resulting string */
	public static String stringArrayToString(String[] args, char c) {
		return stringArrayToString(args, 0, c);
	}
	
	/** Concatenates the strings in the given array using the given separator character and returns the result.<br>
	 * The separator is only added between the elements, and is not added to the beginning or end of the string.
	 * 
	 * @param args The array of strings that will be combined
	 * @return The resulting string */
	public static String stringArrayToString(String[] args) {
		return stringArrayToString(args, ' ');
	}
	
	/** Checks the given string to see if it represents a valid UUID.
	 * 
	 * @param str The string to check
	 * @return True if the given string represents a valid UUID */
	public static boolean isUUID(String str) {
		try {
			UUID.fromString(str);
			return true;
		} catch(IllegalArgumentException ex) {
			return false;
		}
	}
	
	/** Checks the given string to see if it represents a valid integer value.
	 * 
	 * @param str The string to check
	 * @return True if the given string represents a valid integer value */
	public static boolean isInt(String str) {
		try {
			Integer.parseInt(str);
			return true;
		} catch(NumberFormatException ex) {
			return false;
		}
	}
	
	/** Reads a single line of text from a file, using LF(<tt>\n</tt>) as the line separator.<br>
	 * If the resulting line ends with a CR(<tt>\r</tt>), it is removed for convenience.
	 * 
	 * @param in The InputStream to read from
	 * @return The read String, or <tt><b>null</b></tt> if the stream is closed
	 * @throws IOException Thrown if there was an error reading data from the input stream */
	public static String readLine(InputStream in) throws IOException {
		try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			int b;
			while((b = in.read()) != -1) {
				if(b == 10) {
					break;
				}
				baos.write(b);
			}
			if(b == -1 && baos.size() == 0) {
				return null;
			}
			byte[] data = baos.toByteArray();
			String line = new String(data, 0, data.length, StandardCharsets.ISO_8859_1);
			if(line.endsWith("\r")) {
				line = line.substring(0, line.length() - 1);
			}
			return line;
		}
	}
	
	/** Returns the save folder for the requested list type.
	 * 
	 * @param whitelistOrBlacklist True for the WhiteList save folder, or false for the BlackList folder
	 * @return The requested save folder */
	public static File getListSaveFolder(boolean whitelistOrBlacklist) {
		File folder = new File(plugin.getDataFolder(), whitelistOrBlacklist ? "WhiteLists" : "BlackLists");
		folder.mkdirs();
		return folder;
	}
	
	/** Returns the save file for the requested world and list type.
	 * 
	 * @param worldName The name of the world
	 * @param whitelistOrBlacklist True for the WhiteList save file, or false for the BlackList file
	 * @return The requested save file for the given world */
	public static File getListSaveFile(String worldName, boolean whitelistOrBlacklist) {
		return new File(getListSaveFolder(whitelistOrBlacklist), worldName.replaceAll("[^a-zA-Z0-9-_\\.]", "-").concat(".txt"));
	}
	
	/** Deletes the file or folder completely. If it is a folder, all children are stepped through recursively until all child files and folders are deleted.
	 * 
	 * @param path The path to the file or folder to delete recursively
	 * @throws IOException Thrown if there was an error deleting a file or folder
	 * @author <a href="https://softwarecave.org/2018/03/24/delete-directory-with-contents-in-java/">Standard recursion using NIO (since Java 7)</a> */
	protected static void deleteDirectoryRecursion(Path path) throws IOException {
		if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
			try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
				for (Path entry : entries) {
					deleteDirectoryRecursion(entry);
				}
			}
		}
		Files.delete(path);
	}
	
	/** Saves the whitelists and blacklists in memory to file, optionally deleting the existing files on disk beforehand.
	 * 
	 * @param deleteExistingFiles Whether or not all existing save files should be deleted before saving the current ones from memory
	 * @return An integer array containing the number of world whitelists and blacklists saved, in that order */
	public static int[] saveListsToFile(boolean deleteExistingFiles) {
		if(deleteExistingFiles) {
			try {
				deleteDirectoryRecursion(getListSaveFolder(true).toPath());
				deleteDirectoryRecursion(getListSaveFolder(false).toPath());
			} catch(IOException ex) {
				System.err.print("Failed to clean out list data save folder: ");
				ex.printStackTrace(System.err);
				System.err.flush();
			}
		}
		
		updateLists(deleteExistingFiles);//Fix/update lists
		
		//Save list data
		int numWhiteListsSaved = 0, numBlackListsSaved = 0;
		for(int i = 0; i < 2; i++) {
			boolean whitelistOrBlacklist = i == 0;
			ConcurrentHashMap<String, List<UUID>> worldList = whitelistOrBlacklist ? whitelistedWorlds : blacklistedWorlds;
			ConcurrentHashMap<String, Boolean> enableFlags = whitelistOrBlacklist ? whitelistEnableFlags : blacklistEnableFlags;
			for(String worldName : worldList.keySet()) {
				List<UUID> list = worldList.get(worldName);
				Boolean enabled = enableFlags.get(worldName);
				if(enabled == null) {
					enabled = Boolean.FALSE;
					enableFlags.put(worldName, enabled);
				}
				try(PrintWriter pr = new PrintWriter(new OutputStreamWriter(new FileOutputStream(getListSaveFile(worldName, whitelistOrBlacklist)), StandardCharsets.ISO_8859_1), true)) {
					pr.println("worldName=".concat(worldName));
					pr.println("enabled=".concat(enabled.toString()));
					pr.println("# Player-UUID # Player-Name");
					pr.println("# Player UUIDs must be present; player names are ignored when loading. They are only there to help readers know who's who.");
					pr.println("# Each player's UUID must be on its own line.");
					pr.println();
					for(UUID player : list) {
						if(player == null) {
							continue;
						}
						pr.println(player.toString().concat(" # ").concat(Main.server.getOfflinePlayer(player).getName()));
					}
					pr.flush();
					if(whitelistOrBlacklist) {
						numWhiteListsSaved++;
					} else {
						numBlackListsSaved++;
					}
					pr.flush();
				} catch(IOException ex) {
					System.err.print("Failed to save ".concat(whitelistOrBlacklist ? "whitelist" : "blacklist").concat(" for world \"").concat(worldName).concat("\": "));
					ex.printStackTrace(System.err);
					System.err.flush();
				}
			}
		}
		return new int[] {numWhiteListsSaved, numBlackListsSaved};
	}
	
	/** Loads the whitelists and blacklists from file to memory, optionally clearing the existing lists in memory beforehand.
	 * 
	 * @param clearExistingLists Whether or not all existing lists in memory should be cleared before loading the ones on disk
	 * @return An integer array containing the number of world whitelists and blacklists loaded, in that order */
	public static int[] loadListsFromFile(boolean clearExistingLists) {
		if(clearExistingLists) {
			whitelistedWorlds.clear();
			blacklistedWorlds.clear();
		}
		int numWhiteListsLoaded = 0, numBlackListsLoaded = 0;
		for(int i = 0; i < 2; i++) {
			boolean whitelistOrBlacklist = i == 0;
			ConcurrentHashMap<String, List<UUID>> worldList = whitelistOrBlacklist ? whitelistedWorlds : blacklistedWorlds;
			ConcurrentHashMap<String, Boolean> enableFlags = whitelistOrBlacklist ? whitelistEnableFlags : blacklistEnableFlags;
			File folder = getListSaveFolder(whitelistOrBlacklist);
			String[] fileNames = folder.list();
			if(fileNames != null) {//This can happen sometimes. (e.g. no read access)
				for(String fileName : fileNames) {
					if(fileName.toLowerCase().endsWith(".txt")) {
						File file = new File(folder, fileName);
						try(FileInputStream in = new FileInputStream(file)) {
							String worldName = readLine(in);
							if(worldName == null || !worldName.startsWith("worldName=")) {
								continue;
							}
							worldName = worldName.substring(10);
							String enabled = readLine(in);
							if(enabled == null || !enabled.startsWith("enabled=")) {
								continue;
							}
							enableFlags.put(worldName, Boolean.valueOf(enabled.replace("enabled=", "").toLowerCase().trim()));
							List<UUID> list = worldList.get(worldName);
							if(list == null) {
								list = new ArrayList<>();
								worldList.put(worldName, list);
							}
							String line;
							while((line = readLine(in)) != null) {
								line = (line.contains("#") ? line.substring(0, line.indexOf("#")) : line).trim();
								if(line.isEmpty()) {
									continue;
								}
								if(isUUID(line)) {
									UUID uuid = UUID.fromString(line);
									if(!contains(list, uuid)) {
										list.add(uuid);
									}
								}
							}
							if(whitelistOrBlacklist) {
								numWhiteListsLoaded++;
							} else {
								numBlackListsLoaded++;
							}
						} catch(IOException ex) {
							System.err.print("Failed to load ".concat(whitelistOrBlacklist ? "whitelist" : "blacklist").concat(" from file \"").concat(fileName).concat("\": "));
							ex.printStackTrace(System.err);
							System.err.flush();
						}
					}
				}
			}
		}
		
		updateLists(clearExistingLists);//Fix/update lists
		return new int[] {numWhiteListsLoaded, numBlackListsLoaded};
	}
	
	/** Returns true if the specified world has a list of the specified type.
	 * 
	 * @param world The world to check
	 * @param whitelistOrBlacklist Whether or not the list in question is a whitelist or a blacklist
	 * @return True if the specified world has a list of the specified type */
	public static boolean doesWorldHaveAList(World world, boolean whitelistOrBlacklist) {
		return getListFor(world, whitelistOrBlacklist) != null;
	}
	
	/** Returns true if the specified world has a list of the specified type.
	 * 
	 * @deprecated Use {@link #doesWorldHaveAList(World, boolean)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param worldName The name of the world to check
	 * @param whitelistOrBlacklist Whether or not the list in question is a whitelist or a blacklist
	 * @return True if the specified world has a list of the specified type */
	@Deprecated
	public static boolean doesWorldHaveAList(String worldName, boolean whitelistOrBlacklist) {
		return getListFor(worldName, whitelistOrBlacklist) != null;
	}
	
	/** Returns true if the specified world's list of the specified type is enabled
	 * 
	 * @param world The world to check
	 * @param whitelistOrBlacklist Whether or not the list in question is a whitelist or a blacklist
	 * @return True if the specified world has a list of the specified type enabled */
	public static boolean isWorldListEnabled(World world, boolean whitelistOrBlacklist) {
		ConcurrentHashMap<String, Boolean> enableFlags = whitelistOrBlacklist ? whitelistEnableFlags : blacklistEnableFlags;
		Boolean check = enableFlags.get(world.getName());
		if(check == null) {
			check = Boolean.FALSE;
			enableFlags.put(world.getName(), check);
		}
		return check.booleanValue();
	}
	
	/** Returns true if the specified world's list of the specified type is enabled
	 * 
	 * @deprecated Use {@link #isWorldListEnabled(World, boolean)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param worldName The name of the world to check
	 * @param whitelistOrBlacklist Whether or not the list in question is a whitelist or a blacklist
	 * @return True if the specified world has a list of the specified type enabled */
	@Deprecated
	public static boolean isWorldListEnabled(String worldName, boolean whitelistOrBlacklist) {
		World checkWorld = Main.server.getWorld(worldName);
		if(checkWorld != null && checkWorld.getName().equals(worldName)) {
			return isWorldListEnabled(checkWorld, whitelistOrBlacklist);
		}
		ConcurrentHashMap<String, Boolean> enableFlags = whitelistOrBlacklist ? whitelistEnableFlags : blacklistEnableFlags;
		Boolean check = enableFlags.get(worldName);
		if(check == null) {
			return false;
		}
		return check.booleanValue();
	}
	
	/** Sets the specified world's list of the specified type's enable state.
	 * 
	 * @param world The world whose list's state will be changed
	 * @param whitelistOrBlacklist  Whether or not the list in question is a whitelist or a blacklist
	 * @param enabled Whether or not the specified list will be enabled or disabled
	 * @return True if the state of the list was just changed as a result */
	public static boolean setWorldListEnabled(World world, boolean whitelistOrBlacklist, boolean enabled) {
		ConcurrentHashMap<String, Boolean> enableFlags = whitelistOrBlacklist ? whitelistEnableFlags : blacklistEnableFlags;
		boolean wasEnabled = enableFlags.get(world.getName()) == null ? false : enableFlags.get(world.getName()).booleanValue();
		enableFlags.put(world.getName(), Boolean.valueOf(enabled));
		updateLists(true);
		return wasEnabled != enableFlags.get(world.getName()).booleanValue();
	}
	
	/** Sets the specified world's list of the specified type's enable state.
	 * 
	 * @deprecated Use {@link #setWorldListEnabled(World, boolean, boolean)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param worldName The name of the world whose list's state will be changed
	 * @param whitelistOrBlacklist  Whether or not the list in question is a whitelist or a blacklist
	 * @param enabled Whether or not the specified list will be enabled or disabled
	 * @return True if the state of the list was just changed as a result */
	@Deprecated
	public static boolean setWorldListEnabled(String worldName, boolean whitelistOrBlacklist, boolean enabled) {
		World check = Main.server.getWorld(worldName);
		if(check != null && check.getName().equals(worldName)) {
			return setWorldListEnabled(check, whitelistOrBlacklist, enabled);
		}
		ConcurrentHashMap<String, Boolean> enableFlags = whitelistOrBlacklist ? whitelistEnableFlags : blacklistEnableFlags;
		if(enableFlags.get(worldName) == null) {
			return false;
		}
		boolean wasEnabled = enableFlags.get(worldName).booleanValue();
		enableFlags.put(worldName, Boolean.valueOf(enabled));
		updateLists(true);
		return wasEnabled != enableFlags.get(worldName).booleanValue();
	}
	
	/** Checks whether or not the given player is whitelisted for the specified world.
	 * 
	 * @param player The UUID of the player to check
	 * @param world The world to check
	 * @return True if the specified world doesn't have a whitelist enabled, or has one and the given player is listed on it */
	public static boolean isWhitelisted(UUID player, World world) {
		if(!isWorldListEnabled(world, true)) {
			return true;
		}
		List<UUID> list = getListFor(world, true);
		return list == null ? false : contains(list, player);
	}
	
	/** Checks whether or not the given player is whitelisted for the specified world.
	 * 
	 * @param player The player to check
	 * @param world The world to check
	 * @return True if the specified world doesn't have a whitelist enabled, or has one and the given player is listed on it */
	public static boolean isWhitelisted(OfflinePlayer player, World world) {
		return isWhitelisted(player.getUniqueId(), world);
	}
	
	/** Checks whether or not the given player is whitelisted for the specified world.
	 * 
	 * @deprecated Use {@link #isWhitelisted(UUID, World)} or {@link #isWhitelisted(OfflinePlayer, World)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param player The UUID of the player to check
	 * @param worldName The name of the world to check
	 * @return True if the specified world doesn't have a whitelist enabled, or has one and the given player is listed on it */
	@Deprecated
	public static boolean isWhitelisted(UUID player, String worldName) {
		if(!isWorldListEnabled(worldName, true)) {
			return true;
		}
		List<UUID> list = null;
		for(String check : whitelistedWorlds.keySet()) {
			if(check.equals(worldName)) {
				list = whitelistedWorlds.get(check);
			}
		}
		return list == null ? false : contains(list, player);
	}
	
	/** Checks whether or not the given player is whitelisted for the specified world.
	 * 
	 * @deprecated Use {@link #isWhitelisted(UUID, World)} or {@link #isWhitelisted(OfflinePlayer, World)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param player The player to check
	 * @param worldName The name of the world to check
	 * @return True if the specified world doesn't have a whitelist enabled, or has one and the given player is listed on it */
	@Deprecated
	public static boolean isWhitelisted(OfflinePlayer player, String worldName) {
		return isWhitelisted(player.getUniqueId(), worldName);
	}
	
	/** Checks whether or not the given player is blacklisted from the specified world.
	 * 
	 * @param player The UUID of the player to check
	 * @param world The world to check
	 * @return True if the specified world has a blacklist enabled and the given player is listed on it */
	public static boolean isBlacklisted(UUID player, World world) {
		return isWorldListEnabled(world, false) && contains(getListFor(world, false), player);
	}
	
	/** Checks whether or not the given player is blacklisted from the specified world.
	 * 
	 * @param player The player to check
	 * @param world The world to check
	 * @return True if the specified world has a blacklist enabled and the given player is listed on it */
	public static boolean isBlacklisted(OfflinePlayer player, World world) {
		return isBlacklisted(player.getUniqueId(), world);
	}
	
	/** Checks whether or not the given player is blacklisted from the specified world.
	 * 
	 * @deprecated Use {@link #isBlacklisted(UUID, World)} or {@link #isBlacklisted(OfflinePlayer, World)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param player The UUID of the player to check
	 * @param worldName The name of the world to check
	 * @return True if the specified world has a blacklist enabled and the given player is listed on it */
	@Deprecated
	public static boolean isBlacklisted(UUID player, String worldName) {
		if(!isWorldListEnabled(worldName, false)) {
			return false;
		}
		List<UUID> list = null;
		for(String check : blacklistedWorlds.keySet()) {
			if(check.equals(worldName)) {
				list = blacklistedWorlds.get(check);
			}
		}
		return list == null ? false : contains(list, player);
	}
	
	/** Checks whether or not the given player is blacklisted from the specified world.
	 * 
	 * @deprecated Use {@link #isBlacklisted(UUID, World)} or {@link #isBlacklisted(OfflinePlayer, World)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param player The player to check
	 * @param worldName The name of the world to check
	 * @return True if the specified world has a blacklist and the given player is listed on it */
	@Deprecated
	public static boolean isBlacklisted(OfflinePlayer player, String worldName) {
		return isBlacklisted(player.getUniqueId(), worldName);
	}
	
	/** Adds the specified player to the whitelist for the specified world.
	 * 
	 * @param player The UUID of the player to whitelist
	 * @param world The world to whitelist the player in
	 * @return True if the specified player was not whitelisted in the specified world's whitelist beforehand, and was just added; false otherwise */
	public static boolean whitelistPlayer(UUID player, World world) {
		List<UUID> list = getListFor(world, true);
		if(!contains(list, player)) {
			return list.add(player);
		}
		return false;
	}
	
	/** Adds the specified player to the whitelist for the specified world.
	 * 
	 * @param player The player to whitelist
	 * @param world The world to whitelist the player in
	 * @return True if the specified player was not whitelisted in the specified world's whitelist beforehand, and was just added; false otherwise */
	public static boolean whitelistPlayer(OfflinePlayer player, World world) {
		return whitelistPlayer(player.getUniqueId(), world);
	}
	
	/** Adds the specified player to the whitelist for the specified world.
	 * 
	 * @deprecated Use {@link #whitelistPlayer(UUID, World)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param player The UUID of the player to whitelist
	 * @param worldName The name of the world to whitelist the player in
	 * @return True if the specified player was not whitelisted in the specified world's whitelist beforehand, and was just added; false otherwise */
	@Deprecated
	public static boolean whitelistPlayer(UUID player, String worldName) {
		List<UUID> list = getListFor(worldName, true);
		if(list != null && !contains(list, player)) {
			return list.add(player);
		}
		return false;
	}
	
	/** Adds the specified player to the whitelist for the specified world.
	 * 
	 * @deprecated Use {@link #whitelistPlayer(UUID, World)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param player The player to whitelist
	 * @param worldName The name of the world to whitelist the player in
	 * @return True if the specified player was not whitelisted in the specified world's whitelist beforehand, and was just added; false otherwise */
	@Deprecated
	public static boolean whitelistPlayer(OfflinePlayer player, String worldName) {
		return whitelistPlayer(player.getUniqueId(), worldName);
	}
	
	/** Removes the specified player from the whitelist for the specified world.
	 * 
	 * @param player The UUID of the player to un-whitelist
	 * @param world The world to un-whitelist the player in
	 * @return True if the specified player was whitelisted in the specified world's whitelist beforehand, and was just removed; false otherwise */
	public static boolean unwhitelistPlayer(UUID player, World world) {
		List<UUID> list = getListFor(world, true);
		if(list == null) {
			list = new ArrayList<>();
			whitelistedWorlds.put(world.getName(), list);
		}
		return remove(list, player);
	}
	
	/** Removes the specified player from the whitelist for the specified world.
	 * 
	 * @param player The player to un-whitelist
	 * @param world The world to un-whitelist the player in
	 * @return True if the specified player was whitelisted in the specified world's whitelist beforehand, and was just removed; false otherwise */
	public static boolean unwhitelistPlayer(OfflinePlayer player, World world) {
		return unwhitelistPlayer(player.getUniqueId(), world);
	}
	
	/** Removes the specified player from the whitelist for the specified world.
	 * 
	 * @deprecated Use {@link #unwhitelistPlayer(UUID, World)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param player The UUID of the player to un-whitelist
	 * @param worldName The name of the world to un-whitelist the player in
	 * @return True if the specified player was whitelisted in the specified world's whitelist beforehand, and was just removed; false otherwise */
	@Deprecated
	public static boolean unwhitelistPlayer(UUID player, String worldName) {
		return remove(getListFor(worldName, true), player);
	}
	
	/** Removes the specified player from the whitelist for the specified world.
	 * 
	 * @deprecated Use {@link #unwhitelistPlayer(UUID, World)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param player The player to un-whitelist
	 * @param worldName The name of the world to un-whitelist the player in
	 * @return True if the specified player was whitelisted in the specified world's whitelist beforehand, and was just removed; false otherwise */
	@Deprecated
	public static boolean unwhitelistPlayer(OfflinePlayer player, String worldName) {
		return unwhitelistPlayer(player.getUniqueId(), worldName);
	}
	
	/** Adds the specified player to the blacklist for the specified world.
	 * 
	 * @param player The UUID of the player to blacklist
	 * @param world The world to blacklist the player in
	 * @return True if the specified player was not blacklisted in the specified world's blacklist beforehand, and was just added; false otherwise */
	public static boolean blacklistPlayer(UUID player, World world) {
		List<UUID> list = getListFor(world, false);
		if(list == null) {
			list = new ArrayList<>();
			blacklistedWorlds.put(world.getName(), list);
		}
		if(!contains(list, player)) {
			return list.add(player);
		}
		return false;
	}
	
	/** Adds the specified player to the blacklist for the specified world.
	 * 
	 * @param player The player to blacklist
	 * @param world The world to blacklist the player in
	 * @return True if the specified player was not blacklisted in the specified world's blacklist beforehand, and was just added; false otherwise */
	public static boolean blacklistPlayer(OfflinePlayer player, World world) {
		return blacklistPlayer(player.getUniqueId(), world);
	}
	
	/** Adds the specified player to the blacklist for the specified world.
	 * 
	 * @deprecated Use {@link #blacklistPlayer(UUID, World)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param player The UUID of the player to blacklist
	 * @param worldName The name of the world to blacklist the player in
	 * @return True if the specified player was not blacklisted in the specified world's blacklist beforehand, and was just added; false otherwise */
	@Deprecated
	public static boolean blacklistPlayer(UUID player, String worldName) {
		List<UUID> list = getListFor(worldName, false);
		if(list != null && !contains(list, player)) {
			return list.add(player);
		}
		return false;
	}
	
	/** Adds the specified player to the blacklist for the specified world.
	 * 
	 * @deprecated Use {@link #blacklistPlayer(UUID, World)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param player The player to blacklist
	 * @param worldName The name of the world to blacklist the player in
	 * @return True if the specified player was not blacklisted in the specified world's blacklist beforehand, and was just added; false otherwise */
	@Deprecated
	public static boolean blacklistPlayer(OfflinePlayer player, String worldName) {
		return blacklistPlayer(player.getUniqueId(), worldName);
	}
	
	/** Removes the specified player from the blacklist for the specified world.
	 * 
	 * @param player The UUID of the player to un-blacklist
	 * @param world The world to un-blacklist the player in
	 * @return True if the specified player was blacklisted in the specified world's blacklist beforehand, and was just removed; false otherwise */
	public static boolean unblacklistPlayer(UUID player, World world) {
		List<UUID> list = getListFor(world, false);
		if(list == null) {
			list = new ArrayList<>();
			blacklistedWorlds.put(world.getName(), list);
		}
		return remove(list, player);
	}
	
	/** Removes the specified player from the blacklist for the specified world.
	 * 
	 * @param player The player to un-blacklist
	 * @param world The world to un-blacklist the player in
	 * @return True if the specified player was blacklisted in the specified world's blacklist beforehand, and was just removed; false otherwise */
	public static boolean unblacklistPlayer(OfflinePlayer player, World world) {
		return unblacklistPlayer(player.getUniqueId(), world);
	}
	
	/** Removes the specified player from the blacklist for the specified world.
	 * 
	 * @deprecated Use {@link #unblacklistPlayer(UUID, World)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param player The UUID of the player from un-blacklist
	 * @param worldName The name of the world to un-blacklist the player in
	 * @return True if the specified player was blacklisted in the specified world's blacklist beforehand, and was just removed; false otherwise */
	@Deprecated
	public static boolean unblacklistPlayer(UUID player, String worldName) {
		return remove(getListFor(worldName, false), player);
	}
	
	/** Removes the specified player from the blacklist for the specified world.
	 * 
	 * @deprecated Use {@link #unblacklistPlayer(UUID, World)} where possible.<br>
	 * This method will only work properly if the given world name exactly matches an existing world's name.
	 * 
	 * @param player The player to un-blacklist
	 * @param worldName The name of the world to un-blacklist the player in
	 * @return True if the specified player was blacklisted in the specified world's blacklist beforehand, and was just removed; false otherwise */
	@Deprecated
	public static boolean unblacklistPlayer(OfflinePlayer player, String worldName) {
		return unblacklistPlayer(player.getUniqueId(), worldName);
	}
	
	private static volatile Main plugin;
	/** Represents a server implementation. */
	public static Server server;
	/** Represents a scheduler implementation. */
	public static BukkitScheduler scheduler;
	/** Handles all plugin management from the Server. */
	public static PluginManager pluginMgr;
	/** Represents the server console's command sender. */
	public static ConsoleCommandSender console;
	
	/** @return The plugin instance */
	public static Main getPlugin() {
		return plugin;
	}
	
	/**  */
	public Main() {
	}
	
	@Override
	public void onLoad() {
		plugin = this;
		server = this.getServer();
		scheduler = server.getScheduler();
		pluginMgr = server.getPluginManager();
		
		int[] numListsLoaded = loadListsFromFile(false);
		int numWhitelistsLoaded = numListsLoaded[0];
		int numBlacklistsLoaded = numListsLoaded[1];
		this.getLogger().info("Loaded ".concat(Integer.toString(numWhitelistsLoaded)).concat(" world whitelist").concat(numWhitelistsLoaded == 1 ? "" : "s").concat(" from file and ").concat(Integer.toString(numBlacklistsLoaded)).concat(" blacklist").concat(numBlacklistsLoaded == 1 ? "" : "s").concat(" from file successfully."));
	}
	
	@Override
	public void onEnable() {
		console = server.getConsoleSender();
		pluginMgr.registerEvents(this, this);
	}
	
	@Override
	public void onDisable() {
		HandlerList.unregisterAll((Plugin) this);
		int[] numListsSaved = saveListsToFile(true);
		int numWhitelistsSaved = numListsSaved[0];
		int numBlacklistsSaved = numListsSaved[1];
		this.getLogger().info("Saved ".concat(Integer.toString(numWhitelistsSaved)).concat(" world whitelist").concat(numWhitelistsSaved == 1 ? "" : "s").concat(" to file and ").concat(Integer.toString(numBlacklistsSaved)).concat(" blacklist").concat(numBlacklistsSaved == 1 ? "" : "s").concat(" to file successfully."));
	}
	
	/** The usage message that is displayed to a command sender when they type the command with no arguments (or incorrect starting arguments) */
	public static volatile String usage = "\u00A7eUsage: \"\u00A7f/%s {whitelist|blacklist} {add|remove/enable|disable} {playerName} {world name ...}\u00A7e\": Whitelist or Blacklist a player for a specified world.";
	/** The usage message that is displayed to a command sender when they type the command with incorrect add/remove or enable/disable arguments */
	public static volatile String usage1 = "\u00A7eUsage: \"\u00A7f/%s %s {add|remove/enable|disable} {playerName} {world name ...}\u00A7e\": Whitelist or Blacklist a player for a specified world.";
	/** The usage message that is displayed to a command sender when they type the command with invalid player or world arguments */
	public static volatile String usage2 = "\u00A7eUsage: \"\u00A7f/%s %s %s {playerName} {world name ...}\u00A7e\": Whitelist or Blacklist a player for a specified world.";
	/** The usage message that is displayed to a command sender when they type the command with an invalid world argument for the enable/disable subcommands */
	public static volatile String usage3 = "\u00A7eUsage: \"\u00A7f/%s %s %s {world name ...}\u00A7e\": Enable or disable a whitelist or blacklist for a specified world.";
	
	/** The response message that is displayed to a command sender when they type the command with an invalid world argument */
	public static volatile String listWrongWorldName = "\u00A7eUnable to find %slist for world \u00A7f%s\u00A7r\u00A7e. Check your spelling and try again.";
	
	/** The response message that is displayed to a command sender when they successfully add a player to a list */
	public static volatile String listAddSuccess = "\u00A7aSuccessfully added \"\u00A7f%s\u00A7r\u00A7a\" to the %slist for world \u00A7f%s\u00A7r\u00A7a.";
	/** The response message that is displayed to a command sender when they try to add a player to a list that already contains them */
	public static volatile String listAlreadyAdded = "\u00A7eThe player \"\u00A7f%s\u00A7r\u00A7e\" is already in the %slist for world \u00A7f%s\u00A7r\u00A7e.";
	/** The response message that is displayed to a command sender when the plugin is unable to add the player to the list for some unknown reason */
	public static volatile String listAddFailure = "\u00A74Error: \u00A7cUnable to add player \"\u00A7f%s\u00A7r\u00A7c\" to the %slist for world \u00A7f%s\u00A7r\u00A7c.";
	/** The response message that is displayed to a command sender when the plugin is unable to add the player to the list for some unknown reason, but is likely due to a mistyped world name */
	public static volatile String listAddFailureWorld = "\u00A74Error: \u00A7cUnable to add player \"\u00A7f%s\u00A7r\u00A7c\" to the %slist for world \u00A7f%s\u00A7r\u00A7c. Check the world's spelling and try again.";
	
	/** The response message that is displayed to a command sender when they successfully remove a player from a list */
	public static volatile String listRemoveSuccess = "\u00A7aSuccessfully removed \"\u00A7f%s\u00A7r\u00A7a\" from the %slist for world \u00A7f%s\u00A7r\u00A7a.";
	/** The response message that is displayed to a command sender when they try to remove a player from a list that doesn't contain them */
	public static volatile String listAlreadyRemoved = "\u00A7eThe player \"\u00A7f%s\u00A7r\u00A7e\" was never in the %slist for world \u00A7f%s\u00A7r\u00A7e.";
	/** The response message that is displayed to a command sender when the plugin is unable to remove the player from the list for some unknown reason */
	public static volatile String listRemoveFailure = "\u00A74Error: \u00A7cUnable to remove player \"\u00A7f%s\u00A7r\u00A7c\" from the %slist for world \u00A7f%s\u00A7r\u00A7c.";
	/** The response message that is displayed to a command sender when the plugin is unable to remove the player from the list for some unknown reason, but is likely due to a mistyped world name */
	public static volatile String listRemoveFailureWorld = "\u00A74Error: \u00A7cUnable to remove player \"\u00A7f%s\u00A7r\u00A7c\" from the %slist for world \u00A7f%s\u00A7r\u00A7c. Check the world's spelling and try again.";
	
	/** The response message that is displayed to a command sender when they successfully enable a world's whitelist or blacklist */
	public static volatile String listEnableSuccess = "\u00A7aSuccessfully enabled the %slist for world \u00A7f%s\u00A7r\u00A7a.";
	/** The response message that is displayed to a command sender when they try to enable a world's whitelist or blacklist that is already enabled */
	public static volatile String listAlreadyEnabled = "\u00A7eThe %slist for world \u00A7f%s\u00A7r\u00A7e is already enabled.";
	/** The response message that is displayed to a command sender when the plugin is unable to enable a world's whitelist or blacklist for some unknown reason */
	public static volatile String listEnableFailure = "\u00A74Error: \u00A7cUnable to enable the %slist for world \u00A7f%s\u00A7r\u00A7c.";
	/** The response message that is displayed to a command sender when the plugin is unable to enable a world's whitelist or blacklist for some unknown reason, but is likely due to a mistyped world name */
	public static volatile String listEnableFailureWorld = "\u00A74Error: \u00A7cUnable to enable the %slist for world \u00A7f%s\u00A7r\u00A7c. Check the world's spelling and try again.";
	
	/** The response message that is displayed to a command sender when they successfully disable a world's whitelist or blacklist */
	public static volatile String listDisableSuccess = "\u00A7aSuccessfully disabled the %slist for world \u00A7f%s\u00A7r\u00A7a.";
	/** The response message that is displayed to a command sender when they try to disable a world's whitelist or blacklist that is already disabled */
	public static volatile String listAlreadyDisabled = "\u00A7eThe %slist for world \u00A7f%s\u00A7r\u00A7e is already disabled.";
	/** The response message that is displayed to a command sender when the plugin is unable to disable a world's whitelist or blacklist for some unknown reason */
	public static volatile String listDisableFailure = "\u00A74Error: \u00A7cUnable to disable the %slist for world \u00A7f%s\u00A7r\u00A7c.";
	/** The response message that is displayed to a command sender when the plugin is unable to disable a world's whitelist or blacklist for some unknown reason, but is likely due to a mistyped world name */
	public static volatile String listDisableFailureWorld = "\u00A74Error: \u00A7cUnable to disable the %slist for world \u00A7f%s\u00A7r\u00A7c. Check the world's spelling and try again.";
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String command, String[] args) {
		if(command.equalsIgnoreCase("worldList") || command.equalsIgnoreCase("wl")) {
			if(!sender.hasPermission("worldwhitelist.use") && !sender.hasPermission("worldwhitelist.use.*") && !sender.hasPermission("worldwhitelist.*")) {
				sender.sendMessage("\u00A7cYou do not have permission to use this command.");
				return true;
			}
			if(args.length >= 1) {
				boolean whitelist = args[0].equalsIgnoreCase("whitelist") || args[0].equalsIgnoreCase("white") || args[0].equalsIgnoreCase("allow");
				boolean blacklist = args[0].equalsIgnoreCase("blacklist") || args[0].equalsIgnoreCase("black") || args[0].equalsIgnoreCase("deny");
				if((whitelist || blacklist)) {
					if(args.length >= 2) {
						if(args[1].equalsIgnoreCase("list") || args[1].equalsIgnoreCase("show") || args[1].equalsIgnoreCase("display")) {
							if(args.length >= 3) {
								int page = 0;
								String worldName;
								if(args.length > 3 && isInt(args[args.length - 1]) && _doesWorldHaveAListIgnoreCase(worldName = stringArrayToString(args, 2, args.length - 1, ' '), whitelist)) {
									page = Integer.parseInt(args[args.length - 1]) - 1;
								} else {
									worldName = stringArrayToString(args, 2, ' ');
								}
								World world = Main.server.getWorld(worldName);
								worldName = world != null ? world.getName() : worldName;
								List<UUID> list = world == null ? getListFor(worldName, whitelist) : getListFor(world, whitelist);
								if(list != null) {
									int maxPages = (list.size() / 10) + 1;
									if(page < 0 || page >= maxPages) {
										sender.sendMessage(String.format("\u00A7eThe [page] parameter must be between 1 and %d for this list.", Integer.valueOf(maxPages)));
										return true;
									}
									if(list.isEmpty()) {
										sender.sendMessage(String.format("\u00A7aThere are no players in the %slist for world \"\u00A7f%s\u00A7r\u00A7a\"", whitelist ? "white" : "black", worldName));
									}
									StringBuilder sb = new StringBuilder(String.format("\u00A7aDisplaying Page \u00A76%d\u00A7a of \u00A76%d\u00A7a in %slist for world \"\u00A7f%s\u00A7r\u00A7a\":\n", Integer.valueOf(page + 1), Integer.valueOf(maxPages), whitelist ? "white" : "black", worldName));
									for(int i = page * 10; i < Math.min(list.size(), (page + 1) * 10); i++) {
										UUID uuid = list.get(i);
										OfflinePlayer player = Main.server.getOfflinePlayer(uuid);
										sb.append(String.format("\u00A73[\u00A76%d\u00A73]: \u00A7f%s\n", Integer.valueOf(i + 1), player.getName()));
									}
									sender.sendMessage(sb.toString());
									return true;
								}
								sender.sendMessage(String.format("\u00A7eWorld \"\u00A7f%s\u00A7r\u00A7e\" does not have a %slist or does not exist. Check your spelling and try again.", worldName, whitelist ? "white" : "black"));
								return true;
							}
							sender.sendMessage(String.format("\u00A7eUsage: \"\u00A7f/%s %s %s {world name ...} [page]\u00A7e\": Display the specified list's contents for the specified world.", command, args[0], args[1]));
							return true;
						}
						World check = Main.server.getWorld(stringArrayToString(args, 1, ' '));
						if(check != null) {
							sender.sendMessage(String.format("\u00A7aThe %slist for world \"\u00A7f%s\u00A7r\u00A7a\" is currently: \u00A7%s", whitelist ? "white" : "black", check.getName(), isWorldListEnabled(check, whitelist) ? "2enabled" : "cdisabled"));
							return true;
						}
					}
				}
				if(whitelist) {
					if(!sender.hasPermission("worldwhitelist.use.whitelist") && !sender.hasPermission("worldwhitelist.use.*") && !sender.hasPermission("worldwhitelist.*")) {
						sender.sendMessage("\u00A7eYou do not have permission to modify whitelists.");
						sender.sendMessage("\u00A7eMissing permission node: \u00A7aworldwhitelist.use.whitelist");
						return true;
					}
					if(args.length >= 2) {
						if(args[1].equalsIgnoreCase("add")) {
							if(args.length >= 4) {
								@SuppressWarnings("deprecation")
								OfflinePlayer target = Main.server.getOfflinePlayer(args[2]);
								World world = Main.server.getWorld(args[3]);
								if(world == null) {
									if(whitelistPlayer(target, args[3])) {
										sender.sendMessage(String.format(listAddSuccess, target.getName(), "white", args[3]));//Success
									} else {
										if(isWhitelisted(target, args[3])) {
											sender.sendMessage(String.format(listAlreadyAdded, target.getName(), "white", args[3]));//Player already whitelisted
										} else {
											sender.sendMessage(String.format(listAddFailureWorld, target.getName(), "white", args[3]));//Failure: player not whitelisted and worldname likely incorrect
										}
									}
								} else {
									if(whitelistPlayer(target, world)) {
										sender.sendMessage(String.format(listAddSuccess, target.getName(), "white", world.getName()));//Success
									} else {
										if(isWhitelisted(target, world)) {
											sender.sendMessage(String.format(listAlreadyAdded, target.getName(), "white", world.getName()));//Player already whitelisted
										} else {
											sender.sendMessage(String.format(listAddFailure, target.getName(), "white", world.getName()));//Failure: undefined situation (player not whitelisted and cannot be whitelisted)
										}
									}
								}
								return true;
							}
							sender.sendMessage(String.format(usage2, command, args[0], args[1]));
							return true;
						} else if(args[1].equalsIgnoreCase("remove")) {
							if(args.length >= 4) {
								@SuppressWarnings("deprecation")
								OfflinePlayer target = Main.server.getOfflinePlayer(args[2]);
								String worldName = stringArrayToString(args, 3, ' ');
								World world = Main.server.getWorld(worldName);
								if(world == null) {
									if(unwhitelistPlayer(target, worldName)) {
										sender.sendMessage(String.format(listRemoveSuccess, target.getName(), "white", worldName));//Success
									} else {
										if(!isWhitelisted(target, worldName)) {
											sender.sendMessage(String.format(listAlreadyRemoved, target.getName(), "white", worldName));//Player was never whitelisted
										} else {
											sender.sendMessage(String.format(listRemoveFailureWorld, target.getName(), "white", worldName));//Failure: undefined situation (player is whitelisted and cannot be unwhitelisted)
										}
									}
								} else {
									if(unwhitelistPlayer(target, world)) {
										sender.sendMessage(String.format(listRemoveSuccess, target.getName(), "white", world.getName()));//Success
									} else {
										if(!isWhitelisted(target, world)) {
											sender.sendMessage(String.format(listAlreadyRemoved, target.getName(), "white", world.getName()));//Player was never whitelisted
										} else {
											sender.sendMessage(String.format(listRemoveFailure, target.getName(), "white", world.getName()));//Failure: undefined situation (player is whitelisted and cannot be unwhitelisted)
										}
									}
								}
								return true;
							}
							sender.sendMessage(String.format(usage2, command, args[0], args[1]));
							return true;
						} else if(args[1].equalsIgnoreCase("enable") || args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("activate")) {
							if(args.length >= 3) {
								String worldName = stringArrayToString(args, 2, ' ');
								World world = Main.server.getWorld(worldName);
								if(world == null) {
									if(setWorldListEnabled(worldName, true, true)) {
										sender.sendMessage(String.format(listEnableSuccess, "white", worldName));//Success
									} else if(isWorldListEnabled(worldName, true)) {
										sender.sendMessage(String.format(listAlreadyEnabled, "white", worldName));//The world list is already enabled
									} else {
										sender.sendMessage(String.format(listEnableFailureWorld, "white", worldName));//Failure: The world name was mistyped.
									}
								} else {
									if(setWorldListEnabled(world, true, true)) {
										sender.sendMessage(String.format(listEnableSuccess, "white", world.getName()));//Success
									} else if(isWorldListEnabled(world, true)) {
										sender.sendMessage(String.format(listAlreadyEnabled, "white", world.getName()));//The world list is already enabled
									} else {
										sender.sendMessage(String.format(listEnableFailure, "white", world.getName()));//Failure: undefined situation (world whitelist is enabled and cannot be disabled)
									}
								}
								return true;
							}
							sender.sendMessage(String.format(usage3, command, args[0], args[1]));
							return true;
						} else if(args[1].equalsIgnoreCase("disable") || args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("deactivate")) {
							if(args.length >= 3) {
								String worldName = stringArrayToString(args, 2, ' ');
								World world = Main.server.getWorld(worldName);
								if(world == null) {
									if(setWorldListEnabled(worldName, true, false)) {
										sender.sendMessage(String.format(listDisableSuccess, "white", worldName));//Success
									} else if(!isWorldListEnabled(worldName, true)) {
										sender.sendMessage(String.format(listAlreadyDisabled, "white", worldName));//The world list is already disabled
									} else {
										sender.sendMessage(String.format(listDisableFailureWorld, "white", worldName));//Failure: The world name was mistyped.
									}
								} else {
									if(setWorldListEnabled(world, true, false)) {
										sender.sendMessage(String.format(listDisableSuccess, "white", world.getName()));//Success
									} else if(!isWorldListEnabled(world, true)) {
										sender.sendMessage(String.format(listAlreadyDisabled, "white", world.getName()));//The world list is already disabled
									} else {
										sender.sendMessage(String.format(listDisableFailure, "white", world.getName()));//Failure: undefined situation (world whitelist is not enabled and cannot be enabled)
									}
								}
								return true;
							}
							sender.sendMessage(String.format(usage3, command, args[0], args[1]));
							return true;
						}
					}
					sender.sendMessage(String.format(usage1, command, args[0]));
					return true;
				} else if(blacklist) {
					if(!sender.hasPermission("worldwhitelist.use.blacklist") && !sender.hasPermission("worldwhitelist.use.*") && !sender.hasPermission("worldwhitelist.*")) {
						sender.sendMessage("\u00A7eYou do not have permission to modify blacklists.");
						sender.sendMessage("\u00A7eMissing permission node: \u00A7aworldwhitelist.use.blacklist");
						return true;
					}
					if(args.length >= 2) {
						if(args[1].equalsIgnoreCase("add")) {
							if(args.length >= 4) {
								@SuppressWarnings("deprecation")
								OfflinePlayer target = Main.server.getOfflinePlayer(args[2]);
								World world = Main.server.getWorld(args[3]);
								if(world == null) {
									if(blacklistPlayer(target, args[3])) {
										sender.sendMessage(String.format(listAddSuccess, target.getName(), "black", args[3]));//Success
									} else {
										if(isBlacklisted(target, args[3])) {
											sender.sendMessage(String.format(listAlreadyAdded, target.getName(), "black", args[3]));//Player already blacklisted
										} else {
											sender.sendMessage(String.format(listAddFailureWorld, target.getName(), "black", args[3]));//Failure: player not blacklisted and worldname likely incorrect
										}
									}
								} else {
									if(blacklistPlayer(target, world)) {
										sender.sendMessage(String.format(listAddSuccess, target.getName(), "black", world.getName()));//Success
									} else {
										if(isBlacklisted(target, world)) {
											sender.sendMessage(String.format(listAlreadyAdded, target.getName(), "black", world.getName()));//Player already blacklisted
										} else {
											sender.sendMessage(String.format(listAddFailure, target.getName(), "black", world.getName()));//Failure: undefined situation (player not blacklisted and cannot be blacklisted)
										}
									}
								}
								return true;
							}
							sender.sendMessage(String.format(usage2, command, args[0], args[1]));
							return true;
						} else if(args[1].equalsIgnoreCase("remove")) {
							if(args.length >= 4) {
								@SuppressWarnings("deprecation")
								OfflinePlayer target = Main.server.getOfflinePlayer(args[2]);
								String worldName = stringArrayToString(args, 3, ' ');
								World world = Main.server.getWorld(worldName);
								if(world == null) {
									if(unblacklistPlayer(target, worldName)) {
										sender.sendMessage(String.format(listRemoveSuccess, target.getName(), "black", worldName));//Success
									} else {
										if(!isBlacklisted(target, worldName)) {
											sender.sendMessage(String.format(listAlreadyRemoved, target.getName(), "black", worldName));//Player was never blacklisted
										} else {
											sender.sendMessage(String.format(listRemoveFailureWorld, target.getName(), "black", worldName));//Failure: undefined situation (player is blacklisted and cannot be unblacklisted)
										}
									}
								} else {
									if(unblacklistPlayer(target, world)) {
										sender.sendMessage(String.format(listRemoveSuccess, target.getName(), "black", world.getName()));//Success
									} else {
										if(!isBlacklisted(target, world)) {
											sender.sendMessage(String.format(listAlreadyRemoved, target.getName(), "black", world.getName()));//Player was never blacklisted
										} else {
											sender.sendMessage(String.format(listRemoveFailure, target.getName(), "black", world.getName()));//Failure: undefined situation (player is blacklisted and cannot be unblacklisted)
										}
									}
								}
								return true;
							}
							sender.sendMessage(String.format(usage2, command, args[0], args[1]));
							return true;
						} else if(args[1].equalsIgnoreCase("enable") || args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("activate")) {
							if(args.length >= 3) {
								String worldName = stringArrayToString(args, 2, ' ');
								World world = Main.server.getWorld(worldName);
								if(world == null) {
									if(setWorldListEnabled(worldName, false, true)) {
										sender.sendMessage(String.format(listEnableSuccess, "black", worldName));//Success
									} else if(isWorldListEnabled(worldName, false)) {
										sender.sendMessage(String.format(listAlreadyEnabled, "black", worldName));//The world list is already enabled
									} else {
										sender.sendMessage(String.format(listEnableFailureWorld, "black", worldName));//Failure: The world name was mistyped.
									}
								} else {
									if(setWorldListEnabled(world, false, true)) {
										sender.sendMessage(String.format(listEnableSuccess, "black", world.getName()));//Success
									} else if(isWorldListEnabled(world, false)) {
										sender.sendMessage(String.format(listAlreadyEnabled, "black", world.getName()));//The world list is already enabled
									} else {
										sender.sendMessage(String.format(listEnableFailure, "black", world.getName()));//Failure: undefined situation (world blacklist is not enabled and cannot be enabled)
									}
								}
								return true;
							}
							sender.sendMessage(String.format(usage3, command, args[0], args[1]));
							return true;
						} else if(args[1].equalsIgnoreCase("disable") || args[1].equalsIgnoreCase("off") || args[1].equalsIgnoreCase("deactivate")) {
							if(args.length >= 3) {
								String worldName = stringArrayToString(args, 2, ' ');
								World world = Main.server.getWorld(worldName);
								if(world == null) {
									if(setWorldListEnabled(worldName, false, false)) {
										sender.sendMessage(String.format(listDisableSuccess, "black", worldName));//Success
									} else if(!isWorldListEnabled(worldName, false)) {
										sender.sendMessage(String.format(listAlreadyDisabled, "black", worldName));//The world list is already disabled
									} else {
										sender.sendMessage(String.format(listDisableFailureWorld, "black", worldName));//Failure: The world name was mistyped.
									}
								} else {
									if(setWorldListEnabled(world, false, false)) {
										sender.sendMessage(String.format(listDisableSuccess, "black", world.getName()));//Success
									} else if(!isWorldListEnabled(world, false)) {
										sender.sendMessage(String.format(listAlreadyDisabled, "black", world.getName()));//The world list is already disabled
									} else {
										sender.sendMessage(String.format(listDisableFailure, "black", world.getName()));//Failure: undefined situation (world blacklist is enabled and cannot be disabled)
									}
								}
								return true;
							}
							sender.sendMessage(String.format(usage3, command, args[0], args[1]));
							return true;
						}
					}
					sender.sendMessage(String.format(usage1, command, args[0]));
					return true;
				}
			}
			sender.sendMessage(String.format(usage, command));
			return true;
		}
		return false;
	}
	
	/** Called whenever a player moves or looks around.
	 * 
	 * @param event Holds information for player movement events */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public static void onPlayerMoveEvent(PlayerMoveEvent event) {
		Player traveler = event.getPlayer();
		UUID uuid = traveler.getUniqueId();
		Location destination = event.getTo();
		World world = destination == null ? null : destination.getWorld();
		if(world != null) {
			if(traveler.hasPermission("worldwhitelist.bypass.".concat(world.getName().toLowerCase())) || traveler.hasPermission("worldwhitelist.bypass.*") || traveler.hasPermission("worldwhitelist.*")) {
				return;
			}
			List<UUID> whitelist = getListFor(world, true);
			List<UUID> blacklist = getListFor(world, false);
			boolean blackListed = isWorldListEnabled(world, false) && contains(blacklist, uuid);
			boolean notInWhiteList = isWorldListEnabled(world, true) && !contains(whitelist, uuid);
			if(blackListed || notInWhiteList) {
				event.setCancelled(true);
				event.setTo(event.getFrom());
				traveler.sendMessage("\u00A7".concat(blackListed ? "cYou are blacklisted from the world " : "eYou are not whitelisted in the world ").concat("\"\u00A7f").concat(world.getName()).concat("\u00A7r\u00A7").concat(blackListed ? "c\"!" : "e\"."));
				return;
			}
		}
	}
	
	/** Called whenever a player teleports.
	 * 
	 * @param event Holds information for player teleport events */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public static void onPlayerTeleportEvent(PlayerTeleportEvent event) {
		onPlayerMoveEvent(event);
	}
	
	/** Called when a world is loaded.
	 * 
	 * @param event Holds information for world load events */
	@EventHandler(priority = EventPriority.LOWEST)
	public static void onWorldLoadEvent(WorldLoadEvent event) {
		getListFor(event.getWorld(), true);
		getListFor(event.getWorld(), false);
		Main.scheduler.runTask(getPlugin(), () -> {
			updateLists(true);
		});
	}
	
	/** Called when a world is unloaded.
	 * 
	 * @param event Holds information for world unload events */
	@EventHandler(priority = EventPriority.LOWEST)
	public static void onWorldUnloadEvent(WorldUnloadEvent event) {
		getListFor(event.getWorld(), true);
		getListFor(event.getWorld(), false);
		updateLists(true);
	}
	
	/** Called when a command is run by a non-player.
	 * 
	 * @param event Holds information for server command events */
	@EventHandler(priority = EventPriority.LOWEST)
	public static void onServerCommandEvent(ServerCommandEvent event) {
		CommandSender sender = event.getSender();
		String[] split = event.getCommand().split(Pattern.quote(" "));
		String command = split[0];
		@SuppressWarnings("unused")
		String[] args = Arrays.copyOfRange(split, 1, split.length);
		
		if(command.equalsIgnoreCase("save-all") && sender.hasPermission("minecraft.command.save-all")) {
			saveListsToFile(false);
		}
	}
	
	/** Called whenever a player runs a command.
	 * 
	 * @param event Holds information for player commands before they are processed */
	@EventHandler(priority = EventPriority.LOWEST)
	public static void onPlayerCommandPreprocessEvent(PlayerCommandPreprocessEvent event) {
		Player sender = event.getPlayer();
		String command = event.getMessage();
		command = command.startsWith("/") ? command.substring(1) : command;
		String[] split = command.split(Pattern.quote(" "));
		command = split[0];
		@SuppressWarnings("unused")
		String[] args = Arrays.copyOfRange(split, 1, split.length);
		
		if(command.equalsIgnoreCase("save-all") && sender.hasPermission("minecraft.command.save-all")) {
			saveListsToFile(false);
		}
	}
	
}
