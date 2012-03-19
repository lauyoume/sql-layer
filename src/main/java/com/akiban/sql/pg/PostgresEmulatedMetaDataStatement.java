/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.sql.pg;

import com.akiban.server.error.UnsupportedParametersException;
import com.akiban.server.types.AkType;
import com.akiban.sql.server.ServerValueEncoder;

import java.io.IOException;
import java.io.ByteArrayOutputStream;

/**
 * Canned handling for fixed SQL text that comes from tools that
 * believe they are talking to a real Postgres database.
 */
public class PostgresEmulatedMetaDataStatement implements PostgresStatement
{
    enum Query {
        // ODBC driver sends this at the start; returning no rows is fine (and normal).
        ODBC_LO_TYPE_QUERY("select oid, typbasetype from pg_type where typname = 'lo'"),
        // SEQUEL 3.33.0 (http://sequel.rubyforge.org/) sends this when opening a new connection
        SEQUEL_B_TYPE_QUERY("select oid, typname from pg_type where typtype = 'b'");

        // TODO: May need regex for some cases.
        private String sql;

        Query(String sql) {
            this.sql = sql;
        }

        public String getSQL() {
            return sql;
        }
    }

    private Query query;

    protected PostgresEmulatedMetaDataStatement(Query query) {
        this.query = query;
    }

    static final PostgresType OID_PG_TYPE = 
        new PostgresType(PostgresType.TypeOid.OID_TYPE_OID.getOid(), (short)4, -1, AkType.LONG);
    static final PostgresType TYPNAME_PG_TYPE = 
        new PostgresType(PostgresType.TypeOid.NAME_TYPE_OID.getOid(), (short)255, -1, AkType.VARCHAR);

    @Override
    public PostgresType[] getParameterTypes() {
        return null;
    }

    @Override
    public void sendDescription(PostgresQueryContext context, boolean always) 
            throws IOException {
        int ncols;
        String[] names;
        PostgresType[] types;
        switch (query) {
        case ODBC_LO_TYPE_QUERY:
            ncols = 2;
            names = new String[] { "oid", "typbasetype" };
            types = new PostgresType[] { OID_PG_TYPE, OID_PG_TYPE };
            break;
        case SEQUEL_B_TYPE_QUERY:
            ncols = 2;
            names = new String[] { "oid", "typname" };
            types = new PostgresType[] { OID_PG_TYPE, TYPNAME_PG_TYPE };
            break;
        default:
            return;
        }

        PostgresServerSession server = context.getServer();
        PostgresMessenger messenger = server.getMessenger();
        messenger.beginMessage(PostgresMessages.ROW_DESCRIPTION_TYPE.code());
        messenger.writeShort(ncols);
        for (int i = 0; i < ncols; i++) {
            PostgresType type = types[i];
            messenger.writeString(names[i]); // attname
            messenger.writeInt(0);    // attrelid
            messenger.writeShort(0);  // attnum
            messenger.writeInt(type.getOid()); // atttypid
            messenger.writeShort(type.getLength()); // attlen
            messenger.writeInt(type.getModifier()); // atttypmod
            messenger.writeShort(0);
        }
        messenger.sendMessage();
    }

    @Override
    public TransactionMode getTransactionMode() {
        return TransactionMode.READ;
    }

    @Override
    public int execute(PostgresQueryContext context, int maxrows) throws IOException {
        PostgresServerSession server = context.getServer();
        PostgresMessenger messenger = server.getMessenger();
        int nrows = 0;
        switch (query) {
        case ODBC_LO_TYPE_QUERY:
            nrows = odbcLoTypeQuery(messenger, maxrows);
            break;
        case SEQUEL_B_TYPE_QUERY:
            nrows = sequelBTypeQuery(messenger, maxrows);
            break;
        }
        {        
          messenger.beginMessage(PostgresMessages.COMMAND_COMPLETE_TYPE.code());
          messenger.writeString("SELECT " + nrows);
          messenger.sendMessage();
        }
        return nrows;
    }

    private int odbcLoTypeQuery(PostgresMessenger messenger, int maxrows) {
        return 0;
    }

    private int sequelBTypeQuery(PostgresMessenger messenger, int maxrows) throws IOException {
        int nrows = 0;
        ServerValueEncoder encoder = new ServerValueEncoder(messenger.getEncoding());
    	for (PostgresType.TypeOid pgtype : PostgresType.TypeOid.values()) {
            if (pgtype.getType() == PostgresType.TypeOid.TypType.BASE) {
                messenger.beginMessage(PostgresMessages.DATA_ROW_TYPE.code());
                messenger.writeShort(2); // 2 columns for this query
                ByteArrayOutputStream bytes = encoder.encodeObject(pgtype.getOid(), OID_PG_TYPE, false);
                if (bytes == null) {
                    messenger.writeInt(-1);
                } else {
                    messenger.writeInt(bytes.size());
                    messenger.writeByteStream(bytes);
                }
                bytes = encoder.encodeObject(pgtype.getName(), TYPNAME_PG_TYPE, false);
                if (bytes == null) {
                    messenger.writeInt(-1);
                } else {
                    messenger.writeInt(bytes.size());
                    messenger.writeByteStream(bytes);
                }
                messenger.sendMessage();
                nrows++;
                if ((maxrows > 0) && (nrows >= maxrows)) {
                    break;
                }
            }
        }
        return nrows;
    }

}
