package android.os;

import java.io.File;

public class StatFs {
    private final String path;

    public StatFs(String path) {
        this.path = path;
    }

    public StatFs(File path) {
        this.path = path.getAbsolutePath();
    }

    public long getBlockSizeLong() {
        return 4096;
    }

    public long getBlockCountLong() {
        File f = new File(path);
        long free = f.getUsableSpace();
        return Math.max(1, free / 4096);
    }

    public long getAvailableBlocksLong() {
        return getBlockCountLong();
    }

    public long getFreeBlocksLong() {
        return getBlockCountLong();
    }

    public int getBlockSize() {
        return 4096;
    }

    public int getBlockCount() {
        return (int) Math.min(Integer.MAX_VALUE, getBlockCountLong());
    }
}
