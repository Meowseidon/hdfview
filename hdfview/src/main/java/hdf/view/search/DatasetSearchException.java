/****************************************************************************
 * Copyright by The HDF Group.                                               *
 * All rights reserved.                                                       *
 ****************************************************************************/

package hdf.view.search;

/**
 * A Dataset-search failure with a stable presentation key and arguments.
 *
 * <p>The exception deliberately keeps the native or object-library cause in
 * its cause chain.  Search presentation code can translate {@link #messageKey()}
 * without comparing or replacing the technical exception message.</p>
 */
public final class DatasetSearchException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Stable presentation categories emitted by the search engine. */
    public enum Code {
        DATASET_ENUMERATION("search.error.enumerateDatasets"),
        DIMENSIONS_UNAVAILABLE("search.error.dimensionsUnavailable"),
        BLOCK_READ_NO_DATA("search.error.blockReadNoData"),
        SUBSET_UNAVAILABLE("search.error.subsetUnavailable"),
        SELECTION_RANK_CHANGED("search.error.selectionRankChanged"),
        BLOCK_READ_FAILED("search.error.blockReadFailed"),
        DATASET_READER_UNAVAILABLE("search.error.datasetReaderUnavailable"),
        UNSUPPORTED_DATATYPE("search.unsupportedDatatype"),
        UNKNOWN_DATATYPE("search.error.unknownDatatype"),
        SEARCH_FAILED("search.error.failed");

        private final String messageKey;

        Code(String messageKey) { this.messageKey = messageKey; }

        public String messageKey() { return messageKey; }
    }

    private final Code code;
    private final Object[] messageArgs;

    private DatasetSearchException(Code code, Throwable cause, Object[] messageArgs)
    {
        super(code == null ? "search.error.failed" : code.messageKey(), cause);
        this.code        = code == null ? Code.SEARCH_FAILED : code;
        this.messageArgs = messageArgs == null ? new Object[0] : messageArgs.clone();
    }

    /** Create a stable failure without an underlying cause. */
    public static DatasetSearchException localized(Code code, Object... messageArgs)
    {
        return new DatasetSearchException(code, null, messageArgs);
    }

    /** Create a stable failure while retaining the original technical cause. */
    public static DatasetSearchException localizedWithCause(Code code, Throwable cause,
                                                            Object... messageArgs)
    {
        return new DatasetSearchException(code, cause, messageArgs);
    }

    /** Wrap an unexpected worker failure in the generic localized category. */
    public static DatasetSearchException from(Throwable error)
    {
        if (error instanceof DatasetSearchException)
            return (DatasetSearchException)error;

        return localizedWithCause(Code.SEARCH_FAILED, error);
    }

    public Code code() { return code; }

    public String messageKey() { return code.messageKey(); }

    public Object[] messageArgs() { return messageArgs.clone(); }

    /** Keep a useful detail available for legacy callback consumers. */
    public String technicalDetail()
    {
        return technicalDetail(getCause());
    }

    private static String technicalDetail(Throwable error)
    {
        if (error == null)
            return "";
        String message = error.getMessage();
        return message == null || message.isEmpty()
            ? error.getClass().getSimpleName() : message;
    }
}
