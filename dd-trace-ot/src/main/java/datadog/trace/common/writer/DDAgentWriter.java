package datadog.trace.common.writer;

import static datadog.trace.api.Config.DEFAULT_AGENT_HOST;
import static datadog.trace.api.Config.DEFAULT_TRACE_AGENT_PORT;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import datadog.opentracing.DDSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.msgpack.jackson.dataformat.MessagePackFactory;

/**
 * This writer buffers traces and sends them to the provided DDApi instance.
 *
 * <p>Written traces are passed off to a disruptor so as to avoid blocking the application's thread.
 * If a flood of traces arrives that exceeds the disruptor ring size, the traces exceeding the
 * threshold will be counted and sampled.
 */
@Slf4j
public class DDAgentWriter implements Writer {
  private static final int DISRUPTOR_BUFFER_SIZE = 8192;
  private static final int FLUSH_PAYLOAD_BYTES = 5_000_000; // 5 MB
  private static final int FLUSH_PAYLOAD_DELAY = 1; // 1/second

  private static final EventTranslatorOneArg<Event<List<DDSpan>>, List<DDSpan>> TRANSLATOR =
      new EventTranslatorOneArg<Event<List<DDSpan>>, List<DDSpan>>() {
        @Override
        public void translateTo(
            final Event<List<DDSpan>> event, final long sequence, final List<DDSpan> trace) {
          final List<DDSpan> discarded = event.getAndSet(trace);
          if (discarded != null) {
            log.debug("Trace discarded due to spike in traces: {}", discarded);
          }
        }
      };
  private static final EventTranslator<Event<List<DDSpan>>> FLUSH_TRANSLATOR =
      new EventTranslator<Event<List<DDSpan>>>() {
        @Override
        public void translateTo(final Event<List<DDSpan>> event, final long sequence) {
          event.shouldFlush = true;
        }
      };

  private static final ThreadFactory DISRUPTOR_THREAD_FACTORY =
      new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
          final Thread thread = new Thread(r, "dd-trace-disruptor");
          thread.setDaemon(true);
          return thread;
        }
      };
  private static final ThreadFactory SCHEDULED_FLUSH_THREAD_FACTORY =
      new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable r) {
          final Thread thread = new Thread(r, "dd-trace-writer");
          thread.setDaemon(true);
          return thread;
        }
      };

  private static final ObjectMapper objectMapper = new ObjectMapper(new MessagePackFactory());

  private final DDApi api;
  private final int flushFrequencySeconds;
  private final Disruptor<Event<List<DDSpan>>> disruptor;
  private final EventHandler consumer = new TraceConsumer();
  private final ScheduledExecutorService scheduledWriterExecutor;
  private final AtomicInteger traceCount = new AtomicInteger(0);
  private final AtomicReference<ScheduledFuture<?>> flushSchedule = new AtomicReference<>();
  private final Phaser apiPhaser;
  private volatile boolean running = false;

  public DDAgentWriter() {
    this(new DDApi(DEFAULT_AGENT_HOST, DEFAULT_TRACE_AGENT_PORT));
  }

  public DDAgentWriter(final DDApi api) {
    this(api, DISRUPTOR_BUFFER_SIZE, FLUSH_PAYLOAD_DELAY);
  }

  /**
   * Used in the tests.
   *
   * @param api
   * @param disruptorSize Rounded up to next power of 2
   * @param flushFrequencySeconds value < 1 disables scheduled flushes
   */
  private DDAgentWriter(final DDApi api, final int disruptorSize, final int flushFrequencySeconds) {
    this.api = api;
    this.flushFrequencySeconds = flushFrequencySeconds;
    disruptor =
        new Disruptor<>(
            new DisruptorEventFactory<List<DDSpan>>(),
            Math.max(2, Integer.highestOneBit(disruptorSize - 1) << 1), // Next power of 2
            DISRUPTOR_THREAD_FACTORY,
            ProducerType.MULTI,
            new SleepingWaitStrategy(0, TimeUnit.MILLISECONDS.toNanos(5)));
    disruptor.handleEventsWith(consumer);
    scheduledWriterExecutor = Executors.newScheduledThreadPool(1, SCHEDULED_FLUSH_THREAD_FACTORY);
    apiPhaser = new Phaser(); // Ensure API calls are completed when flushing
    apiPhaser.register(); // Register for the executor thread.
  }

  @Override
  public void write(final List<DDSpan> trace) {
    // We can't add events after shutdown otherwise it will never complete shutting down.
    if (running) {
      final boolean published = disruptor.getRingBuffer().tryPublishEvent(TRANSLATOR, trace);
      if (!published) {
        // We're discarding the trace, but we still want to count it.
        traceCount.incrementAndGet();
      }
    }
  }

  public int getTotalTraces() {
    return traceCount.get();
  }

  public DDApi getApi() {
    return api;
  }

  @Override
  public void start() {
    disruptor.start();
    running = true;
    scheduleFlush();
  }

  @Override
  public void close() {
    running = false;
    flush();
    disruptor.shutdown();
    scheduledWriterExecutor.shutdown();
  }

  public void flush() {
    apiPhaser.register();
    disruptor.publishEvent(FLUSH_TRANSLATOR);
    apiPhaser.arriveAndAwaitAdvance();
    apiPhaser.arriveAndDeregister();
  }

  @Override
  public String toString() {
    return "DDAgentWriter { api=" + api + " }";
  }

  private void scheduleFlush() {
    if (flushFrequencySeconds > 0) {
      final ScheduledFuture<?> previous =
          flushSchedule.getAndSet(
              scheduledWriterExecutor.schedule(flushTask, flushFrequencySeconds, SECONDS));
      if (previous != null) {
        previous.cancel(true);
      }
    }
  }

  private final Runnable flushTask = new FlushTask();

  private class FlushTask implements Runnable {
    @Override
    public void run() {
      // Don't call flush() because it would block the thread also used for sending the traces.
      disruptor.publishEvent(FLUSH_TRANSLATOR);
    }
  }

  /**
   * This class is not threadsafe. It should only be used by a disruptor with a single consumer (the
   * default executor).
   */
  private class TraceConsumer implements EventHandler<Event<List<DDSpan>>> {
    private List<byte[]> serializedTraces = new ArrayList<>();
    private int payloadSize = 0;

    @Override
    public void onEvent(
        final Event<List<DDSpan>> event, final long sequence, final boolean endOfBatch) {
      final List<DDSpan> trace = event.getAndSet(null);
      if (trace != null) {
        traceCount.incrementAndGet();
        try {
          final byte[] serializedTrace = objectMapper.writeValueAsBytes(trace);
          payloadSize += serializedTrace.length;
          serializedTraces.add(serializedTrace);
        } catch (final JsonProcessingException e) {
          log.warn("Error serializing trace", e);
        }
      }
      if (event.shouldFlush || payloadSize >= FLUSH_PAYLOAD_BYTES) {
        reportTraces();
        event.shouldFlush = false;
      }
    }

    private void reportTraces() {
      try {
        if (serializedTraces.isEmpty()) {
          apiPhaser.arrive(); // Allow flush to return
          return;
        }
        final List<byte[]> toSend = serializedTraces;
        serializedTraces = new ArrayList<>(toSend.size());
        // ^ Initialize with similar size to reduce arraycopy churn.

        final int totalSize = traceCount.getAndSet(0);
        final int sizeInBytes = payloadSize;

        // Run the actual IO task on a different thread to avoid blocking the consumer.
        scheduledWriterExecutor.schedule(
            new Runnable() {
              @Override
              public void run() {
                try {
                  final boolean sent = api.sendSerializedTraces(totalSize, toSend);
                  if (!sent) {
                    log.debug(
                        "Failed to send {} traces (representing {}) of size {} bytes to the API",
                        toSend.size(),
                        totalSize,
                        sizeInBytes);
                  }
                } finally {
                  apiPhaser.arrive(); // Flush completed.
                }
              }
            },
            0,
            SECONDS);
      } finally {
        payloadSize = 0;
        scheduleFlush();
      }
    }
  }

  private static class Event<T> extends AtomicReference<T> {
    private volatile boolean shouldFlush = false;
  }

  private static class DisruptorEventFactory<T> implements EventFactory<Event<T>> {
    @Override
    public Event<T> newInstance() {
      return new Event<>();
    }
  }
}
