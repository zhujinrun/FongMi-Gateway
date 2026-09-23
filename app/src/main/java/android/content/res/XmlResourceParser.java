package android.content.res;

public interface XmlResourceParser extends java.io.Closeable {
    int getAttributeCount();

    String getAttributeName(int index);

    String getAttributeValue(int index);

    String getAttributeValue(String namespace, String name);

    int getAttributeNameResource(int index);

    int getAttributeResourceValue(String namespace, String name, int defaultValue);

    boolean next();

    int nextToken();

    int getEventType();

    String getName();

    void close();
}
