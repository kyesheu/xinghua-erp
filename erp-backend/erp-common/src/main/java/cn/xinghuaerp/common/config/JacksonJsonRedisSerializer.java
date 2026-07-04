package cn.xinghuaerp.common.config;

import cn.xinghuaerp.json.JsonUtils;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Jackson 3 cache values, with a restricted reader for existing cache entries. */
public class JacksonJsonRedisSerializer implements RedisSerializer<Object> {
    private static final Set<String> LEGACY_COLLECTION_TYPES = Set.of(
            "java.util.HashMap", "java.util.LinkedHashMap", "java.util.TreeMap",
            "java.util.ArrayList", "java.util.LinkedList", "java.util.HashSet",
            "java.util.LinkedHashSet", "java.util.TreeSet");

    private final GenericJacksonJsonRedisSerializer delegate = GenericJacksonJsonRedisSerializer.builder()
            .typePropertyName("@jsonType")
            .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                    .allowIfSubType("cn.xinghuaerp.")
                    .allowIfSubType("java.util.")
                    .allowIfSubType("java.time.")
                    .allowIfSubType("java.math.")
                    .allowIfSubType(Long.class)
                    .allowIfSubType(Float.class)
                    .allowIfSubType(Short.class)
                    .allowIfSubType(Byte.class)
                    .allowIfSubTypeIsArray()
                    .build())
            .writer((mapper, value) -> mapper.writerFor(Object.class).writeValueAsBytes(value))
            .build();

    @Override
    public byte[] serialize(Object value) throws SerializationException {
        return delegate.serialize(value);
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
        if (bytes == null || bytes.length == 0) return null;
        try {
            return delegate.deserialize(bytes);
        } catch (SerializationException currentFormatFailure) {
            String json = new String(bytes, StandardCharsets.UTF_8);
            if (json.contains("\"@jsonType\"")) throw currentFormatFailure;
            try {
                String trimmed = json.trim();
                if (trimmed.matches("-?\\d+L")) return Long.valueOf(trimmed.substring(0, trimmed.length() - 1));
                Object value = readLegacy(JsonUtils.readTree(normalizeLegacyJson(json)));
                if (trimmed.startsWith("Set[") && value instanceof java.util.List<?> list) {
                    return new java.util.HashSet<>(list);
                }
                return value;
            } catch (Exception legacyFailure) {
                throw new SerializationException("Cannot deserialize cached JSON", legacyFailure);
            }
        }
    }

    private Object readLegacy(JsonNode node) throws ClassNotFoundException {
        if (node == null || node.isNull()) return null;
        if (node.isArray()) {
            var values = new ArrayList<Object>();
            for (JsonNode child : node) values.add(readLegacy(child));
            return values;
        }
        if (!node.isObject()) return JsonUtils.mapper().convertValue(node, Object.class);
        String typeName = JsonUtils.text(node, "@type");
        Map<String, Object> values = new LinkedHashMap<>();
        for (var field : node.properties()) {
            if (!field.getKey().equals("@type")) values.put(field.getKey(), readLegacy(field.getValue()));
        }
        if (typeName == null) return values;
        if (LEGACY_COLLECTION_TYPES.contains(typeName)) return values;
        if (!typeName.startsWith("cn.xinghuaerp.")) {
            throw new SerializationException("Unsupported legacy cache type: " + typeName);
        }
        return JsonUtils.mapper().convertValue(values, Class.forName(typeName));
    }

    /** Normalize Fastjson Set prefixes and integer L suffixes only outside quoted strings. */
    private String normalizeLegacyJson(String json) {
        StringBuilder result = new StringBuilder(json.length());
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (!quoted && (json.startsWith("Set[", i) || json.startsWith("TreeSet[", i))) {
                i += json.startsWith("TreeSet[", i) ? 6 : 2;
                continue;
            }
            if (!quoted && (c == '-' || Character.isDigit(c)) && isValueStart(json, i)) {
                int end = i + (c == '-' ? 1 : 0);
                int digits = end;
                while (end < json.length() && json.charAt(end) >= '0' && json.charAt(end) <= '9') end++;
                if (end > digits && end < json.length() && json.charAt(end) == 'L'
                        && isValueEnd(json, end + 1)) {
                    result.append(json, i, end);
                    i = end;
                    continue;
                }
            }
            result.append(c);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
        }
        return result.toString();
    }

    private boolean isValueStart(String json, int index) {
        if (index == 0) return true;
        char previous = json.charAt(index - 1);
        return Character.isWhitespace(previous) || previous == ':' || previous == ',' || previous == '[';
    }

    private boolean isValueEnd(String json, int index) {
        if (index == json.length()) return true;
        char next = json.charAt(index);
        return Character.isWhitespace(next) || next == ',' || next == ']' || next == '}';
    }
}
