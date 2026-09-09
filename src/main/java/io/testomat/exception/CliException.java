package io.testomat.exception;

public class CliException extends RuntimeException {
    public CliException(String message) {
        super(message);
    }

    public CliException(String message, Throwable cause) {
        super(message, cause);
    }

    public static String describe(Throwable error) {
        String message = error.getMessage();
        if (message == null) {
            return error.getClass().getSimpleName();
        }

        Throwable cause = error.getCause();
        if (cause != null && cause.getMessage() != null
                && !cause.getMessage().equals(message)) {
            return message + ": " + cause.getMessage();
        }

        return message;
    }
}
