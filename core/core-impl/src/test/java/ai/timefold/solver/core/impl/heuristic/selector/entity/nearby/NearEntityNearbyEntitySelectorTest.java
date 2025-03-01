package ai.timefold.solver.core.impl.heuristic.selector.entity.nearby;

import static ai.timefold.solver.core.impl.heuristic.selector.SelectorTestUtils.mockEntitySelector;
import static ai.timefold.solver.core.impl.heuristic.selector.SelectorTestUtils.mockReplayingEntitySelector;
import static ai.timefold.solver.core.impl.heuristic.selector.SelectorTestUtils.phaseStarted;
import static ai.timefold.solver.core.impl.heuristic.selector.SelectorTestUtils.solvingStarted;
import static ai.timefold.solver.core.impl.heuristic.selector.SelectorTestUtils.stepStarted;
import static ai.timefold.solver.core.impl.testdata.util.PlannerAssert.assertCodesOfNeverEndingOfEntitySelector;
import static ai.timefold.solver.core.impl.testdata.util.PlannerAssert.verifyPhaseLifecycle;
import static ai.timefold.solver.core.impl.testdata.util.PlannerTestUtils.mockScoreDirector;

import ai.timefold.solver.core.api.score.buildin.simple.SimpleScore;
import ai.timefold.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import ai.timefold.solver.core.impl.heuristic.selector.entity.EntitySelector;
import ai.timefold.solver.core.impl.heuristic.selector.entity.mimic.MimicReplayingEntitySelector;
import ai.timefold.solver.core.impl.phase.scope.AbstractPhaseScope;
import ai.timefold.solver.core.impl.phase.scope.AbstractStepScope;
import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;
import ai.timefold.solver.core.impl.testdata.domain.TestdataEntity;
import ai.timefold.solver.core.impl.testdata.domain.TestdataSolution;
import ai.timefold.solver.core.impl.testutil.TestNearbyRandom;
import ai.timefold.solver.core.impl.testutil.TestRandom;

import org.junit.jupiter.api.Test;

class NearEntityNearbyEntitySelectorTest {

    @Test
    void randomSelection() {
        final TestdataEntity morocco = new TestdataEntity("Morocco");
        final TestdataEntity spain = new TestdataEntity("Spain");
        final TestdataEntity australia = new TestdataEntity("Australia");
        final TestdataEntity brazil = new TestdataEntity("Brazil");

        EntitySelector<TestdataSolution> childEntitySelector = mockEntitySelector(TestdataEntity.buildEntityDescriptor(),
                morocco, spain, australia, brazil);
        NearbyDistanceMeter<TestdataEntity, TestdataEntity> meter = (origin, destination) -> {
            if (origin == morocco) {
                if (destination == morocco) {
                    return 0.0;
                } else if (destination == spain) {
                    return 1.0;
                } else if (destination == australia) {
                    return 100.0;
                } else if (destination == brazil) {
                    return 50.0;
                } else {
                    throw new IllegalStateException("The destination (" + destination + ") is not implemented.");
                }
            } else if (origin == spain) {
                if (destination == morocco) {
                    return 1.0;
                } else if (destination == spain) {
                    return 0.0;
                } else if (destination == australia) {
                    return 101.0;
                } else if (destination == brazil) {
                    return 51.0;
                } else {
                    throw new IllegalStateException("The destination (" + destination + ") is not implemented.");
                }
            } else if (origin == australia) {
                if (destination == morocco) {
                    return 100.0;
                } else if (destination == spain) {
                    return 101.0;
                } else if (destination == australia) {
                    return 0.0;
                } else if (destination == brazil) {
                    return 60.0;
                } else {
                    throw new IllegalStateException("The destination (" + destination + ") is not implemented.");
                }
            } else if (origin == brazil) {
                if (destination == morocco) {
                    return 55.0;
                } else if (destination == spain) {
                    return 53.0;
                } else if (destination == australia) {
                    return 61.0;
                } else if (destination == brazil) {
                    return 0.0;
                } else {
                    throw new IllegalStateException("The destination (" + destination + ") is not implemented.");
                }
            } else {
                throw new IllegalStateException("The origin (" + origin + ") is not implemented.");
            }
        };

        MimicReplayingEntitySelector<TestdataSolution> mimicReplayingEntitySelector =
                // The last entity () is not used, it just makes the selector appear never ending.
                mockReplayingEntitySelector(TestdataEntity.buildEntityDescriptor(), morocco, spain, australia, brazil, morocco);

        NearEntityNearbyEntitySelector<TestdataSolution> entitySelector = new NearEntityNearbyEntitySelector<>(
                childEntitySelector, mimicReplayingEntitySelector, meter, new TestNearbyRandom(), true);

        TestRandom workingRandom = new TestRandom(0, 1, 2, 0);

        InnerScoreDirector<TestdataSolution, SimpleScore> scoreDirector =
                mockScoreDirector(TestdataSolution.buildSolutionDescriptor());
        SolverScope<TestdataSolution> solverScope = solvingStarted(entitySelector, scoreDirector, workingRandom);
        AbstractPhaseScope<TestdataSolution> phaseScopeA = phaseStarted(entitySelector, solverScope);
        AbstractStepScope<TestdataSolution> stepScopeA1 = stepStarted(entitySelector, phaseScopeA);
        assertCodesOfNeverEndingOfEntitySelector(entitySelector, childEntitySelector.getSize() - 1,
                "Spain", "Brazil", "Spain", "Spain");
        entitySelector.stepEnded(stepScopeA1);
        entitySelector.phaseEnded(phaseScopeA);
        entitySelector.solvingEnded(solverScope);

        verifyPhaseLifecycle(childEntitySelector, 1, 1, 1);
    }
}
