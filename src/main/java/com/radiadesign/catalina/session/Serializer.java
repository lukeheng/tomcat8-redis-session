package com.radiadesign.catalina.session;

/**
 * Created by lenovo on 2016/9/21.
 */

import javax.servlet.http.HttpSession;
import java.io.IOException;

public interface Serializer {
    void setClassLoader(ClassLoader var1);

    byte[] serializeFrom(HttpSession var1) throws IOException;

    HttpSession deserializeInto(byte[] var1, HttpSession var2) throws IOException, ClassNotFoundException;
}