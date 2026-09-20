package hdf.view.fileusage;

/** A checked failure while querying or acting on Windows file usage. */
public final class FileUsageException extends Exception {
    private final String messageKey;
    private final Object[] messageArgs;

    public FileUsageException(String message) {
        super(message);
        messageKey = null;
        messageArgs = new Object[0];
    }

    public FileUsageException(String message, Throwable cause) {
        super(message, cause);
        messageKey = null;
        messageArgs = new Object[0];
    }

    private FileUsageException(String messageKey, Throwable cause, Object[] messageArgs) {
        super(messageKey, cause);
        this.messageKey = messageKey;
        this.messageArgs = messageArgs.clone();
    }

    public static FileUsageException localized(String messageKey, Object... messageArgs) {
        return new FileUsageException(messageKey, null, messageArgs);
    }

    public static FileUsageException localizedWithCause(
            String messageKey, Throwable cause, Object... messageArgs) {
        return new FileUsageException(messageKey, cause, messageArgs);
    }

    public String messageKey() {
        return messageKey;
    }

    public Object[] messageArgs() {
        return messageArgs.clone();
    }
}
