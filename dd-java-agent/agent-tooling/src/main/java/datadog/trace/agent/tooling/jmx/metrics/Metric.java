package datadog.trace.agent.tooling.jmx.metrics;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import datadog.trace.agent.tooling.jmx.Config;
import datadog.trace.agent.tooling.jmx.StatsdMetricType;
import java.util.Map;

/** Statsd metric interface */
public interface Metric {

  /** @return current metric value, may return null if value is unknown */
  Double getValue();

  /** @return statsd metric config */
  Config.Metric getConfig();

  /** @return statsd metric tags */
  Map<String, String> getTags();

  /**
   * Updates metric value trying to determine its type
   *
   * @param newValue new value
   */
  void updateValue(Object newValue);

  /** Builder class for Statsd metrics. */
  class Builder {

    private final Map<String, String> tags;

    private Builder(final Map<String, String> tags) {
      this.tags = ImmutableMap.copyOf(checkNotNull(tags));
    }

    /**
     * Create builder for given set of tags
     *
     * @param tags tags map
     * @return new builder instance
     */
    public static Builder withTags(final Map<String, String> tags) {
      return new Builder(tags);
    }

    /**
     * Create metric instance for a given attribute
     *
     * @param config attribute to use
     * @return new metric instance
     */
    public Metric forConfig(final Config.Metric config) {
      if (config.getMetricType() == StatsdMetricType.COUNTER) {
        return new DifferentialMetric(config, tags);
      } else {
        return new SimpleMetric(config, tags);
      }
    }

    /**
     * Create absent metric. This metric doesn't report any values to statd server
     *
     * @param alias alias to use
     * @return new metric instance
     */
    public Metric absent(final String alias) {
      return new AbsentMetric(alias);
    }
  }
}
