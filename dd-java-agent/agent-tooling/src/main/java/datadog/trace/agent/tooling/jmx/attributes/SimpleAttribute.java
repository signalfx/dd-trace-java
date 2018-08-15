package datadog.trace.agent.tooling.jmx.attributes;

import com.google.common.collect.ImmutableList;
import datadog.trace.agent.tooling.jmx.Config;
import datadog.trace.agent.tooling.jmx.metrics.Metric;
import java.util.List;
import java.util.Map;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SimpleAttribute extends AbstractAttribute {

  private final Metric metric;

  protected SimpleAttribute(
      final ObjectName beanName,
      final MBeanAttributeInfo attributeInfo,
      final List<Config.Metric> metrics,
      final Map<String, String> tags) {
    super(beanName, attributeInfo);
    metric = createMetric(metrics, tags);
  }

  private Metric createMetric(final List<Config.Metric> metrics, final Map<String, String> tags) {
    final Metric.Builder builder = Metric.Builder.withTags(tags);

    if (metrics.size() <= 0) {
      log.debug("No fields defined for {}", getFullName());
      return builder.absent(getFullName());
    }
    if (metrics.size() > 1) {
      log.debug("Too many fields defined for {}, ignoring all but the first one", getFullName());
    }
    return builder.forConfig(metrics.get(0));
  }

  @Override
  public void updateMetrics(final MBeanServer beanServer) {
    metric.updateValue(getJMXValue(beanServer));
  }

  @Override
  public List<Metric> getMetrics() {
    return ImmutableList.of(metric);
  }
}
