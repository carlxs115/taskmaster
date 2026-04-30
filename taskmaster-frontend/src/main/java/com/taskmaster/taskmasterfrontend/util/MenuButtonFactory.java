package com.taskmaster.taskmasterfrontend.util;

import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Factoría de botones de menú contextual con tres puntos (•••).
 *
 * <p>Centraliza la creación de botones que abren un {@link ContextMenu}
 * con opciones estándar de Editar y Eliminar, manteniendo la coherencia
 * visual y de comportamiento en toda la aplicación.</p>
 *
 * @author Carlos
 */
public final class MenuButtonFactory {

    private MenuButtonFactory() {}

    /**
     * Crea un botón con icono de tres puntos horizontales que, al pulsarse,
     * abre un menú contextual con las opciones Editar y Eliminar.
     *
     * <p>Internamente aplica las clases CSS {@code task-menu-btn},
     * {@code task-menu-btn-icon} y {@code menu-item-danger} para que el
     * estilo sea consistente con el resto de la app.</p>
     *
     * @param editLabel   Etiqueta localizada del item Editar.
     * @param deleteLabel Etiqueta localizada del item Eliminar.
     * @param onEdit      Acción a ejecutar al pulsar Editar.
     * @param onDelete    Acción a ejecutar al pulsar Eliminar.
     * @return Botón configurado y listo para añadir al layout.
     */
    public static Button createEditDeleteMenu(String editLabel, String deleteLabel,
                                              Runnable onEdit, Runnable onDelete) {
        Button menuBtn = new Button();
        menuBtn.getStyleClass().add("task-menu-btn");

        FontIcon dotsIcon = new FontIcon("fas-ellipsis-h");
        dotsIcon.getStyleClass().add("task-menu-btn-icon");
        menuBtn.setGraphic(dotsIcon);

        menuBtn.setOnAction(e -> {
            ContextMenu menu = new ContextMenu();

            MenuItem edit = new MenuItem(editLabel);
            edit.setGraphic(new FontIcon("fas-pen"));
            edit.setOnAction(ev -> onEdit.run());

            MenuItem delete = new MenuItem(deleteLabel);
            delete.setGraphic(new FontIcon("fas-trash"));
            delete.getStyleClass().add("menu-item-danger");
            delete.setOnAction(ev -> onDelete.run());

            menu.getItems().addAll(edit, delete);
            menu.show(menuBtn, javafx.geometry.Side.BOTTOM, 0, 0);
        });

        return menuBtn;
    }
}
