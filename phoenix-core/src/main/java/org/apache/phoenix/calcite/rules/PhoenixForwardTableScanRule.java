package org.apache.phoenix.calcite.rules;

import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Sort;
import org.apache.phoenix.calcite.rel.PhoenixTableScan;
import org.apache.phoenix.calcite.rel.PhoenixTableScan.ScanOrder;

import com.google.common.base.Predicate;

public class PhoenixForwardTableScanRule extends RelOptRule {
    private static final Predicate<Sort> NON_EMPTY_COLLATION =
            new Predicate<Sort>() {
        @Override
        public boolean apply(Sort input) {
            return !input.getCollation().getFieldCollations().isEmpty();
        }
    };
    
    private static final Predicate<PhoenixTableScan> APPLICABLE_TABLE_SCAN =
            new Predicate<PhoenixTableScan>() {
        @Override
        public boolean apply(PhoenixTableScan input) {
            return input.scanOrder == ScanOrder.NONE;
        }
    };

    public PhoenixForwardTableScanRule(Class<? extends Sort> sortClass) {
        super(operand(sortClass, null, NON_EMPTY_COLLATION,
                operand(PhoenixTableScan.class, null, APPLICABLE_TABLE_SCAN, any())),
            PhoenixForwardTableScanRule.class.getName() + ":" + sortClass.getName());
    }

    @Override
    public void onMatch(RelOptRuleCall call) {
        final Sort sort = call.rel(0);
        final PhoenixTableScan scan = call.rel(1);
        final RelCollation collation = sort.getCollation();
        assert !collation.getFieldCollations().isEmpty();
        for (RelCollation candidate : scan.getTable().getCollationList()) {
            if (candidate.satisfies(collation)) {
                RelNode newRel = PhoenixTableScan.create(
                        scan.getCluster(), scan.getTable(), scan.filter, ScanOrder.FORWARD);
                if (sort.offset != null || sort.fetch != null) {
                    newRel = sort.copy(
                            sort.getTraitSet().replace(RelCollations.EMPTY),
                            newRel, RelCollations.EMPTY, sort.offset, sort.fetch);
                }
                call.transformTo(newRel);
                break;
            }
        }
    }

}
