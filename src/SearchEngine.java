import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.StringTokenizer;

public class SearchEngine 
{
	public static void main(String[] args) throws IOException, ParseException 
	{
		System.out.println("Enter absolute file path:");
		String FILE_PATH = System.console().readLine();
		
		System.out.println("Enter query :");
		String querystr = System.console().readLine();
		
		/*
		 * Note that for proximity searches, exact matches (foo bar) are proximity zero
		 * and word transpositions (bar foo) are proximity 1
		 */
		if(querystr.indexOf("WITHIN") != -1)
			querystr = proximityQueryFormat(querystr);
		
		
		// Specify the analyzer for tokenizing text.
		// The same analyzer should be used for indexing and searching
		StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_40);

		// Create the index
		Directory index = new RAMDirectory();

		IndexWriterConfig config = new IndexWriterConfig(Version.LUCENE_40, analyzer);
		IndexWriter w = new IndexWriter(index, config);

		// Read the docs
		File file = new File(FILE_PATH);
		BufferedReader br = new BufferedReader(new FileReader(file));

		for(String line; (line = br.readLine())!=null;)
		{
			String s[] = line.split(" ", 2);
			addDoc(w, s[1], s[0]);
		}
		w.close();

		// The "title" arg specifies the default field to use
		// when no field is explicitly specified in the query.
		Query q = new QueryParser(Version.LUCENE_40, "title", analyzer).parse(querystr);

		// search
		int hitsPerPage = 10;
		IndexReader reader = DirectoryReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);

		//TODO: CHANGED THE SIMILARITY
		searcher.setSimilarity(new BM25Similarity());

		TopScoreDocCollector collector = TopScoreDocCollector.create(hitsPerPage, true);
		searcher.search(q, collector);
		ScoreDoc[] hits = collector.topDocs().scoreDocs;

		// display results
		System.out.println("Found " + hits.length + " hits.");
		for(int i=0;i<hits.length;++i) 
		{
			int docId = hits[i].doc;
			float score = hits[i].score;
			Document d = searcher.doc(docId);
			System.out.println((i + 1) + ". " + d.get("isbn") + "\t" + " Score: " + score);
		}

		// reader can only be closed when there
		// is no need to access the documents any more.
		reader.close();
	}

	private static String proximityQueryFormat(String querystr) 
	{
		StringTokenizer st = new StringTokenizer(querystr);
		
		String proxQuery = "";		
		proxQuery += "\"";					//delim
		proxQuery += st.nextToken();				//first word
		st.nextToken();						//skip over middle word
		proxQuery += " " + st.nextToken();			//second word
		proxQuery += "\"";					//delim
		st.nextToken();						//skip over "WITHIN"
		proxQuery += "~" + st.nextToken();		
		
		return proxQuery;
	}

	private static void addDoc(IndexWriter w, String title, String isbn) throws IOException 
	{
		Document doc = new Document();
		doc.add(new TextField("title", title, Field.Store.YES));

		// Use a string field for isbn because we don't want it tokenized
		doc.add(new StringField("isbn", isbn, Field.Store.YES));
		w.addDocument(doc);
	}
}
