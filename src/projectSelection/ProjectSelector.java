package projectSelection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.aggregations.metrics.avg.AvgAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import vulnerabilitySearch.VulnerabilityDataQueryHandler;

/**
 * @author Antoniya Ivanova Searches via the GitHub API for usable projects for
 *         the metric correlation analysis tool.
 *
 */

public class ProjectSelector {

	private RestHighLevelClient elasticClient;
	private String OAuthToken = "183c10a9725ad6c00195df59c201040e1b3d1d07";
	protected static String repositoryDatabaseName = "repositories_database_no_minimum_stars";

	public void projectSelector() {
	}

	/**
	 * Searches for Java + Gradle repositories on GitHub.
	 * 
	 * @return a HashSet of {@link Repository} results, which are Java and Gradle
	 *         projects.
	 */

	private HashSet<Repository> searchForJavaRepositoryNames() {
		HashSet<Repository> respositoryResults = new HashSet<Repository>();
		String url;

		try {
			CloseableHttpClient httpClient = HttpClientBuilder.create().build();

			// Requests per page x 100
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

						respositoryResults.add(new Repository(vendor, product, stars));
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

	/**
	 * Tests if a repository is a Gradle repository. Checks for file "build.gradle"
	 * in its contents.
	 * 
	 * @param repositoryName
	 *            the name of the repository to be tested
	 * @return true if it is a Gradle repository, false otherwise.
	 */
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
			System.err.println("Could not check if repository is a Gradle repository.");
			System.out.println(e.getStackTrace());
		}

		return result;
	}

	/**
	 * Adds the found repositories to an Elasticsearch DB.
	 * 
	 * @param repositoriesSet
	 *            repositories to be added to the Elasticsearch database (index).
	 */
	private void addDocumentsToElastic(HashSet<Repository> repositoriesSet) {
		ArrayList<HashMap<String, Object>> repositories = new ArrayList<HashMap<String, Object>>();

		// Build the repository document
		for (Repository repositoryResult : repositoriesSet) {
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
				elasticClient.close();
			} catch (Exception e) {
				System.err.println("Could not index document " + iterator.toString());
				e.printStackTrace();
			}
		}
		System.out.println("Inserting " + repositories.size() + " documents into index.");
	}

	/**
	 * Gets the average number of stars in the Elasticsearch repository database.
	 */
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
			elasticClient.close();
		} catch (Exception e) {
			System.err.println("Could not get average number of stars.");
			e.printStackTrace();
		}
	}

	/**
	 * Gets the average number of discovered vulnerabilities for the repositories in
	 * the Elasticsearch repository database.
	 */
	 public double getAverageNumberOfDiscoveredVulnerabilities(){
		elasticClient = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http")));
		VulnerabilityDataQueryHandler vulnerabilityDataQueryHandler = new VulnerabilityDataQueryHandler();
		long totalNumberOfProjects = 0;
		long totalNumberOfVulnerabilites = 0;
		double averageVulnerabilitiesPerProject = 0;

		SearchRequest searchRequest = new SearchRequest(repositoryDatabaseName);
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.size(1000);
		searchSourceBuilder.query(QueryBuilders.matchAllQuery());
		searchRequest.source(searchSourceBuilder);

		try {
			SearchResponse searchResponse = elasticClient.search(searchRequest);
			totalNumberOfProjects = searchResponse.getHits().getTotalHits();

			SearchHits repositoryHits = searchResponse.getHits();
			SearchHit[] searchHits = repositoryHits.getHits();

			for (SearchHit searchHit : searchHits) {
				Map<String, Object> map = searchHit.getSourceAsMap();
				String product = map.get("Product").toString();
				String vendor = map.get("Vendor").toString();
				HashSet<SearchHit> vulnerabilities = vulnerabilityDataQueryHandler.getVulnerabilities(product, vendor,
						"", "TWO");
				totalNumberOfVulnerabilites += vulnerabilityDataQueryHandler
						.getNumberOfVulnerabilities(vulnerabilities);
			}

			averageVulnerabilitiesPerProject = totalNumberOfVulnerabilites / totalNumberOfProjects;

			System.out.println(
					"The average number of discovered vulnerabilities is : " + averageVulnerabilitiesPerProject);
			System.out.println("The total number of discovered vulnerabilities is : " + totalNumberOfVulnerabilites);

			elasticClient.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return averageVulnerabilitiesPerProject;
	}
	
	@Test
	public void getNumberOfProjectsWithAtLeastOneVulnerabilityEntry(){
		elasticClient = new RestHighLevelClient(RestClient.builder(new HttpHost("localhost", 9200, "http")));
		VulnerabilityDataQueryHandler vulnerabilityDataQueryHandler = new VulnerabilityDataQueryHandler();
		float repositoriesWithVulnerabilities = 0;
		float percentageOfRepositoriesWithVulnerabilities = 0;
		float totalNumberOfProjects = 0;

		SearchRequest searchRequest = new SearchRequest(repositoryDatabaseName);
		SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
		searchSourceBuilder.size(1000);
		searchSourceBuilder.query(QueryBuilders.matchAllQuery());
		searchRequest.source(searchSourceBuilder);

		try {
			SearchResponse searchResponse = elasticClient.search(searchRequest);
			totalNumberOfProjects = searchResponse.getHits().getTotalHits();

			SearchHits repositoryHits = searchResponse.getHits();
			SearchHit[] searchHits = repositoryHits.getHits();

			for (SearchHit searchHit : searchHits) {
				Map<String, Object> map = searchHit.getSourceAsMap();
				String product = map.get("Product").toString();
				String vendor = map.get("Vendor").toString();
				HashSet<SearchHit> vulnerabilities = vulnerabilityDataQueryHandler.getVulnerabilities(product, vendor,
						"", "TWO");
				if(!vulnerabilities.isEmpty())
					repositoriesWithVulnerabilities++;
			}

			percentageOfRepositoriesWithVulnerabilities = (repositoriesWithVulnerabilities / totalNumberOfProjects) * 100;

			System.out.println(
					"The percentage of repositories with a vulnerability is : " + percentageOfRepositoriesWithVulnerabilities + "%");
			System.out.println("Repositories with at least one vulnerability : " + repositoriesWithVulnerabilities);

			elasticClient.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
