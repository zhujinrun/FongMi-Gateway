package android.content;

public class ClipData {
    private CharSequence label;
    private Item item;

    public ClipData(CharSequence label, String[] mimeTypes, Item item) {
        this.label = label;
        this.item = item;
    }

    public static ClipData newPlainText(CharSequence label, CharSequence text) {
        return new ClipData(label, new String[]{"text/plain"}, new Item(text));
    }

    public static ClipData newRawUri(CharSequence label, android.net.Uri uri) {
        return new ClipData(label, new String[]{"text/uri-list"}, new Item(uri));
    }

    public Item getItemAt(int index) {
        return item;
    }

    public int getItemCount() {
        return item == null ? 0 : 1;
    }

    public CharSequence getDescription() {
        return label;
    }

    public static class Item {
        private final CharSequence text;
        private final android.net.Uri uri;

        public Item(CharSequence text) {
            this.text = text;
            this.uri = null;
        }

        public Item(android.net.Uri uri) {
            this.text = null;
            this.uri = uri;
        }

        public CharSequence getText() {
            return text;
        }

        public android.net.Uri getUri() {
            return uri;
        }
    }
}
