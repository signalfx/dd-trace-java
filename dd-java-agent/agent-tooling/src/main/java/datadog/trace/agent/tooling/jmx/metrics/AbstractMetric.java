package datadog.trace.agent.tooling.jmx.metrics;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.MoreObjects;
import datadog.trace.agent.tooling.jmx.Config;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/** Statsd metric base class */
@Slf4j
abstract class AbstractMetric implements Metric {

  /**
   * Represents possible value types that can be read from JMX and provides ways of converting them
   * to double to be sent to Statsd. Also provides way to properly calculate difference between to
   * values taking datatype wrapping into account.
   */
  protected enum ValueType {
    STRING() {
      @Override
      public boolean match(final Object value) {
        return value instanceof String;
      }

      @Override
      public Double convert(final Object value) {
        return Double.parseDouble((String) value);
      }
    },

    INTEGER {
      @Override
      public boolean match(final Object value) {
        return value instanceof Integer;
      }

      @Override
      public Double convert(final Object value) {
        return ((Integer) value).doubleValue();
      }

      @Override
      public Double difference(final Object newValue, final Object previousValue) {
        return Integer.valueOf(((Integer) newValue) - ((Integer) previousValue)).doubleValue();
      }
    },

    LONG {
      @Override
      public boolean match(final Object value) {
        return value instanceof Long;
      }

      @Override
      public Double convert(final Object value) {
        return ((Long) value).doubleValue();
      }

      @Override
      public Double difference(final Object newValue, final Object previousValue) {
        return Long.valueOf(((Long) newValue) - ((Long) previousValue)).doubleValue();
      }
    },

    BOOLEAN {
      @Override
      public boolean match(final Object value) {
        return value instanceof Boolean;
      }

      @Override
      public Double convert(final Object value) {
        return ((Boolean) value) ? 1.0 : 0.0;
      }
    },

    ATOMIC_INTEGER {
      @Override
      public boolean match(final Object value) {
        return value instanceof AtomicInteger;
      }

      @Override
      public Double convert(final Object value) {
        return INTEGER.convert(((AtomicInteger) value).get());
      }

      @Override
      public Double difference(final Object newValue, final Object previousValue) {
        return Integer.valueOf(
                ((AtomicInteger) newValue).get() - ((AtomicInteger) previousValue).get())
            .doubleValue();
      }
    },

    ATOMIC_LONG {
      @Override
      public boolean match(final Object value) {
        return value instanceof AtomicLong;
      }

      @Override
      public Double convert(final Object value) {
        return LONG.convert(((AtomicLong) value).get());
      }

      @Override
      public Double difference(final Object newValue, final Object previousValue) {
        return Long.valueOf(((AtomicLong) newValue).get() - ((AtomicLong) previousValue).get())
            .doubleValue();
      }
    },

    DOUBLE {
      @Override
      public boolean match(final Object value) {
        return value instanceof Double;
      }

      @Override
      public Double convert(final Object value) {
        return (Double) value;
      }
    },

    NUMBER {
      @Override
      public boolean match(final Object value) {
        return value instanceof Number;
      }

      @Override
      public Double convert(final Object value) {
        return ((Number) value).doubleValue();
      }
    };

    /**
     * Determine type of provided value object
     *
     * @param value value object
     * @return true iff provided value matches current value type
     */
    public abstract boolean match(Object value);

    /**
     * Convert given value to Double
     *
     * @param value value object to convert
     * @return double value, may return null of conversion fails
     */
    public abstract Double convert(Object value);

    /**
     * Calculate difference between values
     *
     * @param newValue new value
     * @param previousValue previous value
     * @return difference between values, taking datatype wrapping into account where applicable.
     *     May return null if conversion fails.
     */
    public Double difference(final Object newValue, final Object previousValue) {
      final Double convertedNewValue = convert(newValue);
      final Double convertedPreviousValue = convert(previousValue);
      if (convertedNewValue != null && convertedPreviousValue != null) {
        return convert(newValue) - convert(previousValue);
      } else {
        return null;
      }
    }
  }

  @Getter private final Config.Metric config;
  @Getter private final Map<String, String> tags;

  AbstractMetric(final Config.Metric config, final Map<String, String> tags) {
    this.config = checkNotNull(config);
    this.tags = checkNotNull(tags);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("config", config)
        .add("tags", tags)
        .add("value", getValue())
        .toString();
  }

  protected static ValueType findValueType(final Object value) {
    for (final ValueType type : ValueType.values()) {
      if (type.match(value)) {
        return type;
      }
    }

    return null;
  }

  protected Double castToDouble(final Object value, final ValueType valueType) {
    try {
      if (valueType == null) {
        return (Double) value;
      } else {
        return valueType.convert(value);
      }
    } catch (final ClassCastException | NumberFormatException e) {
      log.debug(
          "Cannot convert metric {} to double: {}, {}",
          config.getAlias(),
          value,
          value.getClass().getName(),
          e);
      return null;
    }
  }

  protected Double castToDouble(final Object value) {
    return castToDouble(value, findValueType(value));
  }
}
