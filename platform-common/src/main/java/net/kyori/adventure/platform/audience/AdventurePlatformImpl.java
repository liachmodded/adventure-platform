/*
 * This file is part of text-extras, licensed under the MIT License.
 *
 * Copyright (c) 2018 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.kyori.adventure.platform.audience;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import net.kyori.adventure.audience.MultiAudience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.platform.AdventureRenderer;
import net.kyori.adventure.platform.audience.AdventurePlayerAudience;
import net.kyori.adventure.platform.audience.AdventureAudience;
import net.kyori.adventure.platform.AdventurePlatform;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * A base implementation of adventure platform.
 */
public abstract class AdventurePlatformImpl implements AdventurePlatform {

  private Audience all;
  private Audience console;
  private Audience players;
  private Map<UUID, AdventurePlayerAudience> playerMap;
  private Set<AdventureAudience> senderSet;
  private Map<String, Audience> permissionMap;
  private Map<UUID, Audience> worldMap;
  private Map<String, Audience> serverMap;
  private AdventureRenderer renderer;
  private volatile boolean closed;

  protected AdventurePlatformImpl() {
    this.console = new ConsoleAudience();
    this.players = (MultiAudience) () -> this.playerMap.values();
    this.senderSet = ConcurrentHashMap.newKeySet();
    this.all = (MultiAudience) () -> this.senderSet;
    this.playerMap = new ConcurrentHashMap<>();
    this.permissionMap = new ConcurrentHashMap<>();
    this.worldMap = new ConcurrentHashMap<>();
    this.serverMap = new ConcurrentHashMap<>();
    this.renderer = new EmptyAdventureRenderer(); // TODO: pass to constructor for customization
    this.closed = false;
  }

  // TODO: Not a fan of this implementation, especially copying Titles and Books
  // Should do component rendering at a "lower level"
  private class AdventureAudienceImpl implements ForwardingAudience, AdventureAudience {

    private final AdventureAudience audience;

    private AdventureAudienceImpl(AdventureAudience audience) {
      this.audience = audience;
    }

    private Component render(@NonNull Component component) {
      return renderer.render(component, this.audience);
    }

    @Override
    public @Nullable Audience audience() {
      return closed ? null : audience;
    }

    @Override
    public void sendMessage(@NonNull Component message) {
      if (closed) return;
      final Component newMessage = render(message);
      this.audience.sendMessage(newMessage);
    }

    @Override
    public void sendActionBar(@NonNull Component message) {
      if (closed) return;
      final Component newMessage = render(message);
      this.audience.sendActionBar(newMessage);
    }

    @Override
    public void showTitle(@NonNull Title title) {
      if (closed) return;
      final Title newTitle = Title.of(render(title.title()), render(title.subtitle()), title.fadeInTime(), title.stayTime(), title.fadeOutTime());
      this.audience.showTitle(newTitle);
    }

    @Override
    public void showBossBar(@NonNull BossBar bar) {
      if (closed) return;
      final BossBar newBar = BossBar.of(render(bar.name()), bar.percent(), bar.color(), bar.overlay(), bar.flags());
      this.audience.showBossBar(newBar);
    }

    @Override
    public void openBook(@NonNull Book book) {
      if (closed) return;
      final List<Component> newPages = new ArrayList<>(book.pages().size());
      for(Component page : book.pages()) {
        newPages.add(render(page));
      }
      final Book newBook = Book.of(render(book.title()), render(book.author()), newPages);
      this.audience.openBook(newBook);
    }

    @Override
    public @Nullable Locale locale() {
      return this.audience.locale();
    }

    @Override
    public boolean hasPermission(@NonNull String permission) {
      return this.audience.hasPermission(permission);
    }

    @Override
    public boolean console() {
      return this.audience.console();
    }
  }

  private class AdventurePlayerAudienceImpl extends AdventureAudienceImpl implements AdventurePlayerAudience {

    private final AdventurePlayerAudience player;

    private AdventurePlayerAudienceImpl(AdventurePlayerAudience audience) {
      super(audience);
      this.player = audience;
    }

    @Override
    public @NonNull UUID id() {
      return this.player.id();
    }

    @Override
    public @Nullable UUID worldId() {
      return this.player.worldId();
    }

    @Override
    public @Nullable String serverName() {
      return this.player.serverName();
    }
  }

  /**
   * Adds an audience to the registry.
   *
   * @param audience an audience
   */
  protected void add(AdventureAudience audience) {
    if (closed) return;

    final AdventureAudience wrapped = audience instanceof AdventurePlayerAudience ?
      new AdventurePlayerAudienceImpl((AdventurePlayerAudience) audience) :
      new AdventureAudienceImpl(audience);

    this.senderSet.add(wrapped);
    if (audience instanceof AdventurePlayerAudience) {
      this.playerMap.put(((AdventurePlayerAudience) wrapped).id(), (AdventurePlayerAudience) wrapped);
    }
  }

  /**
   * Removes an audience from the registry.
   *
   * @param playerId a player id
   */
  protected void remove(UUID playerId) {
    final Audience removed = this.playerMap.remove(playerId);
    this.senderSet.remove(removed);
  }

  @Override
  public @NonNull Audience all() {
    return this.all;
  }

  private class ConsoleAudience implements MultiAudience {
    private final Iterable<AdventureAudience> console = filter(senderSet, AdventureAudience::console);
    @Override
    public @NonNull Iterable<? extends Audience> audiences() {
      return this.console;
    }
  }

  @Override
  public @NonNull Audience console() {
    return this.console;
  }

  @Override
  public @NonNull Audience players() {
    return this.players;
  }

  @Override
  public @NonNull Audience player(@NonNull UUID playerId) {
    final AdventurePlayerAudience player = this.playerMap.get(playerId);
    return player == null ? Audience.empty() : player;
  }

  private class PermissionAudience implements MultiAudience {
    private final Iterable<AdventureAudience> filtered = filter(senderSet, this::hasPermission);
    private final String permission;

    private PermissionAudience(final @NonNull String permission) {
      this.permission = requireNonNull(permission, "permission");
    }

    private boolean hasPermission(AdventureAudience audience) {
      return audience.hasPermission(this.permission);
    }

    @Override
    public @NonNull Iterable<? extends Audience> audiences() {
      return this.filtered;
    }
  }

  @Override
  public @NonNull Audience permission(@NonNull String permission) {
    // TODO: potential memory leak, can we limit collection size somehow?
    // the rest of the collections could run into the same issue, but this one presents the most potential for unbounded growth
    // maybe don't even cache, ask ppl to hold references?
    return this.permissionMap.computeIfAbsent(permission, PermissionAudience::new);
  }

  private class WorldAudience implements MultiAudience {
    private final Iterable<AdventurePlayerAudience> filtered = filter(playerMap.values(), this::inWorld);
    private final UUID worldId;

    private WorldAudience(final @NonNull UUID worldId) {
      this.worldId = requireNonNull(worldId, "world id");
    }

    private boolean inWorld(AdventurePlayerAudience audience) {
      return this.worldId.equals(audience.worldId());
    }

    @Override
    public @NonNull Iterable<? extends Audience> audiences() {
      return this.filtered;
    }
  }

  @Override
  public @NonNull Audience world(@NonNull UUID worldId) {
    return this.worldMap.computeIfAbsent(worldId, WorldAudience::new);
  }

  private class ServerAudience implements MultiAudience {
    private final Iterable<AdventurePlayerAudience> filtered = filter(playerMap.values(), this::isOnServer);
    private final String serverName;

    private ServerAudience(final @NonNull String serverName) {
      this.serverName = requireNonNull(serverName, "server name");
    }

    private boolean isOnServer(AdventurePlayerAudience audience) {
      return this.serverName.equals(audience.serverName());
    }

    @Override
    public @NonNull Iterable<? extends Audience> audiences() {
      return this.filtered;
    }
  }

  @Override
  public @NonNull Audience server(@NonNull String serverName) {
    return this.serverMap.computeIfAbsent(serverName, ServerAudience::new);
  }

  private static class EmptyAdventureRenderer implements AdventureRenderer {
    @Override
    public @NonNull Component render(@NonNull Component component, @NonNull AdventureAudience audience) {
      return component;
    }

    @Override
    public int compare(AdventureAudience a1, AdventureAudience a2) {
      return 0; // All audiences are equivalent, since there is no customization
    }
  }

  @Override
  public @NonNull AdventureRenderer renderer() {
    return renderer;
  }

  @Override
  public void close() {
    if (!this.closed) {
      this.closed = true;
      this.all = Audience.empty();
      this.console = Audience.empty();
      this.players = Audience.empty();
      this.playerMap = Collections.emptyMap();
      this.senderSet = Collections.emptySet();
      this.permissionMap = Collections.emptyMap();
      this.worldMap = Collections.emptyMap();
      this.serverMap = Collections.emptyMap();
      this.renderer = new EmptyAdventureRenderer();
    }
  }

  /**
   * Return a live filtered view of the input {@link Iterable}.
   *
   * <p>Only elements that match {@code filter} will be returned
   * by {@linkplain Iterator Iterators} provided.</p>
   *
   * <p>Because this is a <em>live</em> view, any changes to the state of
   * the parent {@linkplain Iterable} will be reflected in iterations over
   * the return value.</p>
   *
   * @param input The source iterator
   * @param filter Predicate to filter on
   * @param <T> value type
   * @return live filtered view
   */
  private static <T> Iterable<T> filter(final Iterable<T> input, Predicate<T> filter) {
    return new Iterable<T>() {
      // create a lazy iterator
      // pre-fetches by one output value to determine whether or not we have another value
      // one value will be fetched on iterator creation, and each next value will be
      // fetched after returning the previous value.
      @Override
      public @NonNull Iterator<T> iterator() {
        return new Iterator<T>() {
          private final Iterator<T> parent = input.iterator();
          private T next;

          private void populate() {
            while(this.parent.hasNext()) {
              T next = this.parent.next();
              if(filter.test(next)) {
                this.next = next;
                return;
              }
            }
          }

          // initialize first value
          {
            this.populate();
          }

          @Override
          public boolean hasNext() {
            return this.next != null;
          }

          @Override
          public T next() {
            if(this.next == null) {
              throw new NoSuchElementException();
            }
            T next = this.next;
            this.populate();
            return next;
          }
        };
      }

      @Override
      public void forEach(final Consumer<? super T> action) {
        input.forEach(el -> {
          if(filter.test(el)) action.accept(el);
        });
      }
    };
  }
}