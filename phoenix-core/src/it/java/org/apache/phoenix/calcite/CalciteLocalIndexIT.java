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
package org.apache.phoenix.calcite;

import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.util.Properties;

import org.junit.Test;

public class CalciteLocalIndexIT extends BaseCalciteIndexIT {
    
    public CalciteLocalIndexIT() {
        super(true);
    }
    
    @Test public void testIndex() throws Exception {
        final Start start1000 = start(true, 1000f);
        final Start start1000000 = start(true, 1000000f);

        start1000.sql("select * from aTable where b_string = 'b'")
            .explainIs("PhoenixToEnumerableConverter\n" +
                       "  PhoenixServerProject(ORGANIZATION_ID=[$1], ENTITY_ID=[$2], A_STRING=[$3], B_STRING=[$0], A_INTEGER=[$4], A_DATE=[$5], A_TIME=[$6], A_TIMESTAMP=[$7], X_DECIMAL=[$8], X_LONG=[$9], X_INTEGER=[$10], Y_INTEGER=[$11], A_BYTE=[$12], A_SHORT=[$13], A_FLOAT=[$14], A_DOUBLE=[$15], A_UNSIGNED_FLOAT=[$16], A_UNSIGNED_DOUBLE=[$17])\n" +
                       "    PhoenixTableScan(table=[[phoenix, IDX_FULL]], filter=[=($0, 'b')])\n");
        start1000.sql("select x_integer from aTable")
            .explainIs("PhoenixToEnumerableConverter\n" +
                       "  PhoenixServerProject(X_INTEGER=[$10])\n" +
                       "    PhoenixTableScan(table=[[phoenix, ATABLE]])\n");
            /*.explainIs("PhoenixToEnumerableConverter\n" +
                       "  PhoenixServerProject(X_INTEGER=[$4])\n" +
                       "    PhoenixTableScan(table=[[phoenix, IDX1]])\n")*/
        start1000.sql("select a_string from aTable order by a_string")
            .explainIs("PhoenixToEnumerableConverter\n" +
                       "  PhoenixServerProject(0:A_STRING=[$0])\n" +
                       "    PhoenixTableScan(table=[[phoenix, IDX1]], scanOrder=[FORWARD])\n");
        start1000000.sql("select a_string from aTable order by organization_id")
            .explainIs("PhoenixToEnumerableConverter\n" +
                       "  PhoenixServerProject(A_STRING=[$2], ORGANIZATION_ID=[$0])\n" +
                       "    PhoenixTableScan(table=[[phoenix, ATABLE]], scanOrder=[FORWARD])\n");
        start1000.sql("select a_integer from aTable order by a_string")
            .explainIs("PhoenixToEnumerableConverter\n" +
                       "  PhoenixServerSort(sort0=[$1], dir0=[ASC])\n" +
                       "    PhoenixServerProject(A_INTEGER=[$4], A_STRING=[$2])\n" +
                       "      PhoenixTableScan(table=[[phoenix, ATABLE]])\n");
            /*.explainIs("PhoenixToEnumerableConverter\n" +
                       "  PhoenixServerSort(sort0=[$1], dir0=[ASC])\n" +
                       "    PhoenixServerProject(A_INTEGER=[$4], A_STRING=[$3])\n" +
                       "      PhoenixTableScan(table=[[phoenix, IDX_FULL]])\n")*/
        start1000.sql("select a_string, b_string from aTable where a_string = 'a'")
            .explainMatches("PhoenixToEnumerableConverter\n" +
                       "  PhoenixServerProject\\((0:)?A_STRING=\\[\\$0\\], (0:)?B_STRING=\\[\\$3\\]\\)\n" +
                       "    PhoenixTableScan\\(table=\\[\\[phoenix, IDX1\\]\\], filter=\\[=\\(\\$0, 'a'\\)\\]\\)\n");
        start1000.sql("select a_string, b_string from aTable where b_string = 'b'")
            .explainIs("PhoenixToEnumerableConverter\n" +
                       "  PhoenixServerProject(A_STRING=[$3], B_STRING=[$0])\n" +
                       "    PhoenixTableScan(table=[[phoenix, IDX2]], filter=[=($0, 'b')])\n");
        start1000.sql("select a_string, b_string, x_integer, y_integer from aTable where b_string = 'b'")
            .explainIs("PhoenixToEnumerableConverter\n" +
                       "  PhoenixServerProject(A_STRING=[$3], B_STRING=[$0], X_INTEGER=[$10], Y_INTEGER=[$11])\n" +
                       "    PhoenixTableScan(table=[[phoenix, IDX_FULL]], filter=[=($0, 'b')])\n");
        start1000.sql("select a_string, count(*) from aTable group by a_string")
            .explainIs("PhoenixToEnumerableConverter\n" +
                       "  PhoenixServerAggregate(group=[{0}], EXPR$1=[COUNT()], isOrdered=[true])\n" +
                       "    PhoenixTableScan(table=[[phoenix, IDX1]], scanOrder=[FORWARD])\n");

        start1000.close();
        start1000000.close();
    }
    
    @Test public void testSaltedIndex() throws Exception {
        final Start start1 = start(true, 1f);
        start1.sql("select count(*) from " + NOSALT_TABLE_NAME + " where col0 > 3")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerAggregate(group=[{}], EXPR$0=[COUNT()])\n" +
                           "    PhoenixTableScan(table=[[phoenix, IDXSALTED_NOSALT_TEST_TABLE]], filter=[>(CAST($0):INTEGER, 3)])\n")
                .resultIs(0, new Object[][]{{999L}});
        start1.sql("select mypk0, mypk1, col0 from " + NOSALT_TABLE_NAME + " where col0 <= 4")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerProject(MYPK0=[$1], MYPK1=[$2], COL0=[CAST($0):INTEGER])\n" +
                           "    PhoenixTableScan(table=[[phoenix, IDXSALTED_NOSALT_TEST_TABLE]], filter=[<=(CAST($0):INTEGER, 4)])\n")
                .resultIs(0, new Object[][] {
                        {2, 3, 4},
                        {1, 2, 3}});
        start1.sql("select * from " + SALTED_TABLE_NAME + " where mypk0 < 3")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixTableScan(table=[[phoenix, SALTED_TEST_TABLE]], filter=[<($0, 3)])\n")
                .resultIs(0, new Object[][] {
                        {1, 2, 3, 4},
                        {2, 3, 4, 5}});
        start1.sql("select count(*) from " + SALTED_TABLE_NAME + " where col0 > 3")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerAggregate(group=[{}], EXPR$0=[COUNT()])\n" +
                           "    PhoenixTableScan(table=[[phoenix, IDX_SALTED_TEST_TABLE]], filter=[>(CAST($0):INTEGER, 3)])\n")
                .sameResultAsPhoenixStandalone(0)
                /*.resultIs(0, new Object[][]{{999L}})*/;
        start1.sql("select mypk0, mypk1, col0 from " + SALTED_TABLE_NAME + " where col0 <= 4")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerProject(MYPK0=[$1], MYPK1=[$2], COL0=[CAST($0):INTEGER])\n" +
                           "    PhoenixTableScan(table=[[phoenix, IDX_SALTED_TEST_TABLE]], filter=[<=(CAST($0):INTEGER, 4)])\n")
                .resultIs(0, new Object[][] {
                        {2, 3, 4},
                        {1, 2, 3}});
        start1.sql("select count(*) from " + SALTED_TABLE_NAME + " where col1 > 4")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerAggregate(group=[{}], EXPR$0=[COUNT()])\n" +
                           "    PhoenixTableScan(table=[[phoenix, IDXSALTED_SALTED_TEST_TABLE]], filter=[>(CAST($0):INTEGER, 4)])\n")
                .sameResultAsPhoenixStandalone(0)
                /*.resultIs(0, new Object[][]{{999L}})*/;
        start1.sql("select * from " + SALTED_TABLE_NAME + " where col1 <= 5 order by col1")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerProject(MYPK0=[$1], MYPK1=[$2], COL0=[$3], COL1=[CAST($0):INTEGER])\n" +
                           "    PhoenixTableScan(table=[[phoenix, IDXSALTED_SALTED_TEST_TABLE]], filter=[<=(CAST($0):INTEGER, 5)], scanOrder=[FORWARD])\n")
                .resultIs(new Object[][] {
                        {1, 2, 3, 4},
                        {2, 3, 4, 5}});
        start1.sql("select * from " + SALTED_TABLE_NAME + " s1, " + SALTED_TABLE_NAME + " s2 where s1.mypk1 = s2.mypk1 and s1.mypk0 > 500 and s2.col1 < 505")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerJoin(condition=[=($1, $5)], joinType=[inner])\n" +
                           "    PhoenixTableScan(table=[[phoenix, SALTED_TEST_TABLE]], filter=[>($0, 500)])\n" +
                           "    PhoenixServerProject(MYPK0=[$1], MYPK1=[$2], COL0=[$3], COL1=[CAST($0):INTEGER])\n" +
                           "      PhoenixTableScan(table=[[phoenix, IDXSALTED_SALTED_TEST_TABLE]], filter=[<(CAST($0):INTEGER, 505)])\n")
                .resultIs(0, new Object[][] {
                        {501, 502, 503, 504, 501, 502, 503, 504}});
        start1.close();
    }
    
    @Test public void testMultiTenant() throws Exception {
        Properties props = getConnectionProps(true, 1f);
        final Start start = start(props);
        props = getConnectionProps(true, 1f);
        props.setProperty("TenantId", "15");
        final Start startTenant15 = start(props);
        props = getConnectionProps(true, 1f);
        props.setProperty("TenantId", "10");
        final Start startTenant10 = start(props);
        props = getConnectionProps(true, 1f);
        props.setProperty("TenantId", "20");
        final Start startTenant20 = start(props);

        start.sql("select * from " + MULTI_TENANT_TABLE + " where tenant_id = '10' and id <= '0004'")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixTableScan(table=[[phoenix, MULTITENANT_TEST_TABLE]], filter=[AND(=($0, CAST('10'):VARCHAR CHARACTER SET \"ISO-8859-1\" COLLATE \"ISO-8859-1$en_US$primary\" NOT NULL), <=($1, '0004'))])\n")
                .resultIs(0, new Object[][] {
                        {"10", "0002", 3, 4, 5},
                        {"10", "0003", 4, 5, 6},
                        {"10", "0004", 5, 6, 7}});
        
        start.sql("select * from " + MULTI_TENANT_TABLE + " where tenant_id = '20' and col1 < 8")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerProject(TENANT_ID=[$0], ID=[$2], COL0=[$3], COL1=[CAST($1):INTEGER], COL2=[$4])\n" +
                           "    PhoenixTableScan(table=[[phoenix, IDX_MULTITENANT_TEST_TABLE]], filter=[AND(=($0, CAST('20'):VARCHAR CHARACTER SET \"ISO-8859-1\" COLLATE \"ISO-8859-1$en_US$primary\" NOT NULL), <(CAST($1):INTEGER, 8))])\n")
                /*.resultIs(0, new Object[][] {
                        {"20", "0004", 5, 6, 7},
                        {"20", "0005", 6, 7, 8}})*/;
        
        try {
            start.sql("select * from " + MULTI_TENANT_VIEW1)
                .explainIs("");
            fail("Should have got SQLException.");
        } catch (SQLException e) {
        }
        
        startTenant15.sql("select * from " + MULTI_TENANT_TABLE + " where id = '0284'")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixTableScan(table=[[phoenix, MULTITENANT_TEST_TABLE]], filter=[=($0, CAST('0284'):VARCHAR CHARACTER SET \"ISO-8859-1\" COLLATE \"ISO-8859-1$en_US$primary\" NOT NULL)])\n")
                .resultIs(0, new Object[][] {
                        {"0284", 285, 286, 287}});
        
        startTenant15.sql("select * from " + MULTI_TENANT_TABLE + " where col1 > 1000")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerProject(ID=[$1], COL0=[$2], COL1=[CAST($0):INTEGER], COL2=[$3])\n" +
                           "    PhoenixTableScan(table=[[phoenix, IDX_MULTITENANT_TEST_TABLE]], filter=[>(CAST($0):INTEGER, 1000)])\n")
                .resultIs(0, new Object[][] {
                        {"0999", 1000, 1001, 1002},
                        {"1000", 1001, 1002, 1003},
                        {"1001", 1002, 1003, 1004},
                        {"1002", 1003, 1004, 1005}});
        
        try {
            startTenant15.sql("select * from " + MULTI_TENANT_VIEW1)
                .explainIs("");
            fail("Should have got SQLException.");
        } catch (SQLException e) {
        }

        startTenant10.sql("select * from " + MULTI_TENANT_VIEW1 + " where id = '0512'")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixTableScan(table=[[phoenix, MULTITENANT_TEST_TABLE]], filter=[=($0, CAST('0512'):VARCHAR CHARACTER SET \"ISO-8859-1\" COLLATE \"ISO-8859-1$en_US$primary\" NOT NULL)])\n")
                .resultIs(0, new Object[][] {
                        {"0512", 513, 514, 515}});
        
        startTenant10.sql("select * from " + MULTI_TENANT_TABLE + " where col1 <= 6")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerProject(ID=[$1], COL0=[$2], COL1=[CAST($0):INTEGER], COL2=[$3])\n" +
                           "    PhoenixTableScan(table=[[phoenix, IDX_MULTITENANT_TEST_TABLE]], filter=[<=(CAST($0):INTEGER, 6)])\n")
                .sameResultAsPhoenixStandalone(0)
                /*.resultIs(0, new Object[][] {
                        {"0002", 3, 4, 5},
                        {"0003", 4, 5, 6},
                        {"0004", 5, 6, 7}})*/;
        
        startTenant10.sql("select id, col0 from " + MULTI_TENANT_VIEW1 + " where col0 >= 1000")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerProject(ID=[$1], COL0=[CAST($0):INTEGER])\n" +
                           "    PhoenixTableScan(table=[[phoenix, S1, IDX_MULTITENANT_TEST_VIEW1]], filter=[>=(CAST($0):INTEGER, 1000)])\n")
                .sameResultAsPhoenixStandalone(0)
                /*.resultIs(0, new Object[][] {
                        {"0999", 1000},
                        {"1000", 1001},
                        {"1001", 1002}})*/;
        
        startTenant10.sql("select * from " + MULTI_TENANT_VIEW1 + " where col0 = 1000")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerProject(ID=[$1], COL0=[CAST($0):INTEGER], COL1=[$2], COL2=[$3])\n" +
                           "    PhoenixTableScan(table=[[phoenix, S1, IDX_MULTITENANT_TEST_VIEW1]], filter=[=(CAST($0):INTEGER, 1000)], extendedColumns=[{2, 3}])\n")
                .sameResultAsPhoenixStandalone(0)
                /*.resultIs(0, new Object[][] {
                        {"0999", 1000, 1001, 1002}})*/;
        
        startTenant10.sql("select id, col0, col2 from " + MULTI_TENANT_VIEW1 + " where col0 = 1000")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerProject(ID=[$1], COL0=[CAST($0):INTEGER], COL2=[$3])\n" +
                           "    PhoenixTableScan(table=[[phoenix, S1, IDX_MULTITENANT_TEST_VIEW1]], filter=[=(CAST($0):INTEGER, 1000)], extendedColumns=[{3}])\n")
                .sameResultAsPhoenixStandalone(0)
                /*.resultIs(0, new Object[][] {
                        {"0999", 1000, 1002}})*/;

        startTenant20.sql("select * from " + MULTI_TENANT_VIEW2 + " where id = '0765'")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixTableScan(table=[[phoenix, MULTITENANT_TEST_TABLE]], filter=[AND(>($3, 7), =($0, CAST('0765'):VARCHAR CHARACTER SET \"ISO-8859-1\" COLLATE \"ISO-8859-1$en_US$primary\" NOT NULL))])\n")
                .resultIs(0, new Object[][] {
                        {"0765", 766, 767, 768}});
        
        startTenant20.sql("select id, col0 from " + MULTI_TENANT_VIEW2 + " where col0 between 272 and 275")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixServerProject(ID=[$1], COL0=[CAST($0):INTEGER])\n" +
                           "    PhoenixTableScan(table=[[phoenix, S2, IDX_MULTITENANT_TEST_VIEW2]], filter=[AND(>=(CAST($0):INTEGER, 272), <=(CAST($0):INTEGER, 275))])\n")
                .sameResultAsPhoenixStandalone(0)
                /*.resultIs(0, new Object[][] {
                        {"0271", 272},
                        {"0272", 273},
                        {"0273", 274},
                        {"0274", 275}})*/;
        
        startTenant20.sql("select id, col0 from " + MULTI_TENANT_VIEW2 + " order by col0 limit 5")
                .explainIs("PhoenixToEnumerableConverter\n" +
                           "  PhoenixLimit(fetch=[5])\n" +
                           "    PhoenixServerProject(ID=[$1], COL0=[CAST($0):INTEGER])\n" +
                           "      PhoenixTableScan(table=[[phoenix, S2, IDX_MULTITENANT_TEST_VIEW2]], scanOrder=[FORWARD])\n")
                .sameResultAsPhoenixStandalone()
                /*.resultIs(new Object[][] {
                        {"0005", 6},
                        {"0006", 7},
                        {"0007", 8},
                        {"0008", 9},
                        {"0009", 10}})*/;

        start.close();
        startTenant15.close();
        startTenant10.close();
        startTenant20.close();        
    }

}
