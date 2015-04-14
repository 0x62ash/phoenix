package org.apache.phoenix.calcite;

import java.sql.SQLException;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.phoenix.compile.FromCompiler;
import org.apache.phoenix.compile.OrderByCompiler.OrderBy;
import org.apache.phoenix.compile.QueryPlan;
import org.apache.phoenix.compile.RowProjector;
import org.apache.phoenix.compile.SequenceManager;
import org.apache.phoenix.compile.StatementContext;
import org.apache.phoenix.execute.ClientScanPlan;
import org.apache.phoenix.jdbc.PhoenixStatement;
import org.apache.phoenix.schema.TableRef;

public class PhoenixClientSort extends PhoenixSort {

    public PhoenixClientSort(RelOptCluster cluster, RelTraitSet traits,
            RelNode child, RelCollation collation, RexNode offset, RexNode fetch) {
        super(cluster, traits, child, collation, offset, fetch);
    }

    @Override
    public PhoenixClientSort copy(RelTraitSet traitSet, RelNode newInput,
            RelCollation newCollation, RexNode offset, RexNode fetch) {
        return new PhoenixClientSort(getCluster(), traitSet, newInput, newCollation, offset, fetch);
    }
    
    @Override
    public RelOptCost computeSelfCost(RelOptPlanner planner) {
        return super.computeSelfCost(planner)
                .multiplyBy(PHOENIX_FACTOR);
    }

    @Override
    public QueryPlan implement(Implementor implementor) {
        assert getConvention() == getInput().getConvention();
        if (this.offset != null)
            throw new UnsupportedOperationException();
            
        QueryPlan plan = implementor.visitInput(0, (PhoenixRel) getInput());
        
        TableRef tableRef = implementor.getTableRef();
        PhoenixStatement stmt = plan.getContext().getStatement();
        StatementContext context;
        try {
            context = new StatementContext(stmt, FromCompiler.getResolver(tableRef), new Scan(), new SequenceManager(stmt));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        
        OrderBy orderBy = super.getOrderBy(implementor);
        Integer limit = super.getLimit(implementor);
        
        return new ClientScanPlan(context, plan.getStatement(), tableRef, RowProjector.EMPTY_PROJECTOR, limit, null, orderBy, plan);
    }

}
