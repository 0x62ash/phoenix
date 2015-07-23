package org.apache.phoenix.calcite;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.calcite.avatica.util.ByteString;
import org.apache.calcite.rex.RexCall;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlFunction;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.NlsString;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.phoenix.calcite.rel.PhoenixRel.Implementor;
import org.apache.phoenix.expression.AndExpression;
import org.apache.phoenix.expression.CoerceExpression;
import org.apache.phoenix.expression.ComparisonExpression;
import org.apache.phoenix.expression.DateAddExpression;
import org.apache.phoenix.expression.DateSubtractExpression;
import org.apache.phoenix.expression.DecimalAddExpression;
import org.apache.phoenix.expression.DecimalDivideExpression;
import org.apache.phoenix.expression.DecimalMultiplyExpression;
import org.apache.phoenix.expression.DecimalSubtractExpression;
import org.apache.phoenix.expression.Determinism;
import org.apache.phoenix.expression.DoubleAddExpression;
import org.apache.phoenix.expression.DoubleDivideExpression;
import org.apache.phoenix.expression.DoubleMultiplyExpression;
import org.apache.phoenix.expression.DoubleSubtractExpression;
import org.apache.phoenix.expression.Expression;
import org.apache.phoenix.expression.ExpressionType;
import org.apache.phoenix.expression.LiteralExpression;
import org.apache.phoenix.expression.LongAddExpression;
import org.apache.phoenix.expression.LongDivideExpression;
import org.apache.phoenix.expression.LongMultiplyExpression;
import org.apache.phoenix.expression.LongSubtractExpression;
import org.apache.phoenix.expression.OrExpression;
import org.apache.phoenix.expression.TimestampAddExpression;
import org.apache.phoenix.expression.TimestampSubtractExpression;
import org.apache.phoenix.expression.function.AggregateFunction;
import org.apache.phoenix.expression.function.CountAggregateFunction;
import org.apache.phoenix.expression.function.FunctionExpression;
import org.apache.phoenix.expression.function.MaxAggregateFunction;
import org.apache.phoenix.expression.function.MinAggregateFunction;
import org.apache.phoenix.expression.function.RoundDecimalExpression;
import org.apache.phoenix.expression.function.RoundTimestampExpression;
import org.apache.phoenix.schema.SortOrder;
import org.apache.phoenix.schema.TypeMismatchException;
import org.apache.phoenix.schema.types.PDataType;
import org.apache.phoenix.schema.types.PDate;
import org.apache.phoenix.schema.types.PDecimal;
import org.apache.phoenix.schema.types.PDouble;
import org.apache.phoenix.schema.types.PLong;
import org.apache.phoenix.schema.types.PTimestamp;
import org.apache.phoenix.schema.types.PUnsignedTimestamp;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Utilities for interacting with Calcite.
 */
public class CalciteUtils {
    private CalciteUtils() {}
    
    private static AtomicInteger tempAliasCounter = new AtomicInteger(0);
  
    public static String createTempAlias() {
        return "$" + tempAliasCounter.incrementAndGet();
    }
    
    @SuppressWarnings("rawtypes")
    public static PDataType sqlTypeNameToPDataType(SqlTypeName sqlTypeName) {
        return PDataType.fromTypeId(sqlTypeName.getJdbcOrdinal());
    }

	private static final Map<SqlKind, ExpressionFactory> EXPRESSION_MAP = Maps
			.newHashMapWithExpectedSize(ExpressionType.values().length);
	private static final ExpressionFactory getFactory(RexNode node) {
		ExpressionFactory eFactory = EXPRESSION_MAP.get(node.getKind());
		if (eFactory == null) {
			throw new UnsupportedOperationException("Unsupported RexNode: "
					+ node);
		}
		return eFactory;
	}
	static {
        EXPRESSION_MAP.put(SqlKind.AND, new ExpressionFactory() {

            @Override
            public Expression newExpression(RexNode node, Implementor implementor) {
                try {
                    return AndExpression.create(convertChildren((RexCall) node, implementor));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            
        });
        EXPRESSION_MAP.put(SqlKind.OR, new ExpressionFactory() {

            @Override
            public Expression newExpression(RexNode node, Implementor implementor) {
                return new OrExpression(convertChildren((RexCall) node, implementor));
            }
            
        });
		EXPRESSION_MAP.put(SqlKind.EQUALS, new ExpressionFactory() {

			@Override
			public Expression newExpression(RexNode node, Implementor implementor) {
				ImmutableBytesWritable ptr = new ImmutableBytesWritable();
				try {
					return ComparisonExpression.create(CompareOp.EQUAL, convertChildren((RexCall) node, implementor), ptr);
				} catch (SQLException e) {
					throw new RuntimeException(e);
				}
			}
			
		});
        EXPRESSION_MAP.put(SqlKind.NOT_EQUALS, new ExpressionFactory() {

            @Override
            public Expression newExpression(RexNode node, Implementor implementor) {
                ImmutableBytesWritable ptr = new ImmutableBytesWritable();
                try {
                    return ComparisonExpression.create(CompareOp.NOT_EQUAL, convertChildren((RexCall) node, implementor), ptr);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            
        });
        EXPRESSION_MAP.put(SqlKind.GREATER_THAN, new ExpressionFactory() {

            @Override
            public Expression newExpression(RexNode node, Implementor implementor) {
                ImmutableBytesWritable ptr = new ImmutableBytesWritable();
                try {
                    return ComparisonExpression.create(CompareOp.GREATER, convertChildren((RexCall) node, implementor), ptr);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            
        });
        EXPRESSION_MAP.put(SqlKind.GREATER_THAN_OR_EQUAL, new ExpressionFactory() {

            @Override
            public Expression newExpression(RexNode node, Implementor implementor) {
                ImmutableBytesWritable ptr = new ImmutableBytesWritable();
                try {
                    return ComparisonExpression.create(CompareOp.GREATER_OR_EQUAL, convertChildren((RexCall) node, implementor), ptr);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            
        });
        EXPRESSION_MAP.put(SqlKind.LESS_THAN, new ExpressionFactory() {

            @Override
            public Expression newExpression(RexNode node, Implementor implementor) {
                ImmutableBytesWritable ptr = new ImmutableBytesWritable();
                try {
                    return ComparisonExpression.create(CompareOp.LESS, convertChildren((RexCall) node, implementor), ptr);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            
        });
        EXPRESSION_MAP.put(SqlKind.LESS_THAN_OR_EQUAL, new ExpressionFactory() {

            @Override
            public Expression newExpression(RexNode node, Implementor implementor) {
                ImmutableBytesWritable ptr = new ImmutableBytesWritable();
                try {
                    return ComparisonExpression.create(CompareOp.LESS_OR_EQUAL, convertChildren((RexCall) node, implementor), ptr);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            
        });
        EXPRESSION_MAP.put(SqlKind.PLUS, new ExpressionFactory() {

            @SuppressWarnings("rawtypes")
            @Override
            public Expression newExpression(RexNode node, Implementor implementor) {
                try {
                    List<Expression> children = convertChildren((RexCall) node, implementor);
                    Expression expr = null;
                    boolean foundDate = false;
                    Determinism determinism = Determinism.ALWAYS;
                    PDataType theType = null;
                    for(int i = 0; i < children.size(); i++) {
                        Expression e = children.get(i);
                        determinism = determinism.combine(e.getDeterminism());
                        PDataType type = e.getDataType();
                        if (type == null) {
                            continue; 
                        } else if (type.isCoercibleTo(PTimestamp.INSTANCE)) {
                            if (foundDate) {
                                throw TypeMismatchException.newException(type, node.toString());
                            }
                            if (theType == null || (theType != PTimestamp.INSTANCE && theType != PUnsignedTimestamp.INSTANCE)) {
                                theType = type;
                            }
                            foundDate = true;
                        }else if (type == PDecimal.INSTANCE) {
                            if (theType == null || !theType.isCoercibleTo(PTimestamp.INSTANCE)) {
                                theType = PDecimal.INSTANCE;
                            }
                        } else if (type.isCoercibleTo(PLong.INSTANCE)) {
                            if (theType == null) {
                                theType = PLong.INSTANCE;
                            }
                        } else if (type.isCoercibleTo(PDouble.INSTANCE)) {
                            if (theType == null) {
                                theType = PDouble.INSTANCE;
                            }
                        } else {
                            throw TypeMismatchException.newException(type, node.toString());
                        }
                    }
                    if (theType == PDecimal.INSTANCE) {
                        expr = new DecimalAddExpression(children);
                    } else if (theType == PLong.INSTANCE) {
                        expr = new LongAddExpression(children);
                    } else if (theType == PDouble.INSTANCE) {
                        expr = new DoubleAddExpression(children);
                    } else if (theType == null) {
                        expr = LiteralExpression.newConstant(null, theType, determinism);
                    } else if (theType == PTimestamp.INSTANCE || theType == PUnsignedTimestamp.INSTANCE) {
                        expr = new TimestampAddExpression(children);
                    } else if (theType.isCoercibleTo(PDate.INSTANCE)) {
                        expr = new DateAddExpression(children);
                    } else {
                        throw TypeMismatchException.newException(theType, node.toString());
                    }
                    
                    PDataType targetType = sqlTypeNameToPDataType(node.getType().getSqlTypeName());
                    return cast(targetType, expr);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            
        });
        EXPRESSION_MAP.put(SqlKind.MINUS, new ExpressionFactory() {

            @SuppressWarnings("rawtypes")
            @Override
            public Expression newExpression(RexNode node, Implementor implementor) {
                try {
                    List<Expression> children = convertChildren((RexCall) node, implementor);
                    Expression expr = null;
                    int i = 0;
                    PDataType theType = null;
                    Expression e1 = children.get(0);
                    Expression e2 = children.get(1);
                    Determinism determinism = e1.getDeterminism().combine(e2.getDeterminism());
                    PDataType type1 = e1.getDataType();
                    PDataType type2 = e2.getDataType();
                    // TODO: simplify this special case for DATE conversion
                    /**
                     * For date1-date2, we want to coerce to a LONG because this
                     * cannot be compared against another date. It has essentially
                     * become a number. For date1-5, we want to preserve the DATE
                     * type because this can still be compared against another date
                     * and cannot be multiplied or divided. Any other time occurs is
                     * an error. For example, 5-date1 is an error. The nulls occur if
                     * we have bind variables.
                     */
                    boolean isType1Date = 
                            type1 != null 
                            && type1 != PTimestamp.INSTANCE
                            && type1 != PUnsignedTimestamp.INSTANCE
                            && type1.isCoercibleTo(PDate.INSTANCE);
                    boolean isType2Date = 
                            type2 != null
                            && type2 != PTimestamp.INSTANCE
                            && type2 != PUnsignedTimestamp.INSTANCE
                            && type2.isCoercibleTo(PDate.INSTANCE);
                    if (isType1Date || isType2Date) {
                        if (isType1Date && isType2Date) {
                            i = 2;
                            theType = PDecimal.INSTANCE;
                        } else if (isType1Date && type2 != null
                                && type2.isCoercibleTo(PDecimal.INSTANCE)) {
                            i = 2;
                            theType = PDate.INSTANCE;
                        } else if (type1 == null || type2 == null) {
                            /*
                             * FIXME: Could be either a Date or BigDecimal, but we
                             * don't know if we're comparing to a date or a number
                             * which would be disambiguate it.
                             */
                            i = 2;
                            theType = null;
                        }
                    } else if(type1 == PTimestamp.INSTANCE || type2 == PTimestamp.INSTANCE) {
                        i = 2;
                        theType = PTimestamp.INSTANCE;
                    } else if(type1 == PUnsignedTimestamp.INSTANCE || type2 == PUnsignedTimestamp.INSTANCE) {
                        i = 2;
                        theType = PUnsignedTimestamp.INSTANCE;
                    }
                    
                    for (; i < children.size(); i++) {
                        // This logic finds the common type to which all child types are coercible
                        // without losing precision.
                        Expression e = children.get(i);
                        determinism = determinism.combine(e.getDeterminism());
                        PDataType type = e.getDataType();
                        if (type == null) {
                            continue;
                        } else if (type.isCoercibleTo(PLong.INSTANCE)) {
                            if (theType == null) {
                                theType = PLong.INSTANCE;
                            }
                        } else if (type == PDecimal.INSTANCE) {
                            // Coerce return type to DECIMAL from LONG or DOUBLE if DECIMAL child found,
                            // unless we're doing date arithmetic.
                            if (theType == null
                                    || !theType.isCoercibleTo(PDate.INSTANCE)) {
                                theType = PDecimal.INSTANCE;
                            }
                        } else if (type.isCoercibleTo(PDouble.INSTANCE)) {
                            // Coerce return type to DOUBLE from LONG if DOUBLE child found,
                            // unless we're doing date arithmetic or we've found another child of type DECIMAL
                            if (theType == null
                                    || (theType != PDecimal.INSTANCE && !theType.isCoercibleTo(PDate.INSTANCE) )) {
                                theType = PDouble.INSTANCE;
                            }
                        } else {
                            throw TypeMismatchException.newException(type, node.toString());
                        }
                    }
                    if (theType == PDecimal.INSTANCE) {
                        expr = new DecimalSubtractExpression(children);
                    } else if (theType == PLong.INSTANCE) {
                        expr = new LongSubtractExpression(children);
                    } else if (theType == PDouble.INSTANCE) {
                        expr = new DoubleSubtractExpression(children);
                    } else if (theType == null) {
                        expr = LiteralExpression.newConstant(null, theType, determinism);
                    } else if (theType == PTimestamp.INSTANCE || theType == PUnsignedTimestamp.INSTANCE) {
                        expr = new TimestampSubtractExpression(children);
                    } else if (theType.isCoercibleTo(PDate.INSTANCE)) {
                        expr = new DateSubtractExpression(children);
                    } else {
                        throw TypeMismatchException.newException(theType, node.toString());
                    }
                    PDataType targetType = sqlTypeNameToPDataType(node.getType().getSqlTypeName());
                    return cast(targetType, expr);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            
        });
        EXPRESSION_MAP.put(SqlKind.TIMES, new ExpressionFactory() {

            @SuppressWarnings("rawtypes")
            @Override
            public Expression newExpression(RexNode node, Implementor implementor) {
                try {
                    List<Expression> children = convertChildren((RexCall) node, implementor);
                    Expression expr = null;
                    PDataType theType = null;
                    Determinism determinism = Determinism.ALWAYS;
                    for(int i = 0; i < children.size(); i++) {
                        Expression e = children.get(i);
                        determinism = determinism.combine(e.getDeterminism());
                        PDataType type = e.getDataType();
                        if (type == null) {
                            continue;
                        } else if (type == PDecimal.INSTANCE) {
                            theType = PDecimal.INSTANCE;
                        } else if (type.isCoercibleTo(PLong.INSTANCE)) {
                            if (theType == null) {
                                theType = PLong.INSTANCE;
                            }
                        } else if (type.isCoercibleTo(PDouble.INSTANCE)) {
                            if (theType == null) {
                                theType = PDouble.INSTANCE;
                            }
                        } else {
                            throw TypeMismatchException.newException(type, node.toString());
                        }
                    }
                    if (theType == PDecimal.INSTANCE) {
                        expr = new DecimalMultiplyExpression(children);
                    } else if (theType == PLong.INSTANCE) {
                        expr = new LongMultiplyExpression(children);
                    } else if (theType == PDouble.INSTANCE) {
                        expr = new DoubleMultiplyExpression(children);
                    } else {
                        expr = LiteralExpression.newConstant(null, theType, determinism);
                    }
                    PDataType targetType = sqlTypeNameToPDataType(node.getType().getSqlTypeName());
                    return cast(targetType, expr);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            
        });
        EXPRESSION_MAP.put(SqlKind.DIVIDE, new ExpressionFactory() {

            @SuppressWarnings("rawtypes")
            @Override
            public Expression newExpression(RexNode node, Implementor implementor) {
                try {
                    List<Expression> children = convertChildren((RexCall) node, implementor);
                    Expression expr = null;
                    PDataType theType = null;
                    Determinism determinism = Determinism.ALWAYS;
                    for(int i = 0; i < children.size(); i++) {
                        Expression e = children.get(i);
                        determinism = determinism.combine(e.getDeterminism());
                        PDataType type = e.getDataType();
                        if (type == null) {
                            continue;
                        } else if (type == PDecimal.INSTANCE) {
                            theType = PDecimal.INSTANCE;
                        } else if (type.isCoercibleTo(PLong.INSTANCE)) {
                            if (theType == null) {
                                theType = PLong.INSTANCE;
                            }
                        } else if (type.isCoercibleTo(PDouble.INSTANCE)) {
                            if (theType == null) {
                                theType = PDouble.INSTANCE;
                            }
                        } else {
                            throw TypeMismatchException.newException(type, node.toString());
                        }
                    }
                    if (theType == PDecimal.INSTANCE) {
                        expr = new DecimalDivideExpression( children);
                    } else if (theType == PLong.INSTANCE) {
                        expr = new LongDivideExpression( children);
                    } else if (theType == PDouble.INSTANCE) {
                        expr = new DoubleDivideExpression(children);
                    } else {
                        expr = LiteralExpression.newConstant(null, theType, determinism);
                    }
                    PDataType targetType = sqlTypeNameToPDataType(node.getType().getSqlTypeName());
                    return cast(targetType, expr);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            
        });
		EXPRESSION_MAP.put(SqlKind.LITERAL, new ExpressionFactory() {

			@SuppressWarnings("rawtypes")
            @Override
			public Expression newExpression(RexNode node, Implementor implementor) {
				RexLiteral lit = (RexLiteral) node;
                PDataType targetType = sqlTypeNameToPDataType(node.getType().getSqlTypeName());
				Object o = lit.getValue();
				if (o instanceof NlsString) {
				    o = ((NlsString) o).getValue();
				} else if (o instanceof ByteString) {
				    o = ((ByteString) o).getBytes();
				}
				try {
                    return LiteralExpression.newConstant(o, targetType);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
			}
			
		});
		EXPRESSION_MAP.put(SqlKind.INPUT_REF, new ExpressionFactory() {

			@Override
			public Expression newExpression(RexNode node, Implementor implementor) {
				RexInputRef ref = (RexInputRef) node;
				int index = ref.getIndex();
				return implementor.newColumnExpression(index);
			}
			
		});
		EXPRESSION_MAP.put(SqlKind.CAST, new ExpressionFactory() {

            @SuppressWarnings("rawtypes")
            @Override
            public Expression newExpression(RexNode node,
                    Implementor implementor) {                
                List<Expression> children = convertChildren((RexCall) node, implementor);
                PDataType targetType = sqlTypeNameToPDataType(node.getType().getSqlTypeName());
                try {
                    return cast(targetType, children.get(0));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
		    
		});
	}
	
    private static final Map<String, FunctionFactory> FUNCTION_MAP = Maps
            .newHashMapWithExpectedSize(ExpressionType.values().length);
    private static final FunctionFactory getFactory(SqlFunction func) {
        FunctionFactory fFactory = FUNCTION_MAP.get(func.getName());
        if (fFactory == null) {
            throw new UnsupportedOperationException("Unsupported SqlFunction: "
                    + func);
        }
        return fFactory;
    }
    static {
        FUNCTION_MAP.put("COUNT", new FunctionFactory() {
            @Override
            public FunctionExpression newFunction(SqlFunction sqlFunc,
                    List<Expression> args) {
                if (args.isEmpty()) {
                    args = Lists.asList(LiteralExpression.newConstant(1), new Expression[0]);
                }
                return new CountAggregateFunction(args);
            }
        });
        // TODO Buggy. Disable for now.
        //FUNCTION_MAP.put("$SUM0", new FunctionFactory() {
        //    @Override
        //    public FunctionExpression newFunction(SqlFunction sqlFunc,
        //            List<Expression> args) {
        //        return new SumAggregateFunction(args);
        //    }
        //});
        FUNCTION_MAP.put("MAX", new FunctionFactory() {
            @Override
            public FunctionExpression newFunction(SqlFunction sqlFunc,
                    List<Expression> args) {
                return new MaxAggregateFunction(args, null);
            }
        });
        FUNCTION_MAP.put("MIN", new FunctionFactory() {
            @Override
            public FunctionExpression newFunction(SqlFunction sqlFunc,
                    List<Expression> args) {
                return new MinAggregateFunction(args, null);
            }
        });
    }
    
    private static List<Expression> convertChildren(RexCall call, Implementor implementor) {
        List<Expression> children = Lists.newArrayListWithExpectedSize(call.getOperands().size());
        for (RexNode op : call.getOperands()) {
            Expression child = getFactory(op).newExpression(op, implementor);
            children.add(child);
        }
        return children;
    }
    
    @SuppressWarnings("rawtypes")
    private static Expression cast(PDataType targetDataType, Expression childExpr) throws SQLException {
        PDataType fromDataType = childExpr.getDataType();
        
        Expression expr = childExpr;
        if(fromDataType != null) {
            expr =  convertToRoundExpressionIfNeeded(fromDataType, targetDataType, childExpr);
        }
        return CoerceExpression.create(expr, targetDataType, SortOrder.getDefault(), expr.getMaxLength());
    }
    
    @SuppressWarnings("rawtypes")
    private static Expression convertToRoundExpressionIfNeeded(PDataType fromDataType, PDataType targetDataType, Expression expr) throws SQLException {
        if(fromDataType == targetDataType) {
            return expr;
        } else if((fromDataType == PDecimal.INSTANCE || fromDataType == PTimestamp.INSTANCE || fromDataType == PUnsignedTimestamp.INSTANCE) && targetDataType.isCoercibleTo(
          PLong.INSTANCE)) {
            return RoundDecimalExpression.create(Arrays.asList(expr));
        } else if((fromDataType == PDecimal.INSTANCE || fromDataType == PTimestamp.INSTANCE || fromDataType == PUnsignedTimestamp.INSTANCE) && targetDataType.isCoercibleTo(
          PDate.INSTANCE)) {
            return RoundTimestampExpression.create(Arrays.asList(expr));
        } else if(fromDataType.isCastableTo(targetDataType)) {
            return expr;
        } else {
            throw TypeMismatchException.newException(fromDataType, targetDataType, expr.toString());
        }
    }

    public static boolean isExpressionSupported(RexNode node) {
        try {
            getFactory(node);
        } catch (UnsupportedOperationException e) {
            return false;
        }
        if (node instanceof RexCall) {
            for (RexNode op : ((RexCall) node).getOperands()) {
                if (!isExpressionSupported(op)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    public static boolean isAggregateFunctionSupported(SqlAggFunction aggFunc) {
        try {
            getFactory(aggFunc);
        } catch (UnsupportedOperationException e) {
            return false;
        }

        return true;
    }

	public static Expression toExpression(RexNode node, Implementor implementor) {
		ExpressionFactory eFactory = getFactory(node);
		Expression expression = eFactory.newExpression(node, implementor);
		return expression;
	}
	
	public static AggregateFunction toAggregateFunction(SqlAggFunction aggFunc, List<Integer> args, Implementor implementor) {
	    FunctionFactory fFactory = getFactory(aggFunc);
	    List<Expression> exprs = Lists.newArrayListWithExpectedSize(args.size());
	    for (Integer index : args) {
	        exprs.add(implementor.newColumnExpression(index));
	    }
	    
	    return (AggregateFunction) (fFactory.newFunction(aggFunc, exprs));
	}
	
	public static Object evaluateStatelessExpression(RexNode node) {
	    try {
	        Expression expression = toExpression(node, null);
	        if (expression.isStateless() && expression.getDeterminism() == Determinism.ALWAYS) {
	            ImmutableBytesWritable ptr = new ImmutableBytesWritable();
	            expression.evaluate(null, ptr);
	            return expression.getDataType().toObject(ptr);
	        }
	    } catch (Exception e) {
	        // Expression is not stateless. do nothing.
	    }
	    
	    return null;
	}
	
	public static interface ExpressionFactory {
		public Expression newExpression(RexNode node, Implementor implementor);
	}
	
	public static interface FunctionFactory {
	    public FunctionExpression newFunction(SqlFunction sqlFunc, List<Expression> args);
	}
}
