package roadmap.util;

import org.eclipse.jgit.lib.*;
import org.junit.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Helper class to execute native Git commands and parse results. */
public class Git {
    public interface LineHandler {
        void line(String line)
                throws IOException;
    }

    public abstract static class ResultHandler<R>
            implements LineHandler {
        public abstract R result();

        protected static void unexpected(String line) {
            throw new IllegalStateException("unexpected output: " + line);
        }
    }

    public static class CaptureResult
            extends ResultHandler<String> {
        private final StringBuilder buf = new StringBuilder();

        @Override public String result() {
            return buf.toString();
        }

        @Override public void line(String line)
                throws IOException {
            buf.append(line).append("\n");
        }
    }

    public static class IgnoreResult
            extends ResultHandler<Void> {
        @Override public Void result() {
            return null;
        }

        @Override public void line(String line)
                throws IOException {}
    }

    public static class EmptyResult
            extends ResultHandler<Void> {
        @Override public Void result() {
            return null;
        }

        @Override public void line(String line)
                throws IOException {
            unexpected(line);
        }
    }

    public static class IdResult
            extends ResultHandler<ObjectId> {
        private ObjectId result;

        @Override public ObjectId result() {
            if (result == null) {
                throw new IllegalStateException("single id expected, none given");
            }
            return result;
        }

        @Override public void line(String line)
                throws IOException {
            if (result != null) {
                throw new IllegalStateException("single id expected, many given");
            }
            result = ObjectId.fromString(line);
        }
    }

    public static class IdListResult
            extends ResultHandler<List<ObjectId>> {
        private final ArrayList<ObjectId> result = new ArrayList<>();

        @Override public List<ObjectId> result() {
            return result;
        }

        @Override public void line(String line)
                throws IOException {
            result.add(ObjectId.fromString(line));
        }
    }

//    public static class LsFilesResult extends ResultHandler<Map<String, CacheEntry>> {
//        private static final Pattern PATTERN =
//                Pattern.compile("^([0-7]{6}) ([a-zA-Z0-9]{40}) ([0-3])\t(.+)$");
//        private final TreeMap<String, CacheEntry> result = new TreeMap<>();
//
//        @Override public Map<String, CacheEntry> result() {
//            return result;
//        }
//
//        @Override public void line(String line)
//                throws IOException {
//            Matcher matcher = PATTERN.matcher(line);
//            if (!matcher.matches()) {
//                unexpected(line);
//            }
//            FileMode mode = parseFileMode(matcher.group(1));
//            ObjectId id = ObjectId.fromString(matcher.group(2));
//            int stage = Integer.parseInt(matcher.group(3));
//            String path = matcher.group(4);
//            CacheEntry entry = result.get(path);
//            if (entry == null) {
//                entry = new CacheEntry(path,
//                        CacheEntry.Stage.MISSING,
//                        CacheEntry.Stage.MISSING,
//                        CacheEntry.Stage.MISSING);
//            }
//            CacheEntry.Stage s = CacheEntry.Stage.make(id, mode);
//            switch (stage) {
//                case 0:
//                    result.put(path, new CacheEntry(s, path));
//                    break;
//                case 1:
//                    result.put(path, new CacheEntry(path, s, entry.getA(), entry.getB()));
//                    break;
//                case 2:
//                    result.put(path, new CacheEntry(path, entry.getO(), s, entry.getB()));
//                    break;
//                case 3:
//                    result.put(path, new CacheEntry(path, entry.getO(), entry.getA(), s));
//                    break;
//            }
//        }
//    }

    public static class StatusResult
            extends ResultHandler<List<StatusResult.Item>> {
        public enum Status {
            UNTRACKED('?'),
            IGNORED('!'),
            UNMODIFIED(' '),
            MODIFIED('M'),
            ADDED('A'),
            DELETED('D'),
            RENAMED('R'),
            COPIED('C'),
            UPDATED_UNMERGED('U');
            final char code;

            Status(char code) {
                this.code = code;
            }

            static Status fromCode(char code) {
                for (Status fs : Status.values()) {
                    if (fs.code == code) {
                        return fs;
                    }
                }
                throw new IllegalStateException();
            }
        }

        public static class Item
                implements Comparable<Item> {
            public final Status index;
            public final Status workingTree;
            public final String path;
            public final String basePath;

            Item(Status index, Status workingTree, String path) {
                this(index, workingTree, path, null);
            }

            Item(Status index, Status workingTree, String path, String basePath) {
                this.index = index;
                this.workingTree = workingTree;
                this.path = path;
                this.basePath = basePath;
            }

            @Override public int compareTo(Item o) {
                return path.compareTo(o.path);
            }

            @Override public String toString() {
                return String.format("%c%c %s", index.code, workingTree.code, path);
            }
        }

        private static final Pattern PATTERN =
                Pattern.compile("^(..) (.+)( \\-\\> (.+))?$");
        private final ArrayList<Item> result = new ArrayList<>();

        @Override public List<Item> result() {
            return result;
        }

        @Override public void line(String line)
                throws IOException {
            Matcher matcher = PATTERN.matcher(line);
            if (!matcher.matches()) {
                unexpected(line);
            }
            String mode = matcher.group(1);
            String path = matcher.group(2);
            Status x = Status.fromCode(mode.charAt(0));
            Status y = Status.fromCode(mode.charAt(1));
            result.add(new Item(x, y, path));
        }
    }

//    public static class DiffTreeResult extends ResultHandler<List<TreeDiffItem>> {
//        private static final Pattern PATTERN =
//                Pattern.compile("^:([0-7]{6}) ([0-7]{6}) ([a-zA-Z0-9]{40}) ([a-zA-Z0-9]{40}) ([AMDRC][0-9]{0,3})(\t[^\\t]+)(\t[^\\t]+)?$");
//        private final ArrayList<TreeDiffItem> result = new ArrayList<>();
//
//        @Override public List<TreeDiffItem> result() {
//            return result;
//        }
//
//        @Override public void line(String line)
//                throws IOException {
//            Matcher matcher = PATTERN.matcher(line);
//            if (!matcher.matches()) {
//                unexpected(line);
//            }
//            result.add(parseTreeDiff(matcher.group(5),
//                    path(matcher.group(6)), path(matcher.group(7)),
//                    parseFileMode(matcher.group(2)), ObjectId.fromString(matcher.group(4)),
//                    parseFileMode(matcher.group(1)), ObjectId.fromString(matcher.group(3))));
//        }
//
//        private static TreeDiffItem parseTreeDiff(String type,
//                                                  String path, String newPath,
//                                                  FileMode modeB, ObjectId idB,
//                                                  FileMode modeA, ObjectId idA) {
//            MutableInteger score = new MutableInteger();
//            switch (parseChangeType(type, score)) {
//                case ADDED:
//                    return TreeDiffItem.added(
//                            new TreeFile(idB, modeB, path));
//                case DELETED:
//                    return TreeDiffItem.deleted(
//                            new TreeFile(idA, modeA, path));
//                case MODIFIED:
//                    return TreeDiffItem.modified(
//                            new TreeFile(idB, modeB, path),
//                            new TreeFile(idA, modeA, path),
//                            score.value);
//                case RENAMED:
//                    return TreeDiffItem.renamed(
//                            new TreeFile(idB, modeB, path),
//                            new TreeFile(idA, modeA, path),
//                            score.value);
//                case COPIED:
//                    return TreeDiffItem.copied(
//                            new TreeFile(idB, modeB, path),
//                            new TreeFile(idA, modeA, path),
//                            score.value);
//                case TYPE_CHANGED:
//                    return TreeDiffItem.typeChanged(
//                            new TreeFile(idB, modeB, path),
//                            new TreeFile(idA, modeA, path));
//            }
//            throw new IllegalStateException();
//        }
//
//        private static String path(String s) {
//            if (s == null) {
//                return s;
//            }
//            while (s.charAt(0) == '\t') {
//                s = s.substring(1);
//            }
//            return s;
//        }
//
//        private static FileStatus parseChangeType(String s, MutableInteger score) {
//            switch (s.charAt(0)) {
//                case 'A':
//                    return FileStatus.ADDED;
//                case 'M':
//                    return FileStatus.MODIFIED;
//                case 'D':
//                    return FileStatus.DELETED;
//                case 'R':
//                    score.value = Integer.parseInt(s.substring(1));
//                    return FileStatus.RENAMED;
//                case 'C':
//                    score.value = Integer.parseInt(s.substring(1));
//                    return FileStatus.COPIED;
//                default:
//                    throw new IllegalStateException("unknown change type: " + s);
//            }
//        }
//    }

    public static class MergeResult
            extends ResultHandler<Void> {
        private static final String NAME = "([/a-zA-Z0-9\\.\\-~]+)";
        private static final Pattern P_ADDING = Pattern.compile(
                "^Adding " + NAME + "$");
        private static final Pattern P_ADDING_AS = Pattern.compile(
                "^Adding as " + NAME + " instead$");
        private static final Pattern P_REMOVING = Pattern.compile(
                "^Removing " + NAME + "$");
        private static final Pattern P_AUTO_MERGING = Pattern.compile(
                "^Auto-merging " + NAME + "$");
        private static final Pattern P_CONTENT = Pattern.compile(
                "^CONFLICT \\(content\\): Merge conflict in " + NAME + "$");
        private static final Pattern P_ADD_ADD = Pattern.compile(
                "^CONFLICT \\(add/add\\): Merge conflict in " + NAME + "$");
        private static final Pattern P_RENAME_ADD = Pattern.compile(
                "^CONFLICT \\(rename/add\\): Rename " + NAME + "->" + NAME + " in " + NAME + "\\. " + NAME + " added in " + NAME + "$");
        private static final Pattern P_RENAME_DELETE = Pattern.compile(
                "^CONFLICT \\(rename/delete\\): Rename " + NAME + "->" + NAME + " in " + NAME + " and deleted in " + NAME + "$");
        private static final Pattern P_RENAME_RENAME = Pattern.compile(
                "^CONFLICT \\(rename/rename\\): Rename \"" + NAME + "\"->\"" + NAME + "\" in branch \"" + NAME + "\" rename \"" + NAME + "\"->\"" + NAME + "\" in \"" + NAME + "\"$");
        private static final Pattern P_DELETE_MODIFY = Pattern.compile(
                "^CONFLICT \\(delete/modify\\): " + NAME + " deleted in " + NAME + " and modified in " + NAME + "\\. Version " + NAME + " of " + NAME + " left in tree\\.$");
        private static final Pattern P_DIRECTORY_FILE = Pattern.compile(
                "^CONFLICT \\(directory/file\\): There is a directory with name " + NAME + " in " + NAME + "\\. Adding " + NAME + " as " + NAME + "$");
        private static final Pattern P_FILE_DIRECTORY = Pattern.compile(
                "^CONFLICT \\(file/directory\\): There is a directory with name " + NAME + " in " + NAME + "\\. Adding " + NAME + " as " + NAME + "$");
        private static final Pattern P_RECURSIVE = Pattern.compile(
                "^Merge made by recursive.$");
        private static final Pattern P_FAILED = Pattern.compile(
                "^Automatic merge failed; fix conflicts and then commit the result\\.$");
        private static final Pattern P_ABORTING = Pattern.compile(
                "^Aborting$");
        private static final Pattern[] PATTERNS = {
                P_ADDING,
                P_ADDING_AS,
                P_REMOVING,
                P_AUTO_MERGING,
                P_CONTENT,
                P_ADD_ADD,
                P_RENAME_ADD,
                P_RENAME_DELETE,
                P_RENAME_RENAME,
                P_DELETE_MODIFY,
                P_DIRECTORY_FILE,
                P_FILE_DIRECTORY,
                P_RECURSIVE,
                P_FAILED,
                P_ABORTING,
        };

        @Override public Void result() {
            return null;
        }

        @Override public void line(String line)
                throws IOException {
            for (Pattern pattern : PATTERNS) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    System.out.println("MATCHED: " + line);
//          for (int n = 0; n < matcher.groupCount(); n++) {
//            System.out.println("\tparameter: " + matcher.group(n + 1));
//          }
                    return;
                }
            }
            System.out.println("UNMATCHED: " + line);
            //unexpected(line);
        }
    }

//    public static class BlameResult extends ResultHandler<BlameList> {
//        private static final Pattern PATTERN_HEADER =
//                Pattern.compile("^([a-zA-Z0-9]{40}) ([0-9]+) ([0-9]+)( [0-9]+)?$");
//        private static final Pattern PATTERN_AUTHOR =
//                Pattern.compile("^author (.*)$");
//        private static final Pattern PATTERN_AUTHOR_MAIL =
//                Pattern.compile("^author-mail (.*)$");
//        private static final Pattern PATTERN_AUTHOR_TIME =
//                Pattern.compile("^author-time ([0-9]+)$");
//        private static final Pattern PATTERN_AUTHOR_TZ =
//                Pattern.compile("^author-tz ([\\-+]?[0-9]+)$");
//        private static final Pattern PATTERN_COMMITTER =
//                Pattern.compile("^committer (.*)$");
//        private static final Pattern PATTERN_COMMITTER_MAIL =
//                Pattern.compile("^committer-mail (.*)$");
//        private static final Pattern PATTERN_COMMITTER_TIME =
//                Pattern.compile("^committer-time ([0-9]+)$");
//        private static final Pattern PATTERN_COMMITTER_TZ =
//                Pattern.compile("^committer-tz ([\\-+]?[0-9]+)$");
//        private static final Pattern PATTERN_SUMMARY =
//                Pattern.compile("^summary (.*)$");
//        private static final Pattern PATTERN_BOUNDARY =
//                Pattern.compile("^boundary$");
//        private static final Pattern PATTERN_FILENAME =
//                Pattern.compile("^filename (.*)$");
//        private static final Pattern PATTERN_PREVIOUS =
//                Pattern.compile("^previous ([a-zA-Z0-9]{40}) (.*)$");
//        private final ArrayList<BlameEntry> entries = new ArrayList<>();
//        private ObjectId id;
//        private Integer originalLine;
//        private Integer finalLine;
//        private String filename;
//
//        @Override public BlameList result() {
//            return new BlameList(entries);
//        }
//
//        @Override public void line(String line)
//                throws IOException {
//            Pattern[] ignore = {
//                    PATTERN_AUTHOR, PATTERN_AUTHOR_MAIL, PATTERN_AUTHOR_TIME, PATTERN_AUTHOR_TZ,
//                    PATTERN_COMMITTER, PATTERN_COMMITTER_MAIL, PATTERN_COMMITTER_TIME, PATTERN_COMMITTER_TZ,
//                    PATTERN_SUMMARY,
//                    PATTERN_BOUNDARY,
//                    PATTERN_PREVIOUS,
//            };
//            for (Pattern pattern : ignore) {
//                Matcher matcher = pattern.matcher(line);
//                if (matcher.matches()) {
//                    return;
//                }
//            }
//            Matcher matcherHeader = PATTERN_HEADER.matcher(line);
//            if (matcherHeader.matches()) {
//                id = ObjectId.fromString(matcherHeader.group(1));
//                originalLine = Integer.parseInt(matcherHeader.group(2).trim());
//                finalLine = Integer.parseInt(matcherHeader.group(3).trim());
//                if (matcherHeader.groupCount() == 4 && matcherHeader.group(4) != null) {
//                    Integer.parseInt(matcherHeader.group(4).trim());
//                }
//                return;
//            }
//            Matcher matcherFilename = PATTERN_FILENAME.matcher(line);
//            if (matcherFilename.matches()) {
//                filename = matcherFilename.group(1);
//                return;
//            }
//            if (line.length() > 0 && line.charAt(0) == '\t') {
//                // Content line.
//                TreeFile treeFile = new TreeFile(id, FileMode.REGULAR_FILE, filename);
//                if (entries.size() > 0) {
//                    BlameEntry last = entries.get(entries.size() - 1);
//                    if (last.getCommitId().equals(id)
//                            && last.getEnd() + 1 == finalLine) {
//                        entries.set(entries.size() - 1, new BlameEntry(id, treeFile, last.getOrigPos(), last.getPos(), finalLine));
//                    }
//                    else {
//                        entries.add(new BlameEntry(id, treeFile, originalLine, finalLine - 1, finalLine));
//                    }
//                }
//                else {
//                    entries.add(new BlameEntry(id, treeFile, originalLine, finalLine - 1, finalLine));
//                }
//                return;
//            }
//            unexpected(line);
//        }
//    }

    /** Command base class. */
    protected abstract static class Command {
        private final String command;
        private File dir;

        protected Command(String command) {
            this.command = command;
        }

        protected Command(String command, File dir) {
            this(command);
            setDir(dir);
        }

        public void setDir(File dir) {
            this.dir = dir;
        }

        public abstract <R, T extends ResultHandler<R>> R execute(T handler)
                throws IOException;

        protected int runExpectSuccess(List<String> args, LineHandler lineHandler)
                throws IOException {
            int errorCode = run(args, lineHandler);
            if (errorCode != 0) {
                throw new IOException("error code: " + errorCode);
            }
            return 0;
        }

        protected int run(List<String> args, LineHandler lineHandler)
                throws IOException {
            validate();

            StringBuilder b = new StringBuilder();
            ArrayList<String> list = new ArrayList<>();
            list.add(command);
            b.append(list);
            for (String arg : args) {
                list.add(arg);
                b.append(" ").append(arg);
            }
            System.out.println(b.toString());

            Process process = new ProcessBuilder()
                    .directory(dir)
                    .command(list)
                    .start();

            ReaderThread outReader = new ReaderThread(
                    process.getInputStream(), lineHandler);
            ReaderThread errReader = new ReaderThread(
                    process.getErrorStream(), new LineHandler() {
                @Override public void line(String line) {
                    System.err.println(line);
                }
            });

            outReader.start();
            errReader.start();

            join(process);
            join(outReader);
            join(errReader);

            outReader.done();
            errReader.done();

            return process.exitValue();
        }

        protected void validate() {
            if (command == null) {
                throw new IllegalStateException("git command not specified");
            }
            if (dir == null) {
                throw new IllegalStateException("git directory not specified");
            }
        }
    }

    public static class Version
            extends Command {
        protected Version(String command) {
            super(command);
        }

        protected Version(String command, File dir) {
            super(command, dir);
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("--version");
            runExpectSuccess(args, result);
            return result.result();
        }

        @Override protected void validate() {}
    }

    public static class Config
            extends Command {
        private String name;
        private String value;

        protected Config(String command) {
            super(command);
        }

        protected Config(String command, File dir) {
            super(command, dir);
        }

        public Config name(String name) {
            this.name = name;
            return this;
        }

        public Config value(String value) {
            this.value = value;
            return this;
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("config");
            args.add(name);
            args.add(value);
            runExpectSuccess(args, result);
            return result.result();
        }

        @Override protected void validate() {}
    }

    /** Git {@code rev-list} command. */
    public static class Checkout
            extends Command {
        private boolean force;
        private boolean orphan;
        private boolean merge;
        private String branch;
        private String newBranch;
        private final ArrayList<String> path = new ArrayList<>();

        protected Checkout(String command) {
            super(command);
        }

        protected Checkout(String command, File dir) {
            super(command, dir);
        }

        public Checkout force(boolean force) {
            this.force = force;
            return this;
        }

        public Checkout orphan(boolean orphan) {
            this.orphan = orphan;
            return this;
        }

        public Checkout merge(boolean merge) {
            this.merge = merge;
            return this;
        }

        public Checkout branch(String branch) {
            this.branch = branch;
            return this;
        }

        public Checkout newBranch(String newBranch) {
            this.newBranch = newBranch;
            return this;
        }

        public Checkout path(String path) {
            this.path.add(path);
            return this;
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("checkout");
            if (force) {
                args.add("-f");
            }
            if (merge) {
                args.add("-m");
            }
            if (newBranch != null) {
                if (orphan) {
                    args.add("--orphan");
                }
                else {
                    if (force) {
                        args.add("-B");
                    }
                    else {
                        args.add("-b");
                    }
                }
                args.add(newBranch);
                if (branch != null) {
                    args.add(branch);
                }
            }
            else {
                if (branch != null) {
                    args.add(branch);
                }
                if (!path.isEmpty()) {
                    args.add("--");
                    for (String path : this.path) {
                        args.add(path);
                    }
                }
            }
            runExpectSuccess(args, result);
            return result.result();
        }
    }

    /** Git {@code rev-list} command. */
    public static class RevList
            extends Command {
        private final ArrayList<String> tree = new ArrayList<>();

        protected RevList(String command) {
            super(command);
        }

        protected RevList(String command, File dir) {
            super(command, dir);
        }

        public RevList tree(String tree) {
            this.tree.add(tree);
            return this;
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("rev-list");
            for (String tree : this.tree) {
                args.add(tree);
            }
            runExpectSuccess(args, result);
            return result.result();
        }
    }

    /** Git {@code merge-base} command. */
    public static class MergeBase
            extends Command {
        private boolean all;
        private boolean octopus;
        private final ArrayList<String> commit = new ArrayList<>();

        protected MergeBase(String command) {
            super(command);
        }

        protected MergeBase(String command, File dir) {
            super(command, dir);
        }

        public MergeBase all(boolean all) {
            this.all = all;
            return this;
        }

        public MergeBase octopus(boolean octopus) {
            this.octopus = octopus;
            return this;
        }

        public MergeBase commit(String commit) {
            this.commit.add(commit);
            return this;
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("merge-base");
            if (all) {
                args.add("-a");
            }
            if (octopus) {
                args.add("--octopus");
            }
            for (String commit : this.commit) {
                args.add(commit);
            }
            run(args, result);
            return result.result();
        }
    }

    /** Git {@code read-tree} command. */
    public static class ReadTree
            extends Command {
        private boolean ignoreWorkTree;
        private boolean updateWorkTree;
        private boolean merge;
        private boolean trivial;
        private boolean aggressive;
        private File indexOutput;
        private final ArrayList<String> tree = new ArrayList<>();

        protected ReadTree(String command) {
            super(command);
        }

        protected ReadTree(String command, File dir) {
            super(command, dir);
        }

        public ReadTree ignoreWorkTree(boolean ignoreWorkTree) {
            this.ignoreWorkTree = ignoreWorkTree;
            return this;
        }

        public ReadTree updateWorkTree(boolean updateWorkTree) {
            this.updateWorkTree = updateWorkTree;
            return this;
        }

        public ReadTree merge(boolean merge) {
            this.merge = merge;
            return this;
        }

        public ReadTree trivial(boolean trivial) {
            this.trivial = trivial;
            return this;
        }

        public ReadTree aggressive(boolean aggressive) {
            this.aggressive = aggressive;
            return this;
        }

        public ReadTree indexOutput(File indexOutput) {
            this.indexOutput = indexOutput;
            return this;
        }

        public ReadTree tree(String tree) {
            this.tree.add(tree);
            return this;
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("read-tree");
            if (ignoreWorkTree) {
                args.add("-i");
            }
            if (updateWorkTree) {
                args.add("-u");
            }
            if (merge) {
                args.add("-m");
            }
            if (trivial) {
                args.add("--trivial");
            }
            if (aggressive) {
                args.add("--aggressive");
            }
            if (indexOutput != null) {
                args.add("--index-output=" + indexOutput.getAbsolutePath());
            }
            for (String tree : this.tree) {
                args.add(tree);
            }
            runExpectSuccess(args, result);
            return null;
        }
    }

    /** Git {@code write-tree} command. */
    public static class WriteTree
            extends Command {
        protected WriteTree(String command) {
            super(command);
        }

        protected WriteTree(String command, File dir) {
            super(command, dir);
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("write-tree");
            runExpectSuccess(args, result);
            return result.result();
        }
    }

    /** Git {@code diff-tree} command. */
    public static class DiffTree
            extends Command {
        private boolean recursive;
        private boolean noRenames;
        private boolean breakRewrites;
        private int breakRewritesN;
        private int breakRewritesM;
        private boolean findRenames;
        private int findRenamesN;
        private boolean findCopies;
        private int findCopiesN;
        private boolean findCopiesHarder;
        private int limit;
        private final ArrayList<String> tree = new ArrayList<>();
        private String path;

        protected DiffTree(String command) {
            super(command);
        }

        protected DiffTree(String command, File dir) {
            super(command, dir);
        }

        public DiffTree recursive(boolean recursive) {
            this.recursive = recursive;
            return this;
        }

        public DiffTree noRenames(boolean noRenames) {
            this.noRenames = noRenames;
            return this;
        }

        public DiffTree breakRewrites(boolean breakRewrites) {
            this.breakRewrites = breakRewrites;
            return this;
        }

        public DiffTree breakRewritesN(int n) {
            breakRewritesN = n;
            return this;
        }

        public DiffTree breakRewritesM(int m) {
            breakRewritesM = m;
            return this;
        }

        public DiffTree findRenames(boolean findRenames) {
            this.findRenames = findRenames;
            return this;
        }

        public DiffTree findRenamesN(int n) {
            findRenamesN = n;
            return this;
        }

        public DiffTree findCopies(boolean findCopies) {
            this.findCopies = findCopies;
            return this;
        }

        public DiffTree findCopiesN(int n) {
            findCopiesN = n;
            return this;
        }

        public DiffTree findCopiesHarder(boolean findCopiesHarder) {
            this.findCopiesHarder = findCopiesHarder;
            return this;
        }

        public DiffTree limit(int limit) {
            this.limit = limit;
            return this;
        }

        public DiffTree tree(String tree) {
            this.tree.add(tree);
            return this;
        }

        public DiffTree path(String path) {
            this.path = path;
            return this;
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("diff-tree");
            args.add("--raw");
            if (recursive) {
                args.add("-r");
            }
            if (noRenames) {
                args.add("--no-renames");
            }
            if (breakRewrites) {
                String a = "--break-rewrites";
                if (breakRewritesN > 0 || breakRewritesM > 0) {
                    a += "=";
                    if (breakRewritesN > 0) {
                        a += breakRewritesN;
                    }
                    if (breakRewritesM > 0) {
                        a += "/" + breakRewritesM;
                    }
                }
                args.add(a);
            }
            if (findRenames) {
                String a = "--find-renames";
                if (findRenamesN > 0) {
                    a += "=" + findRenamesN + "%";
                }
                args.add(a);
            }
            if (findCopies) {
                String a = "--find-copies";
                if (findCopiesN > 0) {
                    a += "=" + findCopiesN + "%";
                }
                args.add(a);
            }
            if (findCopiesHarder) {
                args.add("--find-copies-harder");
            }
            if (limit > 0) {
                args.add("-l" + limit);
            }
            for (String tree : this.tree) {
                args.add(tree);
            }
            if (path != null) {
                args.add(path);
            }
            runExpectSuccess(args, result);
            return result.result();
        }
    }

    /** Git {@code ls-files} command. */
    public static class LsFiles
            extends Command {
        private boolean cached;
        private boolean deleted;
        private boolean modified;
        private boolean others;
        private boolean ignored;
        private boolean stage;
        private boolean directory;
        private boolean unmerged;

        protected LsFiles(String command) {
            super(command);
        }

        protected LsFiles(String command, File dir) {
            super(command, dir);
        }

        public LsFiles cached(boolean cached) {
            this.cached = cached;
            return this;
        }

        public LsFiles deleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        public LsFiles modified(boolean modified) {
            this.modified = modified;
            return this;
        }

        public LsFiles others(boolean others) {
            this.others = others;
            return this;
        }

        public LsFiles ignored(boolean ignored) {
            this.ignored = ignored;
            return this;
        }

        public LsFiles stage(boolean stage) {
            this.stage = stage;
            return this;
        }

        public LsFiles directory(boolean directory) {
            this.directory = directory;
            return this;
        }

        public LsFiles unmerged(boolean unmerged) {
            this.unmerged = unmerged;
            return this;
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("ls-files");
            if (cached) {
                args.add("--cached");
            }
            if (deleted) {
                args.add("--deleted");
            }
            if (modified) {
                args.add("--modified");
            }
            if (others) {
                args.add("--others");
            }
            if (ignored) {
                args.add("--ignored");
            }
            if (stage) {
                args.add("--stage");
            }
            if (directory) {
                args.add("--directory");
            }
            if (unmerged) {
                args.add("--unmerged");
            }
            runExpectSuccess(args, result);
            return result.result();
        }
    }

    /** Git {@code status} command. */
    public static class Status
            extends Command {
        private String untracked;
        private boolean ignored;

        protected Status(String command) {
            super(command);
        }

        protected Status(String command, File dir) {
            super(command, dir);
        }

        public Status untracked(String untracked) {
            this.untracked = untracked;
            return this;
        }

        public Status ignored(boolean ignored) {
            this.ignored = ignored;
            return this;
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("status");
            if (untracked != null) {
                args.add("--untracked-files=" + untracked);
            }
            if (ignored) {
                args.add("--ignored");
            }
            args.add("--porcelain");
            runExpectSuccess(args, result);
            return result.result();
        }
    }

    /** Git {@code reset} command. */
    public static class Reset
            extends Command {
        private boolean soft;
        private boolean mixed;
        private boolean hard;
        private String commit;

        protected Reset(String command) {
            super(command);
        }

        protected Reset(String command, File dir) {
            super(command, dir);
        }

        public Reset soft(boolean soft) {
            this.soft = soft;
            return this;
        }

        public Reset mixed(boolean mixed) {
            this.mixed = mixed;
            return this;
        }

        public Reset hard(boolean hard) {
            this.hard = hard;
            return this;
        }

        public Reset commit(String commit) {
            this.commit = commit;
            return this;
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("reset");
            if (soft) {
                args.add("--soft");
            }
            if (mixed) {
                args.add("--mixed");
            }
            if (hard) {
                args.add("--hard");
            }
            if (commit != null) {
                args.add(commit);
            }
            runExpectSuccess(args, result);
            return result.result();
        }
    }

    /** Git {@code merge} command. */
    public static class Merge
            extends Command {
        private boolean squash;
        private boolean noSquash;
        private boolean stat;
        private boolean noStat;
        private String strategy;
        private String commit;

        protected Merge(String command) {
            super(command);
        }

        protected Merge(String command, File dir) {
            super(command, dir);
        }

        public Merge squash(boolean squash) {
            this.squash = squash;
            return this;
        }

        public Merge noSquash(boolean noSquash) {
            this.noSquash = noSquash;
            return this;
        }

        public Merge stat(boolean stat) {
            this.stat = stat;
            return this;
        }

        public Merge noStat(boolean noStat) {
            this.noStat = noStat;
            return this;
        }

        public Merge strategy(String strategy) {
            this.strategy = strategy;
            return this;
        }

        public Merge commit(String commit) {
            this.commit = commit;
            return this;
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("merge");
            if (squash) {
                args.add("--squash");
            }
            if (noSquash) {
                args.add("--no-squash");
            }
            if (stat) {
                args.add("--stat");
            }
            if (noStat) {
                args.add("--no-stat");
            }
            if (strategy != null) {
                args.add("--strategy=" + strategy);
            }
            if (commit != null) {
                args.add(commit);
            }
            runExpectSuccess(args, result);
            return result.result();
        }
    }

    /** Git {@code merge-file} command. */
    public static class MergeFile
            extends Command {
        private boolean ours;
        private boolean theirs;
        private boolean union;
        private boolean stdOut;
        private Integer markerSize;
        private String currentName;
        private String baseName;
        private String otherName;
        private String currentFile;
        private String baseFile;
        private String otherFile;

        protected MergeFile(String command) {
            super(command);
        }

        protected MergeFile(String command, File dir) {
            super(command, dir);
        }

        public MergeFile ours(boolean ours) {
            this.ours = ours;
            return this;
        }

        public MergeFile theirs(boolean theirs) {
            this.theirs = theirs;
            return this;
        }

        public MergeFile union(boolean union) {
            this.union = union;
            return this;
        }

        public MergeFile stdOut(boolean stdOut) {
            this.stdOut = stdOut;
            return this;
        }

        public MergeFile markerSize(int markerSize) {
            this.markerSize = markerSize;
            return this;
        }

        public MergeFile currentName(String currentName) {
            this.currentName = currentName;
            return this;
        }

        public MergeFile baseName(String baseName) {
            this.baseName = baseName;
            return this;
        }

        public MergeFile otherName(String otherName) {
            this.otherName = otherName;
            return this;
        }

        public MergeFile currentFile(String currentFile) {
            this.currentFile = currentFile;
            return this;
        }

        public MergeFile baseFile(String baseFile) {
            this.baseFile = baseFile;
            return this;
        }

        public MergeFile otherFile(String otherFile) {
            this.otherFile = otherFile;
            return this;
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("merge-file");
            if (ours) {
                args.add("--ours");
            }
            if (theirs) {
                args.add("--theirs");
            }
            if (union) {
                args.add("--union");
            }
            if (stdOut) {
                args.add("--stdout");
            }
            if (markerSize != null) {
                args.add("--marker-size=" + markerSize);
            }
            if (currentName != null) {
                if (currentFile == null) {
                    throw new IllegalStateException("current file not specified");
                }
                args.add("-L");
                args.add(currentName);
                if (baseName != null) {
                    if (baseFile == null) {
                        throw new IllegalStateException("base file not specified");
                    }
                    args.add("-L");
                    args.add(baseName);
                    if (otherName != null) {
                        if (otherFile == null) {
                            throw new IllegalStateException("other file not specified");
                        }
                        args.add("-L");
                        args.add(otherName);
                    }
                }
            }
            if (currentFile != null) {
                args.add(currentFile);
                if (baseFile != null) {
                    args.add(baseFile);
                    if (otherFile != null) {
                        args.add(otherFile);
                    }
                }
            }
            int errorCode = run(args, result);
            if (errorCode != 0) {
                // What does it mean?
            }
            return result.result();
        }
    }

    /** Git {@code blame} command. */
    public static class Blame
            extends Command {
        private boolean findRenames;
        private int findRenamesN;
        private boolean findCopies;
        private int findCopiesN;
        private String rev;
        private String path;

        protected Blame(String command) {
            super(command);
        }

        protected Blame(String command, File dir) {
            super(command, dir);
        }

        public Blame findRenames(boolean findRenames) {
            this.findRenames = findRenames;
            return this;
        }

        public Blame findRenamesN(int n) {
            findRenamesN = n;
            return this;
        }

        public Blame findCopies(boolean findCopies) {
            this.findCopies = findCopies;
            return this;
        }

        public Blame findCopiesN(int n) {
            findCopiesN = n;
            return this;
        }

        public Blame rev(String rev) {
            this.rev = rev;
            return this;
        }

        public Blame path(String path) {
            this.path = path;
            return this;
        }

        @Override public <R, T extends ResultHandler<R>> R execute(T result)
                throws IOException {
            ArrayList<String> args = new ArrayList<>();
            args.add("blame");
            if (findRenames) {
                String a = "-M";
                if (findRenamesN > 0) {
                    a += findRenamesN;
                }
                args.add(a);
            }
            if (findCopies) {
                String a = "-C";
                if (findCopiesN > 0) {
                    a += findCopiesN;
                }
                args.add(a);
            }
            args.add("--porcelain");
            if (rev != null) {
                args.add(rev);
            }
            args.add("--");
            args.add(path);
            runExpectSuccess(args, result);
            return result.result();
        }
    }

    /** Reads data from process output pipe. */
    private static class ReaderThread
            extends Thread {
        private final InputStream in;
        private final LineHandler lineHandler;
        private IOException ex;

        private ReaderThread(InputStream in, LineHandler lineHandler) {
            this.in = in;
            this.lineHandler = lineHandler;
        }

        @Override public void run() {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            try {
                int len;
                while ((len = in.read(buf)) != -1) {
                    for (int n = 0; n < len; n++) {
                        if (buf[n] == '\n') {
                            // Produce new line.
                            lineHandler.line(line(line));
                        }
                        else {
                            line.write(buf[n]);
                        }
                    }
                }
                if (line.size() > 0) {
                    // The last line was not terminated by eol.
                    lineHandler.line(line(line));
                }
            }
            catch (IOException ex) {
                this.ex = ex;
            }
            finally {
                try {
                    in.close();
                }
                catch (IOException e) {
                }
            }
        }

        private static String line(ByteArrayOutputStream line) {
            String s = line.toString();
            line.reset();
            return s;
        }

        public void done()
                throws IOException {
            if (ex != null) {
                throw ex;
            }
        }
    }

    private static void join(Process process) {
        while (true) {
            try {
                process.waitFor();
                break;
            }
            catch (InterruptedException ex) {
            }
        }
    }

    private static void join(Thread thread) {
        while (true) {
            try {
                thread.join();
                break;
            }
            catch (InterruptedException ex) {
            }
        }
    }

    private static FileMode parseFileMode(String s) {
        int mode = 0;
        for (int n = 0; n < s.length(); n++) {
            mode = (mode << 3) + (int) s.charAt(n) - '0';
        }
        return FileMode.fromBits(mode);
    }

    private final String command;
    private final File dir;

    public Git(File dir) {
        this("git", dir);
    }

    public Git(String command, File dir) {
        this.command = command;
        this.dir = dir;
    }

    public Version version() {
        return new Version(command, dir);
    }

    public Config config() {
        return new Config(command, dir);
    }

    public Checkout checkout() {
        return new Checkout(command, dir);
    }

    public RevList revList() {
        return new RevList(command, dir);
    }

    public MergeBase mergeBase() {
        return new MergeBase(command, dir);
    }

    public ReadTree readTree() {
        return new ReadTree(command, dir);
    }

    public WriteTree writeTree() {
        return new WriteTree(command, dir);
    }

    public DiffTree diffTree() {
        return new DiffTree(command, dir);
    }

    public LsFiles lsFiles() {
        return new LsFiles(command, dir);
    }

    public Status status() {
        return new Status(command, dir);
    }

    public Reset reset() {
        return new Reset(command, dir);
    }

    public Merge merge() {
        return new Merge(command, dir);
    }

    public Blame blame() {
        return new Blame(command, dir);
    }

    public MergeFile mergeFile() {
        return new MergeFile(command, dir);
    }

    public static Git get(File dir)
            throws IOException {
        String property = System.getProperty("git");
        if (property != null) {
            return new Git(property, dir);
        }
        ArrayList<String> commandList = new ArrayList<>(Arrays.asList("git"));
        for (String command : commandList) {
            Git git = new Git(command, dir);
            try {
                git.version().execute(new IgnoreResult());
            }
            catch (IOException ex) {
                continue;
            }
            System.setProperty("git", command);
            return git;
        }
        Assert.fail("Cannot execute git");
        throw new IllegalStateException(); // Unreachable
    }
}
