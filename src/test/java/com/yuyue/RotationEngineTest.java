package com.yuyue;

import com.yuyue.common.Constants;
import com.yuyue.config.EloProperties;
import com.yuyue.engine.EloCalculator;
import com.yuyue.engine.RotationEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationEngineTest {

    private final RotationEngine engine = new RotationEngine(new EloCalculator(new EloProperties()));

    private List<RotationEngine.RotationPlayer> players(int males, int females) {
        List<RotationEngine.RotationPlayer> list = new ArrayList<>();
        for (int i = 1; i <= males; i++) {
            list.add(new RotationEngine.RotationPlayer((long) i, "男" + i, Constants.GENDER_MALE, 1000 + i * 20));
        }
        for (int j = 1; j <= females; j++) {
            long id = males + j;
            list.add(new RotationEngine.RotationPlayer(id, "女" + j, Constants.GENDER_FEMALE, 1000 + j * 15));
        }
        return list;
    }

    @Test
    void mixedDoublesEveryCourtHasOneMaleAndOneFemalePerTeam() {
        RotationEngine.RotationPlan plan = engine.plan(players(6, 6), 2, 3,
                Constants.FORMAT_MEN_DOUBLES, true, Constants.GENDER_RULE_MIXED);

        assertEquals(3, plan.rotation().size());
        for (RotationEngine.RotationRound round : plan.rotation()) {
            assertEquals(2, round.matches().size());
            for (RotationEngine.CourtMatch m : round.matches()) {
                assertEquals(Constants.FORMAT_MIXED_DOUBLES, m.format());
                assertEquals(1, males(m.teamA(), 6), "A 队应一男一女");
                assertEquals(1, males(m.teamB(), 6), "B 队应一男一女");
                assertTrue(Math.abs(m.expectedA() - 0.5) < 0.2,
                        "期望胜率应接近 50%，实际 " + m.expectedA());
            }
            // 同一轮没人上两片场地
            long distinct = round.matches().stream()
                    .flatMap(m -> java.util.stream.Stream.concat(m.teamA().stream(), m.teamB().stream()))
                    .distinct().count();
            assertEquals(8, distinct);
        }
    }

    @Test
    void menDoublesOnlyUsesMales() {
        RotationEngine.RotationPlan plan = engine.plan(players(8, 2), 2, 2,
                Constants.FORMAT_MEN_DOUBLES, true, Constants.GENDER_RULE_MEN);
        for (RotationEngine.RotationRound round : plan.rotation()) {
            for (RotationEngine.CourtMatch m : round.matches()) {
                assertEquals(Constants.FORMAT_MEN_DOUBLES, m.format());
                assertEquals(2, males(m.teamA(), 8));
                assertEquals(2, males(m.teamB(), 8));
            }
        }
    }

    @Test
    void playCountsAreBalanced() {
        RotationEngine.RotationPlan plan = engine.plan(players(5, 5), 2, 4,
                Constants.FORMAT_MEN_DOUBLES, true, Constants.GENDER_RULE_ANY);
        int min = plan.stats().stream().mapToInt(RotationEngine.PlayerStat::playCount).min().orElseThrow();
        int max = plan.stats().stream().mapToInt(RotationEngine.PlayerStat::playCount).max().orElseThrow();
        assertTrue(max - min <= 1, "上场次数差应 ≤ 1，实际 " + min + "~" + max);
    }

    private int males(List<Long> ids, int maleCount) {
        int n = 0;
        for (Long id : ids) {
            if (id <= maleCount) {
                n++;
            }
        }
        return n;
    }
}
