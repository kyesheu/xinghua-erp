package cn.xinghuaerp.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/** Shared Jackson configuration for platform payloads; never enables polymorphic input. */
public final class JsonUtils {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
            .defaultTimeZone(TimeZone.getDefault())
            .addModule(new SimpleModule()
                    .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME))
                    .addDeserializer(LocalDateTime.class, new ValueDeserializer<LocalDateTime>() {
                        @Override
                        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
                            return LocalDateTime.parse(parser.getString().replace(' ', 'T'));
                        }
                    }))
            .changeDefaultPropertyInclusion(v -> JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build();

    private JsonUtils() { }

    public static JsonMapper mapper() {
        return MAPPER;
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static JsonNode readTree(String json) {
        return json == null || json.isBlank() ? null : nullable(MAPPER.readTree(json));
    }

    public static <T> T readValue(String json, Class<T> type) {
        return json == null || json.isBlank() ? null : MAPPER.readValue(json, type);
    }

    public static <T> List<T> readList(String json, Class<T> type) {
        return json == null || json.isBlank() ? null : MAPPER.readValue(json,
                MAPPER.getTypeFactory().constructCollectionType(List.class, type));
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static ObjectNode object(Map<String, ?> values) {
        return MAPPER.valueToTree(values);
    }

    public static JsonNode node(JsonNode parent, String key) {
        return containerValue(parent.get(key));
    }

    public static JsonNode node(JsonNode parent, int index) {
        return containerValue(parent.get(index));
    }

    public static String text(JsonNode parent, String key) {
        return text(parent.get(key));
    }

    public static String text(JsonNode parent, int index) {
        return text(parent.get(index));
    }

    public static String text(JsonNode value) {
        value = nullable(value);
        return value == null ? null : value.isContainer() ? value.toString() : value.asString();
    }

    public static Integer integer(JsonNode parent, String key) {
        return convert(node(parent, key), Integer.class);
    }

    public static Long longValue(JsonNode parent, String key) {
        return convert(node(parent, key), Long.class);
    }

    public static Double doubleValue(JsonNode parent, String key) {
        return convert(node(parent, key), Double.class);
    }

    public static BigDecimal decimal(JsonNode parent, String key) {
        return convert(node(parent, key), BigDecimal.class);
    }

    public static Boolean bool(JsonNode parent, String key) {
        return convert(node(parent, key), Boolean.class);
    }

    public static int intOrZero(JsonNode parent, String key) {
        return intOrZero(parent, key, 0);
    }

    public static int intOrZero(JsonNode parent, String key, int defaultValue) {
        Integer value = integer(parent, key);
        return value == null ? defaultValue : value;
    }

    public static long longOrZero(JsonNode parent, String key) {
        Long value = longValue(parent, key);
        return value == null ? 0L : value;
    }

    public static <T> T convert(JsonNode value, Class<T> type) {
        value = nullable(value);
        if (value == null) return null;
        if (value.isString() && type != String.class && type != JsonNode.class) {
            String text = value.asString();
            if (text.isEmpty()) return null;
            if (text.startsWith("{") || text.startsWith("[")) return readValue(text, type);
        }
        return MAPPER.convertValue(value, type);
    }

    public static <T> T convert(JsonNode parent, String key, Class<T> type) {
        return convert(node(parent, key), type);
    }

    public static <T> T convert(JsonNode parent, int index, Class<T> type) {
        return convert(node(parent, index), type);
    }

    public static <T> List<T> list(JsonNode parent, String key, Class<T> type) {
        JsonNode value = node(parent, key);
        if (value == null) return null;
        if (value.isString()) return readList(value.asString(), type);
        return MAPPER.convertValue(value,
                MAPPER.getTypeFactory().constructCollectionType(List.class, type));
    }

    private static JsonNode nullable(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode() ? null : value;
    }

    private static JsonNode containerValue(JsonNode value) {
        value = nullable(value);
        if (value != null && value.isString()) {
            String text = value.asString().stripLeading();
            if (text.startsWith("{") || text.startsWith("[")) return readTree(text);
        }
        return value;
    }
}
