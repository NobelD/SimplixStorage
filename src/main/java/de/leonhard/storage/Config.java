package de.leonhard.storage;

import de.leonhard.storage.internal.FlatFile;
import de.leonhard.storage.internal.settings.ConfigSettings;
import de.leonhard.storage.internal.settings.DataType;
import de.leonhard.storage.internal.settings.ErrorHandler;
import de.leonhard.storage.internal.settings.ReloadSettings;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.function.Consumer;

@SuppressWarnings({"unused"})
public class Config extends Yaml {

  public Config(@NonNull final Config config) {
    super(config);
  }

  public Config(final String name, final String path) {
    this(name, path, null, null, ConfigSettings.PRESERVE_COMMENTS, DataType.SORTED);
  }

  public Config(
      final String name,
      @Nullable final String path,
      @Nullable final InputStream inputStream) {
    this(name, path, inputStream, null, ConfigSettings.PRESERVE_COMMENTS, DataType.SORTED);
  }

  public Config(
      final String name,
      @Nullable final String path,
      @Nullable final InputStream inputStream,
      @Nullable final ReloadSettings reloadSettings,
      @Nullable final ConfigSettings configSettings,
      @Nullable final DataType dataType) {
    super(name, path, inputStream, reloadSettings, configSettings, dataType);
    setConfigSettings(ConfigSettings.PRESERVE_COMMENTS);
  }

  public Config(
      final String name,
      @Nullable final String path,
      @Nullable final InputStream inputStream,
      @Nullable final ReloadSettings reloadSettings,
      @Nullable final ConfigSettings configSettings,
      @Nullable final DataType dataType,
      @Nullable final Consumer<FlatFile> reloadConsumer) {
    super(name, path, inputStream, reloadSettings, configSettings, dataType, reloadConsumer);
    setConfigSettings(ConfigSettings.PRESERVE_COMMENTS);
  }

  public Config(
          final String name,
          @Nullable final String path,
          @Nullable final InputStream inputStream,
          @Nullable final ReloadSettings reloadSettings,
          @Nullable final ConfigSettings configSettings,
          @Nullable final DataType dataType,
          @Nullable final String pathPattern,
          @Nullable final Consumer<FlatFile> reloadConsumer) {
    super(name, path, inputStream, reloadSettings, configSettings, dataType, pathPattern, reloadConsumer);
    setConfigSettings(ConfigSettings.PRESERVE_COMMENTS);
  }

  public Config(
          final String name,
          @Nullable final String path,
          @Nullable final InputStream inputStream,
          @Nullable final ReloadSettings reloadSettings,
          @Nullable final ErrorHandler errorHandler,
          @Nullable final ConfigSettings configSettings,
          @Nullable final DataType dataType,
          @Nullable final String pathPattern,
          @Nullable final Consumer<FlatFile> reloadConsumer) {
    super(name, path, inputStream, reloadSettings, errorHandler, configSettings, dataType, pathPattern, reloadConsumer);
    setConfigSettings(ConfigSettings.PRESERVE_COMMENTS);
  }

  public Config(final File file) {
    super(file);
  }

  // ----------------------------------------------------------------------------------------------------
  // Method overridden from Yaml
  // ----------------------------------------------------------------------------------------------------

  @Override
  public Config addDefaultsFromInputStream() {
    return (Config) super.addDefaultsFromInputStream();
  }

  @Override
  public Config addDefaultsFromInputStream(@Nullable final InputStream inputStream) {
    return (Config) super.addDefaultsFromInputStream(inputStream);
  }
}
