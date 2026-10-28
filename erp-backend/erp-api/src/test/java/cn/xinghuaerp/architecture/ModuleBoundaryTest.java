package cn.xinghuaerp.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

/** 随后端门禁运行，按实际源文件归属校验模块边界，不依赖包名前缀猜测。 */
class ModuleBoundaryTest {
    private static final Set<String> PUBLIC_SECURITY = Set.of(
        "cn.xinghuaerp.system.service.AiAgentPermissionService",
        "cn.xinghuaerp.system.service.BusinessAccessService",
        "cn.xinghuaerp.system.service.BusinessSubjectScopeService",
        "cn.xinghuaerp.system.service.BusinessSubjectValidator",
        "cn.xinghuaerp.system.service.SalesScope");
    @Test void domainModulesUsePublicContractsAndOwnTheirMappers() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("erp-backend/pom.xml"))) {
            root = root.getParent(); assertNotNull(root,"找不到项目根目录");
        }
        var owners = new HashMap<String,String>();
        var sources = new HashMap<Path,String>();
        try (var modules = Files.list(root.resolve("erp-backend"))) {
            for (Path module : modules.filter(Files::isDirectory).toList()) {
                Path java = module.resolve("src/main/java"); if(!Files.exists(java)) continue;
                try (var files = Files.walk(java)) {
                    for(Path file: files.filter(f -> f.toString().endsWith(".java")).toList()) {
                        String source = Files.readString(file).replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", "");
                        sources.put(file,source);
                        var pkg = Pattern.compile("package ([\\w.]+);").matcher(source);
                        if(pkg.find()) owners.put(pkg.group(1)+"."+file.getFileName().toString().replace(".java",""), module.getFileName().toString());
                    }
                }
            }
        }
        assertTrue(owners.containsKey("cn.xinghuaerp.inventory.mapper.OGoodsInventoryMapper"), "必须扫描到实际 Mapper");
        assertTrue(sources.size() > 100, "架构校验必须覆盖所有业务模块源码");
        var violations = new ArrayList<String>();
        for(var entry:sources.entrySet()) {
            String module = root.resolve("erp-backend").relativize(entry.getKey()).getName(0).toString();
            var refs=Pattern.compile("cn\\.xinghuaerp\\.[\\w.]+").matcher(entry.getValue());
            while(refs.find()) {
                String ref=refs.group(); String owner=owners.get(ref);
                if(owner==null || owner.equals(module)) continue;
                boolean mapper=ref.contains(".mapper.");
                boolean internalService=ref.contains(".service.") && !module.equals("erp-api") && !PUBLIC_SECURITY.contains(ref);
                if(mapper || internalService) violations.add(entry.getKey().getFileName()+" -> "+ref);
            }
            if(entry.getKey().toString().contains("facade") && entry.getValue().matches("(?s).*interface .*extends (?:IService|BaseMapper).*"))
                violations.add(entry.getKey()+" 暴露持久化基类");
        }
        assertTrue(violations.isEmpty(),String.join("\n",violations));
    }
}
