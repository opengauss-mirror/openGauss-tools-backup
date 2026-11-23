/*
 * Copyright (c) 2021 Huawei Technologies Co., Ltd
 * Copyright (c) 2012 Tomislav Gountchev <tomi@gountchev.net>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package jdbcgsbackup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

class Index extends DbBackupObject {

    static class IndexFactory implements DBOFactory<Index> {

        @Override
        public Iterable<Index> getDbBackupObjects(Connection con, Schema schema) throws SQLException {
            List<Index> indexes = new ArrayList<Index>();
            PreparedStatement stmt = null;
            try {
                stmt = con.prepareStatement(
                        "SELECT c.relname AS tablename, i.relname AS indexname, " +
                        "pg_get_indexdef(i.oid) AS indexdef " +
                        "FROM pg_index x " +
                        "JOIN pg_class c ON c.oid = x.indrelid " +
                        "JOIN pg_class i ON i.oid = x.indexrelid " +
                        "WHERE c.relkind = 'r'::\"char\" AND i.relkind = 'i'::\"char\" " +
                        "AND NOT x.indisprimary AND c.relnamespace = ?");
                stmt.setInt(1, schema.getOid());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    indexes.add(new Index(rs.getString("indexname"), schema, rs.getString("tablename"), rs.getString("indexdef")));
                }
                rs.close();
            } finally {
                if (stmt != null) stmt.close();
            }

            return indexes;
        }

        @Override
        public Index getDbBackupObject(Connection con, String indexName, Schema schema) throws SQLException {
            Index index = null;
            PreparedStatement stmt = null;
            try {
                stmt = con.prepareStatement("SELECT * FROM pg_indexes WHERE schemaname = ? " +
                        "AND indexname = ?");
                stmt.setString(1, schema.getName());
                stmt.setString(2, indexName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    index = new Index(rs.getString("indexname"), schema, rs.getString("tablename"), rs.getString("indexdef"));
                } else {
                    throw new RuntimeException("no such index: " + indexName);
                }
                rs.close();
            } finally {
                if (stmt != null) stmt.close();
            }
            return index;
        }
    }

    static class CachingIndexFactory extends CachingDBOFactory<Index> {

        private final Table.CachingTableFactory tableFactory;

        protected CachingIndexFactory(Schema.CachingSchemaFactory schemaFactory, Table.CachingTableFactory tableFactory) {
            super(schemaFactory);
            this.tableFactory = tableFactory;
        }

        @Override
        protected PreparedStatement getAllStatement(Connection con) throws SQLException {
            return con.prepareStatement(
                    "SELECT x.indrelid AS table_oid, i.relname AS indexname, " +
                            "pg_get_indexdef(i.oid) AS indexdef, " +
                            "i.relnamespace AS schema_oid " +
                            "FROM pg_index x " +
                            "JOIN pg_class i ON i.oid = x.indexrelid " +
                            "WHERE i.relkind = 'i'::\"char\" " +
                    "AND NOT x.indisprimary ");
        }

        @Override
        protected Index newDbBackupObject(Connection con, ResultSet rs, Schema schema) throws SQLException {
            Table table = tableFactory.getTable(rs.getInt("table_oid"));
            return new Index(rs.getString("indexname"), schema, table.getName(), rs.getString("indexdef"));
        }

    }

    protected final String tableName;
    private final String definition;

    private Index(String name, Schema schema, String tableName, String definition) {
        super(name, schema, null);
        this.tableName = tableName;
        this.definition = definition;
    }

    @Override
    String getSql(DataFilter dataFilter) {
        return definition.replace(" ON " + schema.getName() + ".", " ON ") + " ;\n";  // remove schema name
    }

    @Override
    protected StringBuilder appendCreateSql(StringBuilder buf) {
        throw new UnsupportedOperationException();
    }

}
