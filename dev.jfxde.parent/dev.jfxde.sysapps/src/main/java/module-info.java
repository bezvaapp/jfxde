module dev.jfxde.sysapps {
    requires java.management;
    requires javafx.controls;
    requires javafx.graphics;
    requires javafx.base;
    requires javafx.web;
    requires richtextfx;
    requires flowless;
    requires reactfx;
    requires wellbehavedfx;
    requires org.controlsfx.controls;
    requires jdk.jshell;

    requires dev.jfxde.api;
    requires dev.jfxde.logic;
    requires dev.jfxde.ui;

    provides dev.jfxde.api.App with dev.jfxde.sysapps.appmanager.AppManagerApp,
            dev.jfxde.sysapps.console.ConsoleApp, dev.jfxde.sysapps.exceptionlog.ExceptionLogApp,
            dev.jfxde.sysapps.jvmmonitor.JvmMonitorApp, dev.jfxde.sysapps.jshell.JShellApp, dev.jfxde.sysapps.settings.SettingsApp;

    opens dev.jfxde.sysapps.appmanager.bundles;
    opens dev.jfxde.sysapps.console.bundles;
    opens dev.jfxde.sysapps.console.css;
    opens dev.jfxde.sysapps.exceptionlog.bundles;
    opens dev.jfxde.sysapps.jvmmonitor.bundles;
    opens dev.jfxde.sysapps.jshell.bundles;
    opens dev.jfxde.sysapps.jshell.css;
}
