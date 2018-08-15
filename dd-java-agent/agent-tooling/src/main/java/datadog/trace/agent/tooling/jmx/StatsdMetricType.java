package datadog.trace.agent.tooling.jmx;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/** Types of Statsd metrics */
public enum StatsdMetricType {
  GAUGE("gauge"),
  COUNTER("counter"),
  HISTOGRAM("historgram");

  @Getter private final String name;

  StatsdMetricType(final String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }

  @JsonValue
  public String getValue() {
    return name;
  }
}
