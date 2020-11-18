module com.dlsc.gemsfx {
    requires transitive javafx.controls;
    requires javafx.swing;

    requires org.kordamp.iconli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign;

    requires java.logging;
    requires java.prefs;

    requires commons.validator;
    requires org.apache.commons.lang3;
    requires org.controlsfx.controls;

    requires retrofit2;
    requires okhttp3;
    requires java.desktop;

    exports com.dlsc.gemsfx.util;
    exports com.dlsc.gemsfx;
    exports com.dlsc.gemsfx.richtextarea;
}