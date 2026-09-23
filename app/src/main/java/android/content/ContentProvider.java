package android.content;

public abstract class ContentProvider {

    public boolean onCreate() {
        return true;
    }

    public abstract android.database.Cursor query(android.net.Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder);

    public abstract String getType(android.net.Uri uri);

    public abstract android.net.Uri insert(android.net.Uri uri, ContentValues values);

    public abstract int delete(android.net.Uri uri, String selection, String[] selectionArgs);

    public abstract int update(android.net.Uri uri, ContentValues values, String selection, String[] selectionArgs);

    public Context getContext() {
        return null;
    }
}
