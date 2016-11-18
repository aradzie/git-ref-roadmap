package roadmap;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import roadmap.graph.CommitDetails;
import roadmap.graph.CommitList;
import roadmap.graph.RefDiff;
import roadmap.graph.RefGraph;
import roadmap.ref.Ref;
import roadmap.ref.RefSet;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.text.DateFormat.SHORT;
import static java.text.DateFormat.getDateTimeInstance;

public class Table {
    static class Row {
        static final Comparator<Row> COMPARATOR = new Comparator<Row>() {
            @Override public int compare(Row o1, Row o2) {
                return -(sort(o1, o2));
            }

            int sort(Row o1, Row o2) {
                // Least amount of commits in the master
                if (o2.nb > o1.nb) {
                    return 1;
                }
                if (o2.nb < o1.nb) {
                    return -1;
                }
                // Least amount of commits in the other branch
                if (o2.na > o1.na) {
                    return 1;
                }
                if (o2.na < o1.na) {
                    return -1;
                }
                return 0;
            }
        };
        static final DateFormat FORMAT = getDateTimeInstance(SHORT, SHORT);
        /** Ref on the left side. */
        final Ref a;
        /** Number of commits since merge base on the left side. */
        final int na;
        /** Ref on the right side. */
        final Ref b;
        /** Number of commits since merge base on the right side. */
        final int nb;
        final CommitDetails commit;

        Row(Ref a, int na, Ref b, int nb, CommitDetails commit) {
            this.a = Objects.requireNonNull(a);
            this.b = Objects.requireNonNull(b);
            this.na = na;
            this.nb = nb;
            this.commit = Objects.requireNonNull(commit);
        }

        @Override public String toString() {
            StringBuilder s = new StringBuilder();
            toString(s);
            return s.toString();
        }

        void toString(StringBuilder s) {
            formatDiff(s);
            s.append("\n\t");
            formatCommit(s);
            s.append("\n");
        }

        void formatDiff(StringBuilder s) {
            alignLeft(s, a.getName(), 35);
            s.append(" ");
            alignRight(s, String.valueOf(na), 6);
            s.append(" / ");
            alignLeft(s, String.valueOf(nb), 6);
            s.append(" ");
            align(s, b.getName(), 35);
        }

        void formatCommit(StringBuilder s) {
            s.append("Updated on ");
            s.append(FORMAT.format(commit.getAuthor().getWhen()));
            s.append(" by ");
            s.append(commit.getAuthor().getName());
            s.append(" <");
            s.append(commit.getAuthor().getEmailAddress());
            s.append(">: ");
            String message = commit.getMessage();
            int n = message.indexOf("\n");
            if (n != -1) {
                message = message.substring(0, n);
            }
            align(s, message, 60);
        }
    }

    static List<Row> table(
            ObjectReader objectReader,
            RefSet refSet, CommitList commitList,
            RefGraph refGraph)
            throws IOException {
        ArrayList<Row> rows = new ArrayList<>();
        Ref master = refSet.defaultBranch();
        for (RefDiff diff : refGraph.getRefDiffs()) {
            if (ObjectId.equals(master.getId(), diff.getB())) {
                CommitDetails commit = commitList.loadDetails(objectReader, diff.getA());
                Set<Ref> set = refSet.byId(diff.getA());
                for (Ref ref : set) {
                    rows.add(new Row(ref, diff.getCommitsA(), master, diff.getCommitsB(), commit));
                }
            }
        }
        rows.sort(Row.COMPARATOR);
        return rows;
    }

    static void print(List<Row> rows) {
        StringBuilder s = new StringBuilder();
        for (Row row : rows) {
            row.formatDiff(s);
            s.append("\n\t");
            row.formatCommit(s);
            s.append("\n");
        }
        System.out.print(s);
    }

    static void alignLeft(StringBuilder s, String v, int w) {
        int l = v.length();
        if (l < w) {
            s.append(v);
            for (int n = l; n < w; n++) {
                s.append(" ");
            }
        }
        else {
            s.append("...");
            s.append(v.substring(l - w + 3));
        }
    }

    static void alignRight(StringBuilder s, String v, int w) {
        int l = v.length();
        if (l < w) {
            for (int n = l; n < w; n++) {
                s.append(" ");
            }
            s.append(v);
        }
        else {
            s.append("...");
            s.append(v.substring(l - w + 3));
        }
    }

    static void align(StringBuilder s, String v, int w) {
        int l = v.length();
        if (l < w) {
            s.append(v);
        }
        else {
            s.append("...");
            s.append(v.substring(l - w + 3));
        }
    }
}
