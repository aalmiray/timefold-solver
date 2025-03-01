package ai.timefold.solver.core.impl.localsearch.decider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.score.Score;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import ai.timefold.solver.core.impl.heuristic.selector.move.MoveSelector;
import ai.timefold.solver.core.impl.heuristic.thread.ApplyStepOperation;
import ai.timefold.solver.core.impl.heuristic.thread.DestroyOperation;
import ai.timefold.solver.core.impl.heuristic.thread.MoveEvaluationOperation;
import ai.timefold.solver.core.impl.heuristic.thread.MoveThreadOperation;
import ai.timefold.solver.core.impl.heuristic.thread.MoveThreadRunner;
import ai.timefold.solver.core.impl.heuristic.thread.OrderByMoveIndexBlockingQueue;
import ai.timefold.solver.core.impl.heuristic.thread.SetupOperation;
import ai.timefold.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import ai.timefold.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.timefold.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import ai.timefold.solver.core.impl.score.director.InnerScoreDirector;
import ai.timefold.solver.core.impl.solver.scope.SolverScope;
import ai.timefold.solver.core.impl.solver.termination.Termination;
import ai.timefold.solver.core.impl.solver.thread.ThreadUtils;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
public class MultiThreadedLocalSearchDecider<Solution_> extends LocalSearchDecider<Solution_> {

    protected final ThreadFactory threadFactory;
    protected final int moveThreadCount;
    protected final int selectedMoveBufferSize;

    protected boolean assertStepScoreFromScratch = false;
    protected boolean assertExpectedStepScore = false;
    protected boolean assertShadowVariablesAreNotStaleAfterStep = false;

    protected BlockingQueue<MoveThreadOperation<Solution_>> operationQueue;
    protected OrderByMoveIndexBlockingQueue<Solution_> resultQueue;
    protected CyclicBarrier moveThreadBarrier;
    protected ExecutorService executor;
    protected List<MoveThreadRunner<Solution_, ?>> moveThreadRunnerList;

    public MultiThreadedLocalSearchDecider(String logIndentation, Termination<Solution_> termination,
            MoveSelector<Solution_> moveSelector, Acceptor<Solution_> acceptor, LocalSearchForager<Solution_> forager,
            ThreadFactory threadFactory, int moveThreadCount, int selectedMoveBufferSize) {
        super(logIndentation, termination, moveSelector, acceptor, forager);
        this.threadFactory = threadFactory;
        this.moveThreadCount = moveThreadCount;
        this.selectedMoveBufferSize = selectedMoveBufferSize;
    }

    public void setAssertStepScoreFromScratch(boolean assertStepScoreFromScratch) {
        this.assertStepScoreFromScratch = assertStepScoreFromScratch;
    }

    public void setAssertExpectedStepScore(boolean assertExpectedStepScore) {
        this.assertExpectedStepScore = assertExpectedStepScore;
    }

    public void setAssertShadowVariablesAreNotStaleAfterStep(boolean assertShadowVariablesAreNotStaleAfterStep) {
        this.assertShadowVariablesAreNotStaleAfterStep = assertShadowVariablesAreNotStaleAfterStep;
    }

    @Override
    public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseStarted(phaseScope);
        // Capacity: number of moves in circulation + number of setup xor step operations + number of destroy operations
        operationQueue = new ArrayBlockingQueue<>(selectedMoveBufferSize + moveThreadCount + moveThreadCount);
        // Capacity: number of moves in circulation + number of exception handling results
        resultQueue = new OrderByMoveIndexBlockingQueue<>(selectedMoveBufferSize + moveThreadCount);
        moveThreadBarrier = new CyclicBarrier(moveThreadCount);
        InnerScoreDirector<Solution_, ?> scoreDirector = phaseScope.getScoreDirector();
        executor = createThreadPoolExecutor();
        moveThreadRunnerList = new ArrayList<>(moveThreadCount);
        for (int moveThreadIndex = 0; moveThreadIndex < moveThreadCount; moveThreadIndex++) {
            MoveThreadRunner<Solution_, ?> moveThreadRunner = new MoveThreadRunner<>(
                    logIndentation, moveThreadIndex, true,
                    operationQueue, resultQueue, moveThreadBarrier,
                    assertMoveScoreFromScratch, assertExpectedUndoMoveScore,
                    assertStepScoreFromScratch, assertExpectedStepScore, assertShadowVariablesAreNotStaleAfterStep);
            moveThreadRunnerList.add(moveThreadRunner);
            executor.submit(moveThreadRunner);
            operationQueue.add(new SetupOperation<>(scoreDirector));
        }
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
        super.phaseEnded(phaseScope);
        // Tell the move thread runners to stop
        // Don't clear the operationsQueue to avoid moveThreadBarrier deadlock:
        // The MoveEvaluationOperations are already cleared and the new ApplyStepOperation isn't added yet.
        DestroyOperation<Solution_> destroyOperation = new DestroyOperation<>();
        for (int i = 0; i < moveThreadCount; i++) {
            operationQueue.add(destroyOperation);
        }
        shutdownMoveThreads();
        long childThreadsScoreCalculationCount = 0;
        for (MoveThreadRunner<Solution_, ?> moveThreadRunner : moveThreadRunnerList) {
            childThreadsScoreCalculationCount += moveThreadRunner.getCalculationCount();
        }
        phaseScope.addChildThreadsScoreCalculationCount(childThreadsScoreCalculationCount);
        operationQueue = null;
        resultQueue = null;
        moveThreadRunnerList = null;
    }

    @Override
    public void solvingError(SolverScope<Solution_> solverScope, Exception exception) {
        super.solvingError(solverScope, exception);
        shutdownMoveThreads();
    }

    protected ExecutorService createThreadPoolExecutor() {
        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(moveThreadCount,
                threadFactory);
        if (threadPoolExecutor.getMaximumPoolSize() < moveThreadCount) {
            throw new IllegalStateException(
                    "The threadPoolExecutor's maximumPoolSize (" + threadPoolExecutor.getMaximumPoolSize()
                            + ") is less than the moveThreadCount (" + moveThreadCount + "), this is unsupported.");
        }
        return threadPoolExecutor;
    }

    @Override
    public void decideNextStep(LocalSearchStepScope<Solution_> stepScope) {
        int stepIndex = stepScope.getStepIndex();
        resultQueue.startNextStep(stepIndex);

        int selectMoveIndex = 0;
        int movesInPlay = 0;
        Iterator<Move<Solution_>> moveIterator = moveSelector.iterator();
        do {
            boolean hasNextMove = moveIterator.hasNext();
            // First fill the buffer so move evaluation can run freely in parallel
            // For reproducibility, the selectedMoveBufferSize always need to be entirely selected,
            // even if some of those moves won't end up being evaluated or foraged
            if (movesInPlay > 0 && (selectMoveIndex >= selectedMoveBufferSize || !hasNextMove)) {
                if (forageResult(stepScope, stepIndex)) {
                    break;
                }
                movesInPlay--;
            }
            if (hasNextMove) {
                Move<Solution_> move = moveIterator.next();
                operationQueue.add(new MoveEvaluationOperation<>(stepIndex, selectMoveIndex, move));
                selectMoveIndex++;
                movesInPlay++;
            }
        } while (movesInPlay > 0);

        // Do not evaluate the remaining selected moves for this step that haven't started evaluation yet
        operationQueue.clear();
        pickMove(stepScope);
        // Start doing the step on every move thread. Don't wait for the stepEnded() event.
        if (stepScope.getStep() != null) {
            InnerScoreDirector<Solution_, ?> scoreDirector = stepScope.getScoreDirector();
            if (scoreDirector.requiresFlushing() && stepIndex % 100 == 99) {
                // Calculate score to process changes; otherwise they become a memory leak.
                // We only do it occasionally, as score calculation is a performance cost we do not need to incur here.
                scoreDirector.calculateScore();
            }
            // Increase stepIndex by 1, because it's a preliminary action
            ApplyStepOperation<Solution_, ?> stepOperation =
                    new ApplyStepOperation<>(stepIndex + 1, stepScope.getStep(), (Score) stepScope.getScore());
            for (int i = 0; i < moveThreadCount; i++) {
                operationQueue.add(stepOperation);
            }
        }
    }

    private boolean forageResult(LocalSearchStepScope<Solution_> stepScope, int stepIndex) {
        OrderByMoveIndexBlockingQueue.MoveResult<Solution_> result;
        try {
            result = resultQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
        if (stepIndex != result.getStepIndex()) {
            throw new IllegalStateException("Impossible situation: the solverThread's stepIndex (" + stepIndex
                    + ") differs from the result's stepIndex (" + result.getStepIndex() + ").");
        }
        Move<Solution_> foragingMove = result.getMove().rebase(stepScope.getScoreDirector());
        int foragingMoveIndex = result.getMoveIndex();
        LocalSearchMoveScope<Solution_> moveScope = new LocalSearchMoveScope<>(stepScope, foragingMoveIndex, foragingMove);
        if (!result.isMoveDoable()) {
            logger.trace("{}        Move index ({}) not doable, ignoring move ({}).",
                    logIndentation, foragingMoveIndex, foragingMove);
        } else {
            moveScope.setScore(result.getScore());
            // Every doable move result represents a single score calculation on a move thread.
            moveScope.getScoreDirector().incrementCalculationCount();
            boolean accepted = acceptor.isAccepted(moveScope);
            moveScope.setAccepted(accepted);
            logger.trace("{}        Move index ({}), score ({}), accepted ({}), move ({}).",
                    logIndentation,
                    foragingMoveIndex, moveScope.getScore(), moveScope.getAccepted(),
                    foragingMove);
            forager.addMove(moveScope);
            if (forager.isQuitEarly()) {
                return true;
            }
        }
        stepScope.getPhaseScope().getSolverScope().checkYielding();
        return termination.isPhaseTerminated(stepScope.getPhaseScope());
    }

    private void shutdownMoveThreads() {
        if (executor != null && !executor.isShutdown()) {
            ThreadUtils.shutdownAwaitOrKill(executor, logIndentation, "Multi-threaded Local Search");
        }
    }
}
