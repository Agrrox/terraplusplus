package io.github.terra121;

import io.github.terra121.dataset.OpenStreetMaps;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.Config.Comment;
import net.minecraftforge.common.config.Config.Name;
import net.minecraftforge.common.config.Config.RangeInt;
import net.minecraftforge.common.config.Config.RequiresMcRestart;
import net.minecraftforge.common.config.Config.RequiresWorldRestart;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Map;

@Config(modid = TerraMod.MODID)
public class TerraConfig {
    @Name("overpass_interpreter")
    @Comment({ "Overpass interpreter for road and water OpenStreetMap data",
            "Make sure you follow the instances guidelines",
            "URL must be able to take interpreter input by adding a \"?\"",
            "e.x. \"https://.../api/interpreter\"" })
    public static String serverOverpassDefault = "https://overpass.kumi.systems/api/interpreter"; //"https://overpass-api.de/api/interpreter"

    @Name("fallback_overpass_interpreter")
    @Comment("This is the same as overpass_interpreter, except it's only used as a fallback when overpass_interpreter is down")
    public static String serverOverpassFallback = "https://lz4.overpass-api.de/api/interpreter";

    @Name("overpass_fallback_check_delay")
    @Comment({ "The delay for which to switch to the fallback overpass endpoint",
            "After that time, the game will try switching back to the main one if possible,",
            "This is in minutes" })
    @RangeInt(min = 1)
    public static int overpassCheckDelay = 30;

    @Name("reduced_console_messages")
    @Comment({ "Removes all of Terra121's messages which contain various links in the server console",
            "This is just if it seems to spam the console, it is purely for appearance" })
    public static boolean reducedConsoleMessages = false;

    @Name("rest_tree_services")
    @Comment({ "An ArcGIS REST API instance with tree cover support",
            "Should allow all tree data sources used (just TreeCover2000 right now)",
            "End with a \"/\" e.x. \"https://.../arcgis/rest/services/\"",
            "If the tree cover is not functioning properly, or has errors, leave the value blank" })
    @RequiresMcRestart
    public static String serverTree = "https://gis-treecover.wri.org/arcgis/rest/services/";

    @Name("terrarium_instance")
    @Comment({ "A Mapzen Terrain Tile terrarium instance allowing x/y.png queries",
            "End with a \"/\" e.x. \"https://.../terrarium/\"" })
    @RequiresMcRestart
    public static String serverTerrain = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/";

    @Name("cache_size")
    @Comment({ "Amount of tiles to keep in memory at once",
            "This applies to both tree data and height data",
            "Every tile takes exactly 262,144 bytes of memory (plus some support structures)",
            "The memory requirement for the tiles will be about cacheSize/2 MB",
            "Warning: This number should be at least 4*playerCount to prevent massive slowdowns and internet usage, lower at your own risk" })
    @RangeInt(min = 1)
    @RequiresWorldRestart
    public static int cacheSize = 100;

    @Name("osm_cache_size")
    @Comment({ "Number of OSM regions to keep data about at a time",
            "(these tiles are roughly 1,850 meters/blocks in length but this varies based on position and projection) (they are exactly 1 arcminute across)",
            "Warning: The amount of memory taken by theses tiles fluctuates based on region and is not well studied, raise at your own risk",
            "Warning: This number should be at least 9*playerCount to prevent massive slowdowns and internet useage, lower at your own risk" })
    @RangeInt(min = 1)
    @RequiresMcRestart
    public static int osmCacheSize = 1000;

    @Name("three_water")
    @Comment({ "Require 3 water sources in order to form a new source instead of the vanilla 2",
            "This will make generated streams more stable but will disrupt vanilla water mechanics like 2x2 infinite water sources",
            "Highly expiremental, use at your own risk" })
    public static boolean threeWater = false;

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (TerraMod.MODID.equals(event.getModID())) {
            ConfigManager.sync(TerraMod.MODID, Config.Type.INSTANCE);
            OpenStreetMaps.cancelFallbackThread();
            OpenStreetMaps.setOverpassEndpoint(serverOverpassDefault);
        }
    }
}
