package io.github.ariinyume.fkuxmtv;

import android.app.Application;

/**
 * Every process start is a chance to notice that the accessibility grant is gone: the listener
 * bind, the sync binder, a boot broadcast, or the user opening the app. Checking here means the
 * repair does not depend on which of those happened.
 */
public final class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Revival.onProcessStart(this);
    }
}
