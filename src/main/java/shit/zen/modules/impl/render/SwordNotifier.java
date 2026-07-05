package shit.zen.modules.impl.render;

import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import shit.zen.event.EventTarget;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.event.impl.TickEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.render.FontStore;
import shit.zen.settings.impl.BooleanSetting;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwordNotifier extends Module {

    public static SwordNotifier INSTANCE;

    public final BooleanSetting chatAlert = new BooleanSetting("Chat Alert", true);
    public final BooleanSetting distanceText = new BooleanSetting("Distance Text", true);
    public final BooleanSetting redesp = new BooleanSetting("RED MARK", true);
    public final BooleanSetting sendToPublicChat = new BooleanSetting("Send To Public Chat", false);

    // 🌟 听觉雷达警告
    public final BooleanSetting soundAlert = new BooleanSetting("Sound Alert", true);
    // 🌟 原版光灵箭透视高亮
    public final BooleanSetting nativeGlow = new BooleanSetting("Native Glow", true);

    private final Set<String> markedPlayerNames = new HashSet<>();
    private final Set<String> alertedPlayerNames = new HashSet<>();

    private int textIndex = 0;
    private String pendingMessage = "";
    private long retryTime = 0;

    // 用于精准控制声音频率的 Tick 计数器 (Minecraft 固定 20 TPS)
    private int tickCounter = 0;

    // 缓存最近目标的数据，供 Render2D 渲染文本使用
    private double currentNearestDistance = -1;
    private String currentNearestName = "";

    public SwordNotifier() {
        super("SwordNotifier", Category.RENDER);
        INSTANCE = this;
    }

    public boolean isMarked(String playerName) {
        if (!this.isEnabled() || playerName == null) return false;
        return this.markedPlayerNames.contains(playerName.toLowerCase().trim());
    }

    @Override
    public void onEnable() {
        this.clearMarkers();
        this.pendingMessage = "";
        this.retryTime = 0;
        this.tickCounter = 0;
        this.currentNearestDistance = -1;
        this.currentNearestName = "";
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.clearMarkers();
        this.tickCounter = 0;
        super.onDisable();
    }

    private void clearMarkers() {
        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                if (player != null && player.getGameProfile().getName() != null) {
                    if (this.markedPlayerNames.contains(player.getGameProfile().getName().toLowerCase().trim())) {
                        player.setGlowingTag(false);
                    }
                }
            }
        }
        this.markedPlayerNames.clear();
        this.alertedPlayerNames.clear();
        this.currentNearestDistance = -1;
        this.currentNearestName = "";
    }

    private void sendNativeChatMessage(String text) {
        if (mc.player == null || text.isEmpty()) return;
        mc.player.connection.sendChat(text);
    }

    private void sendNextMessage(String name) {
        if (!this.sendToPublicChat.getValue()) return;

        String msg = "";
        switch (this.textIndex) {
            case 0 -> msg = "我看到了，" + name + "是杀手。";
            case 1 -> msg = name + "是杀手。";
            case 2 -> msg = "这个" + name + "是杀手!";
        }

        this.textIndex = (this.textIndex + 1) % 3;
        this.pendingMessage = msg;
        this.sendNativeChatMessage(msg);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;

        this.tickCounter++;

        double nearestDistance = -1;
        String nearestName = "";

        // 🌟 核心逻辑：距离计算 & 设置原版发光
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;

            String rawName = player.getGameProfile().getName();
            if (rawName == null) continue;

            if (this.markedPlayerNames.contains(rawName.toLowerCase().trim())) {
                // 原版发光机制（每 Tick 强制设置，对抗服务器重置）
                if (this.nativeGlow.getValue()) {
                    player.setGlowingTag(true);
                } else {
                    player.setGlowingTag(false);
                }

                // 寻找最近的被标记玩家
                double distance = mc.player.distanceTo(player);
                if (nearestDistance < 0 || distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestName = rawName;
                }
            }
        }

        // 更新缓存供渲染事件使用
        this.currentNearestDistance = nearestDistance;
        this.currentNearestName = nearestName;

        // 🌟 听觉雷达逻辑 (基于 Tick 控制频率)
        if (this.soundAlert.getValue() && nearestDistance >= 0) {
            if (nearestDistance <= 10.0) {
                // 10格以内：每秒 4 下 (20 / 5 = 4)
                if (this.tickCounter % 5 == 0) {
                    mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                }
            } else if (nearestDistance <= 20.0) {
                // 10到20格：每秒 2 下 (20 / 10 = 2)
                if (this.tickCounter % 10 == 0) {
                    mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                }
            } else if (nearestDistance <= 30.0) {
                // 20到30格：每秒 1 下 (20 / 20 = 1)
                if (this.tickCounter % 20 == 0) {
                    mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
                }
            }
            // 30格以外不响
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.level == null || event.getPacket() == null) return;

        // 1. 底层拦截拔剑动作
        if (event.getPacket() instanceof ClientboundSetEquipmentPacket equipmentPacket) {
            Entity entity = mc.level.getEntity(equipmentPacket.getEntity());
            if (entity instanceof Player targetPlayer && targetPlayer != mc.player) {

                for (Pair<EquipmentSlot, ItemStack> slotChange : equipmentPacket.getSlots()) {
                    EquipmentSlot slot = slotChange.getFirst();
                    ItemStack itemStack = slotChange.getSecond();

                    if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)
                            && itemStack.is(Items.DIAMOND_SWORD)) {

                        String rawName = targetPlayer.getGameProfile().getName();
                        if (rawName == null || rawName.isEmpty()) continue;

                        String lowerName = rawName.toLowerCase().trim();
                        this.markedPlayerNames.add(lowerName);

                        if (this.chatAlert.getValue() && !this.alertedPlayerNames.contains(lowerName)) {
                            mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                                    "§c[Warning] §f玩家 §e" + rawName + " §f曾手持钻石剑，已被全网永久锁定！"
                            ));
                            this.alertedPlayerNames.add(lowerName);

                            this.sendNextMessage(rawName);
                        }
                    }
                }
            }
        }

        // 2. 拦截服务器系统聊天返回
        String chatMessage = "";
        if (event.getPacket() instanceof ClientboundSystemChatPacket packet) {
            chatMessage = packet.content().getString();
        } else if (event.getPacket() instanceof ClientboundDisguisedChatPacket packet) {
            chatMessage = packet.message().getString();
        }

        if (!chatMessage.isEmpty()) {
            if (chatMessage.contains("请不要刷屏或者发送重复消息哦")) {
                Pattern pattern = Pattern.compile("\\((\\d+)\\s*秒\\)");
                Matcher matcher = pattern.matcher(chatMessage);
                if (matcher.find()) {
                    try {
                        int seconds = Integer.parseInt(matcher.group(1));
                        this.retryTime = System.currentTimeMillis() + (seconds + 1) * 1000L;
                    } catch (Exception e) {
                        this.retryTime = System.currentTimeMillis() + 2000L;
                    }
                }
            }

            if (chatMessage.contains("匹配") || chatMessage.contains("游戏开始") || chatMessage.contains("START")) {
                this.clearMarkers();
                this.pendingMessage = "";
                this.retryTime = 0;

                mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                        "§a[SwordNotifier] §f检测到新对局，已重置黑名单并强行终止挂起的重发任务。"
                ));
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;

        // 自动冷却重发原版包
        if (this.sendToPublicChat.getValue() && !this.pendingMessage.isEmpty() && this.retryTime > 0) {
            if (System.currentTimeMillis() >= this.retryTime) {
                this.sendNativeChatMessage(this.pendingMessage);
                this.retryTime = 0;
                this.pendingMessage = "";
            }
        }

        // 读取 onTick 中计算好的距离进行文本渲染
        if (this.currentNearestDistance < 0) return;

        if (this.distanceText.getValue()) {
            String text = String.format("⚠ 危险目标 [%s] 距你 %.1f 格 ⚠", this.currentNearestName, this.currentNearestDistance);

            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int screenHeight = mc.getWindow().getGuiScaledHeight();

            float x = screenWidth / 2.0f;
            float y = screenHeight * 0.65f;

            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

            if (event.poseStack() != null) {
                FontStore.PINGFANG_18.drawStringCenteredColor(
                        event.poseStack(),
                        text,
                        x,
                        y,
                        new java.awt.Color(255, 30, 30)
                );
            }
        }
    }
}