package cn.xinghuaerp.ai.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

class AiConversationPaginationTest {
    @Test void stableCursorDoesNotSkipEqualTimestampsOrReadOtherUsers() {
        var ds=new JdbcDataSource(); ds.setURL("jdbc:h2:mem:pages;MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc=new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE ai_conversation_session(session_id varchar(64) PRIMARY KEY,user_id bigint)");
        jdbc.execute("CREATE TABLE ai_conversation_history(id bigint PRIMARY KEY,session_id varchar(64),user_id bigint,timestamp bigint,role varchar(20),content varchar(100))");
        jdbc.update("INSERT INTO ai_conversation_session VALUES('mine',7),('other',8)");
        for(int i=1;i<=8;i++)jdbc.update("INSERT INTO ai_conversation_history VALUES(?,'mine',7,100,'user','x')",i);
        jdbc.update("INSERT INTO ai_conversation_history VALUES(99,'other',8,200,'user','secret')");
        var access=new AiSessionAccess(jdbc);
        var page=access.recent("mine",7L,null,null,3);
        assertEquals(java.util.List.of(6L,7L,8L),page.stream().map(x->x.getId()).toList());
        var older=access.recent("mine",7L,100L,6L,3);
        assertEquals(java.util.List.of(3L,4L,5L),older.stream().map(x->x.getId()).toList());
        assertEquals(java.util.List.of("mine"),access.sessions(7L));
        assertThrows(IllegalArgumentException.class,()->access.recent("mine",7L,100L,null,3));
        assertThrows(IllegalArgumentException.class,()->access.recent("mine",7L,null,null,101));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->access.recent("other",7L,null,null,3));
    }
}
