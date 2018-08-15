package datadog.trace.agent.tooling.jmx.attributes;

import datadog.trace.agent.tooling.jmx.Config;
import java.util.List;
import java.util.Map;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.InvalidKeyException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MapAttribute extends AbstractComplexAttribute<Map<String, Object>> {
  protected MapAttribute(
      final ObjectName beanName,
      final MBeanAttributeInfo attributeInfo,
      final List<Config.Metric> metrics,
      final Map<String, String> tags) {
    super(beanName, attributeInfo, metrics, tags);
  }

  @Override
  protected Map<String, Object> getJMXValue(final MBeanServer beanServer) {
    // FIXME: can we use SupressWarnings or TypeToken to avoid this warning?
    return getJMXValue(beanServer, Map.class);
  }

  @Override
  protected Object getFieldValue(final Map<String, Object> data, final String field) {
    try {
      return data.get(field);
    } catch (final InvalidKeyException e) {
      log.debug("Cannot read key {} for {}", field, getFullName());
      return null;
    }
  }
}
