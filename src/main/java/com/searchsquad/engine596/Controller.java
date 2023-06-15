package com.searchsquad.engine596;

import com.searchsquad.engine596.DTO.SearchRequest;
import com.searchsquad.engine596.DTO.SearchResult;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
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
import java.util.List;




@RestController
public class Controller {

    private static final int NUM_RESULTS = 10;

    private static final String INDEX_DIRECTORY = "/Users/cihadozcan/Desktop/AAPR_Index";

    private static final String DATA_DIRECTORY = "/Users/cihadozcan/Desktop/AAPR_Dataset";

    @PostMapping("/search")
    public ResponseEntity<List<SearchResult>> searchDocuments(@RequestBody SearchRequest searchRequest) {
        try {
            String fieldName = searchRequest.getFieldName();
            String queryText = searchRequest.getQueryText();

            Directory indexDirectory = FSDirectory.open(Paths.get(INDEX_DIRECTORY));

            IndexReader indexReader = DirectoryReader.open(indexDirectory);
            IndexSearcher indexSearcher = new IndexSearcher(indexReader);

            // Create Lucene query parser and specify the field to search
            QueryParser queryParser = new QueryParser(fieldName, new StandardAnalyzer());

            // Create Lucene multi-field query parser and specify fields to search
            //MultiFieldQueryParser queryParser = new MultiFieldQueryParser(new String[]{"title", "abstract", "text"}, new StandardAnalyzer());

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
    public void createIndex() throws IOException {

            // Set up Lucene index directory
            Directory indexDirectory = FSDirectory.open(Paths.get(INDEX_DIRECTORY));

            // Create the analyzer
            Analyzer analyzer = new StandardAnalyzer();

            // Create the index writer configuration
            IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);

            // Create the index writer
            IndexWriter indexWriter = new IndexWriter(indexDirectory, writerConfig);

            File dataDir = new File(DATA_DIRECTORY);
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

                        String title = jsonObject.getString("title");
                        String abstractText = jsonObject.getString("abstract");
                        String text = jsonObject.getString("text");

                        // Create a new Lucene document
                        Document document = new Document();
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
