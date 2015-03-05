package org.apache.phoenix.calcite;

import com.google.common.collect.Lists;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.phoenix.end2end.BaseClientManagedTimeIT;
import org.apache.phoenix.jdbc.PhoenixConnection;
import org.apache.phoenix.query.BaseTest;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.*;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

/**
 * Integration test for queries powered by Calcite.
 */
public class CalciteTest extends BaseClientManagedTimeIT {
    public static final String ATABLE_NAME = "ATABLE";

    public static Start start() {
        return new Start();
    }

    public static class Start {
        private Connection connection;

        Connection createConnection() throws Exception {
            return CalciteTest.createConnection();
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

        public List<String> getResult(ResultSet resultSet) throws SQLException {
            final List<String> list = Lists.newArrayList();
            populateResult(resultSet, list);
            return list;
        }

        private void populateResult(ResultSet resultSet, List<String> list) throws SQLException {
            final StringBuilder buf = new StringBuilder();
            final int columnCount = resultSet.getMetaData().getColumnCount();
            while (resultSet.next()) {
                for (int i = 0; i < columnCount; i++) {
                    if (i > 0) {
                        buf.append(", ");
                    }
                    buf.append(resultSet.getString(i + 1));
                }
                list.add(buf.toString());
                buf.setLength(0);
            }
        }

        public Sql explainIs(String expected) {
            final List<String> list = getResult("explain plan for " + sql);
            if (list.size() != 1) {
                fail("explain should return 1 row, got " + list.size());
            }
            String explain = list.get(0);
            assertThat(explain, equalTo(expected));
            return this;
        }

        public List<String> getResult(String sql) {
            try {
                final Statement statement = start.getConnection().createStatement();
                final ResultSet resultSet = statement.executeQuery(sql);
                List<String> list = getResult(resultSet);
                resultSet.close();
                statement.close();
                return list;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        public void close() {
            start.close();
        }

        public Sql resultIs(String... lines) {
            assertThat(Arrays.asList(lines), equalTo(getResult(sql)));
            return this;
        }
    }

    private static Connection createConnection() throws SQLException {
        final Connection connection = DriverManager.getConnection(
            "jdbc:calcite:");
        final CalciteConnection calciteConnection =
            connection.unwrap(CalciteConnection.class);
        final String url = getUrl();
        final PhoenixConnection phoenixConnection =
            DriverManager.getConnection(url).unwrap(PhoenixConnection.class);
        BaseTest.ensureTableCreated(url, ATABLE_NAME);
        calciteConnection.getRootSchema().add("phoenix",
            new PhoenixSchema(phoenixConnection));
        calciteConnection.setSchema("phoenix");
        return connection;
    }

    private static Connection connectUsingModel() throws Exception {
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
            DriverManager.getConnection("jdbc:calcite:model=" + file.getAbsolutePath());
        BaseTest.ensureTableCreated(url, ATABLE_NAME);
        return connection;
    }

    @Test public void testConnect() throws Exception {
        final Connection connection = DriverManager.getConnection("jdbc:calcite:");
        final CalciteConnection calciteConnection =
            connection.unwrap(CalciteConnection.class);
        Class.forName("org.apache.phoenix.jdbc.PhoenixDriver");
        final String url = getUrl();
        final PhoenixConnection phoenixConnection =
            DriverManager.getConnection(url).unwrap(PhoenixConnection.class);
        ensureTableCreated(url, ATABLE_NAME);
        initATableValues(getOrganizationId(), null, url);
        calciteConnection.getRootSchema().add("phoenix",
            new PhoenixSchema(phoenixConnection));
        calciteConnection.setSchema("phoenix");
        final Statement statement = calciteConnection.createStatement();
        final ResultSet resultSet = statement.executeQuery("select entity_id, a_string, organization_id from aTable where a_string = 'a'");
        while (resultSet.next()) {
            System.out.println("org_id=" + resultSet.getObject(3) + ",entity_id=" + resultSet.getObject(1) + ",a_string=" + resultSet.getObject("A_STRING"));
        }
        resultSet.close();
        statement.close();
        connection.close();
    }

    @Test public void testExplainPlanForSelectWhereQuery() {
        start()
            .sql("select * from aTable where a_string = 'a'")
            .explainIs(
                "PhoenixToEnumerableConverter\n"
                    + "  PhoenixTableScan(table=[[phoenix, ATABLE]], filter=[=($2, 'a')])\n")
            .close();
    }

    @Test public void testExplainProject() {
        start()
            .sql("select a_string, b_string from aTable where a_string = 'a'")
            .explainIs(
                "PhoenixToEnumerableConverter\n"
                    + "  PhoenixProject(A_STRING=[$2], B_STRING=[$3])\n"
                    + "    PhoenixTableScan(table=[[phoenix, ATABLE]], filter=[=($2, 'a')])\n")
            .close();
    }

    @Test public void testConnectUsingModel() throws Exception {
        final Start start = new Start() {
            @Override
            Connection createConnection() throws Exception {
                return connectUsingModel();
            }
        };
        start.sql("select * from aTable")
            .explainIs("PhoenixToEnumerableConverter\n"
                + "  PhoenixTableScan(table=[[HR, ATABLE]])\n")
            // .resultIs("Xx")
            .close();
    }
}
