package android.content;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public interface SharedPreferences {

    int MODE_PRIVATE = 0;
    int MODE_WORLD_READABLE = 1;
    int MODE_WORLD_WRITEABLE = 2;
    int MODE_MULTI_PROCESS = 4;

    String getString(String key, String defValue);

    int getInt(String key, int defValue);

    long getLong(String key, long defValue);

    float getFloat(String key, float defValue);

    boolean getBoolean(String key, boolean defValue);

    boolean contains(String key);

    Map<String, ?> getAll();

    Editor edit();

    interface Editor {
        Editor putString(String key, String value);

        Editor putInt(String key, int value);

        Editor putLong(String key, long value);

        Editor putFloat(String key, float value);

        Editor putBoolean(String key, boolean value);

        Editor remove(String key);

        Editor clear();

        boolean commit();

        void apply();
    }

    interface OnSharedPreferenceChangeListener {
        void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key);
    }

    final class Impl implements SharedPreferences {
        private final File dir;
        private final String name;
        private final Properties props = new Properties();

        public Impl(File dir, String name) {
            this.dir = dir;
            this.name = name;
            load();
        }

        private File file() {
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, name + ".xml");
        }

        private void load() {
            try (InputStream in = new FileInputStream(file())) {
                props.loadFromXML(in);
            } catch (IOException ignored) {
            }
        }

        private void save() {
            try (OutputStream out = new FileOutputStream(file())) {
                props.storeToXML(out, null, "UTF-8");
            } catch (IOException ignored) {
            }
        }

        @Override
        public String getString(String key, String defValue) {
            return props.getProperty(key, defValue);
        }

        @Override
        public int getInt(String key, int defValue) {
            try {
                return Integer.parseInt(props.getProperty(key, String.valueOf(defValue)));
            } catch (Exception e) {
                return defValue;
            }
        }

        @Override
        public long getLong(String key, long defValue) {
            try {
                return Long.parseLong(props.getProperty(key, String.valueOf(defValue)));
            } catch (Exception e) {
                return defValue;
            }
        }

        @Override
        public float getFloat(String key, float defValue) {
            try {
                return Float.parseFloat(props.getProperty(key, String.valueOf(defValue)));
            } catch (Exception e) {
                return defValue;
            }
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            String v = props.getProperty(key);
            return v == null ? defValue : Boolean.parseBoolean(v);
        }

        @Override
        public boolean contains(String key) {
            return props.containsKey(key);
        }

        @Override
        public Map<String, ?> getAll() {
            Map<String, Object> map = new HashMap<>();
            for (String k : props.stringPropertyNames()) map.put(k, props.getProperty(k));
            return map;
        }

        @Override
        public Editor edit() {
            return new EditImpl();
        }

        private final class EditImpl implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            private final java.util.Set<String> removals = new java.util.HashSet<>();
            private boolean clear;

            @Override
            public Editor putString(String key, String value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                pending.put(key, String.valueOf(value));
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                pending.put(key, String.valueOf(value));
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                pending.put(key, String.valueOf(value));
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                pending.put(key, String.valueOf(value));
                return this;
            }

            @Override
            public Editor remove(String key) {
                removals.add(key);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                return this;
            }

            @Override
            public boolean commit() {
                apply();
                return true;
            }

            @Override
            public void apply() {
                if (clear) props.clear();
                for (String k : removals) props.remove(k);
                props.putAll(pending);
                save();
            }
        }
    }
}
