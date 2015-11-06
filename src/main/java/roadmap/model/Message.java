package roadmap.model;

import java.io.*;
import java.util.*;

public final class Message implements Serializable {
    /**
     * Footer line (e.g. "Signed-off-by") from a commit message for machine processing.
     *
     * <p>A footer line's key must match the pattern {@code ^[A-Za-z0-9-]+:}, while
     * the value is free-form, but must not contain an LF. Very common keys seen
     * in the wild are:</p>
     *
     * <ul>
     * <li>{@code Signed-off-by} (agrees to Developer Certificate of Origin)
     * <li>{@code Acked-by} (thinks change looks sane in context)
     * <li>{@code Reported-by} (originally found the issue this change fixes)
     * <li>{@code Tested-by} (validated change fixes the issue for them)
     * <li>{@code CC}, {@code Cc} (copy on all email related to this change)
     * <li>{@code Bug} (link to project's bug tracking system)
     * </ul>
     */
    public static final class Footer implements Serializable {
        private final String key;
        private final String value;

        public Footer(String key, String value) {
            this.key = Objects.requireNonNull(key);
            this.value = Objects.requireNonNull(value);
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }

    public static final class Fragment implements Serializable, CharSequence {
        /** Full text of this fragment. */
        private final String value;
        /** Parts with special meaning. */
        private final ArrayList<Token> tokens = new ArrayList<>();

        public Fragment(String value) {
            this.value = value;
            linkify(this.value);
        }

        @Override public int length() {
            return value.length();
        }

        @Override public char charAt(int index) {
            return value.charAt(index);
        }

        @Override public CharSequence subSequence(int start, int end) {
            return value.substring(start, end);
        }

        @Override public String toString() {
            return value;
        }

        public boolean addToken(int type, int start, int length, String value) {
            return addToken(new Token(type, start, length, value));
        }

        /**
         * Give part of a message special meaning.
         *
         * <p>Tokens must not interleave. If an attempt is made to add a token
         * whose start or end position is in range of a previously added token,
         * the current token will not be added.</p>
         *
         * @param token Part of a message.
         * @return {@code true} if token was added, {@code false} otherwise.
         */
        public boolean addToken(Token token) {
            // Insert token into appropriate position.
            for (int i = 0; i < tokens.size(); i++) {
                Token curr = tokens.get(i);
                // Don't let tokens interleave.
                if (token.start >= curr.start
                        && token.start < curr.start + curr.length
                        || token.start + token.length > curr.start
                        && token.start + token.length < curr.start + curr.length) {
                    return false;
                }
                if (token.start < tokens.get(i).start) {
                    tokens.add(i, token);
                    return true;
                }
            }
            tokens.add(token);
            return true;
        }

        /** @return Message fragments with special meaning. */
        public List<Token> getTokens() {
            return tokens;
        }

        /**
         * Given a text, search for URIs and make href's out of them.
         *
         * @param text Text to linkify.
         */
        private void linkify(String text) {
            for (String scheme : VALID_URI_SCHEMES) {
                int start = text.indexOf(scheme);
                while (start != -1) {
                    int offset = start + scheme.length();
                    int end = offset;
                    for (; end < text.length(); end++) {
                        if (isInvalidUriCharacter(text.charAt(end))) {
                            break;
                        }
                    }
                    if (end > offset) {
                        String uri = text.substring(start, end);
                        addToken(new Token(Token.TYPE_LINK, start, end - start, uri));
                    }
                    start = text.indexOf(scheme, end);
                }
            }
        }

        /**
         * If the given char is not one of the following in VALID_URI_CHARS then
         * return {@code true}.
         *
         * @param c Char to check against VALID_URI_CHARS array.
         * @return {@code true} if c is a valid URI char.
         */
        private static boolean isInvalidUriCharacter(char c) {
            if (Character.isLetterOrDigit(c)) {
                return false;
            }
            for (int i = 0; i < VALID_URI_CHARS.length; i++) {
                if (VALID_URI_CHARS[i] == c) {
                    return false;
                }
            }
            return true;
        }

        /** Valid URI schemes. */
        private static final String[] VALID_URI_SCHEMES = {
                "http://", "https://", "ftp://", "mailto:",
        };
        /**
         * Specify the only characters that are allowed in a URI besides alpha and
         * numeric characters. Refer RFC2396 - http://www.ietf.org/rfc/rfc2396.txt
         */
        private static final char[] VALID_URI_CHARS = {
                '?', '+', '%', '&', ':', '/', '.', '@', '_', ';',
                '=', '$', ',', '-', '!', '~', '*', '\'', '(', ')',
        };
    }

    /** Fragment of message with a special meaning. */
    public static final class Token implements Serializable {
        /** Highlighted message fragment, probably revealing a search term. */
        public static final int TYPE_HIGHLIGHT = 1;
        /** An arbitrary hyperlink. */
        public static final int TYPE_LINK = 2;
        /** Part of a message denotes object identifier. */
        public static final int TYPE_COMMIT = 3;
        /** Part of a message denotes person name. */
        public static final int TYPE_PERSON = 4;
        /** Token type. */
        public final int type;
        /** Token start offset. */
        public final int start;
        /** Token length. */
        public final int length;
        /**
         * Extended token value. For instance, if the original token is partial SHA-1, then extended
         * value would be full SHA-1.
         */
        public final String value;

        public Token(int type, int start, int length, String value) {
            this.start = start;
            this.length = length;
            this.type = type;
            this.value = value;
        }
    }

    /** Empty message to avoid NPEs. */
    public static final Message EMPTY = new Message("EMPTY");
    /** First line of a message. */
    private final Fragment title;
    /** Rest lines of a message. */
    private final Fragment body;
    /** Optional footers. */
    private final ArrayList<Footer> footers = new ArrayList<>();

    /**
     * Create new message instance.
     *
     * @param message An unparsed message.
     */
    public Message(String message) {
        if (message == null) {
            throw new NullPointerException();
        }

        String body;

        int n = message.indexOf('\n');
        if (n > 0) {
            // Message with title and body
            title = new Fragment(message.substring(0, n));

            // Separate title and body.
            while (n < message.length() && message.charAt(n) == '\n') {
                n++;
            }

            // Test if body is not empty.
            if (n < message.length()) {
                body = message.substring(n);

                // Parse footer lines.
                while (true) {
                    int i = body.lastIndexOf('\n');
                    if (i == -1) {
                        // Parse last body line.
                        if (parseFooterLine(body, 0)) {
                            body = "";
                        }
                        break;
                    }
                    // Parse line at the end of body.
                    if (parseFooterLine(body, i + 1)) {
                        body = body.substring(0, i);
                    }
                    else {
                        break;
                    }
                }
            }
            else {
                body = "";
            }
        }
        else {
            if (n == -1) {
                // Message with title and without body
                title = new Fragment(message);
                body = "";
            }
            else {
                // Message without title and with body
                title = new Fragment("untitled");
                while (n < message.length() && message.charAt(n) == '\n') {
                    n++;
                }
                if (n < message.length()) {
                    body = message.substring(n);
                }
                else {
                    body = "";
                }
            }
        }

        this.body = new Fragment(body);
    }

    /** @return First line of a message. */
    public Fragment getTitle() {
        return title;
    }

    /** @return Rest lines of a message. */
    public Fragment getBody() {
        return body;
    }

    /** @return Optional footers. */
    public List<Footer> getFooters() {
        return footers;
    }

    private boolean parseFooterLine(String message, int i) {
        if (i == message.length()) {
            return true;
        }
        int n = i;
        for (; n < message.length(); n++) {
            char c = message.charAt(n);
            if (c == ':') {
                break;
            }
            if (Character.isLetterOrDigit(c) || c == '-') {
                continue;
            }
            return false;
        }
        if (n > i && n < message.length()) {
            String key = message.substring(i, n);
            if (isValidFooter(key, message, n + 1)) {
                String value = message.substring(n + 1).trim();
                footers.add(0, new Footer(key, value));
                return true;
            }
        }
        return false;
    }

    @Override public String toString() {
        return "Message{title=" + title + "}";
    }

    private static boolean isValidFooter(String key, String message, int i) {
        for (String s : include) {
            if (s.equalsIgnoreCase(key)) {
                return true;
            }
        }
        for (String s : exclude) {
            if (s.equalsIgnoreCase(key)) {
                return false;
            }
        }
        if (i + 1 < message.length()) {
            if (message.charAt(i) == '/' && message.charAt(i + 1) == '/') {
                return false;
            }
        }
        return true;
    }

    private static final String[] include = {
            "signed-off-by",
            "acked-by",
            "reported-by",
            "tested-by",
            "cc",
            "bug",
    };
    private static final String[] exclude = {
            "mailto",
    };
}
