package roadmap.model;

import org.eclipse.jgit.lib.*;

import java.util.*;

/**
 * Annotated tag information.
 *
 * <p>Annotated tags are similar to simple tag refs, except that they are defined
 * by their own object type in repository, have distinct tag id, tagger person and
 * message.</p>
 *
 * <p>While this class is a subclass of ref, generally speaking, annotated tags are
 * NOT refs. Tags are named and discovered by means of refs, still they are not refs.
 * In details, refs may be unannotated tags, bat annotated tags are not refs. But for
 * simplicity reason, we still represent annotated tags as refs with a simple trick.</p>
 *
 * <p>Here is the difference between annotated tags and refs in more details.
 * Lets consider we have a tag object with SHA-1 id <em>T1</em>. The tag has name
 * <em>v-0.1</em> and references commit id <em>C1</em>. At the same time we have
 * ref <em>refs/tags/v-0.1</em> which binds to object id <em>T1</em>. Therefore ref
 * is used to discover tag with the same name, but here is the subtle detail: ref may
 * be named differently than the tag it references.</p>
 *
 * <p>Annotated tags, just like refs may refer to commits, trees and blobs. Annotated
 * tags may also recursively point to another annotated tags. If later is the case,
 * then peeling procedure is required to walk through refs chain to discover referenced
 * object that is not tag. This class, however, always represents final, peeled object
 * resolved from referenced tags, if any.</p>
 *
 * <p>So how do we represent tags as ref? With refs we have two attributes: ref name
 * and referenced object id. With annotated tags we have more attributes: tag name,
 * tag id, referenced object id which is peeled, tagger person and tag message. The
 * trick is to map tag name to ref name attribute, and referenced object id to the same
 * attribute of the base class. This is approximation which works just well.</p>
 */
public class AnnotatedTag extends roadmap.model.Ref {
    private final ObjectId tagId;
    private final PersonIdent tagger;
    private final Message message;

    /**
     * Create new annotated tag.
     *
     * @param name     Tag name that is also ref name.
     * @param commitId Commit id this annotated tag was placed on.
     * @param tagId    Tag object id.
     * @param tagger   Tagger person who created this tag.
     * @param message  Tag message.
     */
    public AnnotatedTag(String name, ObjectId commitId,
                        ObjectId tagId, PersonIdent tagger, Message message) {
        super(name, commitId);
        this.tagId = Objects.requireNonNull(tagId).copy();
        this.tagger = Objects.requireNonNull(tagger);
        this.message = Objects.requireNonNull(message);
    }

    private AnnotatedTag(AnnotatedTag that, boolean forced) {
        super(that, forced);
        tagId = that.getTagId();
        tagger = that.getTagger();
        message = that.getMessage();
    }

    /**
     * Tag object has object id on its own, which is distinct from the tagged object.
     *
     * @return Tag object id.
     */
    public ObjectId getTagId() {
        return tagId;
    }

    /** @return The person who created tag. */
    public PersonIdent getTagger() {
        return tagger;
    }

    /** @return Tag message. */
    public Message getMessage() {
        return message;
    }

    @Override public roadmap.model.Ref asForced() {
        return new AnnotatedTag(this, true);
    }

    @Override public boolean isBranch() {
        return false;
    }

    @Override public boolean isTag() {
        return true;
    }

    @Override public boolean isAnnotatedTag() {
        return true;
    }

    @Override public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!Objects.equals(getClass(), o.getClass())) { return false; }
        AnnotatedTag that = (AnnotatedTag) o;
        if (!Objects.equals(getName(), that.getName())) { return false; }
        if (!Objects.equals(getId(), that.getId())) { return false; }
        return true;
    }

    @Override public int hashCode() {
        return Objects.hash(getName(), getId());
    }

    @Override public String toString() {
        return getName() + ":" + getId().getName() + "[" + tagId.getName() + "]";
    }
}
