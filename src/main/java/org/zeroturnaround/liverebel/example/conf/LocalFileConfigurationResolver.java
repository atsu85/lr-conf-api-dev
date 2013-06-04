package org.zeroturnaround.liverebel.example.conf;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.zeroturnaround.liverebel.managedconf.client.ConfigurationResolverFactory;
import com.zeroturnaround.liverebel.managedconf.client.LrAppConfiguration;
import com.zeroturnaround.liverebel.managedconf.client.LrConfigMetadata;
import com.zeroturnaround.liverebel.managedconf.client.LrConfigMetadata.ConfInfoFile;
import com.zeroturnaround.liverebel.managedconf.client.LrConfigSnapshot;
import com.zeroturnaround.liverebel.managedconf.client.listener.ConfigurationUpdateListener;
import com.zeroturnaround.liverebel.managedconf.client.spi.AbstractConfigurationResolver;

/**
 * Base class for development environment, where server is not running with LiveRebel agent that would otherwise provide runtime configuration for the application.
 * This class extends {@link AbstractConfigurationResolver} just like implementation used by LiveRebel agent,
 * but it loads configuration from local file system file (see {@link #getRuntimeConfFilePath()}). <br>
 * <br>
 * <b>To use custom configuration resolver in development environment, create class with precise cannonial name indicated by following field:
 * {@link ConfigurationResolverFactory#NO_LIVEREBEL_CONFIGURATION_RESOLVER_CLASS_NAME}. To take advantage of this class you should extend created class from this class.</b>
 * You may want to customize the file path that is used to read in runtime properties
 *
 * @author Ats Uiboupin
 */
public abstract class LocalFileConfigurationResolver extends AbstractConfigurationResolver {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LocalFileConfigurationResolver.class);

  public LocalFileConfigurationResolver() {
    super();
    monitorFileModification(getRuntimeConfFile());
  }

  protected File getRuntimeConfFile() {
    String runtimeConfFilePath = getRuntimeConfFilePath();
    final File file = new File(runtimeConfFilePath);
    if (!file.isFile()) {
      throw new IllegalArgumentException("File " + file.getAbsolutePath() + " not found");
    }
    return file;
  }

  /**
   * @return Path to runtime properties - for example "/tmp/managed.runtime.properties"
   */
  abstract protected String getRuntimeConfFilePath();

  @Override
  protected Set<String> parseDeclaredProps(InputStream is) {
    try {
      List<String> declaredPropsFileLines = IOUtils.readLines(is, "UTF-8");
      Set<String> declaredRuntimeProps = parseDeclaredPropsFileLines(declaredPropsFileLines);
      log.info("Following runtime properties are defined in WEB-INF/classes/" + LrAppConfiguration.DECLARED_RUNTIME_PROPERTIES_FILE_NAME + ": " + declaredRuntimeProps);
      return declaredRuntimeProps;
    }
    catch (Exception e) {
      throw new RuntimeException("Couldn't read declared runtime properties for current application version from " +
          "WEB-INF/classes/" + LrAppConfiguration.DECLARED_RUNTIME_PROPERTIES_FILE_NAME, e);
    }
  }

  @Override
  protected LrConfigSnapshot createLrConfigSnapshot() {
    Properties properties = new Properties();
    FileInputStream fis = null;
    File runtimeConfFile = getRuntimeConfFile();
    try {
      fis = new FileInputStream(runtimeConfFile);
      properties.load(fis);
    }
    catch (IOException e) {
      log.error("Failed to load properties from " + runtimeConfFile.getAbsolutePath(), e);
    }
    finally {
      IOUtils.closeQuietly(fis);
    }
    Map<String, String> propertiesMap = new HashMap<String, String>();
    Set<Object> keySet = properties.keySet();
    for (Object propName : keySet) {
      propertiesMap.put(((String) propName), properties.getProperty((String) propName));
    }
    return new LrConfigSnapshot(propertiesMap, createLrConfigMetadata(), this);
  }

  protected LrConfigMetadata createLrConfigMetadata() {
    HashMap<String, String> metadataMap = new HashMap<String, String>();
    metadataMap.put(ConfInfoFile.CONF_INFO_PROP_CREATED, String.valueOf(getRuntimeConfFile().lastModified()));
    return new LrConfigMetadata(metadataMap);
  }

  @Override
  protected void logListenerException(ConfigurationUpdateListener listener, Exception e) {
    log.error("Configuration update listener " + listener + " threw exception", e);
  }

  protected void monitorFileModification(final File file) {
    Thread fileMonitor = new Thread() {
      private long lastModified;

      @Override
      public void run() {
        while (true) {
          long newLastModified = file.lastModified();
          if (lastModified == 0) {
            lastModified = newLastModified;
          }
          else if (newLastModified != lastModified) {
            lastModified = newLastModified;
            log.info("Configuration file " + file.getAbsolutePath() + " is updated, updating runtime configuration...");
            configurationUpdated();
            log.info("Configuration file " + file.getAbsolutePath() + " was updated, updated runtime configuration");
          }
          try {
            Thread.sleep(1000);
          }
          catch (InterruptedException e) {
            // restore interrupted status
            Thread.currentThread().interrupt();
          }
        }
      }
    };
    fileMonitor.setDaemon(true);
    fileMonitor.setName(file.getName() + "-monitor");
    log.info("Monitoring " + file.getAbsolutePath() + " for changes");
    fileMonitor.start();
  }

}
