package projectSelection;

import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.FuzzyQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.adjacency.AdjacencyMatrix.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.*;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.aggregations.metrics.avg.AvgAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import utils.LevensteinDistanceImplementation;
import vulnerabilitySearch.VulnerabilityDataToElasticImporter;

public class projectSelector {

	private RestHighLevelClient elasticClient;
	private String OAuthToken = "183c10a9725ad6c00195df59c201040e1b3d1d07";
	protected static String repositoryDatabaseName = "repositories_database_no_minimum_stars";

	class RepositoryResult {

		private String vendor;
		private String product;
		private int stars;

		public RepositoryResult(String vendor, String product, int stars) {
			this.vendor = vendor;
			this.product = product;
			this.stars = stars;
		}

		public String getVendor() {
			return vendor;
		}

		public String getProduct() {
			return product;
		}

		public int getStars() {
			return stars;
		}

		@Override
		public int hashCode() {
			return vendor.hashCode() ^ product.hashCode() ^ ((int) stars);
			// return Objects.hash(vendor, product, stars);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null)
				return false;
			if (!(obj instanceof RepositoryResult))
				return false;
			if (obj == this)
				return true;
			return this.getVendor().equals(((RepositoryResult) obj).getVendor())
					&& this.getProduct().equals(((RepositoryResult) obj).getProduct())
					&& this.getStars() == ((RepositoryResult) obj).getStars();
		}

		@Override
		public String toString() {
			return this.vendor + " " + this.product + " " + this.stars;
		}
	}

	// @Test
	public void projectSelector() {
		searchForJavaRepositoryNames();
	}

	private HashSet<RepositoryResult> searchForJavaRepositoryNames() {
		HashSet<RepositoryResult> respositoryResults = new HashSet<RepositoryResult>();
		String url;

		try {
			CloseableHttpClient httpClient = HttpClientBuilder.create().build();

			// (requests x 100)
			for (int i = 1; i <= 100; i++) {
				url = "https://api.github.com/search/repositories?q=language:java&sort=stars&order=desc"
						+ "&access_token=" + OAuthToken + "&page=" + i + "&per_page=89";

				HttpGet request = new HttpGet(url);
				request.addHeader("content-type", "application/json");
				HttpResponse result = httpClient.execute(request);

				String json = EntityUtils.toString(result.getEntity(), "UTF-8");

				JsonElement jelement = new JsonParser().parse(json);
				JsonObject jobject = jelement.getAsJsonObject();
				JsonArray jarray = jobject.getAsJsonArray("items");

				for (int j = 0; j < jarray.size(); j++) {

					JsonObject jo = (JsonObject) jarray.get(j);
					String fullName = jo.get("full_name").toString().replace("\"", "");
					int stars = Integer.parseInt(jo.get("stargazers_count").toString());

					// if ((stars >= 100) && isGradleRepository(fullName)) {
					if (isGradleRepository(fullName)) {
						System.out.println("MATCH : " + fullName);

						String product = jo.get("name").toString().replace("\"", "");

						JsonObject owner = (JsonObject) jo.get("owner");
						String vendor = owner.get("login").toString().replace("\"", "");

						respositoryResults.add(new RepositoryResult(vendor, product, stars));
					}

					System.out.println(j);

					if ((j == 28) || (j == 58) || (j == 88)) {
						TimeUnit.MINUTES.sleep(1);
					}

				}
				addDocumentsToElastic(respositoryResults);
				respositoryResults.clear();
			}
			httpClient.close();
		} catch (Exception e) {
			System.out.println(e.getStackTrace());
		}

		return respositoryResults;
	}

	private boolean isGradleRepository(String repositoryName) {
		boolean result = false;
		String gradleKeyword = "build.gradle";
		String gradleSearchUrl;

		try {
			CloseableHttpClient httpClient = HttpClientBuilder.create().build();

			gradleSearchUrl = "https://api.github.com/search/code?q=repo:" + repositoryName + "+filename:"
					+ gradleKeyword + "&access_token=" + OAuthToken;
			HttpGet requestGradleRepository = new HttpGet(gradleSearchUrl);
			requestGradleRepository.addHeader("content-type", "application/json");

			HttpResponse resultResponse = httpClient.execute(requestGradleRepository);
			String json = EntityUtils.toString(resultResponse.getEntity(), "UTF-8");

			JsonElement jelement = new JsonParser().parse(json);

			try {
				result = jelement.getAsJsonObject().get("total_count").getAsInt() != 0 ? true : false;

			} catch (Exception e) {
				System.out.println(e.toString());
			}

			httpClient.close();

		} catch (Exception e) {
			System.out.println(e.getStackTrace());
		}

		return result;
	}

	private void addDocumentsToElastic(HashSet<RepositoryResult> repositoriesSet) {
		ArrayList<HashMap<String, Object>> repositories = new ArrayList<HashMap<String, Object>>();

		for (RepositoryResult repositoryResult : repositoriesSet) {
			HashMap<String, Object> repository = new HashMap<String, Object>();
			repository.put("Vendor", repositoryResult.getVendor());
			repository.put("Product", repositoryResult.getProduct());
			repository.put("Stars", repositoryResult.getStars());
			repositories.add(repository);
		}

		elasticClient = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http")));

		IndexRequest indexRequest = null;

		for (Iterator<HashMap<String, Object>> iterator = (repositories).iterator(); iterator.hasNext();) {
			indexRequest = new IndexRequest(repositoryDatabaseName, "doc").source(iterator.next());

			try {
				elasticClient.index(indexRequest);
			} catch (Exception e) {
				System.err.println("Could not index document " + iterator.toString());
				e.printStackTrace();
			}
		}

		try {
			elasticClient.close();
		} catch (Exception e) {
			System.err.println("Could not close RestHighLevelClient!");
			e.printStackTrace();
		}

		System.out.println("Inserting " + repositories.size() + " documents into index.");
	}

	@Test
	// private int getAverageNumberOfStars(){
	public void getAverageNumberOfStars() {
		elasticClient = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http")));

		SearchRequest searchRequest = new SearchRequest(repositoryDatabaseName);
		AvgAggregationBuilder avgAB = AggregationBuilders.avg("avg_stars").field("Stars");

		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.query(QueryBuilders.matchAllQuery()).aggregation(avgAB);
		searchRequest.source(searchSourceBuilder);

		try {
			SearchResponse searchResponse = elasticClient.search(searchRequest);
			Aggregations aggregations = searchResponse.getAggregations();
			
			Avg avgStars = aggregations.get("avg_stars");
	
			double avg = avgStars.getValue();

			System.out.println(avg);
		} catch (Exception e) {
			// TODO: handle exception
		}
	}
}
