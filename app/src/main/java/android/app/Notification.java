package android.app;

public class Notification {
    public static class Builder {
        public Builder(android.content.Context context, String channelId) {
        }

        public Builder setContentTitle(CharSequence title) {
            return this;
        }

        public Builder setContentText(CharSequence text) {
            return this;
        }

        public Builder setSmallIcon(int icon) {
            return this;
        }

        public Notification build() {
            return new Notification();
        }
    }
}
