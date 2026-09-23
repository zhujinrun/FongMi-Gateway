package android.database;

public interface Cursor extends java.io.Closeable {
    boolean moveToFirst();
    boolean moveToNext();
    boolean moveToPosition(int position);
    int getCount();
    int getColumnIndex(String columnName);
    int getColumnIndexOrThrow(String columnName);
    String getString(int columnIndex);
    int getInt(int columnIndex);
    long getLong(int columnIndex);
    double getDouble(int columnIndex);
    byte[] getBlob(int columnIndex);
    boolean isNull(int columnIndex);
    void close();
}
