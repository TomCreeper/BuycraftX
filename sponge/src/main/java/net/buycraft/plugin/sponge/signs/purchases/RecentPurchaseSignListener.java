package net.buycraft.plugin.sponge.signs.purchases;

import lombok.RequiredArgsConstructor;
import net.buycraft.plugin.sponge.BuycraftPlugin;
import net.buycraft.plugin.sponge.tasks.SignUpdater;
import net.buycraft.plugin.sponge.util.SerializedBlockLocation;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Optional;


@RequiredArgsConstructor
public class RecentPurchaseSignListener {

    private final BuycraftPlugin plugin;

    @Listener
    public void onSignChange(ChangeSignEvent event) {
        boolean ourSign;

        try {
            ourSign = event.getOriginalText().lines().get(0).toPlain().equalsIgnoreCase("[buycraft_rp]");
        } catch (IndexOutOfBoundsException e) {
            return;
        }

        if (!ourSign) {
            return;
        }

        Optional<Player> pl = event.getCause().first(Player.class);

        if (!pl.isPresent()) {
            // This change was not caused by a player.
            return;
        }

        Player player = pl.get();

        if (!player.hasPermission("buycraft.admin")) {
            event.getCause().first(Player.class).get().sendMessage(Text.builder("You can't create Buycraft signs.").color(TextColors.RED).build());
            return;
        }

        int pos;
        try {
            pos = Integer.parseInt(event.getOriginalText().lines().get(1).toPlain());
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            event.getCause().first(Player.class).get().sendMessage(Text.builder("The second line must be a number.").color(TextColors.RED).build());
            return;
        }
        if (pos <= 0) {
            player.sendMessage(Text.builder("You can't show negative or zero purchases!").color(TextColors.RED).build());
            return;
        }

        if (pos > 100) {
            player.sendMessage(Text.builder("You can't show more than 100 recent purchases!").color(TextColors.RED).build());
            return;
        }

        plugin.getRecentPurchaseSignStorage().addSign(new RecentPurchaseSignPosition(
                SerializedBlockLocation.fromSpongeLocation(event.getTargetTile().getLocation()), pos));
        player.sendMessage(Text.builder("Added new recent purchase sign!").color(TextColors.GREEN).build());

        for (int i = 0; i < 4; i++) {
            event.getText().lines().set(i, Text.EMPTY);
        }

        plugin.getPlatform().executeAsync(new SignUpdater(plugin));
    }

    @Listener
    public void onBlockBreak(ChangeBlockEvent.Break event) {
        // TODO: Check directions too
        for (Transaction<BlockSnapshot> transaction : event.getTransactions()) {
            Optional<Location<World>> locationOptional = transaction.getOriginal().getLocation();
            if (locationOptional.isPresent()) {
                if (locationOptional.get().getBlockType().equals(BlockTypes.WALL_SIGN) ||
                        locationOptional.get().getBlockType().equals(BlockTypes.STANDING_SIGN)) {
                    SerializedBlockLocation location = SerializedBlockLocation.fromSpongeLocation(locationOptional.get());
                    if (plugin.getRecentPurchaseSignStorage().containsLocation(location)) {
                        if (event.getCause().first(Player.class).isPresent()) {
                            Player player = event.getCause().first(Player.class).get();
                            if (!player.hasPermission("buycraft.admin")) {
                                player.sendMessage(Text.builder("You don't have permission to break this sign.").color(TextColors.RED).build());
                                event.setCancelled(true);
                                return;
                            }
                            if (plugin.getRecentPurchaseSignStorage().removeSign(location)) {
                                player.sendMessage(Text.builder("Removed recent purchase sign!").color(TextColors.RED).build());
                                plugin.getPlatform().executeAsync(new SignUpdater(plugin));
                                return;
                            }
                        } else {
                            event.setCancelled(true);
                        }
                    }
                }
            }
        }
    }
}