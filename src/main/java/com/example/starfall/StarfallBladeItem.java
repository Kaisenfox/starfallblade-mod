package com.example.starfall;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.entity.damage.DamageSource;

import java.util.List;

public class StarfallBladeItem extends SwordItem {

    // ---- Tweak these ----
    private static final double DASH_DISTANCE = 9.0;
    private static final float DASH_DAMAGE = 12.0f;
    private static final float SHOCKWAVE_DAMAGE = 8.0f;
    private static final int DASH_COOLDOWN = 50;

    private static final int WHIRL_TICKS = 24;
    private static final float WHIRL_DAMAGE = 9.0f;
    private static final int WHIRL_COOLDOWN = 90;

    private static final float STARFALL_BONUS_DAMAGE = 4.0f;

    public StarfallBladeItem(Settings settings) {
        super(ToolMaterials.NETHERITE, settings);
    }

    // ===== Left click: slash arc + falling stars =====
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (attacker.getWorld() instanceof ServerWorld world) {
            Effects.slashArc(world, attacker, world.getTime() % 2 == 0);
            starfall(world, target);
            world.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.4f);
            world.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.0f, 1.2f);
        }
        return super.postHit(stack, target, attacker);
    }

    private void starfall(ServerWorld world, LivingEntity target) {
        final Vec3d base = target.getPos();
        final int ticks = 6;
        for (int i = 0; i < ticks; i++) {
            final int tick = i;
            Scheduler.later(i, () -> {
                double height = 6.0 - tick;
                for (int k = 0; k < 4; k++) {
                    double ox = (world.random.nextDouble() - 0.5) * 1.6;
                    double oz = (world.random.nextDouble() - 0.5) * 1.6;
                    world.spawnParticles(ParticleTypes.END_ROD, base.x + ox, base.y + height, base.z + oz, 1, 0, -0.2, 0, 0.05);
                }
            });
        }
        Scheduler.later(ticks, () -> {
            world.spawnParticles(ParticleTypes.FIREWORK, base.x, base.y + 0.5, base.z, 30, 0.4, 0.5, 0.4, 0.15);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, base.x, base.y + 0.8, base.z, 25, 0.5, 0.6, 0.5, 0.3);
            world.playSound(null, base.x, base.y, base.z,
                    SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.0f, 1.6f);
            if (target.isAlive()) {
                target.timeUntilRegen = 0;
                DamageSource magic = world.getDamageSources().magic();
                target.damage(magic, STARFALL_BONUS_DAMAGE);
            }
        });
    }

    // ===== Right click: Dash Slash. Sneak + right click: Whirlwind =====
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (player.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.pass(stack);
        }

        boolean whirl = player.isSneaking();
        if (world instanceof ServerWorld serverWorld) {
            if (whirl) {
                whirlwind(serverWorld, player);
            } else {
                dash(serverWorld, player);
            }
            stack.damage(2, player, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }

        player.getItemCooldownManager().set(this, whirl ? WHIRL_COOLDOWN : DASH_COOLDOWN);
        player.swingHand(hand);
        return TypedActionResult.success(stack, world.isClient());
    }

    private void dash(ServerWorld world, PlayerEntity player) {
        Vec3d start = player.getPos().add(0, player.getHeight() * 0.5, 0);
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d move = new Vec3d(look.x, look.y * 0.5, look.z).normalize();
        Vec3d end = start.add(move.multiply(DASH_DISTANCE));

        BlockHitResult hit = world.raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) {
            end = hit.getPos().subtract(move.multiply(0.6));
        }

        // Damage everything the dash passes through.
        final Vec3d pathEnd = end;
        Box box = new Box(start, pathEnd).expand(1.5);
        List<Entity> targets = world.getOtherEntities(player, box,
                e -> e instanceof LivingEntity && e.isAlive());
        for (Entity e : targets) {
            if (e.getBoundingBox().expand(1.0).raycast(start, pathEnd).isPresent()) {
                e.timeUntilRegen = 0;
                e.damage(world.getDamageSources().playerAttack(player), DASH_DAMAGE);
            }
        }

        // Light trail along the path.
        double length = start.distanceTo(pathEnd);
        for (double d = 0; d < length; d += 0.5) {
            Vec3d p = start.add(move.multiply(d));
            world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 3, 0.2, 0.3, 0.2, 0.05);
            if (((int) (d * 2)) % 2 == 0) {
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y - 0.3, p.z, 1, 0.05, 0.05, 0.05, 0.02);
            }
        }
        world.spawnParticles(ParticleTypes.FLASH, start.x, start.y, start.z, 1, 0, 0, 0, 0);
        Effects.slashArc(world, player, false);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.6f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.2f);

        // Launch the player.
        player.setVelocity(move.x * 2.6, Math.max(move.y * 2.6, 0.15), move.z * 2.6);
        player.velocityModified = true;
        player.fallDistance = 0;

        // Landing shockwave a few ticks later, wherever the player ended up.
        Scheduler.later(5, () -> {
            if (!player.isAlive() || player.isRemoved()) return;
            Vec3d at = player.getPos();
            Effects.shockwave(world, at, 5.0, 6);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
            Effects.damageNearby(world, player, at, 5.0, SHOCKWAVE_DAMAGE, 0.9);
            world.playSound(null, at.x, at.y, at.z,
                    SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.0f, 1.6f);
        });
    }

    private void whirlwind(ServerWorld world, PlayerEntity player) {
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.8f);

        for (int t = 0; t < WHIRL_TICKS; t++) {
            final int tick = t;
            Scheduler.later(t, () -> {
                if (!player.isAlive() || player.isRemoved()) return;
                Vec3d c = player.getPos();
                double height = 0.1 + (tick / (double) WHIRL_TICKS) * 2.2;

                // Three spinning strands that climb as the move goes on.
                for (int s = 0; s < 3; s++) {
                    double ang = tick * 0.55 + s * (Math.PI * 2 / 3);
                    double x = c.x + Math.cos(ang) * 2.8;
                    double z = c.z + Math.sin(ang) * 2.8;
                    world.spawnParticles(ParticleTypes.END_ROD, x, c.y + height, z, 1, 0, 0, 0, 0);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, c.y + height, z, 2, 0.05, 0.05, 0.05, 0.01);
                    world.spawnParticles(ParticleTypes.CRIT, x, c.y + height, z, 1, 0.1, 0.1, 0.1, 0.1);
                }

                // Hit everything nearby every 4 ticks.
                if (tick % 4 == 3) {
                    Effects.damageNearby(world, player, c, 4.5, WHIRL_DAMAGE, 0.7);
                    world.playSound(null, c.x, c.y, c.z,
                            SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 0.8f + tick * 0.04f);
                }

                // Big finish.
                if (tick == WHIRL_TICKS - 1) {
                    Effects.shockwave(world, c, 5.5, 6);
                    world.spawnParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
                    world.playSound(null, c.x, c.y, c.z,
                            SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.0f, 1.4f);
                }
            });
        }
    }

    // ===== Holding it: a little star orbits you =====
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (selected && world instanceof ServerWorld sw && entity instanceof PlayerEntity p && world.getTime() % 4 == 0) {
            double a = world.getTime() * 0.25;
            sw.spawnParticles(ParticleTypes.END_ROD,
                    p.getX() + Math.cos(a) * 0.7,
                    p.getY() + 1.0 + Math.sin(a * 0.5) * 0.4,
                    p.getZ() + Math.sin(a) * 0.7,
                    1, 0, 0, 0, 0);
        }
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Hit: slash + falling stars").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("Right-click: Dash Slash").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Sneak + right-click: Whirlwind").formatted(Formatting.GRAY));
    }
}
