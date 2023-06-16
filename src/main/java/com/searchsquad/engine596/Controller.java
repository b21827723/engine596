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

    private static final int NUM_RESULTS = 10;

    private static final String ROOT_DIR = "/Users/bartu/Desktop/";
    private static final String AAPR_INDEX_DIRECTORY = ROOT_DIR + "AAPR_Index";
    private static final String AAPR_DATA_DIRECTORY = ROOT_DIR + "AAPR_Dataset";
    private static final String CISI_INDEX_DIRECTORY = ROOT_DIR + "CISI_Index";
    private static final String CISI_DATA_DIRECTORY = ROOT_DIR + "CISI_Dataset";
    private static final String CISI_REL_DIRECTORY = ROOT_DIR + "CISI_Dataset/Relations";


    @PostMapping("/search")
    public ResponseEntity<List<SearchResult>> searchDocuments(@RequestBody SearchRequest searchRequest) {
        try {
            String fieldName = searchRequest.getFieldName();
            String queryText = searchRequest.getQueryText();

            Directory indexDirectory = FSDirectory.open(Paths.get(AAPR_INDEX_DIRECTORY));

            IndexReader indexReader = DirectoryReader.open(indexDirectory);
            IndexSearcher indexSearcher = new IndexSearcher(indexReader);

            QueryParser queryParser;
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
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            // Parse the user query text
            Query query = queryParser.parse(QueryParser.escape(queryText));

            // Perform the search
            TopDocs topDocs = indexSearcher.search(query, NUM_RESULTS);

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

                searchResults.add(result);
            }

            return new ResponseEntity<>(searchResults, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    @GetMapping("/index")
    public void AAPR_index() throws IOException {
        createIndex(AAPR_INDEX_DIRECTORY, AAPR_DATA_DIRECTORY);
    }

    // The function below is (almost) a duplicate of searchDocuments() because i am not sure how to call it with another function.. -----------------------------------------------------------------
    // The only difference is String index_dir argument (and http stuff is removed),
    // So this function can be deleted if we can properly use the function above inside /eval
    public List<SearchResult> retrieveDocuments(SearchRequest searchRequest, String index_dir) {
        try {
            String fieldName = searchRequest.getFieldName();
            String queryText = searchRequest.getQueryText();
            Directory indexDirectory = FSDirectory.open(Paths.get(index_dir));

            IndexReader indexReader = DirectoryReader.open(indexDirectory);
            IndexSearcher indexSearcher = new IndexSearcher(indexReader);

            QueryParser queryParser;
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
                // return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
                queryParser = new QueryParser("text", new StandardAnalyzer());
            }

            // Parse the user query text
            Query query = queryParser.parse(QueryParser.escape(queryText));

            // Perform the search
            TopDocs topDocs = indexSearcher.search(query, NUM_RESULTS);

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

                searchResults.add(result);
            }
            return searchResults;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    // ---------------------------------------------------------------------------------------
    @GetMapping("/eval")
    public void evalCISI() throws IOException {

        // Create index for CISI documents
        createIndex(CISI_INDEX_DIRECTORY, CISI_DATA_DIRECTORY);

        // Read Relations JSON to obtain relevant document ids for every query
        File dataDir = new File(CISI_REL_DIRECTORY);
        File[] targetJsonFiles = dataDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

        List<String> query_texts = new ArrayList<String>();
        List<List<String>> doc_id_lists = new ArrayList<List<String>>();
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

                // Iterate over the JSON objects and get relations
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);

                    String query_text = jsonObject.getString("query_text");
                    query_texts.add(query_text);

                    // Every query has relevant documents, given as an array of doc ids
                    JSONArray doc_id_list = jsonObject.getJSONArray("doc_id_list");

                    List<String> doc_ids = new ArrayList<String>();
                    for (int j = 0; j < doc_id_list.length(); j++){
                        Object obj = doc_id_list.get(j);
                        String doc_id = obj.toString();
                        doc_ids.add(doc_id);
                    }
                    doc_id_lists.add(doc_ids);
                }
            }
            System.out.println("CISI Relations are retrieved successfully.");
        }

        // Evaluating the system
        SearchRequest search_request = new SearchRequest();
        search_request.setFieldName("text");
        for (String query_text : query_texts) {
            System.out.println("Query: " + query_text);
            search_request.setQueryText(query_text);

            List<SearchResult> search_result = retrieveDocuments(search_request, CISI_INDEX_DIRECTORY);

            System.out.println("Results:");
            System.out.println("_____________");
            for(SearchResult res : search_result){
                System.out.println(res.getTitle());
            }
        }

        System.out.println("Average R@10: ...");
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
