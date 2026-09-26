package android.os;

public final class StrictMode {

    private StrictMode() {
    }

    public static void setThreadPolicy(ThreadPolicy policy) {
    }

    public static ThreadPolicy getThreadPolicy() {
        return new ThreadPolicy.Builder().build();
    }

    public static void setVmPolicy(VmPolicy policy) {
    }

    public static final class ThreadPolicy {

        ThreadPolicy() {
        }

        public static final class Builder {

            public Builder() {
            }

            public Builder permitAll() {
                return this;
            }

            public Builder penaltyLog() {
                return this;
            }

            public Builder detectNetwork() {
                return this;
            }

            public Builder detectDiskReads() {
                return this;
            }

            public Builder detectDiskWrites() {
                return this;
            }

            public ThreadPolicy build() {
                return new ThreadPolicy();
            }
        }
    }

    public static final class VmPolicy {

        VmPolicy() {
        }

        public static final class Builder {

            public Builder() {
            }

            public Builder penaltyLog() {
                return this;
            }

            public VmPolicy build() {
                return new VmPolicy();
            }
        }
    }
}
