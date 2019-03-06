package datadog.opentracing.propagation;

import static datadog.opentracing.propagation.HttpCodec.ZERO;
import static datadog.opentracing.propagation.HttpCodec.validateUInt64BitsID;

import datadog.opentracing.DDSpanContext;
import datadog.trace.api.sampling.PrioritySampling;
import io.opentracing.SpanContext;
import io.opentracing.propagation.TextMap;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/** A codec designed for HTTP transport via headers using B3 headers */
@Slf4j
class B3HttpCodec {

  private static final String TRACE_ID_KEY = "X-B3-TraceId";
  private static final String SPAN_ID_KEY = "X-B3-SpanId";
  private static final String SAMPLING_PRIORITY_KEY = "X-B3-Sampled";
  private static final String SAMPLING_PRIORITY_ACCEPT = String.valueOf(1);
  private static final String SAMPLING_PRIORITY_DROP = String.valueOf(0);
  private static final int HEX_RADIX = 16;

  public static class Injector implements HttpCodec.Injector {

    @Override
    public void inject(final DDSpanContext context, final TextMap carrier) {

      try {
        // TODO: should we better store ids as BigInteger in context to avoid parsing it twice.
        final BigInteger traceId = new BigInteger(context.getTraceId());
        final BigInteger spanId = new BigInteger(context.getSpanId());

        carrier.put(TRACE_ID_KEY, traceId.toString(HEX_RADIX).toLowerCase());
        carrier.put(SPAN_ID_KEY, spanId.toString(HEX_RADIX).toLowerCase());

        if (context.lockSamplingPriority()) {
          carrier.put(
              SAMPLING_PRIORITY_KEY, convertSamplingPriority(context.getSamplingPriority()));
        }
        log.debug("{} - B3 parent context injected", context.getTraceId());
      } catch (final NumberFormatException e) {
        log.debug(
            "Cannot parse context id(s): {} {}", context.getTraceId(), context.getSpanId(), e);
      }
    }

    private String convertSamplingPriority(final int samplingPriority) {
      return samplingPriority > 0 ? SAMPLING_PRIORITY_ACCEPT : SAMPLING_PRIORITY_DROP;
    }
  }

  public static class Extractor implements HttpCodec.Extractor {

    @Override
    public SpanContext extract(final TextMap carrier) {
      try {
        String traceId = ZERO;
        String spanId = ZERO;
        int samplingPriority = PrioritySampling.UNSET;

        for (final Map.Entry<String, String> entry : carrier) {
          final String key = entry.getKey().toLowerCase();
          final String value = entry.getValue();

          if (value == null) {
            continue;
          }

          if (TRACE_ID_KEY.equalsIgnoreCase(key)) {
            traceId = validateUInt64BitsID(value, HEX_RADIX);
          } else if (SPAN_ID_KEY.equalsIgnoreCase(key)) {
            spanId = validateUInt64BitsID(value, HEX_RADIX);
          } else if (SAMPLING_PRIORITY_KEY.equalsIgnoreCase(key)) {
            samplingPriority = convertSamplingPriority(value);
          }
        }

        if (!ZERO.equals(traceId)) {
          final ExtractedContext context =
              new ExtractedContext(
                  traceId,
                  spanId,
                  samplingPriority,
                  null,
                  Collections.<String, String>emptyMap(),
                  Collections.<String, String>emptyMap());
          context.lockSamplingPriority();

          log.debug("{} - Parent context extracted", context.getTraceId());
          return context;
        }
      } catch (final RuntimeException e) {
        log.debug("Exception when extracting context", e);
      }

      return null;
    }

    private int convertSamplingPriority(final String samplingPriority) {
      return Integer.parseInt(samplingPriority) == 1
          ? PrioritySampling.SAMPLER_KEEP
          : PrioritySampling.SAMPLER_DROP;
    }
  }
}
