package com.wellartdev.horsescope;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.EntityHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Horse stat inspector for breeders who hate guessing.
 *
 * HorseScope shows the stats of a horse right on the HUD: speed (m/s),
 * jump height (blocks), and current/max health. The target is either the
 * horse the player is currently riding, or — when not mounted — the
 * horse under the crosshair. Stats refresh every 10 ticks, and the
 * panel clears automatically when no horse is targeted.
 *
 * Client-side only. No mixins, no networking, nothing persisted.
 */
public class HorseScope implements ClientModInitializer {
    public static final String MOD_ID = "horsescope";

    /** Vanilla's internal movement-speed unit scaled to real meters per second. */
    private static final double SPEED_UNIT_TO_MPS = 42.16;

    /** Coefficients of the community-fitted jump-height polynomial for jump strength. */
    private static final double JUMP_C3 = -0.1817584952;
    private static final double JUMP_C2 = 3.6897138428055278;
    private static final double JUMP_C1 = 2.1285991344756826;
    private static final double JUMP_C0 = -0.3439303677385397;

    // Read by the HUD element every frame; rebuilt on tick below.
    private final List<String> statLines = new ArrayList<>();
    private int tick = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "horse_scope"),
                (extractor, deltaTracker) -> {
                    if (statLines.isEmpty()) {
                        return;
                    }
                    Minecraft mc = Minecraft.getInstance();
                    int y = mc.getWindow().getGuiScaledHeight() - 70;
                    for (String line : statLines) {
                        int x = (mc.getWindow().getGuiScaledWidth() - mc.font.width(line)) / 2;
                        extractor.text(mc.font, line, x, y, 0xFFFFFF);
                        y += 10;
                    }
                });
    }

    private void onTick(Minecraft mc) {
        tick++;
        if (mc.player == null || tick % 10 != 0) {
            return;
        }

        statLines.clear();
        AbstractHorse horse = findTargetHorse(mc);
        if (horse == null) {
            return;
        }

        double speedMps = horse.getAttributeValue(Attributes.MOVEMENT_SPEED) * SPEED_UNIT_TO_MPS;
        double jumpStrength = horse.getAttributeValue(Attributes.JUMP_STRENGTH);
        double jumpHeight = JUMP_C3 * Math.pow(jumpStrength, 3)
                + JUMP_C2 * Math.pow(jumpStrength, 2)
                + JUMP_C1 * jumpStrength
                + JUMP_C0;
        int health = (int) horse.getHealth();
        int maxHealth = (int) horse.getMaxHealth();

        statLines.add(String.format("Speed: %.1f m/s", speedMps));
        statLines.add(String.format("Jump: %.1f blocks", jumpHeight));
        statLines.add("Health: " + health + "/" + maxHealth);
    }

    /**
     * Picks the horse to inspect: the one the player is riding, or else
     * the one under the crosshair. Returns null when neither applies.
     */
    private AbstractHorse findTargetHorse(Minecraft mc) {
        Entity vehicle = mc.player.getVehicle();
        if (vehicle instanceof AbstractHorse riddenHorse) {
            return riddenHorse;
        }
        if (mc.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof AbstractHorse lookedHorse) {
            return lookedHorse;
        }
        return null;
    }
}
