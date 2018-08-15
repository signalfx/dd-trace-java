package datadog.trace.agent.tooling.jmx.metrics;

import com.google.common.collect.ImmutableMap;
import datadog.trace.agent.tooling.jmx.Config;
import datadog.trace.agent.tooling.jmx.StatsdMetricType;

/** Metric that is absent from the bean server. Useful to avoid handling null values. */
class AbsentMetric extends AbstractMetric {

  AbsentMetric(final String name) {
    super(new Config.Metric(name, StatsdMetricType.GAUGE, name), ImmutableMap.<String, String>of());
  }

  @Override
  public Double getValue() {
    return null;
  }

  @Override
  public void updateValue(final Object newValue) {
    /* nothing to do */
  }
}
