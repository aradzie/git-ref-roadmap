package roadmap.ref;

public class RefNotFoundException extends RuntimeException {
    private final String ref;

    public RefNotFoundException(String ref) {
        super("Ref '" + ref + "' not found");
        this.ref = ref;
    }

    public String getRef() {
        return ref;
    }
}
