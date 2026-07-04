package shit.zen.modules.impl.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Squid;

import shit.zen.event.EventTarget;
import shit.zen.event.impl.EntityHurtEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.utils.misc.SoundUtil;

public class KillEffect extends Module {

    private static final double RISE_SPEED = 0.21145;
    private static final double PERCENT_STEP_MAX = 0.048;

    private static final class SquidEffect {
        final Squid squid;
        double percent = 0.0;

        SquidEffect(Squid squid) {
            this.squid = squid;
        }
    }

    // 记录被我打过的实体：UUID -> 实体引用
    private final Map<UUID, LivingEntity> damagedByMe = new HashMap<>();

    // 当前正在上升的假墨鱼列表
    private final List<SquidEffect> activeSquids = new ArrayList<>();

    public KillEffect() {
        super("KillEffect", Category.RENDER);
    }

    /**
     * 只有自己造成的伤害，才将目标记入小本本
     */
    @EventTarget
    public void onEntityHurt(EntityHurtEvent event) {
        if (mc.player == null) {
            return;
        }

        LivingEntity target = event.entity();

        // 判断伤害来源是自己，且打的不是自己
        if (event.damageSource().getEntity() == mc.player && target != mc.player) {
            this.damagedByMe.put(target.getUUID(), target);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null || mc.player == null) {
            clearEffects();
            return;
        }

        // 1. 检查“被我打过”的实体有没有死掉
        Iterator<Map.Entry<UUID, LivingEntity>> trackedIterator = this.damagedByMe.entrySet().iterator();
        while (trackedIterator.hasNext()) {
            LivingEntity entity = trackedIterator.next().getValue();

            // 如果实体已经死亡，或者血量归零
            if (entity.isDeadOrDying() || entity.getHealth() <= 0.0f) {
                // 【高度修复】：在 Y 轴基础上加 1.0，防止卡在地里
                this.spawnSquid(entity.getX(), entity.getY() + 1.0, entity.getZ());
                trackedIterator.remove();
                continue;
            }

            // 如果实体因为距离过远被卸载(isRemoved)但没死，单纯移出记录，防止内存泄漏
            if (entity.isRemoved()) {
                trackedIterator.remove();
            }
        }

        // 2. 推进所有正在上升的假墨鱼
        Iterator<SquidEffect> squidIterator = this.activeSquids.iterator();
        while (squidIterator.hasNext()) {
            SquidEffect effect = squidIterator.next();
            Squid squid = effect.squid;

            if (squid.isRemoved()) {
                squidIterator.remove();
                continue;
            }

            if (effect.percent < 1.0) {
                effect.percent += Math.random() * PERCENT_STEP_MAX;
            }

            if (effect.percent >= 1.0) {
                // 上升结束：原地炸开一圈火焰粒子
                for (int i = 0; i < 8; i++) {
                    mc.level.addParticle(
                            ParticleTypes.FLAME,
                            squid.getX(), squid.getY() + 0.5, squid.getZ(),
                            (Math.random() - 0.5) * 0.2,
                            Math.random() * 0.2,
                            (Math.random() - 0.5) * 0.2
                    );
                }

                // 彻底从客户端清理假实体
                squid.discard();
                mc.level.removeEntity(squid.getId(), Entity.RemovalReason.DISCARDED);

                squidIterator.remove();
                continue;
            }

            // 强制修改 Y 轴坐标，无视物理引擎
            squid.setPos(squid.getX(), squid.getY() + RISE_SPEED, squid.getZ());
            squid.setDeltaMovement(0.0, RISE_SPEED, 0.0);
        }
    }

    private void spawnSquid(double x, double y, double z) {
        SoundUtil.playSound("kill.wav", 1.0f);

        Squid squid = new Squid(EntityType.SQUID, mc.level);

        // 强制分配假实体 ID，防止冲突
        int fakeEntityId = -114514 - (int)(Math.random() * 100000);
        squid.setId(fakeEntityId);

        // 这里的 Y 已经是加过 1.0 的了
        squid.setPos(x, y, z);
        squid.setNoGravity(true);
        squid.setInvulnerable(true);
        squid.setYRot(mc.player.getYRot());

        // 1.20 官方映射下，往客户端塞假实体用 putNonPlayerEntity
        mc.level.putNonPlayerEntity(squid.getId(), squid);

        this.activeSquids.add(new SquidEffect(squid));
    }

    private void clearEffects() {
        if (mc.level != null) {
            for (SquidEffect effect : this.activeSquids) {
                effect.squid.discard();
                mc.level.removeEntity(effect.squid.getId(), Entity.RemovalReason.DISCARDED);
            }
        }
        this.activeSquids.clear();
        this.damagedByMe.clear();
    }

    @Override
    public void onDisable() {
        clearEffects();
    }
}