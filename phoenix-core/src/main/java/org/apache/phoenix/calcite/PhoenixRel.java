package org.apache.phoenix.calcite;

import org.apache.calcite.plan.Convention;
import org.apache.calcite.rel.RelNode;
import org.apache.phoenix.compile.QueryPlan;
import org.apache.phoenix.expression.ColumnExpression;
import org.apache.phoenix.schema.TableRef;

/**
 * Relational expression in Phoenix.
 *
 * <p>Phoenix evaluates relational expressions using {@link java.util.Iterator}s
 * over streams of {@link org.apache.phoenix.schema.tuple.Tuple}s.</p>
 */
public interface PhoenixRel extends RelNode {
  /** Calling convention for relational operations that occur in Phoenix. */
  Convention CONVENTION = new Convention.Impl("PHOENIX", PhoenixRel.class);

  /** Relative cost of Phoenix versus Enumerable convention.
   *
   * <p>Multiply by the value (which is less than unity), and you will get a cheaper cost.
   * Phoenix is cheaper.
   */
  double PHOENIX_FACTOR = 0.5;

  QueryPlan implement(Implementor implementor);
  
  class ImplementorContext {
      private boolean retainPKColumns;
      
      public ImplementorContext(boolean retainPKColumns) {
          this.retainPKColumns = retainPKColumns;
      }
      
      public boolean isRetainPKColumns() {
          return this.retainPKColumns;
      }
  }

  /** Holds context for an traversal over a tree of relational expressions
   * to convert it to an executable plan. */
  interface Implementor {
    QueryPlan visitInput(int i, PhoenixRel input);
    ColumnExpression newColumnExpression(int index);
    void setTableRef(TableRef tableRef);
    void pushContext(ImplementorContext context);
    ImplementorContext popContext();
    ImplementorContext getCurrentContext();
  }
}
