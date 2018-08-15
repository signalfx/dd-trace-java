package datadog.trace.agent.tooling.jmx

import spock.lang.Specification


class JMXCollectorTest extends Specification {

  def "test parse config"() {
    setup:
    JMXCollector.parseConfig()
  }

}
