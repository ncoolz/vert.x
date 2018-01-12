package io.vertx.core.file.impl;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.file.FileModifyWatcher;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;

import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class HostsFileModifyWatcher implements FileModifyWatcher {
  private static final Logger log = LoggerFactory.getLogger(HostsFileModifyWatcher.class);

  private final Vertx vertx;
  private final String hostsPath;

  private Path hostsFileName;

  private WatchService watcher;

  private boolean initialized = false;
  private AtomicBoolean started = new AtomicBoolean(false);

  public HostsFileModifyWatcher(Vertx vertx, String hostsPath) {
    this.vertx = vertx;
    this.hostsPath = hostsPath;

    try {
      this.watcher = FileSystems.getDefault().newWatchService();

    } catch (IOException e) {
      log.error("Failed to initialize watcher.", e);
      return;
    }

    Path hosts = Paths.get(hostsPath);

    this.hostsFileName = hosts.getFileName();
    Path directory = hosts.getParent();

    try {
      directory.register(watcher, ENTRY_MODIFY);

    } catch (IOException e) {
      log.error("Failed to register directory {} to watcher.", directory, e);
      closeWatcher();
      return;
    }

    initialized = true;
  }

  private void closeWatcher() {
    if (watcher != null) {
      try {
        watcher.close();
      } catch (IOException e) {
        log.error("An error occurred while closing watcher.", e);
      }

    }
  }

  private void pollHostsFile(Handler<Path> handler) {
    if (!initialized || !started()) {
      handler.handle(null);
      return;
    }

    vertx.executeBlocking(future -> {
      WatchKey watchKey = watcher.poll();

      if (watchKey == null) {
        pollHostsFile(handler);
        future.complete();
        return;
      }

      for (WatchEvent<?> event : watchKey.pollEvents()) {
        @SuppressWarnings("unchecked")
        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path fileName = ev.context();

        if (ev.kind() == ENTRY_MODIFY && fileName.toString().equals(hostsFileName.toString())) {
          handler.handle(hostsFileName);
          break;
        }

      }

      watchKey.reset();

      pollHostsFile(handler);
      future.complete();

    }, null);
  }

  private boolean started() {
    return started.get();
  }

  private boolean getStarted() {
    return started.compareAndSet(false, true);
  }


  @Override
  public void watch(Handler<Path> handler) {
    if(getStarted()) {
      pollHostsFile(handler);
    }
  }

  @Override
  public void close() {
    started.set(false);
    closeWatcher();
  }
}
