package com.taskmaster.taskmasterfrontend.util;

import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Factoría de botones de menú contextual con icono de tres puntos (⋯).
 *
 * <p>Centraliza la creación de botones que abren un {@link ContextMenu}
 * con opciones estándar de Editar y Eliminar, manteniendo la coherencia
 * visual y de comportamiento en toda la aplicación.</p>
 *
 * <p><b>Nota de diseño:</b> el {@link ContextMenu} se crea en cada clic
 * en lugar de reutilizarse, para evitar problemas de estado cuando el mismo
 * menú se muestra en múltiples instancias simultáneas (p.ej. lista de tareas).</p>
 *
 * @author Carlos
 */
public final class MenuButtonFactory {

    /** Clase de utilidad, no instanciable. */
    private MenuButtonFactory() {}

    /**
     * Crea un botón con icono de tres puntos horizontales que, al pulsarse,
     * abre un menú contextual con las opciones Editar y Eliminar.
     *
     * <p>Clases CSS aplicadas:</p>
     * <ul>
     *   <li>{@code task-menu-btn} - estilo del botón contenedor</li>
     *   <li>{@code task-menu-btn-icon} - estilo del icono de tres puntos</li>
     *   <li>{@code menu-item-danger} - estilo rojo para el item Eliminar</li>
     * </ul>
     *
     * @param editLabel   etiqueta localizada del item Editar
     * @param deleteLabel etiqueta localizada del item Eliminar
     * @param onEdit      acción a ejecutar al pulsar Editar
     * @param onDelete    acción a ejecutar al pulsar Eliminar
     * @return botón configurado y listo para añadir al layout
     */
    public static Button createEditDeleteMenu(String editLabel, String deleteLabel,
                                              Runnable onEdit, Runnable onDelete) {
        Button menuBtn = new Button();
        menuBtn.getStyleClass().add("task-menu-btn");

        // Icono de tres puntos horizontales como indicador visual de menú
        FontIcon dotsIcon = new FontIcon(IconCatalog.UI_MORE);
        dotsIcon.getStyleClass().add("task-menu-btn-icon");
        menuBtn.setGraphic(dotsIcon);

        menuBtn.setOnAction(e -> {
            // Creamos el menú en cada clic para evitar problemas de estado
            // cuando hay múltiples botones visibles simultáneamente
            ContextMenu menu = new ContextMenu();

            MenuItem edit = new MenuItem(editLabel);
            edit.setGraphic(new FontIcon(IconCatalog.ACTION_EDIT));
            // Delegamos la acción al Runnable recibido como parámetro
            edit.setOnAction(ev -> onEdit.run());

            MenuItem delete = new MenuItem(deleteLabel);
            delete.setGraphic(new FontIcon(IconCatalog.ACTION_DELETE));
            // Estilo rojo para indicar que es una acción destructiva
            delete.getStyleClass().add("menu-item-danger");
            delete.setOnAction(ev -> onDelete.run());

            menu.getItems().addAll(edit, delete);
            // Mostramos el menú justo debajo del botón
            menu.show(menuBtn, Side.BOTTOM, 0, 0);
        });

        return menuBtn;
    }
}