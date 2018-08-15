package datadog.trace.agent.tooling.jmx.metrics;

import datadog.trace.agent.tooling.jmx.Config;
import java.util.Map;

/** Simple Statsd metric that provides metric value as-is */
class SimpleMetric extends AbstractMetric {

  private Double value;

  SimpleMetric(final Config.Metric config, final Map<String, String> tags) {
    super(config, tags);
  }

  @Override
  public Double getValue() {
    return value;
  }

  @Override
  public void updateValue(final Object newValue) {
    value = castToDouble(newValue);
  }
}
