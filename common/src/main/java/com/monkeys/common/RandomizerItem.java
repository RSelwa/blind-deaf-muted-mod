package com.monkeys.common;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * The "Randomizer": a throwable bottle. Right-click to lob it like an experience
 * bottle; when it shatters ({@link RandomizerBottleEntity}) it re-rolls everyone's
 * role. The throw/spawn here is generic; the re-roll happens server-side on impact.
 */
public class RandomizerItem extends Item {

    public RandomizerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.ENTITY_EXPERIENCE_BOTTLE_THROW, SoundCategory.PLAYERS,
                0.5F, 0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!world.isClient) {
            RandomizerBottleEntity bottle = new RandomizerBottleEntity(world, user, stack);
            bottle.setVelocity(user, user.getPitch(), user.getYaw(), -20.0F, 0.7F, 1.0F);
            world.spawnEntity(bottle);
        }

        user.incrementStat(Stats.USED.getOrCreateStat(this));
        stack.decrementUnlessCreative(1, user);
        return ActionResult.SUCCESS;
    }
}
