package datadog.trace.agent.tooling.jmx.metrics;

import datadog.trace.agent.tooling.jmx.Config;
import java.util.Map;

/** Statsd metric that provides rate of change for the value */
class DifferentialMetric extends AbstractMetric {

  private Double value;
  private Object previousValue;
  private ValueType previousType;

  DifferentialMetric(final Config.Metric config, final Map<String, String> tags) {
    super(config, tags);
  }

  @Override
  public Double getValue() {
    return value;
  }

  @Override
  public void updateValue(final Object newValue) {
    final ValueType type = findValueType(newValue);
    if (type == null || previousValue == null || previousType == null || type != previousType) {
      value = null;
    } else {
      value = type.difference(newValue, previousValue);
    }
    previousValue = newValue;
    previousType = type;
  }
}
