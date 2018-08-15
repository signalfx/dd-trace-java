package datadog.trace.agent.tooling.jmx;

import com.timgroup.statsd.NonBlockingStatsDClient;
import com.timgroup.statsd.StatsDClient;
import datadog.trace.agent.tooling.jmx.metrics.Metric;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StatsdWriter {

  private final StatsDClient client;

  public StatsdWriter() {
    this(null, "127.0.0.1", 8125);
  }

  public StatsdWriter(final String metricPrefix, final String host, final int port) {
    client = new NonBlockingStatsDClient(metricPrefix, host, port);
  }

  protected void send(final Metric metric) {
    log.debug("Writing metric: {}", metric);
    final Double value = metric.getValue();
    if (value != null) {
      final Config.Metric config = metric.getConfig();
      switch (config.getMetricType()) {
        case GAUGE:
          client.gauge(config.getAlias(), value, transformTags(metric.getTags()));
          break;
        case COUNTER:
          client.count(config.getAlias(), value, transformTags(metric.getTags()));
          break;
        case HISTOGRAM:
          client.histogram(config.getAlias(), value, transformTags(metric.getTags()));
          break;
        default:
          log.debug("Unknown metric type: {}", metric);
      }
    }
  }

  private String[] transformTags(final Map<String, String> tags) {
    final List<String> result = new ArrayList<>(tags.size());
    for (final Map.Entry<String, String> entry : tags.entrySet()) {
      result.add(String.format("%s:%s", entry.getKey(), entry.getValue()));
    }
    return result.toArray(new String[] {});
  }
}
