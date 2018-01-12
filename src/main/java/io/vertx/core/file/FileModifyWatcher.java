package io.vertx.core.file;

import io.vertx.core.Handler;

import java.nio.file.Path;

public interface FileModifyWatcher {
  void watch(Handler<Path> handler);
  void close();
}
