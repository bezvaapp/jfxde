package dev.jfxde.fxmisc.richtext;

import static org.fxmisc.wellbehaved.event.EventPattern.keyPressed;
import static org.fxmisc.wellbehaved.event.InputMap.consume;
import static org.fxmisc.wellbehaved.event.InputMap.sequence;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;

import dev.jfxde.jfx.util.FXResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

public class ContextMenuBuilder {

    private final GenericStyledArea<?, ?, ?> area;

    private ContextMenuBuilder(GenericStyledArea<?, ?, ?> area) {
        this.area = area;
    }

    public static ContextMenuBuilder get(GenericStyledArea<?, ?, ?> area) {
        ContextMenu menu = new ContextMenu();
        menu.setAutoHide(true);
        menu.setHideOnEscape(true);
        menu.setConsumeAutoHidingEvents(true);
        menu.addEventHandler(KeyEvent.ANY, e -> e.consume());
        menu.setOnShown(e -> area.requestFocus());
        menu.setOnHidden(e -> area.requestFocus());
        area.setContextMenu(menu);
        return new ContextMenuBuilder(area);
    }

    @SuppressWarnings("unchecked")
    public ContextMenuBuilder addAll(MenuItem... items) {
        area.getContextMenu().getItems().addAll(items);

        Nodes.addInputMap(area, sequence(
                Arrays.stream(items)
                .map(item -> consume(keyPressed(item.getAccelerator()).onlyIf(e -> !item.isDisable()), e -> item.fire()))
                .collect(Collectors.toList()).toArray(new InputMap[] {})));

        return this;
    }

    public ContextMenuBuilder add(MenuItem item) {
        area.getContextMenu().getItems().add(item);

        Nodes.addInputMap(area, sequence(
                consume(keyPressed(item.getAccelerator()).onlyIf(e -> !item.isDisable()), e -> item.fire())));

        return this;
    }

    public ContextMenuBuilder copy() {
        MenuItem item = new MenuItem();
        item.setAccelerator(KeyCombination.keyCombination("Shortcut+C"));
        FXResourceBundle.getBundle().put(item.textProperty(), "copy");
        item.setOnAction(e -> area.copy());
        item.disableProperty().bind(Bindings.createBooleanBinding(() -> area.getSelection().getLength() == 0, area.selectionProperty()));

        area.getContextMenu().getItems().add(item);

        return this;
    }

    public ContextMenuBuilder cut() {
        MenuItem item = new MenuItem();
        item.setAccelerator(KeyCombination.keyCombination("Shortcut+X"));
        FXResourceBundle.getBundle().put(item.textProperty(), "cut");
        item.setOnAction(e -> area.cut());
        item.disableProperty().bind(Bindings.createBooleanBinding(() -> area.getSelection().getLength() == 0, area.selectionProperty()));

        area.getContextMenu().getItems().add(item);

        return this;
    }

    public ContextMenuBuilder paste() {

        MenuItem item = new MenuItem();
        item.setAccelerator(KeyCombination.keyCombination("Shortcut+V"));
        FXResourceBundle.getBundle().put(item.textProperty(), "paste");
        item.setOnAction(e -> area.paste());

        area.getContextMenu().setOnShowing(e -> {
            item.setDisable(!Clipboard.getSystemClipboard().hasString());
        });
        area.getContextMenu().getItems().add(item);

        return this;
    }

    public ContextMenuBuilder selectAll() {
        MenuItem item = new MenuItem();
        item.setAccelerator(KeyCombination.keyCombination("Shortcut+A"));
        FXResourceBundle.getBundle().put(item.textProperty(), "selectAll");
        item.setOnAction(e -> area.selectAll());
        item.disableProperty().bind(Bindings.createBooleanBinding(() -> area.getSelectedText().length() == area.getText().length(),
                area.selectedTextProperty()));
        area.getContextMenu().getItems().add(item);

        return this;
    }

    public ContextMenuBuilder undo() {
        MenuItem item = new MenuItem();
        item.setAccelerator(KeyCombination.keyCombination("Shortcut+Z"));
        FXResourceBundle.getBundle().put(item.textProperty(), "undo");
        item.setOnAction(e -> area.undo());
        // item.disableProperty().bind(Bindings.createBooleanBinding(() ->
        // !area.getUndoManager().isUndoAvailable(),
        // area.getUndoManager().undoAvailableProperty()));
        item.setDisable(true);
        area.getUndoManager().undoAvailableProperty().addListener((v, o, n) -> item.setDisable(n == null || !(Boolean) n));
        area.getContextMenu().getItems().add(item);

        return this;
    }

    public ContextMenuBuilder redo() {
        MenuItem item = new MenuItem();
        item.setAccelerator(KeyCombination.keyCombination("Shortcut+Y"));
        FXResourceBundle.getBundle().put(item.textProperty(), "redo");
        item.setOnAction(e -> area.redo());
        // item.disableProperty().bind(Bindings.createBooleanBinding(() ->
        // !area.getUndoManager().isRedoAvailable(),
        // area.getUndoManager().redoAvailableProperty()));
        item.setDisable(true);
        area.getUndoManager().redoAvailableProperty().addListener((v, o, n) -> item.setDisable(n == null || !(Boolean) n));
        area.getContextMenu().getItems().add(item);

        return this;
    }

    public ContextMenuBuilder separator() {

        area.getContextMenu().getItems().add(new SeparatorMenuItem());

        return this;
    }

    public ContextMenuBuilder clear() {
        MenuItem item = new MenuItem();
        FXResourceBundle.getBundle().put(item.textProperty(), "clear");
        item.setOnAction(e -> area.clear());
        item.disableProperty().bind(Bindings.createBooleanBinding(() -> area.getLength() == 0, area.lengthProperty()));

        area.getContextMenu().getItems().add(item);

        return this;
    }

    public ContextMenuBuilder clear(EventHandler<ActionEvent> handler) {
        MenuItem item = new MenuItem();
        FXResourceBundle.getBundle().put(item.textProperty(), "clear");
        item.setOnAction(handler);
        item.disableProperty().bind(Bindings.createBooleanBinding(() -> area.getLength() == 0, area.lengthProperty()));

        area.getContextMenu().getItems().add(item);

        return this;
    }
}
