package shit.zen.modules.impl.render;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.player.Player;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.EntityHurtEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.hud.TargetHud;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.modules.impl.combat.KillAura;
import shit.zen.utils.misc.SoundUtil;

public class KillEffect extends Module {

    private static final double RISE_SPEED = 0.1;
    private static final double PERCENT_STEP = 0.02;

    private static class SquidEffect {
        final Squid squid;
        double percent;
        SquidEffect(Squid squid) { this.squid = squid; }
    }

    // 手动攻击的记录（兜底）
    private final Map<UUID, LivingEntity> damagedByMe = new HashMap<>();
    // 当前正在上升的鱿鱼
    private final List<SquidEffect> activeSquids = new ArrayList<>();
    // 已经触发过特效的实体UUID，防止重复
    private final Set<UUID> triggered = new HashSet<>();

    public KillEffect() {
        super("KillEffect", Category.RENDER);
    }

    @EventTarget
    public void onEntityHurt(EntityHurtEvent event) {
        if (mc.player == null) return;
        LivingEntity target = event.entity();
        if (target == mc.player) return;
        if (event.damageSource().getEntity() != mc.player) return;

        // 只是存起来，让 tick 统一判断
        damagedByMe.put(target.getUUID(), target);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.level == null || mc.player == null) {
            clearEffects();
            return;
        }

        // ---- 1. 处理手动攻击记录的实体 ----
        Iterator<Map.Entry<UUID, LivingEntity>> iter = damagedByMe.entrySet().iterator();
        while (iter.hasNext()) {
            LivingEntity entity = iter.next().getValue();
            if (checkAndTrigger(entity)) {
                iter.remove();
            } else if (entity.isRemoved()) {
                iter.remove(); // 防止内存泄漏
            }
        }

        // ---- 2. 处理 KillAura 当前目标（主要针对玩家） ----
        if (KillAura.aimingTarget instanceof LivingEntity target) {
            checkAndTrigger(target);
        }

        // ---- 3. 驱动鱿鱼上升 ----
        Iterator<SquidEffect> squidIter = activeSquids.iterator();
        while (squidIter.hasNext()) {
            SquidEffect effect = squidIter.next();
            Squid squid = effect.squid;

            if (squid.isRemoved()) {
                squidIter.remove();
                continue;
            }

            effect.percent += PERCENT_STEP;
            if (effect.percent >= 1.0) {
                // 爆炸特效
                double ey = squid.getY() + 0.5;
                mc.level.addParticle(ParticleTypes.EXPLOSION, squid.getX(), ey, squid.getZ(), 0, 0, 0);
                for (int i = 0; i < 40; i++) {
                    double sx = (Math.random() - 0.5) * 0.8;
                    double sy = (Math.random() - 0.5) * 0.8;
                    double sz = (Math.random() - 0.5) * 0.8;
                    mc.level.addParticle(ParticleTypes.FIREWORK, squid.getX(), ey, squid.getZ(), sx, sy, sz);
                    mc.level.addParticle(ParticleTypes.FLAME, squid.getX(), ey, squid.getZ(), sx, sy, sz);
                }
                squid.discard();
                squidIter.remove();
            } else {
                squid.setPos(squid.getX(), squid.getY() + RISE_SPEED, squid.getZ());
                squid.setDeltaMovement(0.0, RISE_SPEED, 0.0);
            }
        }
    }

    /**
     * 检查实体是否死亡，若是则触发鱿鱼特效。
     * @return true 表示已死亡（无论是否已触发过，都会防止重复触发）
     */
    private boolean checkAndTrigger(LivingEntity entity) {
        if (triggered.contains(entity.getUUID())) {
            return true; // 已经炸过了，可以从记录中移除
        }

        boolean dead = false;
        if (entity instanceof Player player) {
            // 玩家：用计分板真实血量（TargetHUD 的数据源）
            String name = player.getName().getString();
            if (TargetHud.playerHealthMap.containsKey(name)) {
                int score = TargetHud.playerHealthMap.get(name).get();
                if (score <= 0) {
                    dead = true;
                }
            } else {
                // 没有计分板数据时，回退到常规判断（可能不准确，但聊胜于无）
                dead = player.isDeadOrDying() || player.getHealth() <= 0.0f;
            }
        } else {
            // 动物/怪物：标准死亡判断
            dead = entity.isDeadOrDying() || entity.getHealth() <= 0.0f || entity.isRemoved();
        }

        if (dead) {
            spawnSquid(entity.getX(), entity.getY() + 1.0, entity.getZ());
            triggered.add(entity.getUUID());
        }
        return dead;
    }

    private void spawnSquid(double x, double y, double z) {
        SoundUtil.playSound("kill.wav", 1.0f);
        Squid squid = new Squid(EntityType.SQUID, mc.level);
        int fakeId = -114514 - (int)(Math.random() * 100000);
        squid.setId(fakeId);
        squid.setPos(x, y, z);
        squid.setNoGravity(true);
        squid.setInvulnerable(true);
        squid.setYRot(mc.player.getYRot());
        mc.level.putNonPlayerEntity(squid.getId(), squid);
        activeSquids.add(new SquidEffect(squid));
    }

    private void clearEffects() {
        if (mc.level != null) {
            for (SquidEffect effect : activeSquids) {
                effect.squid.discard();
            }
        }
        activeSquids.clear();
        damagedByMe.clear();
        triggered.clear();
    }

    @Override
    public void onDisable() {
        clearEffects();
    }
}