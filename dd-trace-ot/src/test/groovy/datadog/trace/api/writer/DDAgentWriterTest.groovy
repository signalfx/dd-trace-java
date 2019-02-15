package datadog.trace.api.writer

import datadog.opentracing.DDSpan
import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.DDAgentWriter
import datadog.trace.common.writer.DDApi
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

import static datadog.opentracing.SpanFactory.newSpanOf
import static datadog.trace.common.writer.DDAgentWriter.DISRUPTOR_BUFFER_SIZE

@Timeout(5)
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
    (1..traceCount).each {
      writer.write(trace)
    }
    writer.flush()

    then:
    1 * api.sendSerializedTraces(traceCount, { it.size() < traceCount })
    0 * _

    cleanup:
    writer.close()

    where:
    trace = [newSpanOf(0)]
    disruptorSize = 2
    traceCount = 100 // Shouldn't trigger payload, but bigger than the disruptor size.
  }

  def "test flush by size"() {
    setup:
    def writer = new DDAgentWriter(api, DISRUPTOR_BUFFER_SIZE, -1)
    def phaser = writer.apiPhaser
    writer.start()
    phaser.register()

    when:
    (1..8).each {
      writer.write(trace)
    }
    // Wait for 2 flushes of 3 by size
    phaser.awaitAdvanceInterruptibly(phaser.arrive())
    phaser.awaitAdvanceInterruptibly(phaser.arriveAndDeregister())

    then:
    2 * api.sendSerializedTraces(3, { it.size() == 3 })

    when:
    // Flush the remaining 2
    writer.flush()

    then:
    1 * api.sendSerializedTraces(2, { it.size() == 2 })
    0 * _

    cleanup:
    writer.close()

    where:
    span = [newSpanOf(0)]
    trace = (0..10000).collect { span }
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
    phaser.awaitAdvanceInterruptibly(phaser.arriveAndDeregister())

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
    def writer = new DDAgentWriter(api, DISRUPTOR_BUFFER_SIZE, -1)
    writer.start()

    when:
    (0..maxedPayloadTraceCount).each {
      writer.write(minimalTrace)
      def start = System.nanoTime()
      // (consumer processes a trace in about 20 microseconds
      while (System.nanoTime() - start < TimeUnit.MICROSECONDS.toNanos(100)) {
        // Busywait because we don't want to fill up the ring buffer
      }
    }
    writer.flush()

    then:
    1 * api.sendSerializedTraces(maxedPayloadTraceCount, { it.size() == maxedPayloadTraceCount })

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
    traceSize = DDAgentWriter.MAPPER.writeValueAsBytes(minimalTrace).length
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
