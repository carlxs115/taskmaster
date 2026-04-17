module com.taskmaster.taskmasterfrontend {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.controlsfx.controls;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;
    requires java.desktop;
    requires javafx.swing;

    opens com.taskmaster.taskmasterfrontend to javafx.fxml;
    opens com.taskmaster.taskmasterfrontend.controller to javafx.fxml;
    opens com.taskmaster.taskmasterfrontend.util to com.fasterxml.jackson.databind;

    exports com.taskmaster.taskmasterfrontend;
    exports com.taskmaster.taskmasterfrontend.controller;
}