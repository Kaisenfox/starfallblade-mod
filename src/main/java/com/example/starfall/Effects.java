package com.example.starfall;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Reusable particle animations and area damage. */
public final class Effects {
    private Effects() {}

    /** A diagonal crescent slash that draws itself across 4 ticks. */
    public static void slashArc(ServerWorld world, LivingEntity user, boolean flip) {
        Vec3d look = user.getRotationVec(1.0f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        final Vec3d forward = flat;
        final Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        final Vec3d center = user.getPos().add(0, user.getHeight() * 0.6, 0);

        final int segments = 4;
        final int perSegment = 8;
        for (int s = 0; s < segments; s++) {
            final int seg = s;
            Scheduler.later(s, () -> {
                for (int i = 0; i < perSegment; i++) {
                    double t = ((seg * perSegment + i) / (double) (segments * perSegment - 1)) * 2.0 - 1.0;
                    if (flip) t = -t;
                    double a = Math.toRadians(t * 80.0);
                    Vec3d p = center
                            .add(forward.multiply(Math.cos(a) * 2.4))
                            .add(right.multiply(Math.sin(a) * 2.4))
                            .add(0, -t * 0.9 + 0.3, 0);
                    world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                    world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
                    if (i % 4 == 0) {
                        world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.2);
                    }
                }
            });
        }
    }

    /** An expanding ring on the ground. */
    public static void shockwave(ServerWorld world, Vec3d origin, double maxRadius, int ticks) {
        for (int i = 1; i <= ticks; i++) {
            final double r = maxRadius * i / ticks;
            Scheduler.later(i - 1, () -> {
                int points = (int) (r * 10) + 8;
                for (int k = 0; k < points; k++) {
                    double ang = (Math.PI * 2 * k) / points;
                    double x = origin.x + Math.cos(ang) * r;
                    double z = origin.z + Math.sin(ang) * r;
                    world.spawnParticles(ParticleTypes.END_ROD, x, origin.y + 0.2, z, 1, 0, 0.02, 0, 0.02);
                    if (k % 3 == 0) {
                        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, origin.y + 0.1, z, 1, 0, 0.05, 0, 0.02);
                    }
                }
            });
        }
    }

    /** Damage + knock back every living thing within a horizontal radius. */
    public static void damageNearby(ServerWorld world, PlayerEntity owner, Vec3d c, double radius, float damage, double knock) {
        Box box = new Box(c.x - radius, c.y - 1, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
        for (Entity e : world.getOtherEntities(owner, box, ent -> ent instanceof LivingEntity && ent.isAlive())) {
            double dx = e.getX() - c.x;
            double dz = e.getZ() - c.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius) continue;
            e.timeUntilRegen = 0;
            e.damage(world.getDamageSources().playerAttack(owner), damage);
            if (dist > 0.01) {
                e.addVelocity(dx / dist * knock, 0.35, dz / dist * knock);
                e.velocityModified = true;
            }
        }
    }
}
