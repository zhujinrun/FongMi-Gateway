package android.database.sqlite;

public abstract class SQLiteClosable {
    public void close() {
        releaseReference();
    }

    protected abstract void releaseReference();

    public void acquireReference() {
    }
}
