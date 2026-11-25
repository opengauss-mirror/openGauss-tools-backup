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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class CachingDBOFactory<T extends DbBackupObject> implements DBOFactory<T> {

    protected final Schema.CachingSchemaFactory schemaFactory;

    protected Map<String,Map<String,T>> map = null;

    protected CachingDBOFactory(Schema.CachingSchemaFactory schemaFactory) {
        this.schemaFactory = schemaFactory;
    }

    @Override
    public Iterable<T> getDbBackupObjects(Connection con, Schema schema) throws SQLException {
        if (map == null) {
            loadMap(con);
        }
        Map<String,T> dbos = map.get(schema.getName());
        if (dbos == null) return Collections.emptyList();
        else return Collections.unmodifiableCollection(dbos.values());
    }

    @Override
    public T getDbBackupObject(Connection con, String name, Schema schema) throws SQLException {
        if (schema == null) {
            return null;
        }
        if (map == null) {
            loadMap(con);
        }
        Map<String,T> dbos = map.get(schema.getName());
        return dbos == null ? null : dbos.get(name);
    }

    protected void loadMap(Connection con) throws SQLException {
        map = new HashMap<String,Map<String,T>>();
        PreparedStatement stmt = null;
        try {
            stmt = getAllStatement(con);
            ZipBackup.debug("loading map in " + CachingDBOFactory.this.getClass());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Schema schema = schemaFactory.getFromCurrentBatch(rs.getInt("schema_oid"));
                if (schema == null) continue;
                Map<String,T> dbos = map.get(schema.getName());
                if (dbos == null) {
                    dbos = new HashMap<String,T>();
                    map.put(schema.getName(), dbos);
                }
                T dbo = newDbBackupObject(con, rs, schema);
                dbos.put(dbo.getName(), dbo);
            }
            rs.close();
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    protected abstract PreparedStatement getAllStatement(Connection con) throws SQLException;

    protected abstract T newDbBackupObject(Connection con, ResultSet rs, Schema schema) throws SQLException;

}
