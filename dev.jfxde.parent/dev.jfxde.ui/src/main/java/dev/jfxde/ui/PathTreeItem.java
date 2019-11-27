package dev.jfxde.ui;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import dev.jfxde.jfx.application.XPlatform;
import dev.jfxde.jfx.embed.swing.FXUtils;
import dev.jfxde.logic.data.FXPath;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;

public class PathTreeItem extends TreeItem<FXPath> {

    private Function<FXPath, Node> graphicFactory;
    private boolean dirOnly;
    private ObservableList<TreeItem<FXPath>> allChildren = FXCollections.observableArrayList((i) -> new Observable[] { i.getValue().nameProperty() });
    private ObservableList<TreeItem<FXPath>> sortedChildren;
    private boolean loaded;

    private ListChangeListener<FXPath> pathListener = (Change<? extends FXPath> c) -> {

        while (c.next()) {

            if (c.wasAdded()) {
                addItems(c.getAddedSubList());
            } else if (c.wasRemoved()) {
                removeItems(c.getRemoved());
            }
        }
    };

    public PathTreeItem(FXPath path) {
        this(path, p -> FXUtils.getIcon(p.getPath()), false);
    }

    public PathTreeItem(FXPath path, boolean dirOnly) {
        this(path, p -> FXUtils.getIcon(p.getPath()), dirOnly);
    }

    public PathTreeItem(FXPath path, Function<FXPath, Node> graphicFactory, boolean dirOnly) {
        super(path);
        this.graphicFactory = graphicFactory;
        this.dirOnly = dirOnly;
        setGraphic(graphicFactory.apply(path));

        if (getValue().isLoaded()) {
            loaded = true;
            Platform.runLater(() -> {
                load(getValue().getPaths());
            });
        }

    }

    private void setListeners() {

        var filteredChildren = dirOnly ? new FilteredList<>(allChildren, i -> i.getValue().isDirectory()) : allChildren;
        sortedChildren = new SortedList<>(filteredChildren, Comparator.comparing(i -> i.getValue()));
        Bindings.bindContent(super.getChildren(), sortedChildren);

        getValue().getPaths().addListener(pathListener);
    }

    private void load(List<? extends FXPath> paths) {
        XPlatform.runFX(() -> {
            addItems(paths);
            setListeners();
        });
    }

    private void addItems(List<? extends FXPath> paths) {
        XPlatform.runFX(() -> paths.stream()
                .map(p -> new PathTreeItem(p, graphicFactory, dirOnly))
                .forEach(i -> allChildren.add(i)));
    }

    private void removeItems(List<? extends FXPath> paths) {
        XPlatform.runFX(() -> {
            allChildren.removeIf(i -> ((PathTreeItem) i).remove(paths));

            if (super.getChildren().isEmpty() && getParent() != null) {
                setExpanded(false);
                loaded = false;
                getValue().getPaths().removeListener(pathListener);
            }
        });
    }

    private boolean remove(List<? extends FXPath> paths) {
        boolean remove = paths.contains(getValue());
        if (remove) {
            getValue().getPaths().removeListener(pathListener);
        }

        return remove;
    }

    @Override
    public ObservableList<TreeItem<FXPath>> getChildren() {
        if (!loaded) {
            loaded = true;
            getValue().load(this::load);
        }

        return super.getChildren();
    }

    public ObservableList<TreeItem<FXPath>> getAllChildren() {
        if (!loaded) {
            loaded = true;
            getValue().load(this::load);
        }

        return allChildren;
    }

    @Override
    public boolean isLeaf() {
        return dirOnly ? getValue().isDirLeaf() : getValue().isLeaf();
    }

    @Override
    public String toString() {
        return getValue().toString();
    }
}
