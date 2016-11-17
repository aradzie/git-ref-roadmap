package roadmap.graph;

import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Information about parsed commit, it includes message, author and committer.
 *
 * <p>We do not store these attributes in the commit list because we worry
 * about memory consumption, so we only retrieve commit details on demand by
 * loading bytes from the owning repository and parsing commit body.</p>
 */
public class CommitDetails
        extends Commit {
    private final String message;
    private final PersonIdent author;
    private final PersonIdent committer;

    CommitDetails(RevCommit commit,
                  String message, PersonIdent author, PersonIdent committer) {
        super(commit, commit.getTree());
        this.message = message;
        this.author = author;
        this.committer = committer;
    }

    CommitDetails(Commit commit,
                  String message, PersonIdent author, PersonIdent committer) {
        super(commit);
        this.message = message;
        this.author = author;
        this.committer = committer;
    }

    /** @return Full commit message. */
    public String getMessage() {
        return message;
    }

    /** @return Commit author. */
    public PersonIdent getAuthor() {
        return author;
    }

    /** @return Committer. */
    public PersonIdent getCommitter() {
        return committer;
    }
}
