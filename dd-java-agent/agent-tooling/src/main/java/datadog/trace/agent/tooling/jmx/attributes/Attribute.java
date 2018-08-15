package datadog.trace.agent.tooling.jmx.attributes;

import datadog.trace.agent.tooling.jmx.Config;
import datadog.trace.agent.tooling.jmx.metrics.Metric;
import java.util.List;
import java.util.Map;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import lombok.extern.slf4j.Slf4j;

public interface Attribute {

  @Slf4j
  class Builder {
    public static Attribute create(
        final ObjectName beanName,
        final MBeanAttributeInfo attributeInfo,
        final List<Config.Metric> metrics,
        final Map<String, String> tags) {
      for (final AttributeType type : AttributeType.values()) {
        if (type.match(attributeInfo)) {
          return type.create(beanName, attributeInfo, metrics, tags);
        }
      }

      log.debug(
          "Bean {}, attribute {} has unsupported type: {}",
          beanName,
          attributeInfo.getName(),
          attributeInfo.getType());
      return null;
    }
  }

  String getFullName();

  void updateMetrics(final MBeanServer beanServer);

  List<Metric> getMetrics();
}
