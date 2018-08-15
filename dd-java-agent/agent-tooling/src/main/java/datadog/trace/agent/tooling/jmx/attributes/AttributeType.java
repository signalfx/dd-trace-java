package datadog.trace.agent.tooling.jmx.attributes;

import com.google.common.collect.ImmutableSet;
import datadog.trace.agent.tooling.jmx.Config;
import java.util.List;
import java.util.Map;
import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;

enum AttributeType {
  SIMPLE(
      "long",
      "java.lang.String",
      "int",
      "float",
      "double",
      "java.lang.Double",
      "java.lang.Float",
      "java.lang.Integer",
      "java.lang.Long",
      "java.util.concurrent.atomic.AtomicInteger",
      "java.util.concurrent.atomic.AtomicLong",
      "java.lang.Object",
      "java.lang.Boolean",
      "boolean",
      "java.lang.Number") {
    @Override
    protected Attribute create(
        final ObjectName beanName,
        final MBeanAttributeInfo attributeInfo,
        final List<Config.Metric> metrics,
        final Map<String, String> tags) {
      return new SimpleAttribute(beanName, attributeInfo, metrics, tags);
    }
  },

  COMPOSITE("javax.management.openmbean.CompositeData") {
    @Override
    protected Attribute create(
        final ObjectName beanName,
        final MBeanAttributeInfo attributeInfo,
        final List<Config.Metric> metrics,
        final Map<String, String> tags) {
      return new CompositeAttribute(beanName, attributeInfo, metrics, tags);
    }
  },

  MAP("java.util.HashMap", "java.util.Map") {
    @Override
    protected Attribute create(
        final ObjectName beanName,
        final MBeanAttributeInfo attributeInfo,
        final List<Config.Metric> metrics,
        final Map<String, String> tags) {
      return new MapAttribute(beanName, attributeInfo, metrics, tags);
    }
  }
/*,
    //FIXME: implement tabular attributes!!!
TABULAR("javax.management.openmbean.TabularData") {
  @Override
  protected JMXAttribute create(final MBeanAttributeInfo attributeInfo, final ObjectName beanName, Map<String, List<Config.Attribute> fields, Map<String, String> tags) {
    return new JMXComplexAttribute(attributeInfo, beanName, fields, tags);
  }
}*/ ;

  private final ImmutableSet<String> types;

  AttributeType(final String... types) {
    this.types = ImmutableSet.copyOf(types);
  }

  boolean match(final MBeanAttributeInfo attributeInfo) {
    return types.contains(attributeInfo.getType());
  }

  protected abstract Attribute create(
      final ObjectName beanName,
      final MBeanAttributeInfo attributeInfo,
      List<Config.Metric> metrics,
      Map<String, String> tags);
}
