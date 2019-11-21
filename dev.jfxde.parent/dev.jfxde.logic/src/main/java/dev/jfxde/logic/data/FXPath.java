package dev.jfxde.logic.data;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import dev.jfxde.j.nio.file.WatchServiceRegister;
import dev.jfxde.j.nio.file.XFiles;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener.Change;
import javafx.collections.ObservableList;

public class FXPath implements Comparable<FXPath> {

    private static final Path ROOT_PATH = Path.of(File.separator);
    private final static Map<Path, WeakReference<FXPath>> CACHE = new WeakHashMap<>();
    private static WatchServiceRegister watchServiceRegister;
    private static final ReentrantLock LOCK = new ReentrantLock();

    private Consumer<List<WatchEvent<?>>> directoryWatcher;
    private List<Predicate<FXPath>> onDelete = new ArrayList<>();
    private List<Consumer<FXPath>> onDeleted = new ArrayList<>();
    private List<Consumer<FXPath>> onDeletedExternally = new ArrayList<>();
    private static List<WeakReference<Consumer<FXPath>>> onDeletedGlobally = new ArrayList<>();
    private List<Consumer<FXPath>> onModified = new ArrayList<>();

    private ObjectProperty<Path> path = new SimpleObjectProperty<Path>();
    private StringProperty name;
    private String newName;
    private BooleanProperty directory = new SimpleBooleanProperty();
    private Set<FXPath> parents = new HashSet<>();
    private ObservableList<FXPath> searchPaths = FXCollections.observableArrayList();
    private ObservableList<FXPath> paths = FXCollections.observableArrayList();
    private volatile boolean searching;
    private volatile boolean loading;
    private volatile boolean loaded;
    private AtomicBoolean dirLeaf;
    private AtomicBoolean leaf;
    private FXBasicFileAttributes basicFileAttributes;

    private FXPath() {
        setListeners();
    }

    private FXPath(FXPath parent, Path path, boolean dir) {
        if (parent != null) {
            this.parents.add(parent);
        }

        setListeners();

        setPath(path);
        setDirectory(dir);
        if (dir) {
            directoryWatcher = this::watchDirectory;
        }

        setFileAttributes();
    }

    private void setListeners() {
        path.addListener((v, o, n) -> {
            if (n != null) {
                Path fileName = n.getFileName();
                setName(fileName == null ? n.toString() : fileName.toString());
            }
        });

        searchPaths.addListener((Change<? extends FXPath> c) -> {
            if (searching) {
                return;
            }

            while (c.next()) {

                if (c.wasAdded()) {
                    paths.addAll(c.getAddedSubList());
                } else if (c.wasRemoved()) {
                    paths.removeAll(c.getRemoved());
                }
            }
        });
    }

    public static void setWatchServiceRegister(WatchServiceRegister watchServiceRegister) {
        FXPath.watchServiceRegister = watchServiceRegister;
    }

    static Lock getLock() {
        return LOCK;
    }

    public List<Consumer<FXPath>> getOnModified() {
        return onModified;
    }

    public List<Predicate<FXPath>> getOnDelete() {
        return onDelete;
    }

    public List<Consumer<FXPath>> getOnDeleted() {
        return onDeleted;
    }

    public List<Consumer<FXPath>> getOnDeletedExternally() {
        return onDeletedExternally;
    }

    public static void addOnDeletedGlobally(Consumer<FXPath> consumer) {
        onDeletedGlobally.add(new WeakReference<>(consumer));
    }

    private void onDeletedGlobally(FXPath path) {
        var i = onDeletedGlobally.iterator();

        while (i.hasNext()) {
            var consumer = i.next().get();

            if (consumer == null) {
                i.remove();
            } else {
                consumer.accept(path);
            }
        }
    }

    public static FXPath getRoot() {
        // Create a new root path so that it is not strongly referenced and thus removed
        // from the cache.
        return getFromCache(null, Path.of(ROOT_PATH.toString()), true);
    }

    public static FXPath getPseudoRoot(List<String> paths) {

        var pseudoRoot = new FXPath();
        List<FXPath> pds = paths.stream()
                .map(p -> Path.of(p))
                .map(p -> getFromCache(pseudoRoot, p, Files.isDirectory(p)))
                .collect(Collectors.toList());

        pseudoRoot.setName("");
        pseudoRoot.setDirectory(!pds.isEmpty());
        pseudoRoot.setLeaf(!pseudoRoot.isDirectory());
        pseudoRoot.setDirLeaf(!pseudoRoot.isDirectory());
        pseudoRoot.setLoaded(true);
        pseudoRoot.searchPaths.setAll(pds);

        return pseudoRoot;
    }

    static FXPath createDirectory(FXPath parent, Path path) {
        var pathDescriptor = addInParent(parent, path, true);
        return pathDescriptor;
    }

    static FXPath createFile(FXPath parent, Path path) {
        var pathDescriptor = addInParent(parent, path, false);
        return pathDescriptor;
    }

    void rename(Path newPath, String newName) {
        removeFromCache(getPath());
        var oldPath = getPath();
        setPath(newPath);
        getFromCache(getPath(), p -> new WeakReference<>(this));

        searchPaths.forEach(p -> p.rename(oldPath, getPath()));

        setName(newName);
    }

    private void rename(Path oldParent, Path newParent) {
        removeFromCache(getPath());
        var relative = oldParent.relativize(getPath());
        setPath(newParent.resolve(relative));
        getFromCache(getPath(), p -> new WeakReference<>(this));

        searchPaths.forEach(p -> p.rename(oldParent, newParent));
    }

    void move(FXPath newParent, Path newPath) {
        removeFromCache(getPath());
        new ArrayList<>(parents).stream().filter(p -> !p.isPseudoRoot()).forEach(p -> p.remove(this));
        var oldPath = getPath();
        setPath(newPath);
        getFromCache(getPath(), p -> new WeakReference<>(this));

        searchPaths.forEach(p -> p.rename(oldPath, getPath()));

        newParent.setDirLeaf(!isDirectory());
        newParent.setLeaf(false);
        newParent.setLoaded(true);

        parents.add(newParent);
        newParent.searchPaths.add(this);
    }

    static FXPath copy(FXPath newParent, Path newPath) {
        var fxpath = addInParent(newParent, newPath, Files.isDirectory(newPath));

        return fxpath;
    }

    public List<FXPath> getNotToBeDeleted() {
        List<FXPath> result = new ArrayList<>();
        if (onDelete.stream().anyMatch(f -> !f.test(this))) {
            result.add(this);
        }

        searchPaths.forEach(p -> result.addAll(p.getNotToBeDeleted()));

        return result;
    }

    void delete() {
        onDeleted.forEach(c -> c.accept(this));
        onDeletedGlobally(this);
        removeFromCache(getPath());

        delete(p -> p.delete());
    }

    private void deleteExternally() {
        onDeletedExternally.forEach(c -> c.accept(this));
        onDeletedGlobally(this);
        // Keep in cache if listened to, e.g. file in an editor can be saved again.
        if (onDeletedExternally.isEmpty()) {
            removeFromCache(getPath());
        }

        delete(p -> p.deleteExternally());
    }

    private void delete(Consumer<FXPath> delete) {
        // Only first path in tree will have parents.
        // Next paths' parents will be cleared before.
        new ArrayList<>(parents).forEach(p -> p.remove(this));
        setLoaded(false);
        loading = false;
        setDirLeaf(true);
        setLeaf(true);
        Iterator<FXPath> i = searchPaths.iterator();

        while (i.hasNext()) {
            var p = i.next();
            p.parents.clear();
            delete.accept(p);
            i.remove();
        }
    }

    public void add(FXPath pd) {
        searchPaths.add(pd);
        pd.parents.add(this);

        setDirLeaf(!pd.isDirectory());
        setLeaf(false);
        setLoaded(true);
    }

    public void remove(FXPath pd) {
        searchPaths.remove(pd);
        pd.parents.remove(this);

        if (searchPaths.isEmpty()) {
            setDirLeaf(true);
            setLeaf(true);
            setLoaded(false);
        }
    }

    public FXPath getParent() {
        return parents.stream().filter(p -> !p.isPseudoRoot()).findFirst().orElse(null);
    }

    public Path getPath() {
        return path.get();
    }

    private void setPath(Path value) {
        path.set(value);
    }

    public ReadOnlyObjectProperty<Path> pathProperty() {
        return path;
    }

    public String getName() {
        return name.get();
    }

    private void setName(String value) {
        nameProperty().set(value);
    }

    public StringProperty nameProperty() {

        if (name == null) {
            name = new SimpleStringProperty() {
                @Override
                public Object getBean() {
                    return FXPath.this;
                }

                @Override
                public String toString() {
                    return get();
                }
            };
        }

        return name;
    }

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }

    public boolean isDirectory() {
        return directory.get();
    }

    private void setDirectory(boolean value) {
        directory.set(value);
    }

    public ReadOnlyBooleanProperty directoryProperty() {
        return directory;
    }

    public boolean isFile() {
        return !isDirectory();
    }

    public boolean isReadable() {
        return Files.isReadable(getPath());
    }

    public boolean isDirLeaf() {

        if (dirLeaf == null) {
            setDirLeaf(!isDirectory() || !Files.isReadable(getPath()) || !isRoot() && !XFiles.hasSubDirs(getPath()));
        }

        return dirLeaf.get();
    }

    private void setDirLeaf(boolean value) {
        if (dirLeaf == null) {
            dirLeaf = new AtomicBoolean();
        }

        dirLeaf.set(value);
    }

    public boolean isLeaf() {

        if (leaf == null) {
            setLeaf(!isDirectory() || !Files.isReadable(getPath()) || !isRoot() && !XFiles.isEmpty(getPath()));
        }

        return leaf.get();
    }

    private void setLeaf(boolean value) {
        if (leaf == null) {
            leaf = new AtomicBoolean();
        }

        leaf.set(value);
    }

    boolean isRoot() {
        return ROOT_PATH.equals(getPath());
    }

    boolean isPseudoRoot() {
        return getPath() == null;
    }

    private ObservableList<FXPath> getSearchPaths() {
        return searchPaths;
    }

    public ObservableList<FXPath> getPaths() {
        return paths;
    }

    public void refresh() {
        searchPaths.clear();
        loading = false;
        loaded = false;
    }

    public boolean isLoaded() {
        return loaded;
    }

    private void setLoaded(boolean value) {

        if (watchServiceRegister != null && getPath() != null && !loaded && value) {
            setPath(watchServiceRegister.register(getPath(), directoryWatcher));
        }

        loaded = value;
    }

    public void load() {

        if (loading || isLoaded()) {
            return;
        }

        if (!isReadable()) {
            return;
        }

        if (!searchPaths.isEmpty()) {
            paths.setAll(searchPaths);
            setLoaded(true);
            return;
        }

        loading = true;

        ForkJoinPool.commonPool().execute(() -> {
            getLock().lock();
            try {
                loadSync(getSearchPaths());
                setLoaded(true);
            } finally {
                loading = false;
                getLock().unlock();
            }
        });
    }

    private List<FXPath> loadSync(List<FXPath> loadedPaths) {
        if (isRoot()) {
            listRoots(loadedPaths);
        } else {
            list(loadedPaths);
        }

        return loadedPaths;
    }

    private void listRoots(List<FXPath> loadedPaths) {
        try (var stream = StreamSupport.stream(FileSystems.getDefault().getRootDirectories().spliterator(), false)) {
            stream.forEach(p -> {
                add(loadedPaths, this, p, true);
            });
        }
    }

    private void list(List<FXPath> loadedPaths) {
        try (var stream = Files.newDirectoryStream(getPath())) {
            var iterator = stream.iterator();

            while (iterator.hasNext()) {
                var p = iterator.next();
                boolean directory = Files.isDirectory(p);
                add(loadedPaths, this, p, directory);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static FXPath addInParent(FXPath parent, Path p, boolean directory) {
        var pd = add(parent.getSearchPaths(), parent, p, directory);
        parent.setDirLeaf(!directory);
        parent.setLeaf(false);

        return pd;
    }

    private static FXPath add(List<FXPath> loadedPaths, FXPath parent, Path p, boolean directory) {
        var pd = getFromCache(parent, p, directory);
        loadedPaths.add(pd);

        return pd;
    }

    private static FXPath getFromCache(FXPath parent, Path path, boolean dir) {

        var pd = getFromCache(path, k -> new WeakReference<>(new FXPath(parent, path, dir)));

        if (parent != null) {
            pd.parents.add(parent);
        }

        return pd;
    }

    private static FXPath getFromCache(Path path, Function<Path, WeakReference<FXPath>> function) {

        var fxpath = CACHE.computeIfAbsent(path, function).get();

        if (fxpath == null) {
            var ref = function.apply(path);
            fxpath = ref.get();
            CACHE.put(path, ref);
        }

        if (watchServiceRegister != null && fxpath.isDirectory() && fxpath.isLoaded()) {
            fxpath.setPath(watchServiceRegister.register(path, fxpath.directoryWatcher));
        }

        return fxpath;
    }

    private static FXPath putToCache(Path path, FXPath fxpath) {

        return getFromCache(path, p -> new WeakReference<>(fxpath));
    }

    private static FXPath getFromCache(Path path) {

        var ref = CACHE.get(path);
        var fxpath = ref != null ? ref.get() : null;

        return fxpath;
    }

    private static void removeFromCache(Path path) {
        CACHE.remove(path);
    }

    private void watchDirectory(List<WatchEvent<?>> events) {
        getLock().lock();
        try {
            events.forEach(e -> {

                if (e.context() instanceof Path) {
                    var contextPath = getPath().resolve((Path) e.context());

                    if (e.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        if (getSearchPaths().stream().noneMatch(p -> p.getPath().equals(contextPath))) {
                            addInParent(this, contextPath, Files.isDirectory(contextPath));
                        }
                    } else if (e.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        var fxpath = getFromCache(contextPath);
                        try {
                            if (fxpath != null && Files.exists(contextPath)
                                    && fxpath.basicFileAttributes.getLastModifiedTime() != Files.getLastModifiedTime(contextPath).toMillis()) {
                                fxpath.setFileAttributes();
                                fxpath.onModified.forEach(c -> c.accept(fxpath));
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    } else if (e.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                        var fxpath = getFromCache(contextPath);

                        if (fxpath != null) {
                            fxpath.deleteExternally();
                        }
                    }
                }
            });
        } finally {
            getLock().unlock();
        }
    }

    public FXBasicFileAttributes getBasicFileAttributes() {
        return basicFileAttributes;
    }

    public void setFileAttributes() {
        basicFileAttributes = new FXBasicFileAttributes(this);
    }

    void saved(Path newPath) {
        if (!getPath().equals(newPath)) {
            removeFromCache(getPath());
            setPath(newPath);
            putToCache(newPath, this);
        }

        setFileAttributes();
        onModified.forEach(c -> c.accept(this));
    }

    void search(PathMatcher pathMatcher, String textRegex, Consumer<FilePointer> consumer, AtomicBoolean stop) {

        if (!isReadable()) {
            return;
        }

        if (stop.get()) {
            return;
        }

        if (isFile()) {
            searchFile(pathMatcher, textRegex, consumer, stop);
        } else {

            searching = true;
            List<FXPath> loadedPaths = searchPaths.isEmpty() ? loadSync(searchPaths) : searchPaths;

            loadedPaths.parallelStream().sorted(Comparator.reverseOrder()).forEach(p -> p.search(pathMatcher, textRegex, consumer, stop));
            searching = false;
        }
    }

    private void searchFile(PathMatcher pathNatcher, String textRegex, Consumer<FilePointer> consumer, AtomicBoolean stop) {
        if (stop.get()) {
            return;
        }
        Path fileName = getPath().getFileName();
        if (fileName != null && pathNatcher.matches(fileName)) {
            PathFilePointer pathPointer = new PathFilePointer(this);
            consumer.accept(pathPointer);
        }
    }

    @Override
    public String toString() {
        return name.get();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FXPath)) {
            return false;
        }

        return getPath() != null ? getPath().equals(((FXPath) obj).getPath()) : super.equals(obj);
    }

    @Override
    public int hashCode() {
        return getPath() != null ? getPath().hashCode() : super.hashCode();
    }

    @Override
    public int compareTo(FXPath o) {
        int result = Boolean.compare(!isDirectory(), !o.isDirectory());

        if (result == 0) {
            result = name.get().compareToIgnoreCase(o.name.get());
        }

        return result;
    }

    public static final StringComparator STRING_COMPARATOR = new StringComparator();

    public static class StringComparator implements Comparator<StringProperty> {

        @Override
        public int compare(StringProperty o1, StringProperty o2) {
            FXPath desc1 = (FXPath) o1.getBean();
            FXPath desc2 = (FXPath) o2.getBean();

            int result = Boolean.compare(!desc1.isDirectory(), !desc2.isDirectory());

            if (result == 0) {
                result = o1.getValue().compareToIgnoreCase(o2.getValue());
            }

            return result;
        }
    }

    public static final LongComparator LONG_COMPARATOR = new LongComparator();

    public static class LongComparator implements Comparator<ReadOnlyLongProperty> {

        @Override
        public int compare(ReadOnlyLongProperty o1, ReadOnlyLongProperty o2) {

            if (o1 == null && o2 == null) {
                return 0;
            } else if (o1 == null) {
                return -1;
            } else if (o2 == null) {
                return 1;
            }

            FXPath desc1 = (FXPath) o1.getBean();
            FXPath desc2 = (FXPath) o2.getBean();

            int result = Boolean.compare(!desc1.isDirectory(), !desc2.isDirectory());

            if (result == 0) {
                result = o1.asObject().get().compareTo(o2.asObject().get());
            }

            return result;
        }
    }
}
