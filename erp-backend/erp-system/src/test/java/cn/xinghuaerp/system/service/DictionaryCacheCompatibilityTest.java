package cn.xinghuaerp.system.service;

import org.junit.jupiter.api.AfterEach;
import cn.xinghuaerp.common.config.JacksonJsonRedisSerializer;
import cn.xinghuaerp.common.config.RedisCache;
import cn.xinghuaerp.system.mapper.SysDictDataMapper;
import cn.xinghuaerp.system.entity.SysDictData;
import cn.xinghuaerp.system.util.DictUtils;
import cn.xinghuaerp.system.service.impl.SysDictTypeServiceImpl;
import cn.xinghuaerp.utils.SpringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DictionaryCacheCompatibilityTest {
    private final MemoryCache cache = new MemoryCache();
    private Object oldBeanFactory;

    @BeforeEach
    void setup() throws Exception {
        var field = SpringUtils.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        oldBeanFactory = field.get(null);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("redisCache", cache);
        new SpringUtils().postProcessBeanFactory(beans);
    }

    @AfterEach
    void restoreBeanFactory() throws Exception {
        var field = SpringUtils.class.getDeclaredField("beanFactory");
        field.setAccessible(true);
        field.set(null, oldBeanFactory);
    }

    @Test
    void bothDictionaryUtilitiesReadOldNumericSuffixesAndNewTypedLists() {
        cache.raw("enabled", """
                [{"@type":"cn.xinghuaerp.system.entity.SysDictData","dictCode":6L,"dictSort":1L,
                  "dictLabel":"正常","dictValue":"0","params":{"@type":"java.util.HashMap"}}]
                """);
        assertDictionary(DictUtils.getDictCache("enabled"));
        assertDictionary(cn.xinghuaerp.system.util.DictUtils.getDictCache("enabled"));
        List<SysDictData> old = DictUtils.getDictCache("enabled");
        DictUtils.setDictCache("enabled", new ArrayList<>(old));
        assertDictionary(DictUtils.getDictCache("enabled"));
        assertDictionary(cn.xinghuaerp.system.util.DictUtils.getDictCache("enabled"));
        assertTrue(new String(cache.values.get("sys_dict:enabled"), StandardCharsets.UTF_8).contains("@jsonType"));
        assertEquals("正常", DictUtils.getDictLabel("enabled", "0"));
    }

    @Test
    void untypedLegacyMapsAreConvertedWithoutJavaToStringParsing() {
        cache.raw("enabled", "[{\"dictCode\":6L,\"dictSort\":1L,\"dictLabel\":\"正常\",\"dictValue\":\"0\"}]");
        assertDictionary(DictUtils.getDictCache("enabled"));
        assertNull(DictUtils.getDictCache("missing"));
    }

    @Test
    void damagedDictionaryCacheIsRebuiltFromDatabaseAndThenReused() throws Exception {
        cache.raw("enabled", "{\"dictCode\":6L broken}");
        var mapper = mock(SysDictDataMapper.class);
        var dict = new SysDictData();
        dict.setDictCode(6L);
        dict.setDictSort(1L);
        dict.setDictLabel("正常");
        dict.setDictValue("0");
        when(mapper.selectDictDataByType("enabled")).thenReturn(new ArrayList<>(List.of(dict)));
        var service = new SysDictTypeServiceImpl();
        var field = SysDictTypeServiceImpl.class.getDeclaredField("dictDataMapper");
        field.setAccessible(true);
        field.set(service, mapper);
        assertDictionary(service.selectDictDataByType("enabled"));
        assertDictionary(service.selectDictDataByType("enabled"));
        verify(mapper, times(1)).selectDictDataByType("enabled");
        assertEquals(1, cache.writes);
    }

    private void assertDictionary(List<SysDictData> values) {
        assertNotNull(values);
        assertEquals(1, values.size());
        assertEquals(6L, values.get(0).getDictCode());
        assertEquals(1L, values.get(0).getDictSort());
        assertEquals("正常", values.get(0).getDictLabel());
    }

    private static class MemoryCache extends RedisCache {
        private final Map<String, byte[]> values = new HashMap<>();
        private final JacksonJsonRedisSerializer serializer = new JacksonJsonRedisSerializer();
        private int writes;

        void raw(String key, String json) { values.put("sys_dict:" + key, json.getBytes(StandardCharsets.UTF_8)); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getCacheObject(String key) { return (T) serializer.deserialize(values.get(key)); }

        @Override
        public <T> void setCacheObject(String key, T value) {
            values.put(key, serializer.serialize(value));
            writes++;
        }
    }
}
