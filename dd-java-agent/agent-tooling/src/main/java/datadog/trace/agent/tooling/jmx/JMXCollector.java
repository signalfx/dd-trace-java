package datadog.trace.agent.tooling.jmx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import datadog.trace.agent.tooling.jmx.attributes.Attribute;
import datadog.trace.agent.tooling.jmx.metrics.Metric;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JMXCollector {

  private static final long REPORT_FREQUENCY_SECONDS = 5L;
  private static final ImmutableMap<String, String> DEFAULT_TAGS =
      ImmutableMap.of("default-tag", "default-value");

  private final ThreadFactory threadFactory =
      new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
          final Thread thread = new Thread(r, "dd-jmx-fetcher");
          thread.setDaemon(true);
          return thread;
        }
      };
  private final ScheduledExecutorService executorService =
      Executors.newSingleThreadScheduledExecutor(threadFactory);

  public JMXCollector() {
    log.error("Creating JMXCollector");
  }

  public static List<Config> parseConfig() throws IOException {
    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    final SimpleModule module = new SimpleModule();
    module.addDeserializer(Pattern.class, new Config.PatternDeserializer());
    mapper.registerModule(module);
    mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    final InputStream stream =
        Resources.asByteSource(Resources.getResource("jmx.yaml")).openBufferedStream();
    final List<Config> config = mapper.readValue(stream, new TypeReference<List<Config>>() {});
    stream.close();
    return config;
  }

  public void start() {
    log.error("Starting JMXCollector");
    try {
      executorService.scheduleAtFixedRate(
          new Reporter(parseConfig()),
          REPORT_FREQUENCY_SECONDS,
          REPORT_FREQUENCY_SECONDS,
          TimeUnit.SECONDS);
    } catch (final IOException e) {
      log.error("Cannot parse config", e);
    }
    try {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread() {
                @Override
                public void run() {
                  try {
                    executorService.shutdownNow();
                    executorService.awaitTermination(5, TimeUnit.SECONDS);
                  } catch (final InterruptedException e) {
                    // Don't bother waiting then...
                  }
                }
              });
    } catch (final IllegalStateException ex) {
      // The JVM is already shutting down.
    }
  }

  private static class Reporter implements Runnable {

    private final MBeanServer beanServer = ManagementFactory.getPlatformMBeanServer();
    private final StatsdWriter writer = new StatsdWriter();

    private final List<Config> configs;

    // Not using multimap because empty value means 'we've seen this bean, but have no metrics to
    // report for it defined
    private final Map<ObjectName, List<Attribute>> attributes = new HashMap<>();

    public Reporter(final List<Config> configs) {
      this.configs = configs;
    }

    @Override
    public void run() {
      log.debug("Fetching metrics");
      try {
        final Set<ObjectName> beanNames = beanServer.queryNames(null, null);

        for (final ObjectName beanName : Sets.difference(attributes.keySet(), beanNames)) {
          attributes.remove(beanName);
        }

        for (final ObjectName beanName : Sets.difference(beanNames, attributes.keySet())) {
          final ImmutableList.Builder<Attribute> beanAttributes = ImmutableList.builder();
          for (final Config config : configs) {
            if (config.getInclude().match(beanName) && !config.getExclude().match(beanName)) {
              for (final MBeanAttributeInfo attributeInfo :
                  beanServer.getMBeanInfo(beanName).getAttributes()) {
                final Config.Filter filter = config.getInclude();
                if (filter.getAttributes().containsKey(attributeInfo.getName())) {
                  log.debug("Creating attribute: {}, {}", beanName, attributeInfo);
                  final Attribute attribute =
                      Attribute.Builder.create(
                          beanName,
                          attributeInfo,
                          filter.getAttributes().get(attributeInfo.getName()),
                          filter.getTags(DEFAULT_TAGS, beanName));
                  if (attribute != null) {
                    beanAttributes.add(attribute);
                  }
                }
              }
            }
          }
          attributes.put(beanName, beanAttributes.build());
        }

        for (final List<Attribute> beanAttributes : attributes.values()) {
          for (final Attribute attribute : beanAttributes) {
            attribute.updateMetrics(beanServer);
            for (final Metric metric : attribute.getMetrics()) {
              writer.send(metric);
            }
          }
        }
      } catch (final JMException e) {
        log.error("Cannot fetch bean info", e);
      } catch (final Exception e) {
        log.error("Bad things happen", e);
      }
    }
  }
}
