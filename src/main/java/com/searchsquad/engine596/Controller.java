package com.searchsquad.engine596;

import com.searchsquad.engine596.DTO.SearchResult;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
public class Controller {

    private static final int DEFAULT_NUM_RESULTS = 10;

    private static final String ROOT_DIR = "/Users/cihadozcan/Desktop/";
    private static final String AAPR_INDEX_DIRECTORY = ROOT_DIR + "AAPR_Index";
    private static final String AAPR_DATA_DIRECTORY = ROOT_DIR + "AAPR_Dataset";
    private static final String CISI_INDEX_DIRECTORY = ROOT_DIR + "CISI_Index";
    private static final String CISI_DATA_DIRECTORY = ROOT_DIR + "CISI_Dataset";
    private static final String CISI_REL_DIRECTORY = ROOT_DIR + "CISI_Dataset/Relations";


    @GetMapping("/search")
    public ResponseEntity<List<SearchResult>> searchDocuments(@RequestParam String fieldName,
                                                              @RequestParam String queryText,
                                                              @RequestParam String dataset) {
        try {

            List<SearchResult> searchResults = getSearchResults(fieldName, queryText, dataset, DEFAULT_NUM_RESULTS);

            if (searchResults == null) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

            return new ResponseEntity<>(searchResults, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    private static IndexSearcher getIndexSearcher(String indexPath) throws IOException {
        Directory indexDirectory = FSDirectory.open(Paths.get(indexPath));
        IndexReader indexReader = DirectoryReader.open(indexDirectory);
        return new IndexSearcher(indexReader);
    }

    private static List<SearchResult> getSearchResults(String fieldName, String queryText, String dataset, int num_results) throws ParseException, IOException {
        QueryParser queryParser = null;

        if (fieldName.equalsIgnoreCase("title") || fieldName.equalsIgnoreCase("abstract")) {
            // Create Lucene query parser and specify the field to search
            queryParser = new QueryParser(fieldName, new StandardAnalyzer());
        } else if (fieldName.equalsIgnoreCase("text")) {
            // Create a custom query parser for searching all fields with field weights
            Map<String, Float> fieldWeights = new HashMap<>();
            fieldWeights.put("title", 4.0f);
            fieldWeights.put("abstract", 3.0f);
            fieldWeights.put("text", 2.0f);
            queryParser = new MultiFieldQueryParser(new String[]{"title", "abstract", "text"}, new StandardAnalyzer(), fieldWeights);
        } else {
            System.out.println("Invalid field name: " + fieldName);
            return null;
        }

        // Parse the user query text
        Query query = queryParser.parse(QueryParser.escape(queryText));

        // Perform the search
        IndexSearcher indexSearcher = null;
        if(dataset.equalsIgnoreCase("AAPR")) {
            indexSearcher = getIndexSearcher(AAPR_INDEX_DIRECTORY);
        } else if(dataset.equalsIgnoreCase("CISI")) {
            indexSearcher = getIndexSearcher(CISI_INDEX_DIRECTORY);
        } else
            return null;

        TopDocs topDocs = indexSearcher.search(query, num_results);

        // Retrieve and populate the search results
        List<SearchResult> searchResults = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            int docId = scoreDoc.doc;
            Document document = indexSearcher.doc(docId);

            // Populate SearchResult
            SearchResult result = new SearchResult();
            result.setTitle(document.get("title"));
            result.setAbstractText(document.get("abstract"));
            result.setText(document.get("text"));
            result.setRank(scoreDoc.score);
            result.setId(document.get("id"));

            searchResults.add(result);
        }
        return searchResults;
    }

    // Takes dataset name as parameter. Path variables associated with datasets must be defined at the top of the class.
    @GetMapping("/index")
    public ResponseEntity<String> createIndex(@RequestParam String dataset) throws IOException {

        if (dataset.equalsIgnoreCase("AAPR")) {
            createIndex(AAPR_INDEX_DIRECTORY, AAPR_DATA_DIRECTORY);
        } else if (dataset.equalsIgnoreCase("CISI")) {
            createIndex(CISI_INDEX_DIRECTORY, CISI_DATA_DIRECTORY);
        } else {
            return new ResponseEntity<>("Invalid dataset name", HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>("Index created successfully for " + dataset + " dataset", HttpStatus.OK);

    }

    @GetMapping("/eval")
    public void evalCISI() throws IOException, ParseException {

        List<String> query_texts = new ArrayList<>();
        List<List<String>> doc_id_lists = new ArrayList<>();

        // Read the JSON data from the file
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(CISI_REL_DIRECTORY + "/cisi_relations.json"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Parse the JSON data
        JSONArray jsonArray = new JSONArray(sb.toString());

        // Iterate over the JSON objects and get relations
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject = jsonArray.getJSONObject(i);

            String query_text = jsonObject.getString("query_text");
            query_texts.add(query_text);

            // Every query has relevant documents, given as an array of doc ids
            JSONArray doc_id_list = jsonObject.getJSONArray("doc_id_list");

            List<String> doc_ids = new ArrayList<>();
            for (int j = 0; j < doc_id_list.length(); j++){
                Object obj = doc_id_list.get(j);
                String doc_id = obj.toString();
                doc_ids.add(doc_id);
            }
            doc_id_lists.add(doc_ids);

        }
        System.out.println("CISI Relations are retrieved successfully.");

        long num_samples_at1 = 0;
        long num_samples_at5 = 0;
        long num_samples_at10 = 0;

        double matchingCount_at_10 = 0;
        double matchingCount_at_5 = 0;
        double matchingCount_at_1 = 0;
        double totalMatchingCount = 0;
        long totalRelevantDocNumber = 0;


        // Evaluating the system
        for (int i = 0; i < query_texts.size(); i++) {
            String query_text = query_texts.get(i);
            List<String> doc_id_list = doc_id_lists.get(i);
            int queryRelevantDocNumber = doc_id_list.size();
            totalRelevantDocNumber += queryRelevantDocNumber;

            List<SearchResult> search_results = getSearchResults("text", query_text, "CISI", queryRelevantDocNumber);

            int matchingCount = 0;

            int result_idx = 0;
            for (SearchResult result : search_results) {
                result_idx++;
                if (doc_id_list.contains(result.getId())) {
                    matchingCount++;
                    if (result_idx == 1) {
                        matchingCount_at_1++;
                    }
                    if (result_idx <= 5) {
                        matchingCount_at_5++;
                    }
                    if (result_idx <= 10) {
                        matchingCount_at_10++;
                    }
                }
            }


            totalMatchingCount += matchingCount;
            if(queryRelevantDocNumber >= 10){
                num_samples_at10 += 1;
                num_samples_at5 += 1;
                num_samples_at1 += 1;
            } else if(queryRelevantDocNumber >= 5){
                num_samples_at5 += 1;
                num_samples_at1 += 1;
            } else {
                num_samples_at1 += 1;
            }

        }
        System.out.println("Average P@1: " + String.format("%.2f%%", (matchingCount_at_1 / num_samples_at1 * 100)) + " over " + num_samples_at1 + " samples.");
        System.out.println("Average P@5: " + String.format("%.2f%%", (matchingCount_at_5 / (num_samples_at5 * 5) * 100)) + " over " + num_samples_at5 + " samples.");
        System.out.println("Average P@10: " + String.format("%.2f%%", (matchingCount_at_10 / (num_samples_at10 * 10) * 100)) + " over " + num_samples_at10 + " samples.");
        System.out.println("Average Recall: " + String.format("%.2f%%", (totalMatchingCount / totalRelevantDocNumber * 100)) + " over " + query_texts.size() + " samples.");
    }

    public void createIndex(String index_dir, String data_dir) throws IOException {

        // Set up Lucene index directory
        Directory indexDirectory = FSDirectory.open(Paths.get(index_dir));

        // Create the analyzer
        Analyzer analyzer = new StandardAnalyzer();

        // Create the index writer configuration
        IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);

        // Create the index writer
        IndexWriter indexWriter = new IndexWriter(indexDirectory, writerConfig);

        File dataDir = new File(data_dir);
        File[] targetJsonFiles = dataDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

        if (targetJsonFiles != null) {
            for (File jsonFile : targetJsonFiles) {
                System.out.println("Indexing file: " + jsonFile.getName());

                // Read the JSON data from the file
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(jsonFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                // Parse the JSON data
                JSONArray jsonArray = new JSONArray(sb.toString());

                // Iterate over the JSON objects and create the Lucene documents
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);

                    String id = jsonObject.getString("id");
                    String title = jsonObject.getString("title");
                    String abstractText = jsonObject.getString("abstract");
                    String text = jsonObject.getString("text");

                    // Create a new Lucene document
                    Document document = new Document();
                    document.add(new StringField("id", id, Field.Store.YES));
                    document.add(new TextField("title", title, Field.Store.YES));
                    document.add(new TextField("abstract", abstractText, Field.Store.YES));
                    document.add(new TextField("text", text, Field.Store.YES));

                    // Add the document to the index writer
                    indexWriter.addDocument(document);
                }
            }

            // Commit the changes and close the index writer
            indexWriter.commit();
            indexWriter.close();

            System.out.println("Index created successfully.");
        }
    }

}
