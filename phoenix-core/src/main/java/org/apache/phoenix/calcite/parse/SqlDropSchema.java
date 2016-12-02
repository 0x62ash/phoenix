/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.calcite.parse;

import java.util.List;

import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.parser.SqlParserPos;

import com.google.common.collect.ImmutableList;

/**
 * Parse tree node for SQL {@code DROP SCHEMA} command.
 */
public class SqlDropSchema extends SqlCall {
    public final SqlOperator operator;

    public final SqlIdentifier schemaName;
    public final SqlLiteral ifExists;
    public final SqlLiteral cascade;

    /** Creates a DROP SCHEMA. */
    public SqlDropSchema(SqlParserPos pos, SqlIdentifier schemaName, SqlLiteral ifExists, SqlLiteral cascade) {
        super(pos);
        this.operator = new SqlDdlOperator("DROP SCHEMA", SqlKind.OTHER_DDL);
        this.schemaName = schemaName;
        this.ifExists = ifExists;
        this.cascade = cascade;
    }

    @Override
    public SqlOperator getOperator() {
        return this.operator;
    }

    @Override
    public List<SqlNode> getOperandList() {
        return ImmutableList.of(schemaName, ifExists, cascade);        
    }
}
