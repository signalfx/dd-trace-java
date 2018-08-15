package datadog.trace.agent.tooling.jmx;

import static com.fasterxml.jackson.core.JsonToken.VALUE_STRING;
import static com.google.common.base.Preconditions.checkNotNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.management.ObjectName;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Config {

  private static final ImmutableMap<String, String> RENAMED_STATSD_TAGS =
      ImmutableMap.of("host", "bean_host");

  public static class Metric {

    @Getter private final String alias;
    @Getter private final StatsdMetricType metricType;
    @Getter private final String field;

    /**
     * This constuctor is used by Jackson to create value from bare String value in YAML.
     *
     * <p>Note: do we really need this format, it seems quite limited and might be hard to implement
     * completely
     *
     * @param value String value
     */
    @JsonCreator
    public Metric(final String value) {
      // FIXME: can we add domain name here?
      alias = "jmx." + checkNotNull(value).replaceAll("[^A-Za-z0-9]+", ".").toLowerCase();
      metricType = StatsdMetricType.GAUGE;
      field = "_default_";
    }

    @JsonCreator
    public Metric(
        @JsonProperty("alias") final String alias,
        @JsonProperty("metric_type") final StatsdMetricType metricType,
        @JsonProperty("field") final String field) {
      this.alias = checkNotNull(alias);
      this.metricType = metricType == null ? StatsdMetricType.GAUGE : metricType;
      this.field = field == null ? "_default_" : field;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("alias", alias)
          .add("metricType", metricType)
          .add("field", field)
          .toString();
    }
  }

  public static class Filter {
    private final boolean neverMatch;
    private final List<String> domains;
    private final List<Pattern> domainRegexes;
    private final List<String> beans;
    private final List<Pattern> beanRegexes;
    private final Map<String, String> beanProperties;
    @Getter private final Map<String, List<Metric>> attributes;
    private final Map<String, String> tags;

    @JsonCreator
    public Filter(
        // FIXME: split Filter into interface and normal and 'never matching' implementation
        @JsonProperty("neverMatch") final boolean neverMatch,
        // FIXME: figure out plurals vs singular and make it consistent
        @JsonProperty("domain") final List<String> domains,
        @JsonProperty("domain_regex") final List<Pattern> domainRegexes,
        @JsonProperty("bean") final List<String> beans,
        @JsonProperty("bean_regex") final List<Pattern> beanRegexes,
        @JsonProperty("bean_property") final Map<String, String> beanProperties,
        @JsonProperty("attribute") final Map<String, List<Metric>> attributes,
        @JsonProperty("tags") final Map<String, String> tags) {
      this.neverMatch = neverMatch;
      this.domains = convert(domains);
      this.domainRegexes = convert(domainRegexes);
      this.beans = convert(beans);
      this.beanRegexes = convert(beanRegexes);
      this.beanProperties = convert(beanProperties);
      // FIXME: do we need to make lists immutable?
      this.attributes = convert(attributes);
      this.tags = convert(tags);
    }

    // FIXME: newer Jackson should have a better way of doing this
    private static <V> List<V> convert(final List<V> value) {
      return value == null ? ImmutableList.<V>of() : ImmutableList.copyOf(value);
    }

    private static <K, V> Map<K, V> convert(final Map<K, V> value) {
      return value == null ? ImmutableMap.<K, V>of() : ImmutableMap.copyOf(value);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("neverMatch", neverMatch)
          .add("domains", domains)
          .add("domainRegexes", domainRegexes)
          .add("beans", beans)
          .add("beanRegexes", beanRegexes)
          .add("beanProperties", beanProperties)
          .add("attributes", attributes)
          .add("tags", tags)
          .toString();
    }

    public boolean match(final ObjectName name) {
      return !neverMatch
          && matchDomains(name)
          && matchDomainRegexes(name)
          && matchBeans(name)
          && matchBeansRegexes(name)
          && matchBeanProperties(name);
    }

    public Map<String, String> getTags(
        final Map<String, String> defaultTags, final ObjectName name) {
      // FIXME: add support for $tag values (i.e. read data from bean name)
      final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
      builder.put("jmx_domain", name.getDomain());
      for (final Map.Entry<String, String> entry : name.getKeyPropertyList().entrySet()) {
        String key = cleanStatsdTag(entry.getKey());
        if (RENAMED_STATSD_TAGS.containsKey(key)) {
          key = RENAMED_STATSD_TAGS.get(key);
        }
        builder.put(key, entry.getValue());
      }
      builder.putAll(defaultTags);
      builder.putAll(tags);

      return builder.build();
    }

    private boolean matchDomains(final ObjectName name) {
      return matchStringList(domains, name.getDomain());
    }

    private boolean matchDomainRegexes(final ObjectName name) {
      return matchRegexList(domainRegexes, name.getDomain());
    }

    private boolean matchBeans(final ObjectName name) {
      return matchStringList(beans, name.getCanonicalName());
    }

    private boolean matchBeansRegexes(final ObjectName name) {
      return matchRegexList(beanRegexes, name.getCanonicalName());
    }

    private boolean matchBeanProperties(final ObjectName name) {
      for (final Map.Entry<String, String> entry : beanProperties.entrySet()) {
        final String value = name.getKeyProperty(entry.getKey());
        if (value == null || !value.equals(entry.getValue())) {
          return false;
        }
      }
      return true;
    }

    private static boolean matchStringList(final List<String> list, final String value) {
      if (list.isEmpty()) {
        return true;
      }
      for (final String element : list) {
        if (value.equals(element)) {
          return true;
        }
      }
      return false;
    }

    private static boolean matchRegexList(final List<Pattern> list, final String value) {
      if (list.isEmpty()) {
        return true;
      }
      for (final Pattern pattern : list) {
        if (pattern.matcher(value).matches()) {
          return true;
        }
      }
      return false;
    }

    // Both keys and values on statsd tags do not allow '|' characters, so clean them out
    private String cleanStatsdTag(final String value) {
      return value.replace("|", "");
    }
  }

  /** Helper to convert regexes */
  static class PatternDeserializer extends StdDeserializer<Pattern> {

    public PatternDeserializer() {
      super(Pattern.class);
    }

    @Override
    public Pattern deserialize(final JsonParser parser, final DeserializationContext context)
        throws IOException {
      if (parser.getCurrentToken() == VALUE_STRING) {
        return Pattern.compile(parser.getText());
      } else {
        throw JsonMappingException.from(
            parser, String.format("Cannot convert %s to regex", parser.getCurrentToken()));
      }
    }
  }

  @Getter private final Filter include;
  @Getter private final Filter exclude;

  @JsonCreator
  public Config(
      @JsonProperty("include") final Filter include,
      @JsonProperty("exclude") final Filter exclude) {
    this.include = checkNotNull(include);
    // exclude is optional
    this.exclude =
        exclude == null ? new Filter(true, null, null, null, null, null, null, null) : exclude;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("include", include)
        .add("exclude", exclude)
        .toString();
  }
}
