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

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

final class Sequence extends DbBackupObject {

    static class SequenceFactory implements DBOFactory<Sequence> {

        @Override
        public Iterable<Sequence> getDbBackupObjects(Connection con, Schema schema) throws SQLException {
            List<Sequence> sequences = new ArrayList<Sequence>();
            PreparedStatement stmt = null;
            try {
                stmt = con.prepareStatement(
                        "SELECT c.relname AS sequencename, pg_get_userbyid(c.relowner) AS owner, c.relkind " +
                        "FROM pg_class c " +
                        "WHERE c.relkind in ('S','L','Z','z') AND c.relnamespace = ?");
                stmt.setInt(1, schema.getOid());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Sequence sequence = getSequence(con, schema, rs.getString("sequencename"),
                            rs.getString("owner"), rs.getString("relkind"));
                    sequences.add(sequence);
                }
                rs.close();
            } finally {
                if (stmt != null) stmt.close();
            }
            return sequences;
        }

        @Override
        public Sequence getDbBackupObject(Connection con, String sequenceName, Schema schema) throws SQLException {
            PreparedStatement stmt = null;
            Sequence sequence = null;
            try {
                stmt = con.prepareStatement(
                        "SELECT c.relname AS sequencename, pg_get_userbyid(c.relowner) AS owner, c.relkind " +
                        "FROM pg_class c " +
                        "WHERE c.relkind in ('S','L','Z','z') AND c.relnamespace = ? AND c.relname = ?");
                stmt.setInt(1, schema.getOid());
                stmt.setString(2, sequenceName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    sequence = getSequence(con, schema, rs.getString("sequencename"),
                            rs.getString("owner"), rs.getString("relkind"));
                } else {
                    throw new RuntimeException("no such sequence " + sequenceName);
                }
                rs.close();
            } finally {
                if (stmt != null) stmt.close();
            }
            return sequence;
        }

    }

    static class CachingSequenceFactory extends CachingDBOFactory<Sequence> {

        protected CachingSequenceFactory(Schema.CachingSchemaFactory schemaFactory) {
            super(schemaFactory);
        }

        @Override
        protected PreparedStatement getAllStatement(Connection con) throws SQLException {
            return con.prepareStatement(
                    "SELECT c.relname AS sequencename, pg_get_userbyid(c.relowner) AS owner, c.relkind, " +
                            "c.relnamespace AS schema_oid FROM pg_class c " +
                    "WHERE c.relkind in ('S','L','Z','z')");
        }

        @Override
        protected Sequence newDbBackupObject(Connection con, ResultSet rs, Schema schema) throws SQLException {
            return getSequence(con, schema, rs.getString("sequencename"),
                    rs.getString("owner"), rs.getString("relkind"));
        }


    }

    private static Sequence getSequence(Connection con, Schema schema, String sequenceName,
            String owner, String relkind) throws SQLException {
        PreparedStatement stmt = null;
        Sequence sequence = null;
        try {
            stmt = con.prepareStatement(
                    "SELECT * FROM " + schema.getName() + "." + sequenceName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next())
                sequence = new Sequence(sequenceName, rs, schema, owner, relkind);
            else
                throw new RuntimeException("no such sequence: " + sequenceName);
            rs.close();
        } finally {
            if (stmt != null) stmt.close();
        }
        return sequence;
    }

    private final BigInteger last_value;
    private final BigInteger start_value;
    private final BigInteger increment_by;
    private final BigInteger max_value;
    private final BigInteger min_value;
    private final BigInteger cache_value;
    private final boolean is_cycled;
    private final boolean large;

    private Sequence(String sequenceName, ResultSet rs, Schema schema, String owner, String relkind) throws SQLException {
        super(sequenceName, schema, owner);
        this.last_value = readBigInteger(rs, "last_value");
        this.start_value = readBigInteger(rs, "start_value");
        this.increment_by = readBigInteger(rs, "increment_by");
        this.max_value = readBigInteger(rs, "max_value");
        this.min_value = readBigInteger(rs, "min_value");
        this.is_cycled = rs.getBoolean("is_cycled");
        this.cache_value = readBigInteger(rs, "cache_value");
        this.large = isLargeRelkind(relkind);
    }

    private static boolean isLargeRelkind(String relkind) {
        if (relkind == null || relkind.isEmpty()) {
            return false;
        }
        char kind = relkind.charAt(0);
        return kind == 'L' || kind == 'Z';
    }

    private static BigInteger readBigInteger(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null || value.isEmpty()) {
            return BigInteger.ZERO;
        }
        return new BigInteger(value.trim());
    }

    @Override
    protected StringBuilder appendCreateSql(StringBuilder buf, DataFilter dataFilter) {
        buf.append(large ? "CREATE LARGE SEQUENCE " : "CREATE SEQUENCE ");
        buf.append(getName());
        if (!BigInteger.ONE.equals(increment_by)) {
            buf.append(" INCREMENT BY ");
            buf.append(increment_by);
        }
        buf.append(" MINVALUE ");
        buf.append(min_value);
        buf.append(" MAXVALUE ");
        buf.append(max_value);
        if (is_cycled)
            buf.append(" CYCLE");
        if (cache_value.compareTo(BigInteger.ONE) > 0) {
            buf.append(" CACHE ");
            buf.append(cache_value);
        }
        buf.append(" START ");
        buf.append(start_value);
        buf.append(";\n");
        if (dataFilter.dumpData(schema.getName(), name)) {
            buf.append("SELECT setval('");
            buf.append(getName());
            buf.append("',");
            buf.append(last_value);
            buf.append(") ;\n");
        }
        return buf;
    }

    @Override
    protected StringBuilder appendCreateSql(StringBuilder buf) {
        throw new UnsupportedOperationException();
    }
}
