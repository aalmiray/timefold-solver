package ai.timefold.solver.constraint.streams.bavet.uni;

import java.util.Set;

import ai.timefold.solver.constraint.streams.bavet.BavetConstraintFactory;
import ai.timefold.solver.constraint.streams.bavet.common.BavetAbstractConstraintStream;
import ai.timefold.solver.constraint.streams.bavet.common.NodeBuildHelper;
import ai.timefold.solver.constraint.streams.bavet.common.TupleLifecycle;
import ai.timefold.solver.constraint.streams.common.RetrievalSemantics;
import ai.timefold.solver.core.api.score.Score;

public final class BavetForEachUniConstraintStream<Solution_, A> extends BavetAbstractUniConstraintStream<Solution_, A> {

    private final Class<A> forEachClass;

    public BavetForEachUniConstraintStream(BavetConstraintFactory<Solution_> constraintFactory, Class<A> forEachClass,
            RetrievalSemantics retrievalSemantics) {
        super(constraintFactory, retrievalSemantics);
        this.forEachClass = forEachClass;
        if (forEachClass == null) {
            throw new IllegalArgumentException("The forEachClass (null) cannot be null.");
        }
    }

    @Override
    public boolean guaranteesDistinct() {
        return true;
    }

    // ************************************************************************
    // Node creation
    // ************************************************************************

    @Override
    public void collectActiveConstraintStreams(Set<BavetAbstractConstraintStream<Solution_>> constraintStreamSet) {
        constraintStreamSet.add(this);
    }

    @Override
    public <Score_ extends Score<Score_>> void buildNode(NodeBuildHelper<Score_> buildHelper) {
        TupleLifecycle<UniTuple<A>> tupleLifecycle = buildHelper.getAggregatedTupleLifecycle(childStreamList);
        int outputStoreSize = buildHelper.extractTupleStoreSize(this);
        buildHelper.addNode(new ForEachUniNode<>(forEachClass, tupleLifecycle, outputStoreSize));
    }

    // ************************************************************************
    // Equality for node sharing
    // ************************************************************************

    @Override
    public int hashCode() {
        return forEachClass.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o instanceof BavetForEachUniConstraintStream) {
            BavetForEachUniConstraintStream<?, ?> other = (BavetForEachUniConstraintStream<?, ?>) o;
            return forEachClass.equals(other.forEachClass);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        return "ForEach(" + forEachClass.getSimpleName() + ") with " + childStreamList.size() + " children";
    }

    // ************************************************************************
    // Getters/setters
    // ************************************************************************

    public Class<A> getForEachClass() {
        return forEachClass;
    }

}
