package ai.timefold.solver.constraint.streams.bavet.uni;

import java.util.Set;
import java.util.function.Function;

import ai.timefold.solver.constraint.streams.bavet.BavetConstraintFactory;
import ai.timefold.solver.constraint.streams.bavet.common.AbstractMapNode;
import ai.timefold.solver.constraint.streams.bavet.common.BavetAbstractConstraintStream;
import ai.timefold.solver.constraint.streams.bavet.common.NodeBuildHelper;
import ai.timefold.solver.core.api.score.Score;

public final class BavetMapBridgeUniConstraintStream<Solution_, A, NewA>
        extends BavetAbstractUniConstraintStream<Solution_, A> {

    private final BavetAbstractUniConstraintStream<Solution_, A> parent;
    private final Function<A, NewA> mappingFunction;
    private BavetMapUniConstraintStream<Solution_, NewA> mapStream;

    public BavetMapBridgeUniConstraintStream(BavetConstraintFactory<Solution_> constraintFactory,
            BavetAbstractUniConstraintStream<Solution_, A> parent, Function<A, NewA> mappingFunction) {
        super(constraintFactory, parent.getRetrievalSemantics());
        this.parent = parent;
        this.mappingFunction = mappingFunction;
    }

    @Override
    public boolean guaranteesDistinct() {
        return false;
    }

    public void setMapStream(BavetMapUniConstraintStream<Solution_, NewA> mapStream) {
        this.mapStream = mapStream;
    }

    // ************************************************************************
    // Node creation
    // ************************************************************************

    @Override
    public void collectActiveConstraintStreams(Set<BavetAbstractConstraintStream<Solution_>> constraintStreamSet) {
        parent.collectActiveConstraintStreams(constraintStreamSet);
        constraintStreamSet.add(this);
    }

    @Override
    public BavetAbstractConstraintStream<Solution_> getTupleSource() {
        return parent.getTupleSource();
    }

    @Override
    public <Score_ extends Score<Score_>> void buildNode(NodeBuildHelper<Score_> buildHelper) {
        if (!childStreamList.isEmpty()) {
            throw new IllegalStateException("Impossible state: the stream (" + this
                    + ") has a non-empty childStreamList (" + childStreamList + ") but it's a flattenLast bridge.");
        }
        int inputStoreIndex = buildHelper.reserveTupleStoreIndex(parent.getTupleSource());
        int outputStoreSize = buildHelper.extractTupleStoreSize(mapStream);
        AbstractMapNode<UniTuple<A>, NewA> node = new MapUniNode<>(inputStoreIndex, mappingFunction,
                buildHelper.getAggregatedTupleLifecycle(mapStream.getChildStreamList()), outputStoreSize);
        buildHelper.addNode(node, this);
    }

    // ************************************************************************
    // Equality for node sharing
    // ************************************************************************

    // TODO

    // ************************************************************************
    // Getters/setters
    // ************************************************************************

}
