package org.jabref.logic.importer.fetcher;

import java.io.*;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONException;

import org.jabref.logic.importer.EntryBasedFetcher;
import org.jabref.logic.importer.FetcherException;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.field.StandardField;
import org.jabref.preferences.JabRefPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to fetch for an articles citation relations on opencitations.net's API
 */
public class CitationRelationFetcher implements EntryBasedFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CitationRelationFetcher.class);
    private SearchType searchType;
    private static final String BASIC_URL = "https://opencitations.net/index/api/v1/metadata/";
    private final DoubleProperty progress = new SimpleDoubleProperty(0);

    /**
     * Possible search methods
     */
    public enum SearchType {
        CITING("reference"),
        CITEDBY("citation");

        public final String label;

        SearchType(String label) {
            this.label = label;
        }
    }

    public CitationRelationFetcher(SearchType searchType) {
        this.searchType = searchType;
        progress.set(0);
    }

    /**
     * Executes the search method associated with searchType
     *
     * @param entry Entry to search relations for
     * @return List of BibEntries found
     */
    @Override
    public List<BibEntry> performSearch(BibEntry entry) throws FetcherException {
        String doi = entry.getField(StandardField.DOI).orElse("");
        if (searchType != null) {
            List<BibEntry> list = new ArrayList<>();
            try {
                LOGGER.debug("Search: {}" , BASIC_URL + doi);
                JSONArray json = readJsonFromUrl(BASIC_URL + doi);
                if (json == null) {
                    throw new FetcherException("No internet connection! Please try again.");
                } else if (json.isEmpty()) {
                    return list;
                }
                LOGGER.debug("API Answer: " + json.toString());
                String[] items = json.getJSONObject(0).getString(searchType.label).split("; ");
                if (items.length > 0) {
                    int i = 1;
                    for (String item : items) {
                        LOGGER.debug("Current Item " + i + "/" + items.length);
                        setProgress(i, items.length);
                        if (!doi.equals(item) && !item.equals("")) {
                            DoiFetcher doiFetcher = new DoiFetcher(JabRefPreferences.getInstance().getImportFormatPreferences());
                            try {
                                doiFetcher.performSearchById(item).ifPresent(list::add);
                            } catch (FetcherException fetcherException) {
                                // No information for doi found
                            }
                        }
                        i++;
                    }
                }
            } catch (IOException | JSONException e) {
                throw new FetcherException("Couldn't connect to opencitations.net! Please try again.");
            }
            return list;
        } else {
            return null;
        }
    }

    /**
     * Method to read JSON files from URL
     *
     * @param url API URL to search
     * @return JSONArray containing the response of the API
     */
    public static JSONArray readJsonFromUrl(String url) throws IOException, JSONException {
        try (InputStream is = new URL(url).openStream()) {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String jsonText = readAll(rd);
            return new JSONArray(jsonText);
        } catch (UnknownHostException | SocketException exception) {
            return null;
        }
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    private void setProgress(double i, double total) {
        progress.set(i/total);
    }

    public DoubleProperty getProgress() {
        return progress;
    }

    @Override
    public String getName() {
        return "CitationRelationFetcher";
    }
}