package com.searchsquad.engine596;

import com.searchsquad.engine596.DTO.SearchRequest;
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
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.similarities.BM25Similarity;

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

    private static final int NUM_RESULTS = 10;

    private static final String ROOT_DIR = "/Users/bartu/Desktop/";
    private static final String AAPR_INDEX_DIRECTORY = ROOT_DIR + "AAPR_Index";
    private static final String AAPR_DATA_DIRECTORY = ROOT_DIR + "AAPR_Dataset";
    private static final String CISI_INDEX_DIRECTORY = ROOT_DIR + "CISI_Index";
    private static final String CISI_DATA_DIRECTORY = ROOT_DIR + "CISI_Dataset";
    private static final String CISI_REL_DIRECTORY = ROOT_DIR + "CISI_Dataset/Relations";


    @PostMapping("/search")
    public ResponseEntity<List<SearchResult>> searchDocuments(@RequestBody SearchRequest searchRequest, @RequestParam String dataset) {
        try {
            String fieldName = searchRequest.getFieldName();
            String queryText = searchRequest.getQueryText();

            List<SearchResult> searchResults = getSearchResults(fieldName, queryText, dataset, NUM_RESULTS);

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
        IndexSearcher indexSearcher = new IndexSearcher(indexReader);

        //Similarity similarity = new BM25Similarity(); //ClassicSimilarity();
        //indexSearcher.setSimilarity(similarity);

        return indexSearcher;
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

        double P_at_1 = 0.0;
        double P_at_5 = 0.0;
        double P_at_10 = 0.0;

        double R_at_1 = 0.0;
        double R_at_5 = 0.0;
        double R_at_10 = 0.0;

        double num_samples_at1 = 0;
        double num_samples_at5 = 0;
        double num_samples_at10 = 0;

        double precision_tot = 0.0;
        double recall_tot = 0.0;
        // Evaluating the system
        for (int i = 0; i < query_texts.size(); i++) {
            String query_text = query_texts.get(i);
            List<String> doc_id_list = doc_id_lists.get(i);

            List<SearchResult> search_result = getSearchResults("text", query_text, "CISI", NUM_RESULTS);

            int matchingCount = 0;
            int matchingCount_at_5 = 0;
            int matchingCount_at_1 = 0;
            double num_relevant = doc_id_list.size();

            int result_idx = 0;
            for (SearchResult result : search_result) {
                result_idx++;
                // Debug:
                // System.out.println(result.getTitle());
                if (doc_id_list.contains(result.getId())) {
                    matchingCount++;

                    if(result_idx == 1) {
                        matchingCount_at_1++;
                        matchingCount_at_5++;
                    } else if(result_idx <= 5){
                        matchingCount_at_5++;
                    }
                }
            }


            int nonMatchingCount = search_result.size() - matchingCount;
            double precision = (double) matchingCount / (double) search_result.size();

            precision_tot += precision;

            if(matchingCount_at_5 > 5){
                throw new RuntimeException("Something is wrong with matching count @5.");
            }
            if(matchingCount_at_1 > 1){
                throw new RuntimeException("Something is wrong with matching count @1.");
            }

            R_at_10 = matchingCount / num_relevant;
            R_at_5 = matchingCount_at_5 / num_relevant;
            R_at_1 = matchingCount_at_1 / num_relevant;

            if(num_relevant >= 10){
                P_at_10 += matchingCount / 10.0;
                num_samples_at10 += 1;
                P_at_5 += matchingCount_at_5 / 5.0;
                num_samples_at5 += 1;
                P_at_1 += (double) matchingCount_at_1;
                num_samples_at1 += 1;
            } else if(num_relevant >= 5){
                P_at_5 += matchingCount_at_5 / 5.0;
                num_samples_at5 += 1;
                P_at_1 += (double) matchingCount_at_1;
                num_samples_at1 += 1;
            } else {
                P_at_1 += (double) matchingCount_at_1;
                num_samples_at1 += 1;
            }

            // Average Recall ---------
            List<SearchResult> search_result_at_rel = getSearchResults("text", query_text, "CISI", (int) num_relevant);
            int matchingCount_recall = 0;

            for (SearchResult result : search_result) {
                if (doc_id_list.contains(result.getId())) {
                    matchingCount_recall++;
                }
            }
            double recall = (double) matchingCount_recall / num_relevant;
            recall_tot += recall;

            // ------------------------

            System.out.println("Query Text: " + query_text);
            System.out.println("Total Relevant Docs: " + num_relevant);
            System.out.println("Matching Results: " + matchingCount);
            System.out.println("Non-Matching Results: " + nonMatchingCount);
            System.out.println("Precision: " + precision);
            System.out.println("Recall: " + recall);
            System.out.println("------------------------------------");
            System.out.println("------------------------------------");
        }

        System.out.println("Average R@10: " + R_at_10 / query_texts.size() );
        System.out.println("Average R@5: " + R_at_5 / query_texts.size()  );
        System.out.println("Average R@1: " + R_at_1 / query_texts.size()  );

        System.out.println("Average P@10: " + P_at_10 / num_samples_at10 + " over " + num_samples_at10 + " samples.");
        System.out.println("Average P@5: " + P_at_5 / num_samples_at5 + " over " + num_samples_at5 + " samples.");
        System.out.println("Average P@1: " + P_at_1 / num_samples_at1 + " over " + num_samples_at1 + " samples.");
        System.out.println("Average Precision: " + precision_tot/query_texts.size() + " over " + query_texts.size() + " samples.");
        System.out.println("Average Recall: " + recall_tot/query_texts.size() + " over " + query_texts.size() + " samples.");
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
