package roadmap.util;

import org.eclipse.jgit.lib.*;
import org.kohsuke.args4j.*;
import org.kohsuke.args4j.spi.*;

import java.io.*;

public abstract class CliApp {
    public static class StatusException
            extends IOException {
        private final int code;

        public StatusException(String message) {
            this(message, 1);
        }

        public StatusException(String message, int code) {
            super(message);
            this.code = code;
        }

        public StatusException(String message, Throwable cause) {
            this(message, cause, 1);
        }

        public StatusException(String message, Throwable cause, int code) {
            super(message, cause);
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    public static class ObjectIdOptionHandler
            extends OptionHandler<ObjectId> {
        public ObjectIdOptionHandler(CmdLineParser parser, OptionDef option,
                                     Setter<? super ObjectId> setter) {
            super(parser, option, setter);
        }

        @Override public int parseArguments(Parameters params)
                throws CmdLineException {
            setter.addValue(ObjectId.fromString(params.getParameter(0)));
            return 1;
        }

        @Override public String getDefaultMetaVariable() {
            return "SHA";
        }
    }

    static {
        CmdLineParser.registerHandler(ObjectId.class, ObjectIdOptionHandler.class);
    }

    protected static void exec(String[] args, CliApp app)
            throws Exception {
        PrintWriter out = new PrintWriter(System.out);
        try {
            CmdLineParser parser = new CmdLineParser(app);
            try {
                parser.parseArgument(args);
                if (app.help) {
                    app.describe(out);
                    usage(parser, out);
                }
                else {
                    app.run(parser);
                }
            }
            catch (CmdLineException ex) {
                out.println(ex.getMessage());
                usage(parser, out);
            }
        }
        finally {
            out.flush();
        }
    }

    @Option(
            name = "-h",
            aliases = {"--help"},
            usage = "Show this help"
    )
    private boolean help;

    protected static void usage(CmdLineParser parser, PrintWriter out) {
        out.println("Usage:");
        parser.printUsage(out, null);
    }

    protected abstract void describe(PrintWriter out)
            throws Exception;

    protected abstract void run(CmdLineParser parser)
            throws Exception;
}
