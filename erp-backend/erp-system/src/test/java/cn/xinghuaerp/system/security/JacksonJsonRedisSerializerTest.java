package cn.xinghuaerp.system.security;

import cn.xinghuaerp.common.config.JacksonJsonRedisSerializer;
import cn.xinghuaerp.system.entity.SysDictData;
import cn.xinghuaerp.system.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import cn.xinghuaerp.json.JsonUtils;

class JacksonJsonRedisSerializerTest {
    private final JacksonJsonRedisSerializer serializer = new JacksonJsonRedisSerializer();

    @Test
    void restoresLoginUserAndNestedPermissions() {
        SysUser user = new SysUser();
        user.setUserId(7L);
        user.setUserName("test-user");
        user.setPassword("test-password-hash");
        user.setCreateTime(LocalDateTime.of(2026, 10, 26, 10, 30));
        LoginUser login = new LoginUser(7L, 1L, user, new HashSet<>(Set.of("goods:list")));
        login.setToken("test-token");
        LoginUser restored = assertInstanceOf(LoginUser.class, serializer.deserialize(serializer.serialize(login)));
        assertEquals(7L, restored.getUserId());
        assertEquals("test-user", restored.getUsername());
        assertEquals("test-password-hash", restored.getPassword());
        assertEquals(Set.of("goods:list"), restored.getPermissions());
        assertEquals(user.getCreateTime(), restored.getUser().getCreateTime());
    }

    @Test
    void restoresDictionaryListsAndScalarValues() {
        SysDictData data = new SysDictData();
        data.setDictLabel("Enabled");
        data.setDictValue("1");
        var source = new ArrayList<>(List.of(data));
        List<?> restored = assertInstanceOf(List.class, serializer.deserialize(serializer.serialize(source)));
        assertEquals("Enabled", assertInstanceOf(SysDictData.class, restored.get(0)).getDictLabel());
        assertEquals("text", serializer.deserialize(serializer.serialize("text")));
        assertEquals(42, serializer.deserialize(serializer.serialize(42)));
        assertEquals(42L, serializer.deserialize(serializer.serialize(42L)));
        assertNull(serializer.deserialize(serializer.serialize(null)));
    }

    @Test
    void readsExistingLoginCacheWithoutClearingRedis() {
        String json = """
                {"@type":"cn.xinghuaerp.system.security.LoginUser","userId":7,"deptId":1,
                 "permissions":Set["goods:list"],"token":"existing-token",
                 "user":{"@type":"cn.xinghuaerp.system.entity.SysUser","userId":7,
                         "userName":"existing-user","password":"test-hash","createTime":"2026-10-26 10:30:00"}}
                """;
        LoginUser restored = assertInstanceOf(LoginUser.class,
                serializer.deserialize(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals("existing-user", restored.getUsername());
        assertEquals("test-hash", restored.getPassword());
        assertEquals(Set.of("goods:list"), restored.getPermissions());
        assertEquals(LocalDateTime.of(2026, 10, 26, 10, 30), restored.getUser().getCreateTime());
    }

    @Test
    void readsExistingDictionaryListAndPreservesQuotedSetText() {
        String json = """
                [{"@type":"cn.xinghuaerp.system.entity.SysDictData","dictLabel":"Set[keep]","dictValue":"1"}]
                """;
        List<?> restored = assertInstanceOf(List.class,
                serializer.deserialize(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals("Set[keep]", assertInstanceOf(SysDictData.class, restored.get(0)).getDictLabel());
    }

    @Test
    void rejectsUntrustedCacheTypes() {
        byte[] legacy = "{\"@type\":\"java.lang.Runtime\"}".getBytes(StandardCharsets.UTF_8);
        byte[] current = "{\"@jsonType\":\"java.lang.Runtime\"}".getBytes(StandardCharsets.UTF_8);
        assertThrows(SerializationException.class, () -> serializer.deserialize(legacy));
        assertThrows(SerializationException.class, () -> serializer.deserialize(current));
    }

    @Test
    void readsLegacyRootLongAndSetValues() {
        assertEquals(42L, serializer.deserialize("42L".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Set.of("A", "B"), serializer.deserialize("Set[\"A\",\"B\"]".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsLegacyDictionaryLongSuffixesAtEveryDepth() {
        String json = """
                [{"@type":"cn.xinghuaerp.system.entity.SysDictData","dictCode":6L,"dictSort":1L,
                  "dictLabel":"Keep 6L, Set[ and TreeSet[ unchanged","dictValue":"0",
                  "params":{"@type":"java.util.HashMap","large":9007199254740993L,
                            "nested":[-9223372036854775808L, {"id":42L}]}}]
                """;
        List<?> values = assertInstanceOf(List.class, serializer.deserialize(json.getBytes(StandardCharsets.UTF_8)));
        SysDictData dict = assertInstanceOf(SysDictData.class, values.get(0));
        assertEquals(6L, dict.getDictCode());
        assertEquals(1L, dict.getDictSort());
        assertEquals("Keep 6L, Set[ and TreeSet[ unchanged", dict.getDictLabel());
        assertEquals(9007199254740993L, dict.getParams().get("large"));
        List<?> nested = assertInstanceOf(List.class, dict.getParams().get("nested"));
        assertEquals(Long.MIN_VALUE, nested.get(0));
        SysDictData roundTrip = assertInstanceOf(SysDictData.class,
                ((List<?>) serializer.deserialize(serializer.serialize(values))).get(0));
        assertEquals(dict.getDictCode(), roundTrip.getDictCode());
    }

    @Test
    void supportsNestedLongsAndSetsWithoutChangingQuotedEscapes() {
        String json = """
                {"values":TreeSet[1L,2L],"text":"escaped \\"6L\\" and Set[",
                 "nested":{"id": -42L}}
                """;
        Map<?, ?> values = assertInstanceOf(Map.class, serializer.deserialize(json.getBytes(StandardCharsets.UTF_8)));
        assertEquals("escaped \"6L\" and Set[", values.get("text"));
        assertEquals(List.of(1, 2), values.get("values"));
        assertEquals(-42, ((Map<?, ?>) values.get("nested")).get("id"));
    }

    @Test
    void stillRejectsMalformedNumericTokensAndUntrustedTypesWithLongSuffixes() {
        for (String json : List.of("{\"id\":1.5L}", "{\"id\":1e3L}", "{\"id\":1Lfoo}",
                "{\"id\":01L}", "{\"@type\":\"java.lang.Runtime\",\"id\":1L}")) {
            assertThrows(SerializationException.class, () -> serializer.deserialize(json.getBytes(StandardCharsets.UTF_8)));
        }
        assertThrows(RuntimeException.class, () -> cn.xinghuaerp.json.JsonUtils.readTree("{\"id\":1L}"));
    }
}
