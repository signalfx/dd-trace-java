package datadog.trace.agent.tooling.jmx.attributes;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import datadog.trace.agent.tooling.jmx.Config;
import datadog.trace.agent.tooling.jmx.metrics.Metric;
import java.util.List;
import java.util.Map;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

public abstract class AbstractComplexAttribute<T> extends AbstractAttribute {

  private final List<Metric> metrics;

  public AbstractComplexAttribute(
      final ObjectName beanName,
      final MBeanAttributeInfo attributeInfo,
      final List<Config.Metric> metrics,
      final Map<String, String> tags) {
    super(beanName, attributeInfo);

    final Metric.Builder metricBuilder = Metric.Builder.withTags(checkNotNull(tags));
    final ImmutableList.Builder<Metric> metricsBuilder = ImmutableList.builder();
    for (final Config.Metric metric : checkNotNull(metrics)) {
      metricsBuilder.add(metricBuilder.forConfig(metric));
    }
    this.metrics = metricsBuilder.build();
  }

  @Override
  public void updateMetrics(final MBeanServer beanServer) {
    final T data = getJMXValue(beanServer);
    if (data != null) {
      for (final Metric metric : metrics) {
        metric.updateValue(getFieldValue(data, metric.getConfig().getField()));
      }
    }
  }

  @Override
  protected abstract T getJMXValue(final MBeanServer beanServer);

  protected abstract Object getFieldValue(final T data, final String field);

  @Override
  public List<Metric> getMetrics() {
    return metrics;
  }
}
