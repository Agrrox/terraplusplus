package io.github.terra121.control;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import io.github.opencubicchunks.cubicchunks.api.worldgen.ICubeGenerator;
import io.github.opencubicchunks.cubicchunks.core.server.CubeProviderServer;
import io.github.terra121.TerraConstants;
import io.github.terra121.generator.EarthGenerator;
import io.github.terra121.projection.OutOfProjectionBoundsException;
import io.github.terra121.util.ChatUtil;
import io.github.terra121.util.TranslateUtil;
import io.github.terra121.util.geo.CoordinateParseUtils;
import io.github.terra121.util.geo.LatLng;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.server.permission.PermissionAPI;

public class TerraTeleport extends Command {

    @Override
    public String getName() {
        return "tpll";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "terra121.commands.tpll.usage";
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if(!hasPermission(TerraConstants.controlCommandNode + "tpll", sender)) {
            sender.sendMessage(ChatUtil.getNoPermission());
            return;
        }

        if (this.isOp(sender) || !(sender instanceof EntityPlayer)) {
            if(!hasPermission(TerraConstants.controlCommandNode + "tpll", sender)) {
                sender.sendMessage(ChatUtil.getNoPermission());
                return;
            }

            if (this.isOp(sender) || !(sender instanceof EntityPlayer)) {
                World world = server.getEntityWorld();
                IChunkProvider cp = world.getChunkProvider();

                if (!(cp instanceof CubeProviderServer)) {
                    throw new CommandException("terra121.error.notcc");
                }

                ICubeGenerator gen = ((CubeProviderServer) cp).getCubeGenerator();

                if (!(gen instanceof EarthGenerator)) {
                    throw new CommandException("terra121.error.notterra");
                }

                EarthGenerator terrain = (EarthGenerator) gen;

                if (args.length == 0) {
                    usage(sender);
                    return;
                }

                List<EntityPlayerMP> receivers = new ArrayList<>();

                if(hasPermission(null, sender)) {
                    try {
                        receivers = getPlayers(server, sender, args[0]);
                    } catch (CommandException ignored) { }

                }

                if(!receivers.isEmpty() && args.length < 2) {
                    usage(sender);
                    return;
                }

                double altitude = Double.NaN;
                LatLng defaultCoords = CoordinateParseUtils.parseVerbatimCoordinates(getRawArguments(args).trim());

                if(defaultCoords == null) {
                    LatLng possiblePlayerCoords = CoordinateParseUtils.parseVerbatimCoordinates(getRawArguments(selectArray(args, 1)));
                    if(possiblePlayerCoords != null) {
                        defaultCoords = possiblePlayerCoords;
                    }
                }

                    LatLng possibleHeightCoords = CoordinateParseUtils.parseVerbatimCoordinates(getRawArguments(inverseSelectArray(args, args.length - 1)));
                    if(possibleHeightCoords != null) {
                        defaultCoords = possibleHeightCoords;
                        try {
                            altitude = Double.parseDouble(args[args.length - 1]);
                        } catch (Exception e) {
                            altitude = Double.NaN;
                        }
                    }


                    LatLng possibleHeightNameCoords = CoordinateParseUtils.parseVerbatimCoordinates(getRawArguments(inverseSelectArray(selectArray(args, 1), selectArray(args, 1).length - 1)));
                    if(possibleHeightNameCoords != null) {
                        defaultCoords = possibleHeightNameCoords;
                        try {
                            altitude = Double.parseDouble(selectArray(args, 1)[selectArray(args, 1).length - 1]);
                        } catch (Exception e) {
                            altitude = Double.NaN;
                        }
                    }


                if(defaultCoords == null) {
                    usage(sender);
                    return;
                }

                double[] proj;

                try {
                    proj = terrain.projection.fromGeo(defaultCoords.getLng(), defaultCoords.getLat());
                } catch (Exception e) {
                    sender.sendMessage(ChatUtil.combine(TextFormatting.RED, TranslateUtil.translate("terra121.error.numbers")));
                    return;
                }

                CompletableFuture<Double> altFuture;
                if (Double.isNaN(altitude)) {
                    try {
                        altFuture = terrain.heights.getAsync(defaultCoords.getLng(), defaultCoords.getLat())
                                .thenApply(a -> a + 1.0d);
                    } catch (OutOfProjectionBoundsException e) { //out of bounds, notify user
                        sender.sendMessage(ChatUtil.titleAndCombine(TextFormatting.RED, TranslateUtil.translate("terra121.error.numbers")));
                        return;
                    }
                } else {
                    altFuture = CompletableFuture.completedFuture(altitude);
                }

                if(receivers.isEmpty() && sender instanceof EntityPlayerMP) receivers.add((EntityPlayerMP) sender);
                List<EntityPlayerMP> finalReceivers = receivers;
                LatLng finalDefaultCoords = defaultCoords;
                altFuture.thenAccept(s -> FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(() -> {
                    for(EntityPlayerMP p : finalReceivers) {
                        if(p.getName().equalsIgnoreCase(sender.getName())) {
                            p.sendMessage(ChatUtil.titleAndCombine(TextFormatting.GRAY, "Teleporting to ", TextFormatting.BLUE,
                                    new DecimalFormat("##.#####").format(finalDefaultCoords.getLat()),
                                    TextFormatting.GRAY, ", ", TextFormatting.BLUE, new DecimalFormat("##.#####").format(finalDefaultCoords.getLng())));
                        } else if(!sender.getName().equalsIgnoreCase("@")) {
                            p.sendMessage(ChatUtil.titleAndCombine(TextFormatting.GRAY, "Summoned to ", TextFormatting.BLUE,
                                    new DecimalFormat("##.#####").format(finalDefaultCoords.getLat()),
                                    TextFormatting.GRAY, ", ", TextFormatting.BLUE, new DecimalFormat("##.#####").format(finalDefaultCoords.getLng()),
                                    TextFormatting.GRAY, " by ", TextFormatting.RED, sender.getDisplayName()));
                        } else {
                            p.sendMessage(ChatUtil.titleAndCombine(TextFormatting.GRAY, "Summoned to ", TextFormatting.BLUE,
                                    new DecimalFormat("##.#####").format(finalDefaultCoords.getLat()),
                                    TextFormatting.GRAY, ", ", TextFormatting.BLUE, new DecimalFormat("##.#####").format(finalDefaultCoords.getLng()),
                                    TextFormatting.GRAY));
                        }

                        FMLCommonHandler.instance().getMinecraftServerInstance().getCommandManager().executeCommand(
                                FMLCommonHandler.instance().getMinecraftServerInstance(), String.format(Locale.US, "tp %s %s %s %s", p.getName(), proj[0], s, proj[1]));

                    }
                }));
            }
        }
    }

    private boolean isOp(ICommandSender sender) {
        if (sender instanceof EntityPlayer) {
            return PermissionAPI.hasPermission((EntityPlayer) sender, "terra121.commands.tpll");
        }
        return sender.canUseCommand(2, "");
    }

    /**
     * Gets all objects in a string array above a given index
     * @param args Initial array
     * @param index Starting index
     * @return Selected array
     */
    private String[] selectArray(String[] args, int index) {
        List<String> array = new ArrayList<>();
        for(int i = index; i < args.length; i++)
            array.add(args[i]);

        return array.toArray(array.toArray(new String[array.size()]));
    }

    private String[] inverseSelectArray(String[] args, int index) {
        List<String> array = new ArrayList<>();
        for(int i = 0; i < index; i++)
            array.add(args[i]);

        return array.toArray(array.toArray(new String[array.size()]));

    }

    /**
     * Gets a space seperated string from an array
     * @param args A string array
     * @return The space seperated String
     */
    private String getRawArguments(String[] args) {
        if(args.length == 0) return "";
        if(args.length == 1) return args[0];

        StringBuilder arguments = new StringBuilder(args[0].replace((char) 176, (char) 32).trim());

        for(int x = 1; x < args.length; x++)
            arguments.append(" ").append(args[x].replace((char) 176, (char) 32).trim());

        return arguments.toString();
    }

    private void usage(ICommandSender sender) {
        if(hasPermission(null, sender)) {
            sender.sendMessage(ChatUtil.combine(TextFormatting.RED, TranslateUtil.translate("terra121.commands.tpll.admin.usage")));
        } else {
            sender.sendMessage(ChatUtil.combine(TextFormatting.RED, TranslateUtil.translate("terra121.commands.tpll.usage")));
        }
    }

    @Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos)
    {
        return args.length >= 1 ? getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames()) : Collections.emptyList();
    }
}
