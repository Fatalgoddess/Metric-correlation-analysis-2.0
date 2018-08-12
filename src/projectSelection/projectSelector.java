package projectSelection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class projectSelector {

	private String OAuthToken = "183c10a9725ad6c00195df59c201040e1b3d1d07";

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

	@Test
	// private ArrayList<String> searchForJavaRepositoryNames() {
	public void garbo() {
		HashSet<RepositoryResult> respositoryResults = new HashSet<RepositoryResult>();
		String url;

		try {
			CloseableHttpClient httpClient = HttpClientBuilder.create().build();

			// (requests x 100)
			for (int i = 1; i <= 5; i++) {
				url = "https://api.github.com/search/repositories?q=?page=" + i + "language:java&sort=stars&order=desc"
						+ "&access_token=" + OAuthToken + "&per_page=100";

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

					if ((stars >= 5) && isGradleRepository(fullName)) {
						System.out.println("MATCH : " + fullName);

						String product = jo.get("name").toString().replace("\"", "");

						JsonObject owner = (JsonObject) jo.get("owner");
						String vendor = owner.get("login").toString().replace("\"", "");

						respositoryResults.add(new RepositoryResult(vendor, product, stars));
					}
					System.out.println(j);
				}
			}
			httpClient.close();
		} catch (IOException e) {
			System.out.println(e.getStackTrace());
		}
		for (RepositoryResult repositoryResult : respositoryResults) {
			System.out.println("FOUND PRODUCT " + repositoryResult.getProduct().toString() + " VENDOR IS "
					+ repositoryResult.getVendor().toString());
		}
		// return respositoryResults;
	}

	private boolean isGradleRepository(String repositoryName) {
		boolean result = false;
		String gradleKeyword = "build.gradle";
		String gradleSearchUrl;

		try {
			CloseableHttpClient httpClient = HttpClientBuilder.create().build();

			gradleSearchUrl = "https://api.github.com/search/code?q=repo:" + repositoryName + "+filename:"
					+ gradleKeyword;
			HttpGet requestGradleRepository = new HttpGet(gradleSearchUrl);
			requestGradleRepository.addHeader("content-type", "application/json");

			HttpResponse resultResponse = httpClient.execute(requestGradleRepository);
			String json = EntityUtils.toString(resultResponse.getEntity(), "UTF-8");

			JsonElement jelement = new JsonParser().parse(json);
			result = jelement.getAsJsonObject().get("total_count").getAsInt() != 0 ? true : false;

			httpClient.close();

		} catch (IOException e) {
			System.out.println(e.getStackTrace());
		}

		return result;
	}

}
