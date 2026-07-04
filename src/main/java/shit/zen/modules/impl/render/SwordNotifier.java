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
import shit.zen.event.EventTarget;
import shit.zen.event.impl.PacketEvent;
import shit.zen.event.impl.Render2DEvent;
import shit.zen.modules.Category;
import shit.zen.modules.Module;
import shit.zen.render.FontStore;
import shit.zen.settings.impl.BooleanSetting;

import java.util.HashSet;
import java.util.Set;

public class SwordNotifier extends Module {

    public static SwordNotifier INSTANCE;

    public final BooleanSetting chatAlert = new BooleanSetting("Chat Alert", true);
    public final BooleanSetting redEsp = new BooleanSetting("Red ESP", true);
    public final BooleanSetting distanceText = new BooleanSetting("Distance Text", true);

    // 🛠️ 核心修复：彻底放弃不稳定的 UUID，改用全小写的 String 存储玩家名，彻底免疫服务器实体刷新混淆
    private final Set<String> markedPlayerNames = new HashSet<>();
    private final Set<String> alertedPlayerNames = new HashSet<>();

    public SwordNotifier() {
        super("SwordNotifier", Category.RENDER);
        INSTANCE = this;
    }

    /**
     * 🟢 修复后的 ESP 联动接口：传入玩家的名字字符串进行强匹配！
     */
    public boolean isMarked(String playerName) {
        if (!this.isEnabled() || playerName == null) return false;
        return this.markedPlayerNames.contains(playerName.toLowerCase().trim());
    }

    @Override
    public void onEnable() {
        this.clearMarkers();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.clearMarkers();
        super.onDisable();
    }

    private void clearMarkers() {
        this.markedPlayerNames.clear();
        this.alertedPlayerNames.clear();
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

                        // 🛠️ 获取唯一的、不会随着实体刷新而改变的玩家名字
                        String rawName = targetPlayer.getGameProfile().getName();
                        if (rawName == null || rawName.isEmpty()) continue;

                        String lowerName = rawName.toLowerCase().trim();
                        this.markedPlayerNames.add(lowerName);

                        // 聊天框提示去重
                        if (this.chatAlert.getValue() && !this.alertedPlayerNames.contains(lowerName)) {
                            mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                                    "§c[Warning] §f玩家 §e" + rawName + " §f曾手持钻石剑，已被全网永久锁定！"
                            ));
                            this.alertedPlayerNames.add(lowerName);
                        }
                    }
                }
            }
        }

        // 2. 新对局自动清空过滤器
        String chatMessage = "";
        if (event.getPacket() instanceof ClientboundSystemChatPacket packet) {
            chatMessage = packet.content().getString();
        } else if (event.getPacket() instanceof ClientboundDisguisedChatPacket packet) {
            chatMessage = packet.message().getString();
        }

        if (!chatMessage.isEmpty()) {
            if (chatMessage.contains("匹配") || chatMessage.contains("游戏开始") || chatMessage.contains("START")) {
                this.clearMarkers();
                mc.gui.getChat().addMessage(net.minecraft.network.chat.Component.literal(
                        "§a[SwordNotifier] §f检测到新对局，已彻底重置名字黑名单。"
                ));
            }
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.distanceText.getValue() || mc.player == null || mc.level == null) return;

        double nearestDistance = -1;
        String nearestName = "";

        // 1. 寻找最近的被标记玩家（通过全小写名字匹配）
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;

            String rawName = player.getGameProfile().getName();
            if (rawName == null) continue;

            if (!this.markedPlayerNames.contains(rawName.toLowerCase().trim())) continue;

            double distance = mc.player.distanceTo(player);
            if (nearestDistance < 0 || distance < nearestDistance) {
                nearestDistance = distance;
                nearestName = rawName;
            }
        }

        // 如果没有拉出钻石剑的危险目标，直接掐断不绘制
        if (nearestDistance < 0) return;

        // 2. 组装屏幕中间下方的危险提示文本
        String text = String.format("⚠ 危险目标 [%s] 距你 %.1f 格 ⚠", nearestName, nearestDistance);

        // 3. 动态获取缩放后的屏幕宽高
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // 4. 计算黄金视觉安全坐标区
        float x = screenWidth / 2.0f;
        float y = screenHeight * 0.65f; // 🛠️ 悬浮于准星正下方，完美避开原版物品快捷栏遮挡

        // 5. 强制刷新 OpenGL 混合状态，锁定渲染上下文，防止文字变黑或变透明
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();

        // 6. 调用你客户端原生的 FontStore 绘制方法（移除不稳定的原版反射兜底，杜绝编译报错）
        if (event.poseStack() != null) {
            FontStore.PINGFANG_18.drawStringCenteredColor(
                    event.poseStack(),
                    text,
                    x,
                    y,
                    new java.awt.Color(255, 30, 30) // 刺眼的警示红
            );
        }
    }
}