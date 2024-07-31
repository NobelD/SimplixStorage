package de.leonhard.storage.internal;

import de.leonhard.storage.logger.LoggerInfo;
import de.leonhard.storage.annotation.ConfigPath;
import de.leonhard.storage.internal.provider.SimplixProviders;
import de.leonhard.storage.internal.settings.DataType;
import de.leonhard.storage.internal.settings.ReloadSettings;
import de.leonhard.storage.sections.FlatFileSection;
import de.leonhard.storage.util.FileUtils;
import de.leonhard.storage.util.Valid;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import lombok.*;
import org.jetbrains.annotations.Nullable;

@Getter
@ToString
@EqualsAndHashCode
public abstract class FlatFile implements DataStorage, Comparable<FlatFile> {

  protected final File file;
  protected final FileType fileType;
  @Setter
  protected ReloadSettings reloadSettings = ReloadSettings.INTELLIGENT;
  protected DataType dataType = DataType.UNSORTED;
  protected FileData fileData;
  @Nullable
  protected Consumer<FlatFile> reloadConsumer;
  @Setter
  protected String pathPrefix;
  @Getter
  protected final String pathPattern;
  private long lastLoaded;

  protected FlatFile(
      @NonNull final String name,
      @Nullable final String path,
      @NonNull final FileType fileType,
      @Nullable final String pathPattern,
      @Nullable final Consumer<FlatFile> reloadConsumer) {
    Valid.checkBoolean(!name.isEmpty(), "Name mustn't be empty");
    this.fileType = fileType;
    this.reloadConsumer = reloadConsumer;
    this.pathPattern = Objects.requireNonNullElse(pathPattern, ".");
    if (path == null || path.isEmpty()) {
      this.file = new File(FileUtils.replaceExtensions(name) + "." + fileType.getExtension());
    } else {
      final String fixedPath = path.replace("\\", "/");
      this.file = new File(
          fixedPath
          + File.separator
          + FileUtils.replaceExtensions(name)
          + "."
          + fileType.getExtension());
    }
  }

  protected FlatFile(
      @NonNull final String name,
      @Nullable final String path,
      @NonNull final FileType fileType,
      @Nullable final Consumer<FlatFile> reloadConsumer) {
    this(name, path, fileType, null, reloadConsumer);
  }

  protected FlatFile(@NonNull final File file, @NonNull final FileType fileType, @Nullable String pathPattern) {
    this.file = file;
    this.fileType = fileType;
    this.pathPattern = Objects.requireNonNullElse(pathPattern, ".");
    this.reloadConsumer = null;
    Valid.checkBoolean(
        fileType == FileType.fromExtension(file),
        "Invalid file-extension for file type: '" + fileType + "'",
        "Extension: '" + FileUtils.getExtension(file) + "'");
  }

  protected FlatFile(@NonNull final File file, @NonNull final FileType fileType) {
    this(file, fileType, null);
  }

  /**
   * This constructor should only be used to store for example YAML-LIKE data in a .db file
   *
   * <p>Therefor no validation is possible. Might be unsafe.
   */
  protected FlatFile(@NonNull final File file, @Nullable String pathPattern) {
    this.file = file;
    this.reloadConsumer = null;
    this.pathPattern = pathPattern;
    // Might be null
    this.fileType = FileType.fromFile(file);
  }

  /**
   * This constructor should only be used to store for example YAML-LIKE data in a .db file
   *
   * <p>Therefor no validation is possible. Might be unsafe.
   */
  protected FlatFile(@NonNull final File file) {
    this(file, (String) null);
  }

  // ----------------------------------------------------------------------------------------------------
  //  Creating our file
  // ----------------------------------------------------------------------------------------------------

  /**
   * Creates an empty .yml or .json file.
   *
   * @return true if file was created.
   */
  protected final boolean create() {
    return createFile(this.file);
  }

  private synchronized boolean createFile(final File file) {
    if (file.exists()) {
      this.lastLoaded = System.currentTimeMillis();
      return false;
    } else {
      FileUtils.getAndMake(file);
      this.lastLoaded = System.currentTimeMillis();
      return true;
    }
  }

  // ----------------------------------------------------------------------------------------------------
  // Abstract methods (Reading & Writing)
  // ----------------------------------------------------------------------------------------------------

  /**
   * Forces Re-read/load the content of our flat file Should be used to put the data from the file
   * to our FileData
   */
  protected abstract Map<String, Object> readToMap() throws IOException;

  /**
   * Write our data to file
   *
   * @param data Our data
   */
  protected abstract void write(final FileData data) throws IOException;

  protected void handleReloadException(final IOException ioException) {
    final String fileName = this.fileType == null
        ? "File"
        : this.fileType.name().toLowerCase(); // fileType might be null
    LoggerInfo.getLogger().printMessage("Exception reloading " + fileName + " '" + getName() + "'");
    LoggerInfo.getLogger().printMessage("In '" + FileUtils.getParentDirPath(this.file) + "'");
    LoggerInfo.getLogger().printStackTrace(ioException);
  }
  // ----------------------------------------------------------------------------------------------------
  // Overridden methods from DataStorage
  // ---------------------------------------------------------------------------------------------------->

  @Override
  public synchronized void set(final String key, final Object value) {
    reloadIfNeeded();
    final String finalKey = (this.pathPrefix == null) ? key : this.pathPrefix + this.pathPattern + key;
    this.fileData.insert(finalKey, value);
    write();
    this.lastLoaded = System.currentTimeMillis();
  }

  @Override
  public final Object get(final String key) {
    reloadIfNeeded();
    final String finalKey = this.pathPrefix == null ? key : this.pathPrefix + this.pathPattern + key;
    return getFileData().get(finalKey);
  }

  /**
   * Checks whether a key exists in the file
   *
   * @param key Key to check
   * @return Returned value
   */
  @Override
  public final boolean contains(final String key) {
    reloadIfNeeded();
    final String finalKey = (this.pathPrefix == null) ? key : this.pathPrefix + this.pathPattern + key;
    return this.fileData.containsKey(finalKey);
  }

  @Override
  public final Set<String> singleLayerKeySet() {
    reloadIfNeeded();
    return this.fileData.singleLayerKeySet();
  }

  @Override
  public final Set<String> singleLayerKeySet(final String key) {
    reloadIfNeeded();
    return this.fileData.singleLayerKeySet(key);
  }

  @Override
  public final Set<String> keySet() {
    reloadIfNeeded();
    return this.fileData.keySet();
  }

  @Override
  public final Set<String> keySet(final String key) {
    reloadIfNeeded();
    return this.fileData.keySet(key);
  }

  @Override
  public final synchronized void remove(final String key) {
    reloadIfNeeded();
    this.fileData.remove(key);
    write();
  }

  // ----------------------------------------------------------------------------------------------------
  // More advanced & FlatFile specific operations to add data.
  // ----------------------------------------------------------------------------------------------------

  /**
   * Method to insert the data of a map to our FlatFile
   *
   * @param map Map to insert.
   */
  public final void putAll(final Map<String, Object> map) {
    this.fileData.putAll(map);
    write();
  }

  /**
   * @return The data of our file as a Map<String, Object>
   */
  public final Map<String, Object> getData() {
    return getFileData().toMap();
  }

  // For performance separated from get(String key)
  public final List<Object> getAll(final String... keys) {
    final List<Object> result = new ArrayList<>();
    reloadIfNeeded();

    for (final String key : keys) {
      result.add(get(key));
    }

    return result;
  }

  public void removeAll(final String... keys) {
    for (final String key : keys) {
      this.fileData.remove(key);
    }
    write();
  }

  // ----------------------------------------------------------------------------------------------------
  // Pretty nice utility methods for FlatFile's
  // ----------------------------------------------------------------------------------------------------

  public final void addDefaultsFromMap(@NonNull final Map<String, Object> mapWithDefaults) {
    addDefaultsFromFileData(new FileData(mapWithDefaults, this.dataType));
  }

  public final void addDefaultsFromFileData(@NonNull final FileData newData) {
    reloadIfNeeded();

    // Creating & setting defaults
    for (final String key : newData.keySet()) {
      if (!this.fileData.containsKey(key)) {
        this.fileData.insert(key, newData.get(key));
      }
    }

    write();
  }

  public final void addDefaultsFromFlatFile(@NonNull final FlatFile flatFile) {
    addDefaultsFromFileData(flatFile.getFileData());
  }

  public final String getName() {
    return this.file.getName();
  }

  public final String getFilePath() {
    return this.file.getAbsolutePath();
  }

  public synchronized void replace(
      final CharSequence target,
      final CharSequence replacement) throws IOException {
    final List<String> lines = Files.readAllLines(this.file.toPath());
    final List<String> result = new ArrayList<>();
    for (final String line : lines) {
      result.add(line.replace(target, replacement));
    }
    Files.write(this.file.toPath(), result);
  }

  public void write() {
    try {
      write(this.fileData);
    } catch (final IOException ex) {
      LoggerInfo.getLogger().printMessage("Exception writing to file '" + getName() + "'");
      LoggerInfo.getLogger().printMessage("In '" + FileUtils.getParentDirPath(this.file) + "'");
      LoggerInfo.getLogger().printStackTrace(ex);
    }
    this.lastLoaded = System.currentTimeMillis();
  }

  public final boolean hasChanged() {
    return FileUtils.hasChanged(this.file, this.lastLoaded);
  }

  public final void forceReload() {
    Map<String, Object> out = new HashMap<>();
    try {
      out = readToMap();
    } catch (final IOException ex) {
      handleReloadException(ex);
    } finally {
      if (this.fileData == null) {
        this.fileData = new FileData(out, this.dataType, pathPattern);
      } else {
        this.fileData.loadData(out);
      }
      this.lastLoaded = System.currentTimeMillis();
      if (this.reloadConsumer != null) {
        this.reloadConsumer.accept(this);
      }
    }
  }

  public final void clear() {
    this.fileData.clear();
    write();
  }

  public final void clearPathPrefix() {
    this.pathPrefix = null;
  }

  // ----------------------------------------------------------------------------------------------------
  // Internal stuff
  // ----------------------------------------------------------------------------------------------------

  protected final void reloadIfNeeded() {
    if (shouldReload()) {
      forceReload();
    }
  }

  // Should the file be re-read before the next get() operation?
  // Can be used as utility method for implementations of FlatFile
  protected boolean shouldReload() {
      return switch (this.reloadSettings) {
          case AUTOMATICALLY -> true;
          case INTELLIGENT -> FileUtils.hasChanged(this.file, this.lastLoaded);
          default -> false;
      };
  }

  // ----------------------------------------------------------------------------------------------------
  // Misc
  // ----------------------------------------------------------------------------------------------------

  public final FileData getFileData() {
    Valid.notNull(this.fileData, "FileData mustn't be null");
    return this.fileData;
  }

  public final FlatFileSection getSection(final String pathPrefix) {
    return new FlatFileSection(this, pathPrefix);
  }

  @Override
  public final int compareTo(@NonNull final FlatFile flatFile) {
    return this.file.compareTo(flatFile.file);
  }

  public void annotateClass(Object classInstance, String section) {
    this.annotateClass(classInstance, (s, field) -> section + "." + s);
  }

  public void annotateClass(Object classInstance) {
    this.annotateClass(classInstance, ((s, field) -> s));
  }

  public void annotateClass(Object classInstance, BiFunction<String, Field, String> elementSelector) {
    Class<?> clazz = classInstance.getClass();
    try {
      for (Field field : clazz.getFields()) {
        ConfigPath configPath = field.getAnnotation(ConfigPath.class);
        if(configPath != null) {
          field.setAccessible(true);
          field.set(classInstance, this.get(elementSelector.apply(configPath.value(), field), field.getType()));
        }
      }
    }catch (IllegalAccessException e) {
      throw SimplixProviders.exceptionHandler().create(e.getCause(), "Unable to set the value of fields in " + clazz.getName());
    }
  }

  /**
   * Creates a path for this file with the path separator defined for this file.
   */
  public String createPath(@NonNull final String first, @NonNull final String... other) {
    if (other.length == 0) return first;
    StringBuilder sb = new StringBuilder(first);
    for (String o : other) {
      sb.append(pathPattern).append(o);
    }
    return sb.toString();
  }

  /**
   * Creates a path for this file with the path separator defined for this file.
   */
  public String createPath(@NonNull final String[] path) {
    StringBuilder sb = new StringBuilder();
    for (String o : path) {
      sb.append(pathPattern).append(o);
    }
    return sb.toString();
  }
}
