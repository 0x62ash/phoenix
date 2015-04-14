package org.apache.phoenix.calcite;

import java.util.List;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.RelFieldCollation.Direction;
import org.apache.calcite.rel.RelFieldCollation.NullDirection;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.rex.RexNode;
import org.apache.phoenix.compile.OrderByCompiler.OrderBy;
import org.apache.phoenix.execute.TupleProjector;
import org.apache.phoenix.expression.Expression;
import org.apache.phoenix.expression.OrderByExpression;
import org.apache.phoenix.schema.SortOrder;

import com.google.common.collect.Lists;

/**
 * Implementation of {@link org.apache.calcite.rel.core.Sort}
 * relational expression in Phoenix.
 *
 * <p>Like {@code Sort}, it also supports LIMIT and OFFSET.
 */
abstract public class PhoenixSort extends Sort implements PhoenixRel {
    protected static final double CLIENT_MERGE_FACTOR = 0.5;
    
    public PhoenixSort(RelOptCluster cluster, RelTraitSet traits, RelNode child, RelCollation collation, RexNode offset, RexNode fetch) {
        super(cluster, traits, child, collation, offset, fetch);
        assert getConvention() == PhoenixRel.CONVENTION;
    }
    
    protected OrderBy getOrderBy(Implementor implementor, TupleProjector tupleProjector) {
        assert !getCollation().getFieldCollations().isEmpty();
        
        List<OrderByExpression> orderByExpressions = Lists.newArrayList();
        for (RelFieldCollation fieldCollation : getCollation().getFieldCollations()) {
            Expression expr = tupleProjector == null ? 
                      implementor.newColumnExpression(fieldCollation.getFieldIndex()) 
                    : tupleProjector.getExpressions()[fieldCollation.getFieldIndex()];
            boolean isAscending = fieldCollation.getDirection() == Direction.ASCENDING;
            if (expr.getSortOrder() == SortOrder.DESC) {
                isAscending = !isAscending;
            }
            orderByExpressions.add(new OrderByExpression(expr, fieldCollation.nullDirection == NullDirection.LAST, isAscending));
        }
        
        return new OrderBy(orderByExpressions);
    }
    
    protected Integer getLimit(Implementor implementor) {
        // TODO
        return null;
    }
}
