package org.apache.phoenix.calcite;

import java.sql.SQLException;
import java.util.List;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptCost;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.core.TableScan;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexNode;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.phoenix.compile.ColumnResolver;
import org.apache.phoenix.compile.FromCompiler;
import org.apache.phoenix.compile.OrderByCompiler.OrderBy;
import org.apache.phoenix.compile.QueryPlan;
import org.apache.phoenix.compile.RowProjector;
import org.apache.phoenix.compile.SequenceManager;
import org.apache.phoenix.compile.StatementContext;
import org.apache.phoenix.compile.WhereCompiler;
import org.apache.phoenix.compile.WhereOptimizer;
import org.apache.phoenix.execute.ScanPlan;
import org.apache.phoenix.execute.TupleProjector;
import org.apache.phoenix.expression.Expression;
import org.apache.phoenix.iterate.ParallelIteratorFactory;
import org.apache.phoenix.jdbc.PhoenixStatement;
import org.apache.phoenix.parse.SelectStatement;
import org.apache.phoenix.schema.KeyValueSchema.KeyValueSchemaBuilder;
import org.apache.phoenix.schema.PColumn;
import org.apache.phoenix.schema.PColumnFamily;
import org.apache.phoenix.schema.PTable;
import org.apache.phoenix.schema.TableRef;
import org.apache.phoenix.util.SchemaUtil;

import com.google.common.collect.Lists;

/**
 * Scan of a Phoenix table.
 */
public class PhoenixTableScan extends TableScan implements PhoenixRel {
    public final RexNode filter;

    protected PhoenixTableScan(RelOptCluster cluster, RelTraitSet traits, RelOptTable table, RexNode filter) {
        super(cluster, traits, table);
        this.filter = filter;
    }

    @Override
    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
        assert inputs.isEmpty();
        return this;
    }

    @Override
    public void register(RelOptPlanner planner) {
        RelOptRule[] rules = PhoenixRules.RULES;
        for (RelOptRule rule : rules) {
            planner.addRule(rule);
        }
        planner.addRule(PhoenixFilterScanMergeRule.INSTANCE);
    }

    @Override
    public RelWriter explainTerms(RelWriter pw) {
        return super.explainTerms(pw)
            .itemIf("filter", filter, filter != null);
    }

    @Override
    public RelOptCost computeSelfCost(RelOptPlanner planner) {
        RelOptCost cost = super.computeSelfCost(planner).multiplyBy(PHOENIX_FACTOR);
        if (filter != null && !filter.isAlwaysTrue()) {
            final Double selectivity = RelMetadataQuery.getSelectivity(this, filter);
            cost = cost.multiplyBy(selectivity);
        }
        return cost;
    }
    
    @Override
    public double getRows() {
        return super.getRows()
                * RelMetadataQuery.getSelectivity(this, filter);
    }

    @Override
    public QueryPlan implement(Implementor implementor) {
        final PhoenixTable phoenixTable = table.unwrap(PhoenixTable.class);
        PTable pTable = phoenixTable.getTable();
        TableRef tableRef = new TableRef(CalciteUtils.createTempAlias(), pTable, HConstants.LATEST_TIMESTAMP, false);
        implementor.setTableRef(tableRef);
        try {
            PhoenixStatement stmt = new PhoenixStatement(phoenixTable.pc);
            ColumnResolver resolver = FromCompiler.getResolver(tableRef);
            StatementContext context = new StatementContext(stmt, resolver, new Scan(), new SequenceManager(stmt));
            SelectStatement select = SelectStatement.SELECT_STAR;
            if (filter != null) {
                Expression filterExpr = CalciteUtils.toExpression(filter, implementor);
                filterExpr = WhereOptimizer.pushKeyExpressionsToScan(context, select, filterExpr);
                WhereCompiler.setScanFilter(context, select, filterExpr, true, false);
            }
            projectAllColumnFamilies(context.getScan(), phoenixTable.getTable());
            if (implementor.getCurrentContext().forceProject()) {
                TupleProjector tupleProjector = createTupleProjector(implementor, phoenixTable.getTable());
                TupleProjector.serializeProjectorIntoScan(context.getScan(), tupleProjector);
                PTable projectedTable = implementor.createProjectedTable();
                implementor.setTableRef(new TableRef(projectedTable));
            }
            Integer limit = null;
            OrderBy orderBy = OrderBy.EMPTY_ORDER_BY;
            ParallelIteratorFactory iteratorFactory = null;
            return new ScanPlan(context, select, tableRef, RowProjector.EMPTY_PROJECTOR, limit, orderBy, iteratorFactory, true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    private TupleProjector createTupleProjector(Implementor implementor, PTable table) {
        KeyValueSchemaBuilder builder = new KeyValueSchemaBuilder(0);
        List<Expression> exprs = Lists.<Expression> newArrayList();
        for (PColumn column : table.getColumns()) {
            if (!SchemaUtil.isPKColumn(column) || !implementor.getCurrentContext().isRetainPKColumns()) {
                Expression expr = implementor.newColumnExpression(column.getPosition());
                exprs.add(expr);
                builder.addField(expr);                
            }
        }
        
        return new TupleProjector(builder.build(), exprs.toArray(new Expression[exprs.size()]));
    }
    
    // TODO only project needed columns
    private void projectAllColumnFamilies(Scan scan, PTable table) {
        scan.getFamilyMap().clear();
        for (PColumnFamily family : table.getColumnFamilies()) {
            scan.addFamily(family.getName().getBytes());
        }
    }

    @Override
    public PlanType getPlanType() {
        return PlanType.SERVER_ONLY_FLAT;
    }
}
