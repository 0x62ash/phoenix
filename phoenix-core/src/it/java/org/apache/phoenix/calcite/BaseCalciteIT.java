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

import static org.apache.phoenix.util.TestUtil.TEST_PROPERTIES;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.calcite.avatica.util.ArrayImpl;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.phoenix.end2end.BaseClientManagedTimeIT;
import org.apache.phoenix.schema.TableAlreadyExistsException;
import org.apache.phoenix.util.PhoenixRuntime;
import org.apache.phoenix.util.PropertiesUtil;

import com.google.common.collect.Lists;

public class BaseCalciteIT extends BaseClientManagedTimeIT {
    
    public static Start start(boolean materializationEnabled) {
        return new Start(getConnectionProps(materializationEnabled));
    }
    
    public static Start start(Properties props) {
        return new Start(props);
    }

    public static class Start {
        protected final Properties props;
        private Connection connection;
        
        Start(Properties props) {
            this.props = props;
        }

        Connection createConnection() throws Exception {
            return DriverManager.getConnection(
                    "jdbc:phoenixcalcite:" 
                            + getUrl().substring(PhoenixRuntime.JDBC_PROTOCOL.length() + 1), 
                    props);
        }

        public Sql sql(String sql) {
            return new Sql(this, sql);
        }

        public Connection getConnection() {
            if (connection == null) {
                try {
                    connection = createConnection();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return connection;
        }

        public void close() {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /** Fluid class for a test that has specified a SQL query. */
    static class Sql {
        private final Start start;
        private final String sql;

        public Sql(Start start, String sql) {
            this.start = start;
            this.sql = sql;
        }

        public static List<Object[]> getResult(ResultSet resultSet) throws SQLException {
            final List<Object[]> list = Lists.newArrayList();
            populateResult(resultSet, list);
            return list;
        }

        private static void populateResult(ResultSet resultSet, List<Object[]> list) throws SQLException {
            final int columnCount = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                Object[] row = new Object[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    row[i] = resultSet.getObject(i + 1);
                }
                list.add(row);
            }
        }

        public Sql explainIs(String expected) throws SQLException {
            final List<Object[]> list = getResult("explain plan for " + sql);
            if (list.size() != 1) {
                fail("explain should return 1 row, got " + list.size());
            }
            String explain = (String) (list.get(0)[0]);
            assertEquals(explain, expected);
            return this;
        }


        public boolean execute() throws SQLException {
            final Statement statement = start.getConnection().createStatement();
            final boolean execute = statement.execute(sql);
            statement.close();
            return execute;
        }

        public List<Object[]> getResult(String sql) throws SQLException {
            final Statement statement = start.getConnection().createStatement();
            final ResultSet resultSet = statement.executeQuery(sql);
            List<Object[]> list = getResult(resultSet);
            resultSet.close();
            statement.close();
            return list;
        }

        public void close() {
            start.close();
        }

        public Sql resultIs(Object[]... expected) throws SQLException {
            final Statement statement = start.getConnection().createStatement();
            final ResultSet resultSet = statement.executeQuery(sql);
            for (int i = 0; i < expected.length; i++) {
                assertTrue(resultSet.next());
                Object[] row = expected[i];
                for (int j = 0; j < row.length; j++) {
                    Object obj = resultSet.getObject(j + 1);
                    if (obj instanceof ArrayImpl) {
                        assertEquals(
                                Arrays.toString((Object[]) row[j]),
                                obj.toString());
                    } else {
                        assertEquals(row[j], obj);
                    }
                }
            }
            assertFalse(resultSet.next());
            resultSet.close();
            statement.close();
            return this;
        }
    }

    private static final String FOODMART_SCHEMA = "     {\n"
            + "       type: 'jdbc',\n"
            + "       name: 'foodmart',\n"
            + "       jdbcDriver: 'org.hsqldb.jdbcDriver',\n"
            + "       jdbcUser: 'FOODMART',\n"
            + "       jdbcPassword: 'FOODMART',\n"
            + "       jdbcUrl: 'jdbc:hsqldb:res:foodmart',\n"
            + "       jdbcCatalog: null,\n"
            + "       jdbcSchema: 'foodmart'\n"
            + "     }";
    
    private static final String getPhoenixSchema() {
        return "    {\n"
            + "      name: 'phoenix',\n"
            + "      type: 'custom',\n"
            + "      factory: 'org.apache.phoenix.calcite.PhoenixSchema$Factory',\n"
            + "      operand: {\n"
            + "        url: \"" + getUrl() + "\"\n"
            + "      }\n"
            + "    }";
    }

    protected static Connection connectUsingModel(Properties props) throws Exception {
        final File file = File.createTempFile("model", ".json");
        final String url = getUrl();
        final PrintWriter pw = new PrintWriter(new FileWriter(file));
        pw.print(
            "{\n"
                + "  version: '1.0',\n"
                + "  defaultSchema: 'HR',\n"
                + "  schemas: [\n"
                + "    {\n"
                + "      name: 'HR',\n"
                + "      type: 'custom',\n"
                + "      factory: 'org.apache.phoenix.calcite.PhoenixSchema$Factory',\n"
                + "      operand: {\n"
                + "        url: \"" + url + "\",\n"
                + "        user: \"scott\",\n"
                + "        password: \"tiger\"\n"
                + "      }\n"
                + "    }\n"
                + "  ]\n"
                + "}\n");
        pw.close();
        final Connection connection =
            DriverManager.getConnection("jdbc:phoenixcalcite:model=" + file.getAbsolutePath(), props);
        return connection;
    }

    protected static Connection connectWithHsqldbUsingModel(Properties props) throws Exception {
        final File file = File.createTempFile("model", ".json");
        final PrintWriter pw = new PrintWriter(new FileWriter(file));
        pw.print(
            "{\n"
                + "  version: '1.0',\n"
                + "  defaultSchema: 'phoenix',\n"
                + "  schemas: [\n"
                + getPhoenixSchema() + ",\n"
                + FOODMART_SCHEMA + "\n"
                + "  ]\n"
                + "}\n");
        pw.close();
        final Connection connection =
            DriverManager.getConnection("jdbc:phoenixcalcite:model=" + file.getAbsolutePath(), props);
        return connection;
    }

    protected static Properties getConnectionProps(boolean enableMaterialization) {
        Properties props = new Properties();
        props.setProperty(
                CalciteConnectionProperty.MATERIALIZATIONS_ENABLED.camelName(),
                Boolean.toString(enableMaterialization));
        props.setProperty(
                CalciteConnectionProperty.CREATE_MATERIALIZATIONS.camelName(),
                Boolean.toString(false));
        return props;
    }
    
    protected static final String SCORES_TABLE_NAME = "scores";
    
    protected void initArrayTable() throws Exception {
        Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(getUrl(), props);
        try {
            conn.createStatement().execute(
                    "CREATE TABLE " + SCORES_TABLE_NAME
                    + "(student_id INTEGER NOT NULL, subject_id INTEGER NOT NULL, scores INTEGER[] CONSTRAINT pk PRIMARY KEY (student_id, subject_id))");
            PreparedStatement stmt = conn.prepareStatement(
                    "UPSERT INTO " + SCORES_TABLE_NAME
                    + " VALUES(?, ?, ?)");
            stmt.setInt(1, 1);
            stmt.setInt(2, 1);
            stmt.setArray(3, conn.createArrayOf("INTEGER", new Integer[] {85, 80, 82}));
            stmt.execute();
            stmt.setInt(1, 2);
            stmt.setInt(2, 1);
            stmt.setArray(3, null);
            stmt.execute();
            stmt.setInt(1, 3);
            stmt.setInt(2, 2);
            stmt.setArray(3, conn.createArrayOf("INTEGER", new Integer[] {87, 88, 80}));
            stmt.execute();
            conn.commit();
        } catch (TableAlreadyExistsException e) {
        }
        conn.close();        
    }
    
    protected static final String NOSALT_TABLE_NAME = "nosalt_test_table";
    protected static final String NOSALT_TABLE_SALTED_INDEX_NAME = "idxsalted_nosalt_test_table";
    protected static final String SALTED_TABLE_NAME = "salted_test_table";
    protected static final String SALTED_TABLE_NOSALT_INDEX_NAME = "idx_salted_test_table";
    protected static final String SALTED_TABLE_SALTED_INDEX_NAME = "idxsalted_salted_test_table";
    
    protected void initSaltedTables(String index) throws SQLException {
        Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(getUrl(), props);
        try {
            conn.createStatement().execute(
                    "CREATE TABLE " + NOSALT_TABLE_NAME + " (mypk0 INTEGER NOT NULL, mypk1 INTEGER NOT NULL, col0 INTEGER, col1 INTEGER CONSTRAINT pk PRIMARY KEY (mypk0, mypk1))");
            PreparedStatement stmt = conn.prepareStatement(
                    "UPSERT INTO " + NOSALT_TABLE_NAME
                    + " VALUES(?, ?, ?, ?)");
            stmt.setInt(1, 1);
            stmt.setInt(2, 2);
            stmt.setInt(3, 3);
            stmt.setInt(4, 4);
            stmt.execute();
            stmt.setInt(1, 2);
            stmt.setInt(2, 3);
            stmt.setInt(3, 4);
            stmt.setInt(4, 5);
            stmt.execute();
            stmt.setInt(1, 3);
            stmt.setInt(2, 4);
            stmt.setInt(3, 5);
            stmt.setInt(4, 6);
            stmt.execute();
            conn.commit();
            
            if (index != null) {
                conn.createStatement().execute("CREATE " + index + " " + NOSALT_TABLE_SALTED_INDEX_NAME + " ON " + NOSALT_TABLE_NAME + " (col0) SALT_BUCKETS=4");
                conn.commit();
            }
            
            conn.createStatement().execute(
                    "CREATE TABLE " + SALTED_TABLE_NAME + " (mypk0 INTEGER NOT NULL, mypk1 INTEGER NOT NULL, col0 INTEGER, col1 INTEGER CONSTRAINT pk PRIMARY KEY (mypk0, mypk1)) SALT_BUCKETS=4");
            stmt = conn.prepareStatement(
                    "UPSERT INTO " + SALTED_TABLE_NAME
                    + " VALUES(?, ?, ?, ?)");
            stmt.setInt(1, 1);
            stmt.setInt(2, 2);
            stmt.setInt(3, 3);
            stmt.setInt(4, 4);
            stmt.execute();
            stmt.setInt(1, 2);
            stmt.setInt(2, 3);
            stmt.setInt(3, 4);
            stmt.setInt(4, 5);
            stmt.execute();
            stmt.setInt(1, 3);
            stmt.setInt(2, 4);
            stmt.setInt(3, 5);
            stmt.setInt(4, 6);
            stmt.execute();
            conn.commit();
            
            if (index != null) {
                conn.createStatement().execute("CREATE " + index + " " + SALTED_TABLE_NOSALT_INDEX_NAME + " ON " + SALTED_TABLE_NAME + " (col0)");
                conn.createStatement().execute("CREATE " + index + " " + SALTED_TABLE_SALTED_INDEX_NAME + " ON " + SALTED_TABLE_NAME + " (col1) INCLUDE (col0) SALT_BUCKETS=4");
                conn.commit();
            }
        } catch (TableAlreadyExistsException e) {
        }
        conn.close();        
    }
    
    protected static final String MULTI_TENANT_TABLE = "multitenant_test_table";
    protected static final String MULTI_TENANT_TABLE_INDEX = "idx_multitenant_test_table";
    protected static final String MULTI_TENANT_VIEW1 = "s1.multitenant_test_view1";
    protected static final String MULTI_TENANT_VIEW1_INDEX = "idx_multitenant_test_view1";
    protected static final String MULTI_TENANT_VIEW2 = "s2.multitenant_test_view2";
    protected static final String MULTI_TENANT_VIEW2_INDEX = "idx_multitenant_test_view2";
    
    protected void initMultiTenantTables(String index) throws SQLException {
        Properties props = PropertiesUtil.deepCopy(TEST_PROPERTIES);
        Connection conn = DriverManager.getConnection(getUrl(), props);
        try {
            conn.createStatement().execute(
                    "CREATE TABLE " + MULTI_TENANT_TABLE + " (tenant_id VARCHAR NOT NULL, id VARCHAR NOT NULL, col0 INTEGER, col1 INTEGER, col2 INTEGER CONSTRAINT pk PRIMARY KEY (tenant_id, id)) MULTI_TENANT=true");
            PreparedStatement stmt = conn.prepareStatement(
                    "UPSERT INTO " + MULTI_TENANT_TABLE
                    + " VALUES(?, ?, ?, ?, ?)");
            stmt.setString(1, "10");
            stmt.setString(2, "2");
            stmt.setInt(3, 3);
            stmt.setInt(4, 4);
            stmt.setInt(5, 5);
            stmt.execute();
            stmt.setString(1, "15");
            stmt.setString(2, "3");
            stmt.setInt(3, 4);
            stmt.setInt(4, 5);
            stmt.setInt(5, 6);
            stmt.execute();
            stmt.setString(1, "20");
            stmt.setString(2, "4");
            stmt.setInt(3, 5);
            stmt.setInt(4, 6);
            stmt.setInt(5, 7);
            stmt.execute();
            stmt.setString(1, "20");
            stmt.setString(2, "5");
            stmt.setInt(3, 6);
            stmt.setInt(4, 7);
            stmt.setInt(5, 8);
            stmt.execute();
            conn.commit();
            
            if (index != null) {
                conn.createStatement().execute(
                        "CREATE " + index + " " + MULTI_TENANT_TABLE_INDEX
                        + " ON " + MULTI_TENANT_TABLE + "(col1) INCLUDE (col0, col2)");
                conn.commit();
            }
            
            conn.close();
            props.setProperty("TenantId", "10");
            conn = DriverManager.getConnection(getUrl(), props);
            conn.createStatement().execute("CREATE VIEW " + MULTI_TENANT_VIEW1
                    + " AS select * from " + MULTI_TENANT_TABLE);
            conn.commit();
            
            if (index != null) {
                conn.createStatement().execute(
                        "CREATE " + index + " " + MULTI_TENANT_VIEW1_INDEX
                        + " ON " + MULTI_TENANT_VIEW1 + "(col0)");
                conn.commit();
            }
            
            conn.close();
            props.setProperty("TenantId", "20");
            conn = DriverManager.getConnection(getUrl(), props);
            conn.createStatement().execute("CREATE VIEW " + MULTI_TENANT_VIEW2
                    + " AS select * from " + MULTI_TENANT_TABLE + " where col2 > 7");
            conn.commit();
            
            if (index != null) {
                conn.createStatement().execute(
                        "CREATE " + index + " " + MULTI_TENANT_VIEW2_INDEX
                        + " ON " + MULTI_TENANT_VIEW2 + "(col0)");
                conn.commit();
            }
        } catch (TableAlreadyExistsException e) {
        } finally {
            conn.close();
        }
    }

}
