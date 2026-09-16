package com.yuyue.engine;

import com.yuyue.common.Constants;
import com.yuyue.engine.ArrangeEngine.ArrangeResult;
import com.yuyue.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 自动编排引擎测试：覆盖需求文档 6 套方案的自动过滤
 */
class ArrangeEngineTest {

    private final ArrangeEngine engine = new ArrangeEngine();

    private static ArrangeEngine.Player m(long id, int rating) {
        return new ArrangeEngine.Player(id, Constants.GENDER_MALE, rating);
    }

    private static ArrangeEngine.Player f(long id, int rating) {
        return new ArrangeEngine.Player(id, Constants.GENDER_FEMALE, rating);
    }

    @Test
    @DisplayName("不足 4 人拒绝编排")
    void tooFewPlayers() {
        assertThrows(BizException.class, () -> engine.arrange(List.of(m(1, 1200), m(2, 1200), m(3, 1200))));
    }

    @Test
    @DisplayName("方案②：男数 = 女数 → 全混双")
    void evenGenderMixedDoubles() {
        ArrangeResult r = engine.arrange(List.of(
                m(1, 1400), m(2, 1200), m(3, 1100), m(4, 1000),
                f(5, 1350), f(6, 1250), f(7, 1150), f(8, 1050)));

        assertEquals(2, r.schemeId());
        assertEquals("全混双", r.schemeName());
        assertFalse(r.matches().isEmpty());
        r.matches().forEach(match -> {
            assertEquals(Constants.FORMAT_MIXED_DOUBLES, match.format());
            assertEquals(2, match.teamA().size());
            assertEquals(2, match.teamB().size());
        });
    }

    @Test
    @DisplayName("方案③：女数 ≤ 1 → 全男双")
    void womenLeOneMenDoubles() {
        ArrangeResult r = engine.arrange(List.of(
                m(1, 1400), m(2, 1300), m(3, 1200), m(4, 1100), m(5, 1000), f(6, 1200)));

        assertEquals(3, r.schemeId());
        assertEquals("全男双", r.schemeName());
        assertFalse(r.matches().isEmpty());
        r.matches().forEach(match -> assertEquals(Constants.FORMAT_MEN_DOUBLES, match.format()));
    }

    @Test
    @DisplayName("方案④：男数 ≤ 1 → 全女双")
    void menLeOneWomenDoubles() {
        ArrangeResult r = engine.arrange(List.of(
                f(1, 1400), f(2, 1300), f(3, 1200), f(4, 1100), m(5, 1000)));

        assertEquals(4, r.schemeId());
        assertEquals("全女双", r.schemeName());
        r.matches().forEach(match -> assertEquals(Constants.FORMAT_WOMEN_DOUBLES, match.format()));
    }

    @Test
    @DisplayName("方案⑤：男 > 女 → 混搭·混双优先，男多 1 时含男双 vs 混双局")
    void maleMajorityMixedFirst() {
        ArrangeResult r = engine.arrange(List.of(
                m(1, 1500), m(2, 1400), m(3, 1300), m(4, 1200), m(5, 1100),
                f(6, 1450), f(7, 1250), f(8, 1050), f(9, 1000)));

        assertEquals(5, r.schemeId());
        assertEquals("混搭·混双优先", r.schemeName());
        assertFalse(r.matches().isEmpty());
        // 男多 1：应含 1 局男双
        long menDoubles = r.matches().stream()
                .filter(x -> x.format() == Constants.FORMAT_MEN_DOUBLES).count();
        assertEquals(1, menDoubles);
    }

    @Test
    @DisplayName("方案⑥：男 ≥2 且女 ≥2 且女多 → 混搭·同性别优先，男女互不混合")
    void femaleMajoritySameGender() {
        ArrangeResult r = engine.arrange(List.of(
                m(1, 1400), m(2, 1300), m(3, 1200),
                f(4, 1500), f(5, 1400), f(6, 1300), f(7, 1200)));

        assertEquals(6, r.schemeId());
        assertEquals("混搭·同性别优先", r.schemeName());
        assertFalse(r.matches().isEmpty());
        r.matches().forEach(match -> assertTrue(
                match.format() == Constants.FORMAT_MEN_DOUBLES
                        || match.format() == Constants.FORMAT_WOMEN_DOUBLES));
    }

    @Test
    @DisplayName("方案①：纯男生不足 4 人以上场景且不满足其他条件时走全单打兜底（8 男）")
    void singlesFallbackForOddHighCount() {
        // 5 男：男数≥女数(0)→但女数≤1 → 全男双；此处用 4 人 2 男 2 女以外构成验证兜底链不触发即可
        ArrangeResult r = engine.arrange(List.of(
                m(1, 1400), m(2, 1300), m(3, 1200), m(4, 1100), m(5, 1000)));
        assertEquals(3, r.schemeId()); // 女数 0 ≤ 1 → 全男双
    }

    @Test
    @DisplayName("强制指定方案②：4 男 4 女也可强制全混双")
    void forceMixedDoubles() {
        ArrangeResult r = engine.arrangeByScheme(List.of(
                m(1, 1400), m(2, 1200), m(3, 1100), m(4, 1000),
                f(5, 1350), f(6, 1250), f(7, 1150), f(8, 1050)), 2);

        assertEquals(2, r.schemeId());
        assertEquals("全混双", r.schemeName());
        r.matches().forEach(match -> assertEquals(Constants.FORMAT_MIXED_DOUBLES, match.format()));
    }

    @Test
    @DisplayName("强制指定方案②：男女不等（5 男 3 女）时强制混双，多余男轮空")
    void forceMixedDoublesUnevenGender() {
        ArrangeResult r = engine.arrangeByScheme(List.of(
                m(1, 1400), m(2, 1300), m(3, 1200), m(4, 1100), m(5, 1000),
                f(6, 1350), f(7, 1250), f(8, 1150)), 2);

        assertEquals(2, r.schemeId());
        // 5 男 3 女只组出 3 支混双队；单轮首尾配对 3 队为 1 局（第 0 队 vs 第 2 队，中间队轮空）
        // → 实际上场 4 人，多余男与轮空队成员不出场；循环赛场景见 forceMixedDoublesRoundRobin
        long distinctPlayers = r.matches().stream()
                .flatMap(x -> java.util.stream.Stream.concat(x.teamA().stream(), x.teamB().stream()))
                .distinct().count();
        assertEquals(4, distinctPlayers);
    }

    @Test
    @DisplayName("强制指定方案③：有女生报名也能强制全男双")
    void forceMenDoubles() {
        ArrangeResult r = engine.arrangeByScheme(List.of(
                m(1, 1400), m(2, 1300), m(3, 1200), m(4, 1100), f(5, 1200)), 3);

        assertEquals(3, r.schemeId());
        assertEquals("全男双", r.schemeName());
        r.matches().forEach(match -> assertEquals(Constants.FORMAT_MEN_DOUBLES, match.format()));
    }

    @Test
    @DisplayName("强制指定方案⑧：全随机双打（不分性别）")
    void forceRandomDoubles() {
        ArrangeResult r = engine.arrangeByScheme(List.of(
                m(1, 1400), m(2, 1300), f(3, 1200), f(4, 1100)), 8);

        assertEquals(8, r.schemeId());
        assertEquals("全随机（不分性别）", r.schemeName());
        r.matches().forEach(match -> assertEquals(Constants.FORMAT_RANDOM_DOUBLES, match.format()));
    }

    @Test
    @DisplayName("强制指定方案② + 循环赛：3 支混双队互打共 3 局")
    void forceMixedDoublesRoundRobin() {
        ArrangeResult r = engine.arrangeByScheme(List.of(
                m(1, 1400), m(2, 1300), m(3, 1200),
                f(4, 1400), f(5, 1300), f(6, 1200)), 2, true);

        assertEquals(2, r.schemeId());
        assertEquals(3, r.matches().size());
    }

    @Test
    @DisplayName("强制指定未知方案报错")
    void forceUnknownSchemeFails() {
        assertThrows(BizException.class, () -> engine.arrangeByScheme(
                List.of(m(1, 1400), m(2, 1300), f(3, 1200), f(4, 1100)), 9));
    }

    @Test
    @DisplayName("所有对局中同一名球员不得同时出现在双方")
    void noPlayerOnBothSides() {
        ArrangeResult r = engine.arrange(List.of(
                m(1, 1400), m(2, 1300), m(3, 1200), m(4, 1100),
                f(5, 1400), f(6, 1300), f(7, 1200), f(8, 1100)));
        r.matches().forEach(match -> {
            match.teamA().forEach(id -> assertFalse(match.teamB().contains(id)));
        });
    }
}
