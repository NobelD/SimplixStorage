package de.leonhard.storage;

import de.leonhard.storage.internal.FileData;
import de.leonhard.storage.internal.FileType;
import de.leonhard.storage.internal.FlatFile;
import de.leonhard.storage.internal.editor.toml.TomlManager;
import de.leonhard.storage.internal.provider.SimplixProviders;
import de.leonhard.storage.internal.settings.ErrorHandler;
import de.leonhard.storage.internal.settings.ReloadSettings;
import de.leonhard.storage.util.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Consumer;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

public class Toml extends FlatFile {

  public Toml(@NonNull final Toml toml) {
    super(toml.getFile(), toml.pathSeparator());
    this.fileData = toml.getFileData();
    this.pathPrefix = toml.getPathPrefixArray();
  }

  public Toml(final String name, final String path) {
    this(name, path, null);
  }

  public Toml(final String name, final String path, final InputStream inputStream) {
    this(name, path, inputStream, null, null);
  }

  public Toml(
      @NonNull final String name,
      @NonNull final String path,
      @Nullable final InputStream inputStream,
      @Nullable final ReloadSettings reloadSettings,
      @Nullable final Consumer<FlatFile> reloadConsumer
  ) {
    this(name, path, inputStream, reloadSettings, null, reloadConsumer);
  }

  public Toml(
          @NonNull final String name,
          @NonNull final String path,
          @Nullable final InputStream inputStream,
          @Nullable final ReloadSettings reloadSettings,
          @Nullable final String pathPattern,
          @Nullable final Consumer<FlatFile> reloadConsumer
  ) {
    this(name, path, inputStream, reloadSettings, null, pathPattern, reloadConsumer);
  }

  public Toml(
          @NonNull final String name,
          @NonNull final String path,
          @Nullable final InputStream inputStream,
          @Nullable final ReloadSettings reloadSettings,
          @Nullable final ErrorHandler errorHandler,
          @Nullable final String pathPattern,
          @Nullable final Consumer<FlatFile> reloadConsumer
  ) {
    super(name, path, FileType.TOML, pathPattern, reloadConsumer);

    if (create() && inputStream != null) {
      FileUtils.writeToFile(this.file, inputStream);
    }

    if (reloadSettings != null) {
      this.reloadSettings = reloadSettings;
    }

    if (errorHandler != null) {
      this.errorHandler = errorHandler;
    }

    forceReload();
  }

  public Toml(final File file) {
    super(file, FileType.TOML);
    create();
    forceReload();
  }

  // ----------------------------------------------------------------------------------------------------
  // Abstract methods to implement
  // ----------------------------------------------------------------------------------------------------

  @Override
  protected final Map<String, Object> readToMap() throws IOException {
    return TomlManager.read(getFile());
  }

  @Override
  protected final void write(final FileData data) {
    try {
      TomlManager.write(data.toMap(), getFile());
    } catch (final IOException ioException) {
      SimplixProviders.logger().printMessage("Exception while writing fileData to file '" + getName() + "'");
      SimplixProviders.logger().printMessage("In '" + FileUtils.getParentDirPath(this.file) + "'");
      SimplixProviders.logger().printStackTrace(ioException);
    }
  }
}
