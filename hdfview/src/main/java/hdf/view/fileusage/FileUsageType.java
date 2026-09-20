package hdf.view.fileusage;

/** The application/service category reported by Restart Manager. */
public enum FileUsageType {
    UNKNOWN(0, "dialog.fileUsage.type.unknown", false, false),
    MAIN_WINDOW(1, "dialog.fileUsage.type.mainWindow", false, false),
    OTHER_WINDOW(2, "dialog.fileUsage.type.otherWindow", false, false),
    SERVICE(3, "dialog.fileUsage.type.service", true, false),
    EXPLORER(4, "dialog.fileUsage.type.explorer", false, false),
    CONSOLE(5, "dialog.fileUsage.type.console", false, false),
    CRITICAL(1000, "dialog.fileUsage.type.critical", false, true);

    private final int restartManagerValue;
    private final String messageKey;
    private final boolean service;
    private final boolean critical;

    FileUsageType(int restartManagerValue, String messageKey, boolean service, boolean critical) {
        this.restartManagerValue = restartManagerValue;
        this.messageKey = messageKey;
        this.service = service;
        this.critical = critical;
    }

    public int restartManagerValue() {
        return restartManagerValue;
    }

    public String messageKey() {
        return messageKey;
    }

    public boolean isService() {
        return service;
    }

    public boolean isCritical() {
        return critical;
    }

    public static FileUsageType fromRestartManagerValue(int value) {
        for (FileUsageType type : values()) {
            if (type.restartManagerValue == value) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
