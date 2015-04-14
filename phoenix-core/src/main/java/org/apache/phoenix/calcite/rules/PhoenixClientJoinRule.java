package org.apache.phoenix.calcite.rules;

import java.util.Iterator;
import java.util.List;

import org.apache.calcite.plan.RelOptRule;
import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.RelFieldCollation.Direction;
import org.apache.calcite.rel.RelFieldCollation.NullDirection;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinInfo;
import org.apache.phoenix.calcite.rel.PhoenixClientJoin;
import org.apache.phoenix.calcite.rel.PhoenixClientSort;
import org.apache.phoenix.calcite.rel.PhoenixJoin;
import org.apache.phoenix.calcite.rel.PhoenixRel;
import com.google.common.collect.Lists;

public class PhoenixClientJoinRule extends RelOptRule {
    
    public static PhoenixClientJoinRule INSTANCE = new PhoenixClientJoinRule();

    public PhoenixClientJoinRule() {
        super(operand(PhoenixJoin.class, any()), "PhoenixClientJoinRule");
    }

    @Override
    public void onMatch(RelOptRuleCall call) {
        PhoenixJoin join = call.rel(0);
        RelNode left = join.getLeft();
        RelNode right = join.getRight();
        JoinInfo joinInfo = JoinInfo.of(left, right, join.getCondition());
        
        List<RelFieldCollation> leftFieldCollations = Lists.newArrayList();
        for (Iterator<Integer> iter = joinInfo.leftKeys.iterator(); iter.hasNext();) {
            leftFieldCollations.add(new RelFieldCollation(iter.next(), Direction.ASCENDING,NullDirection.FIRST));
        }
        RelCollation leftCollation = RelCollations.of(leftFieldCollations);
        RelNode newLeft = new PhoenixClientSort(left.getCluster(), left.getTraitSet().replace(PhoenixRel.CONVENTION).replace(leftCollation), left, leftCollation, null, null);
        
        List<RelFieldCollation> rightFieldCollations = Lists.newArrayList();
        for (Iterator<Integer> iter = joinInfo.rightKeys.iterator(); iter.hasNext();) {
            rightFieldCollations.add(new RelFieldCollation(iter.next(), Direction.ASCENDING,NullDirection.FIRST));
        }
        RelCollation rightCollation = RelCollations.of(rightFieldCollations);
        RelNode newRight = new PhoenixClientSort(right.getCluster(), right.getTraitSet().replace(PhoenixRel.CONVENTION).replace(rightCollation), right, rightCollation, null, null);

        call.transformTo(new PhoenixClientJoin(join.getCluster(),
                join.getTraitSet(), newLeft, newRight, join.getCondition(), 
                join.getJoinType(), join.getVariablesStopped()));
    }

}
