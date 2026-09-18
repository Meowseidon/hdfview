/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                       *
 *****************************************************************************/

package hdf.view.search;

import java.util.Arrays;

/**
 * A stable result from a Dataset content search.
 *
 * <p>The result intentionally stores the file path, Dataset path, and the
 * zero-based coordinate rather than any TreeItem or display text.  The HDFView
 * window resolves those stable identities again when a user activates a row,
 * so a result cannot accidentally navigate to a similarly named object.</p>
 */
public final class DatasetSearchResult {
    /** The kind of value that produced the match. */
    public enum MatchType {
        /** The Dataset name matched the query. */
        DATASET_NAME,
        /** A numeric Dataset value matched exactly. */
        NUMERIC_VALUE,
        /** A string Dataset value matched textually. */
        STRING_VALUE,
        /** A character Dataset value matched textually. */
        CHAR_VALUE
    }

    private final String filePath;
    private final String datasetPath;
    private final String matchedValue;
    private final long[] coordinate;
    private final MatchType matchType;

    public DatasetSearchResult(String filePath, String datasetPath, String matchedValue,
                               long[] coordinate, MatchType matchType)
    {
        this.filePath      = filePath == null ? "" : filePath;
        this.datasetPath   = datasetPath == null ? "" : datasetPath;
        this.matchedValue  = matchedValue == null ? "" : matchedValue;
        this.coordinate    = coordinate == null ? new long[0] : coordinate.clone();
        this.matchType     = matchType;
    }

    /** @return the file path captured when the search ran. */
    public String getFilePath() { return filePath; }

    /** @return the full HDF object path of the Dataset. */
    public String getDatasetPath() { return datasetPath; }

    /** @return the value or Dataset name that matched. */
    public String getMatchedValue() { return matchedValue; }

    /** @return a defensive copy of the zero-based Dataset coordinate. */
    public long[] getCoordinate() { return coordinate.clone(); }

    /** @return the match category. */
    public MatchType getMatchType() { return matchType; }

    /** @return a compact, stable coordinate representation for the result table. */
    public String getCoordinateText() { return Arrays.toString(coordinate); }

    @Override
    public String toString()
    {
        return "DatasetSearchResult{" + filePath + ", " + datasetPath + ", " +
               matchedValue + ", " + Arrays.toString(coordinate) + ", " + matchType + "}";
    }
}
