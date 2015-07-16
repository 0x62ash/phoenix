package org.apache.phoenix.calcite.jdbc;

import java.util.Map;

import org.apache.calcite.jdbc.CalcitePrepare;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.plan.RelOptCostFactory;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.prepare.CalcitePrepareImpl;
import org.apache.calcite.rel.rules.JoinCommuteRule;
import org.apache.phoenix.calcite.PhoenixSchema;
import org.apache.phoenix.calcite.rules.PhoenixAddScanLimitRule;
import org.apache.phoenix.calcite.rules.PhoenixCompactClientSortRule;
import org.apache.phoenix.calcite.rules.PhoenixConverterRules;
import org.apache.phoenix.calcite.rules.PhoenixFilterScanMergeRule;
import org.apache.phoenix.calcite.rules.PhoenixInnerSortRemoveRule;
import org.apache.phoenix.calcite.rules.PhoenixJoinSingleValueAggregateMergeRule;

public class PhoenixPrepareImpl extends CalcitePrepareImpl {

    public PhoenixPrepareImpl() {
        super();
    }
    
    @Override
    protected RelOptPlanner createPlanner(
            final CalcitePrepare.Context prepareContext,
            org.apache.calcite.plan.Context externalContext,
            RelOptCostFactory costFactory) {
        RelOptPlanner planner = super.createPlanner(prepareContext, externalContext, costFactory);
        
        planner.removeRule(JoinCommuteRule.INSTANCE);
        planner.addRule(JoinCommuteRule.SWAP_OUTER);
        
        RelOptRule[] rules = PhoenixConverterRules.RULES;
        for (RelOptRule rule : rules) {
            planner.addRule(rule);
        }
        planner.addRule(PhoenixFilterScanMergeRule.INSTANCE);
        planner.addRule(PhoenixAddScanLimitRule.LIMIT_SCAN);
        planner.addRule(PhoenixAddScanLimitRule.LIMIT_SERVERPROJECT_SCAN);
        planner.addRule(PhoenixCompactClientSortRule.SORT_SERVERAGGREGATE);
        planner.addRule(PhoenixJoinSingleValueAggregateMergeRule.INSTANCE);
        planner.addRule(PhoenixInnerSortRemoveRule.INSTANCE);
        
        for (CalciteSchema subSchema : prepareContext.getRootSchema().getSubSchemaMap().values()) {
            if (subSchema.schema instanceof PhoenixSchema) {
                ((PhoenixSchema) subSchema.schema).defineIndexesAsMaterializations();
                for (CalciteSchema phoenixSubSchema : subSchema.getSubSchemaMap().values()) {
                    ((PhoenixSchema) phoenixSubSchema.schema).defineIndexesAsMaterializations();
                }
            }
        }

        return planner;
    }
}
