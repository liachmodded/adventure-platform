package net.kyori.adventure.platform.spongeapi;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.text.channel.ChatTypeMessageReceiver;
import org.spongepowered.api.text.channel.MessageReceiver;
import org.spongepowered.api.text.chat.ChatTypes;

class SpongeAudience implements Audience {
  protected final MessageReceiver receiver;

  public SpongeAudience(MessageReceiver target) {
    this.receiver = target;
  }

  @Override
  public void message(final @NonNull Component message) {
    receiver.sendMessage(Adapters.toSponge(message));
  }

  @Override
  public void showBossBar(@NonNull final BossBar bar) { }

  @Override
  public void hideBossBar(@NonNull final BossBar bar) { }

  @Override
  public void showActionBar(@NonNull final Component message) {
    if(this.receiver instanceof ChatTypeMessageReceiver) {
      ((ChatTypeMessageReceiver) this.receiver).sendMessage(ChatTypes.ACTION_BAR, Adapters.toSponge(message));
    } else {
      message(message);
    }
  }

  @Override
  public void playSound(@NonNull final Sound sound) { }

  @Override
  public void stopSound(@NonNull final SoundStop stop) { }
}