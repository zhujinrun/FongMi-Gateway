package android.database.sqlite;

import android.content.ContentValues;
import android.database.Cursor;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SQLiteDatabase extends SQLiteClosable {

    public interface CursorFactory {
    }

    public static final int OPEN_READWRITE = 0;
    public static final int OPEN_READONLY = 1;

    private final Connection conn;
    private boolean closed;

    private SQLiteDatabase(Connection conn) {
        this.conn = conn;
    }

    public static SQLiteDatabase openDatabase(String path, CursorFactory factory, int flags) {
        try {
            File f = new File(path);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + f.getAbsolutePath());
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS Config (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, type INTEGER, time INTEGER)");
                try (var rs = st.executeQuery("PRAGMA table_info(Config)")) {
                    boolean hasUrl = false, hasK = false;
                    while (rs.next()) {
                        String col = rs.getString("name");
                        if ("url".equalsIgnoreCase(col)) hasUrl = true;
                        if ("k".equalsIgnoreCase(col)) hasK = true;
                    }
                    if (hasK && !hasUrl) {
                        st.execute("DROP TABLE Config");
                        st.execute("CREATE TABLE Config (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, type INTEGER, time INTEGER)");
                    }
                }
            } catch (SQLException ignored) {
            }
            return new SQLiteDatabase(c);
        } catch (Exception e) {
            throw new RuntimeException("openDatabase failed: " + path, e);
        }
    }

    public static SQLiteDatabase openOrCreateDatabase(String path, CursorFactory factory) {
        return openDatabase(path, factory, OPEN_READWRITE);
    }

    public Cursor rawQuery(String sql, String[] selectionArgs) {
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            if (selectionArgs != null) {
                for (int i = 0; i < selectionArgs.length; i++) {
                    ps.setString(i + 1, selectionArgs[i]);
                }
            }
            ResultSet rs = ps.executeQuery();
            return new JdbcCursor(rs);
        } catch (SQLException e) {
            System.err.println("[sqlite] rawQuery fail: " + e.getMessage() + " sql=" + sql);
            return new JdbcCursor(null, e);
        }
    }

    public void execSQL(String sql) {
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void execSQL(String sql, Object[] bindArgs) {
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            if (bindArgs != null) {
                for (int i = 0; i < bindArgs.length; i++) {
                    ps.setObject(i + 1, bindArgs[i]);
                }
            }
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public long insert(String table, String nullColumnHack, ContentValues values) {
        try {
            if (values == null || values.size() == 0) return -1;
            StringBuilder cols = new StringBuilder();
            StringBuilder qs = new StringBuilder();
            List<Object> params = new ArrayList<>();
            for (var e : values.asMap().entrySet()) {
                if (cols.length() > 0) {
                    cols.append(',');
                    qs.append(',');
                }
                cols.append(e.getKey());
                qs.append('?');
                params.add(e.getValue());
            }
            String sql = "INSERT INTO " + table + " (" + cols + ") VALUES (" + qs + ")";
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        try {
            if (values == null || values.size() == 0) return 0;
            StringBuilder sets = new StringBuilder();
            List<Object> params = new ArrayList<>();
            for (var e : values.asMap().entrySet()) {
                if (sets.length() > 0) sets.append(',');
                sets.append(e.getKey()).append("=?");
                params.add(e.getValue());
            }
            StringBuilder sql = new StringBuilder("UPDATE " + table + " SET " + sets);
            if (whereClause != null && !whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
                if (whereArgs != null) {
                    for (String a : whereArgs) params.add(a);
                }
            }
            PreparedStatement ps = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int delete(String table, String whereClause, String[] whereArgs) {
        try {
            StringBuilder sql = new StringBuilder("DELETE FROM " + table);
            List<Object> params = new ArrayList<>();
            if (whereClause != null && !whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
                if (whereArgs != null) {
                    for (String a : whereArgs) params.add(a);
                }
            }
            PreparedStatement ps = conn.prepareStatement(sql.toString());
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isOpen() {
        return !closed;
    }

    @Override
    protected void releaseReference() {
        if (closed) return;
        closed = true;
        try {
            conn.close();
        } catch (SQLException ignored) {
        }
    }

    private static final class JdbcCursor implements Cursor {
        private final ResultSet rs;
        private final SQLException error;
        private final List<String> columns = new ArrayList<>();

        JdbcCursor(ResultSet rs) throws SQLException {
            this.rs = rs;
            this.error = null;
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                columns.add(md.getColumnLabel(i).toLowerCase());
            }
        }

        JdbcCursor(ResultSet rs, SQLException error) {
            this.rs = rs;
            this.error = error;
        }

        @Override
        public boolean moveToFirst() {
            if (rs == null) return false;
            try {
                return rs.isBeforeFirst() && rs.next();
            } catch (SQLException e) {
                return false;
            }
        }

        @Override
        public boolean moveToNext() {
            if (rs == null) return false;
            try {
                return rs.next();
            } catch (SQLException e) {
                return false;
            }
        }

        @Override
        public boolean moveToPosition(int position) {
            if (rs == null) return false;
            try {
                rs.beforeFirst();
                int i = -1;
                while (i < position && rs.next()) i++;
                return i == position;
            } catch (SQLException e) {
                return false;
            }
        }

        @Override
        public int getCount() {
            if (rs == null) return 0;
            try {
                int n = 0;
                boolean before = rs.isBeforeFirst();
                if (before) {
                    while (rs.next()) n++;
                    rs.beforeFirst();
                } else {
                    // best effort
                }
                return n;
            } catch (SQLException e) {
                return 0;
            }
        }

        @Override
        public int getColumnIndex(String columnName) {
            if (rs == null) return -1;
            int i = columns.indexOf(columnName == null ? null : columnName.toLowerCase());
            if (i >= 0) return i;
            for (int j = 0; j < columns.size(); j++) {
                if (columns.get(j).equalsIgnoreCase(columnName)) return j;
            }
            return -1;
        }

        @Override
        public int getColumnIndexOrThrow(String columnName) {
            int i = getColumnIndex(columnName);
            if (i < 0) throw new IllegalArgumentException("column not found: " + columnName);
            return i;
        }

        @Override
        public String getString(int columnIndex) {
            if (rs == null) return null;
            try {
                return rs.getString(columnIndex + 1);
            } catch (SQLException e) {
                return null;
            }
        }

        @Override
        public int getInt(int columnIndex) {
            if (rs == null) return 0;
            try {
                return rs.getInt(columnIndex + 1);
            } catch (SQLException e) {
                return 0;
            }
        }

        @Override
        public long getLong(int columnIndex) {
            if (rs == null) return 0;
            try {
                return rs.getLong(columnIndex + 1);
            } catch (SQLException e) {
                return 0;
            }
        }

        @Override
        public double getDouble(int columnIndex) {
            if (rs == null) return 0;
            try {
                return rs.getDouble(columnIndex + 1);
            } catch (SQLException e) {
                return 0;
            }
        }

        @Override
        public byte[] getBlob(int columnIndex) {
            if (rs == null) return null;
            try {
                return rs.getBytes(columnIndex + 1);
            } catch (SQLException e) {
                return null;
            }
        }

        @Override
        public boolean isNull(int columnIndex) {
            if (rs == null) return true;
            try {
                rs.getObject(columnIndex + 1);
                return rs.wasNull();
            } catch (SQLException e) {
                return true;
            }
        }

        @Override
        public void close() {
            if (rs == null) return;
            try {
                rs.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
