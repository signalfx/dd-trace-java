package datadog.trace.agent.tooling.jmx.attributes;

import static com.google.common.base.Preconditions.checkNotNull;

import datadog.trace.agent.tooling.jmx.metrics.Metric;
import java.util.List;
import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractAttribute implements Attribute {

  private final ObjectName beanName;
  private final MBeanAttributeInfo attributeInfo;

  protected AbstractAttribute(final ObjectName beanName, final MBeanAttributeInfo attributeInfo) {
    this.beanName = checkNotNull(beanName);
    this.attributeInfo = checkNotNull(attributeInfo);
  }

  @Override
  public String getFullName() {
    return beanName.toString() + "." + attributeInfo.getName();
  }

  protected Object getJMXValue(final MBeanServer beanServer) {
    return getJMXValue(beanServer, Object.class);
  }

  protected <T> T getJMXValue(final MBeanServer beanServer, final Class<T> clazz) {
    try {
      return clazz.cast(beanServer.getAttribute(beanName, attributeInfo.getName()));
    } catch (final JMException e) {
      log.debug("Cannot get metric value: {}", getFullName(), e);
      return null;
    } catch (final ClassCastException e) {
      log.debug("Cannot cast attribute to: {}", clazz.getName(), e);
      return null;
    }
  }

  @Override
  public abstract void updateMetrics(final MBeanServer beanServer);

  @Override
  public abstract List<Metric> getMetrics();
}
