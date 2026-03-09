module com.taskmaster.taskmasterfrontend {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;

    opens com.taskmaster.taskmasterfrontend to javafx.fxml;
    exports com.taskmaster.taskmasterfrontend;
}