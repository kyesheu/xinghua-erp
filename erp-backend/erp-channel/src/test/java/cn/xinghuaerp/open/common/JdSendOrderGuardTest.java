package cn.xinghuaerp.open.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「京东每个出口都先问限流器」的结构护栏。
 *
 * <p>为什么需要它：{@code PlatformRateLimiterTest} 与 {@code PlatformRateLimitWiringTest} 都是
 * <b>行为</b>断言，它们证明不了「顺序」。实测过：把那 14 处 {@code acquire} 整体挪到
 * {@code client.send} 之后，全部用例依然全绿（限流器仍然被调用，只是等请求发出去之后才调用），
 * 等于这一层保护可以被一次 rebase / 一次格式化静默破坏。
 *
 * <p>做法：直接读 {@code open/jd}（含 {@code jdl} 子包）的源码文本，对每个活跃的
 * {@code client.send(} 断言它<b>之前 10 行内</b>出现过 {@code PlatformRateLimiter.acquire(}。
 * 只读文件、不发请求、不依赖打包产物。
 *
 * <p>被 {@code //} 注释掉的 {@code client.send}（{@code JdWaybillApiHelper} 里那段废弃方法）
 * 不算出口，必须跳过，否则护栏会因为死代码误报。
 */
class JdSendOrderGuardTest {

    /** 向上找多少行内的 acquire 才算「紧邻」。取 10 是为了容下注释与空行，又不至于跨到上一个方法。 */
    private static final int LOOKBACK_LINES = 10;

    /** 当前活跃的京东出口数。新增/删除京东出口时必须同步改这里，并确认新出口也接了限流。 */
    private static final int EXPECTED_ACTIVE_SENDS = 14;

    private static final String ACQUIRE = "PlatformRateLimiter.acquire(";
    private static final String SEND = "client.send(";

    @Test
    void everyActiveJdSendIsPrecededByAcquire() {
        List<Path> sources = jdSources();
        List<String> violations = new ArrayList<>();
        int activeSends = 0;
        boolean sawJdlSubpackage = false;

        for (Path file : sources) {
            if (file.toString().replace('\\', '/').contains("/jdl/")) {
                sawJdlSubpackage = true;
            }
            List<String> lines = readLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isCommentedOut(line) || !line.contains(SEND)) {
                    continue;
                }
                activeSends++;
                if (!hasAcquireBefore(lines, i)) {
                    violations.add(file.getFileName() + ":" + (i + 1) + " → " + line.strip());
                }
            }
        }

        // 子包 jdl 里的云打印出口曾经被 jd/*.java 这种 glob 漏掉，这里显式确认递归walk 覆盖到了
        assertTrue(sawJdlSubpackage, "必须递归扫描 jd/jdl 子包（CloudPrintApiHelper 在那里）");
        assertTrue(violations.isEmpty(),
                "以下京东出口的 client.send 之前没有 PlatformRateLimiter.acquire（限流会被绕过）：\n"
                        + String.join("\n", violations));
        assertEquals(EXPECTED_ACTIVE_SENDS, activeSends,
                "京东活跃出口数与预期不符：若新增出口，请确认它也接了 PlatformRateLimiter 并更新本常量；"
                        + "若变成更少，可能是有人把出口注释掉或删掉了");
    }

    /**
     * 京东换 token 的出口（{@code JdOAuthController}）不在 {@code open/jd} 目录下，
     * 是唯一一处游离在公共 HTTP 客户端之外的京东调用，单独盯住它。
     */
    @Test
    void oauthTokenExchangeIsPrecededByAcquire() {
        Path file = firstExisting(
                Path.of("src", "main", "java", "cn", "xinghuaerp", "platform", "controller", "JdOAuthController.java"),
                Path.of("erp-channel", "src", "main", "java", "cn", "xinghuaerp", "platform", "controller",
                        "JdOAuthController.java"));
        List<String> lines = readLines(file);
        List<String> violations = new ArrayList<>();
        int activeSends = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isCommentedOut(line) || !line.contains(SEND)) {
                continue;
            }
            activeSends++;
            if (!hasAcquireBefore(lines, i)) {
                violations.add(file.getFileName() + ":" + (i + 1) + " → " + line.strip());
            }
        }
        assertEquals(1, activeSends, "JdOAuthController 里应恰好有 1 处 client.send（换 token）");
        assertTrue(violations.isEmpty(), "京东换 token 出口必须先问限流器：" + String.join("\n", violations));
    }

    /** 从 {@code sendLine} 往前看 {@link #LOOKBACK_LINES} 行，找到未被注释的 acquire 才算通过。 */
    private static boolean hasAcquireBefore(List<String> lines, int sendLine) {
        for (int i = sendLine - 1; i >= 0 && i >= sendLine - LOOKBACK_LINES; i--) {
            String previous = lines.get(i);
            if (isCommentedOut(previous)) {
                continue;
            }
            if (previous.contains(ACQUIRE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCommentedOut(String line) {
        String trimmed = line.stripLeading();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
    }

    private static List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 递归列出 open/jd 下的所有 .java；找不到源码目录必须让用例失败，不能静默跳过。 */
    private static List<Path> jdSources() {
        Path dir = jdSourceDir();
        try (Stream<Path> stream = Files.walk(dir)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
            assertTrue(files.size() >= 10, "open/jd 下的 .java 只有 " + files.size() + " 个，路径可能找错了：" + dir);
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 定位源码目录。surefire 的工作目录是模块根（erp-channel），但为了在 IDE／父目录里直接跑
     * 单测也能工作，这里按「模块内 → 仓库内」两个候选顺序尝试。
     */
    private static Path jdSourceDir() {
        return firstExisting(
                Path.of("src", "main", "java", "cn", "xinghuaerp", "open", "jd"),
                Path.of("erp-channel", "src", "main", "java", "cn", "xinghuaerp", "open", "jd"));
    }

    /** 按顺序返回第一个存在的路径；全都不存在时直接失败（护栏不能因为找不到文件就静默通过）。 */
    private static Path firstExisting(Path... candidates) {
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate) || Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("找不到目标源码，已尝试：" + List.of(candidates)
                + "；当前工作目录=" + Path.of("").toAbsolutePath());
    }
}
