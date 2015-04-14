package org.apache.phoenix.calcite.rel;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.apache.phoenix.compile.QueryPlan;
import org.apache.phoenix.compile.OrderByCompiler.OrderBy;
import org.apache.phoenix.execute.HashJoinPlan;
import org.apache.phoenix.execute.AggregatePlan;
import org.apache.phoenix.execute.TupleProjectionPlan;
import org.apache.phoenix.execute.TupleProjector;

public class PhoenixCompactClientSort extends PhoenixAbstractSort {

    public PhoenixCompactClientSort(RelOptCluster cluster, RelTraitSet traits,
            RelNode child, RelCollation collation, RexNode offset, RexNode fetch) {
        super(cluster, traits, child, collation, offset, fetch);
    }

    @Override
    public PhoenixCompactClientSort copy(RelTraitSet traitSet, RelNode newInput,
            RelCollation newCollation, RexNode offset, RexNode fetch) {
        return new PhoenixCompactClientSort(getCluster(), traitSet, newInput, newCollation, offset, fetch);
    }
    
    @Override
    public RelOptCost computeSelfCost(RelOptPlanner planner) {
        return super.computeSelfCost(planner)
                .multiplyBy(CLIENT_MERGE_FACTOR)
                .multiplyBy(PHOENIX_FACTOR);
    }

    @Override
    public QueryPlan implement(Implementor implementor) {
        assert getConvention() == getInput().getConvention();
        if (this.offset != null)
            throw new UnsupportedOperationException();
            
        QueryPlan plan = implementor.visitInput(0, (PhoenixRel) getInput());
        assert plan instanceof TupleProjectionPlan;
        
        // PhoenixServerAggregate wraps the AggregatePlan with a TupleProjectionPlan,
        // so we need to unwrap the TupleProjectionPlan.
        TupleProjectionPlan tupleProjectionPlan = (TupleProjectionPlan) plan;
        assert tupleProjectionPlan.getPostFilter() == null;
        QueryPlan innerPlan = tupleProjectionPlan.getDelegate();
        TupleProjector tupleProjector = tupleProjectionPlan.getTupleProjector();
        assert (innerPlan instanceof AggregatePlan 
                    || innerPlan instanceof HashJoinPlan)
                && innerPlan.getLimit() == null; 
        
        AggregatePlan basePlan;
        HashJoinPlan hashJoinPlan = null;
        if (innerPlan instanceof AggregatePlan) {
            basePlan = (AggregatePlan) innerPlan;
        } else {
            hashJoinPlan = (HashJoinPlan) innerPlan;
            QueryPlan delegate = hashJoinPlan.getDelegate();
            assert delegate instanceof AggregatePlan;
            basePlan = (AggregatePlan) delegate;
        }
        
        OrderBy orderBy = super.getOrderBy(implementor, tupleProjector);
        Integer limit = super.getLimit(implementor);
        QueryPlan newPlan = AggregatePlan.create((AggregatePlan) basePlan, orderBy, limit);
        
        if (hashJoinPlan != null) {        
            newPlan = HashJoinPlan.create(hashJoinPlan.getStatement(), newPlan, hashJoinPlan.getJoinInfo(), hashJoinPlan.getSubPlans());
        }
        // Recover the wrapping of TupleProjectionPlan
        newPlan = new TupleProjectionPlan(newPlan, tupleProjector, null);
        return newPlan;
    }

}
