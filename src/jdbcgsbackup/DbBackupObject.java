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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class DbBackupObject {

    protected final String name;
    protected final Schema schema;
    protected final String owner;

    protected DbBackupObject(String name, Schema schema, String owner) {
        this.name = name;
        this.schema = schema;
        this.owner = owner;
    }

    final String getName() {
        String regex = "^[0-9a-z_]{1,}$";
        Matcher ma = Pattern.compile(regex).matcher(name);
        if (ma.matches()) {
            return name;
        }
        return "\"" + name + "\"";
    }

    String getFullname() {
        return schema.getName() + "." + getName();
    }

    final String getOwner() {
        return owner;
    }

    String getSql(DataFilter dataFilter) {
        StringBuilder buf = new StringBuilder();
        appendCreateSql(buf, dataFilter);
        return buf.toString();
    }

    protected abstract StringBuilder appendCreateSql(StringBuilder buf);
    
    protected StringBuilder appendCreateSql(StringBuilder buf, DataFilter dataFilter) {
        return appendCreateSql(buf);
    }

}
