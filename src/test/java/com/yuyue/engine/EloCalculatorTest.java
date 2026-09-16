package com.yuyue.engine;

import com.yuyue.config.EloProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ELO 计算规则测试：覆盖需求文档中的全部场景
 */
class EloCalculatorTest {

    private EloCalculator calculator;

    @BeforeEach
    void setUp() {
        EloProperties props = new EloProperties();
        props.setDefaultRating(1200);
        props.setKNew(40);
        props.setKMid(24);
        props.setKPro(16);
        calculator = new EloCalculator(props);
    }

    private static final EloCalculator.PlayerInput P(Long id, int rating, int games) {
        return new EloCalculator.PlayerInput(id, rating, games);
    }

    @Test
    @DisplayName("势均力敌 1200 vs 1200：E=0.5，K=40 胜 +20 / 负 -20")
    void evenMatch() {
        EloCalculator.TeamResult[] r = calculator.settle(
                List.of(P(1L, 1200, 10)), List.of(P(2L, 1200, 10)), 1);

        assertEquals(0.5, r[0].expected(), 1e-9);
        assertEquals(+20, r[0].players().get(0).delta());
        assertEquals(-20, r[1].players().get(0).delta());
    }

    @Test
    @DisplayName("强打弱 1400 vs 1200：E=0.76，K=40 强胜 +9.6≈+10，强负 -30.4≈-30")
    void strongVsWeak() {
        EloCalculator.TeamResult[] r = calculator.settle(
                List.of(P(1L, 1400, 10)), List.of(P(2L, 1200, 10)), 1);

        assertEquals(0.76, r[0].expected(), 0.005);
        assertEquals(+10, r[0].players().get(0).delta()); // 强胜弱 +9.6 四舍五入
        assertEquals(-10, r[1].players().get(0).delta()); // 弱方失利仅扣 -9.6
    }

    @Test
    @DisplayName("弱胜强 1200 vs 1400：爆冷 +30.4≈+30，强负 -9.6≈-10")
    void upset() {
        EloCalculator.TeamResult[] r = calculator.settle(
                List.of(P(1L, 1200, 10)), List.of(P(2L, 1400, 10)), 1);

        assertEquals(+30, r[0].players().get(0).delta()); // 弱胜强爆冷 +30.4
        assertEquals(-30, r[1].players().get(0).delta()); // 强方爆冷失利 -30.4
    }

    @Test
    @DisplayName("K 因子衰减：≤20 场 40，21-60 场 24，>60 场 16")
    void kFactorDecay() {
        assertEquals(40, calculator.kFactor(0));
        assertEquals(40, calculator.kFactor(20));
        assertEquals(24, calculator.kFactor(21));
        assertEquals(24, calculator.kFactor(60));
        assertEquals(16, calculator.kFactor(61));
    }

    @Test
    @DisplayName("双打：队伍均值算期望，同队共用 E，但各自按自己的 K 更新")
    void doublesSharedExpectedPerPlayerK() {
        // A 队 (1400, 1200) vs B 队 (1200, 1000)，均值 1300 vs 1100
        EloCalculator.TeamResult[] r = calculator.settle(
                List.of(P(1L, 1400, 5), P(2L, 1200, 50)),
                List.of(P(3L, 1200, 5), P(4L, 1000, 50)),
                1);

        // 同队期望一致（均值 1300 vs 1100 → 0.76）
        assertEquals(r[0].players().get(0).expected(), r[0].players().get(1).expected(), 1e-9);

        // 各自按自己的 K：1400 分新手 (K=40) 得 +9.6≈+10，1200 分老手 (K=24) 得 +5.8≈+6
        assertEquals(40, r[0].players().get(0).kFactor());
        assertEquals(24, r[0].players().get(1).kFactor());
        assertEquals(+10, r[0].players().get(0).delta());
        assertEquals(+6, r[0].players().get(1).delta());
    }

    @Test
    @DisplayName("防刷：积分差越大，强胜弱得分越少")
    void antiFarming() {
        EloCalculator.TeamResult[] bigGap = calculator.settle(
                List.of(P(1L, 1800, 10)), List.of(P(2L, 1200, 10)), 1);
        EloCalculator.TeamResult[] smallGap = calculator.settle(
                List.of(P(3L, 1400, 10)), List.of(P(4L, 1200, 10)), 1);

        int bigGapGain = bigGap[0].players().get(0).delta();
        int smallGapGain = smallGap[0].players().get(0).delta();
        assertTrue(bigGapGain < smallGapGain,
                "分差 600 时强方得分应少于分差 200 时: " + bigGapGain + " vs " + smallGapGain);
    }

    @Test
    @DisplayName("非法胜方值抛异常")
    void invalidWinner() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.settle(List.of(P(1L, 1200, 0)), List.of(P(2L, 1200, 0)), 3));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.settle(List.of(), List.of(P(2L, 1200, 0)), 1));
    }
}
