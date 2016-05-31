package org.apache.phoenix.calcite.rel;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.core.CorrelationId;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.JoinInfo;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.util.ImmutableIntList;
import org.apache.phoenix.compile.QueryPlan;
import org.apache.phoenix.expression.CoerceExpression;
import org.apache.phoenix.expression.Expression;
import org.apache.phoenix.expression.LiteralExpression;
import org.apache.phoenix.schema.SortOrder;

/**
 * Implementation of {@link org.apache.calcite.rel.core.Join}
 * relational expression in Phoenix.
 */
abstract public class PhoenixAbstractJoin extends Join implements PhoenixQueryRel {
    public final JoinInfo joinInfo;
    public final boolean isSingleValueRhs;

    protected PhoenixAbstractJoin(RelOptCluster cluster, RelTraitSet traits, RelNode left, RelNode right, RexNode condition, Set<CorrelationId> variablesSet, JoinRelType joinType, boolean isSingleValueRhs) {
        super(cluster, traits, left, right, condition, variablesSet, joinType);
        this.joinInfo = JoinInfo.of(left, right, condition);
        this.isSingleValueRhs = isSingleValueRhs;
    }
    
    abstract public PhoenixAbstractJoin copy(RelTraitSet traits, RexNode condition, RelNode left,
            RelNode right, JoinRelType joinRelType, boolean semiJoinDone, boolean isSingleValueRhs);

    @Override
    public RelWriter explainTerms(RelWriter pw) {
        return super.explainTerms(pw)
            .itemIf("isSingleValueRhs", isSingleValueRhs, isSingleValueRhs);
    }
    
    protected ImmutableIntList getColumnRefList(int index) {
        assert index <= 1;
        
        return index == 0 ? 
                ImmutableIntList.identity(getLeft().getRowType().getFieldCount()) 
              : ImmutableIntList.identity(getRight().getRowType().getFieldCount());
    }
    
    protected QueryPlan implementInput(PhoenixRelImplementor implementor, int index, List<Expression> conditionExprs) {
        assert index <= 1;
        
        PhoenixQueryRel input = index == 0 ? (PhoenixQueryRel) left : (PhoenixQueryRel) right;
        QueryPlan plan = implementor.visitInput(0, input);
        
        if (conditionExprs != null) {
            ImmutableIntList keys = index == 0 ? joinInfo.leftKeys : joinInfo.rightKeys;
            for (Iterator<Integer> iter = keys.iterator(); iter.hasNext();) {
                Integer i = iter.next();
                Expression e = implementor.newColumnExpression(i);
                if (e.getSortOrder() == SortOrder.DESC) {
                    try {
                        e = CoerceExpression.create(
                                e, e.getDataType(), SortOrder.ASC, e.getMaxLength());
                    } catch (SQLException ex) {
                        throw new RuntimeException(ex);
                    }
                }
                conditionExprs.add(e);
            }
            if (conditionExprs.isEmpty()) {
                conditionExprs.add(LiteralExpression.newConstant(0));
            }
        }
        
        return plan;
    }
}
