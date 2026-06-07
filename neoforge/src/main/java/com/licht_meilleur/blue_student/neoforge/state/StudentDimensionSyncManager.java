package com.licht_meilleur.blue_student.neoforge.state;

import com.licht_meilleur.blue_student.entity.AbstractStudentEntity;
import com.licht_meilleur.blue_student.state.StudentWorldState;
import com.licht_meilleur.blue_student.student.StudentAiMode;
import com.licht_meilleur.blue_student.student.StudentId;
import com.licht_meilleur.blue_student.student.StudentPresenceState;
import com.licht_meilleur.blue_student.util.DimensionTransferHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class StudentDimensionSyncManager {

    private StudentDimensionSyncManager() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }

    private static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();

        // 1秒に1回で十分
        if (overworld.getGameTime() % 20 != 0) return;

        StudentWorldState state = StudentWorldState.get(server);

        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            for (StudentId sid : StudentId.values()) {
                StudentWorldState.StudentData data = state.getData(sid);
                if (data == null) continue;
                if (data.owner == null || !data.owner.equals(owner.getUUID())) continue;

                if (state.getPresence(sid) == StudentPresenceState.RESPAWNING
                        || state.getPresence(sid) == StudentPresenceState.SLEEPING) {
                    continue;
                }

                syncOne(server, owner, sid, data, state);
            }
        }
    }

    private static void syncOne(
            MinecraftServer server,
            ServerPlayer owner,
            StudentId sid,
            StudentWorldState.StudentData data,
            StudentWorldState state
    ) {
        ServerLevel ownerLevel = owner.level();

        String ownerDim = dimensionId(ownerLevel);
        if (ownerDim.equals(data.dimension)) {
            return;
        }

        BlockPos spawn = DimensionTransferHelper.findSafeNear(ownerLevel, owner.blockPosition());

        // 1. 記録上の旧ディメンションにロード済み個体がいるなら、同個体転送を優先
        AbstractStudentEntity found = findLoadedStudent(server, data);
        if (found != null && found.isAlive()) {
            if (found.getAiMode() != StudentAiMode.FOLLOW) {
                return;
            }

            if (found.isLifeLockedForGoal()) {
                return;
            }

            AbstractStudentEntity moved =
                    DimensionTransferHelper.transferStudent(found, ownerLevel, spawn, owner.getYRot());

            if (moved != null) {
                moved.setDeltaMovement(Vec3.ZERO);
                moved.getNavigation().stop();
                moved.fallDistance = 0;
                moved.noFallTicks = Math.max(moved.noFallTicks, 20);

                cleanupDuplicates(server, sid, owner.getUUID(), moved.getUUID());
                state.setStudent(sid, moved.getUUID(), owner.getUUID(), ownerLevel, spawn);
            }

            return;
        }

        // 2. packed があるなら復元。ただし復元後に旧個体が残っていないか掃除
        if (state.isPacked(sid) || state.getPacked(sid) != null) {
            AbstractStudentEntity spawned =
                    DimensionTransferHelper.spawnPackedStudent(ownerLevel, sid, spawn, owner.getYRot());

            if (spawned != null) {
                spawned.setDeltaMovement(Vec3.ZERO);
                spawned.getNavigation().stop();
                spawned.fallDistance = 0;
                spawned.noFallTicks = Math.max(spawned.noFallTicks, 20);

                cleanupDuplicates(server, sid, owner.getUUID(), spawned.getUUID());
                state.setStudent(sid, spawned.getUUID(), owner.getUUID(), ownerLevel, spawn);
                return;
            }
        }

        // 3. 見つからない場合は MISSING
        state.markMissing(sid, ownerLevel);
    }

    private static AbstractStudentEntity findLoadedStudent(MinecraftServer server, StudentWorldState.StudentData data) {
        if (data == null || data.uuid == null || data.dimension == null) return null;

        ResourceKey<Level> key = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.parse(data.dimension)
        );

        ServerLevel level = server.getLevel(key);
        if (level == null) return null;

        Entity e = level.getEntity(data.uuid);
        return e instanceof AbstractStudentEntity ase ? ase : null;
    }

    private static String dimensionId(ServerLevel level) {
        return level.dimension().toString();
    }

    private static void cleanupDuplicates(
            MinecraftServer server,
            StudentId sid,
            java.util.UUID ownerUuid,
            java.util.UUID keepUuid
    ) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof AbstractStudentEntity student)) {
                    continue;
                }

                if (student.getUUID().equals(keepUuid)) {
                    continue;
                }

                if (student.getStudentId() != sid) {
                    continue;
                }

                if (student.getOwnerUuid() == null || !student.getOwnerUuid().equals(ownerUuid)) {
                    continue;
                }

                student.discard();
            }
        }
    }
}