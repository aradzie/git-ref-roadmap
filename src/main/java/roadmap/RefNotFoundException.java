package roadmap;

public class RefNotFoundException extends RuntimeException {
    private final String ref;

    public RefNotFoundException(String ref) {
        this.ref = Check.notNull(ref);
    }

    public String getRef() {
        return ref;
    }

    @Override public String getMessage() {
        return "Ref '" + ref + "' not found";
    }
}
