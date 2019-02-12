package datadog.trace.api.writer

import datadog.opentracing.DDSpan
import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.DDAgentWriter
import datadog.trace.common.writer.DDApi
import spock.lang.Specification

import java.util.concurrent.TimeUnit

import static datadog.opentracing.SpanFactory.newSpanOf

class DDAgentWriterTest extends Specification {

  def api = Mock(DDApi)

  def "test happy path"() {
    setup:
    def writer = new DDAgentWriter(api, 2, -1)
    writer.start()

    when:
    writer.write(trace)
    writer.write(trace)
    writer.flush()

    then:
    1 * api.sendSerializedTraces(2, { it.size() == 2 })
    0 * _

    cleanup:
    writer.close()

    where:
    trace = [newSpanOf(0)]
  }

  def "test flood of traces"() {
    setup:
    def writer = new DDAgentWriter(api, disruptorSize, -1)
    writer.start()

    when:
    def start = System.nanoTime()
    (1..20).each {
      writer.write(trace)
    }
    def difference = System.nanoTime() - start;
    println "Processing took ${TimeUnit.NANOSECONDS.toMillis(difference)}ms"
    writer.flush()

    then:
    1 * api.sendSerializedTraces(20, { it.size() == disruptorSize })
    0 * _

    cleanup:
    writer.close()

    where:
    trace = [newSpanOf(0)]
    disruptorSize = 4
  }

  def "test flush by size"() {
    setup:
    def writer = new DDAgentWriter(api)
    def phaser = writer.apiPhaser
    phaser.register()
    writer.start()

    when:
    (1..55).each {
      writer.write(trace)
    }
    // Wait for 2 flushes of 25 from size
    phaser.arriveAndAwaitAdvance()
    phaser.arriveAndAwaitAdvance()
    // Flush the remaining 5
    writer.flush()

    then:
    2 * api.sendSerializedTraces(25, { it.size() == 25 })
    1 * api.sendSerializedTraces(5, { it.size() == 5 })
    0 * _

    cleanup:
    writer.close()

    where:
    span = [newSpanOf(0)]
    trace = (1..1000).collect { span }
    // Each trace is 202003 bytes serialized
  }

  def "test flush by time"() {
    setup:
    def writer = new DDAgentWriter(api)
    def phaser = writer.apiPhaser
    phaser.register()
    writer.start()

    when:
    (1..5).each {
      writer.write(trace)
    }
    phaser.arriveAndAwaitAdvance()

    then:
    1 * api.sendSerializedTraces(5, { it.size() == 5 })
    0 * _

    cleanup:
    writer.close()

    where:
    span = [newSpanOf(0)]
    trace = (1..10).collect { span }
  }

  def "test default buffer size"() {
    setup:
    def writer = new DDAgentWriter(api, DDAgentWriter.DISRUPTOR_BUFFER_SIZE, -1)
    writer.start()

    when:
    (0..maxedPayloadTraceCount).each {
      writer.write(minimalTrace)
      def start = System.nanoTime()
      // Busywait because we don't want to fill up the ring buffer
      // (consumer processes a trace in about 20 microseconds
      while (System.nanoTime() - start < TimeUnit.MICROSECONDS.toNanos(50));
    }
    writer.flush()

    then:
    1 * api.sendSerializedTraces(maxedPayloadTraceCount, { it.size() == maxedPayloadTraceCount })
    1 * api.sendSerializedTraces(1, { it.size() == 1 })
    0 * _

    cleanup:
    writer.close()

    where:
    minimalContext = new DDSpanContext(
      "1",
      "1",
      "0",
      "",
      "",
      "",
      PrioritySampling.UNSET,
      Collections.emptyMap(),
      false,
      "",
      Collections.emptyMap(),
      Mock(PendingTrace),
      Mock(DDTracer))
    minimalSpan = new DDSpan(0, minimalContext)
    minimalTrace = [minimalSpan]
    traceSize = DDAgentWriter.objectMapper.writeValueAsBytes(minimalTrace).length
    maxedPayloadTraceCount = ((int) (DDAgentWriter.FLUSH_PAYLOAD_BYTES / traceSize)) + 1
  }

  def "check that are no interactions after close"() {

    setup:
    def writer = new DDAgentWriter(api)
    writer.start()

    when:
    writer.close()
    writer.write([])
    writer.flush()

    then:
    0 * _
    writer.totalTraces == 0
  }
}
