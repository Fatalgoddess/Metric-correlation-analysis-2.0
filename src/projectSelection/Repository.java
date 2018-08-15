package projectSelection;

/**
 * @author Antoniya Ivanova Represents a GitHub repository.
 *
 */

public class Repository {

	private String vendor;
	private String product;
	private int stars;

	/**
	 * Build a new GitHub repository representation with
	 * @param vendor - the Vendor
	 * @param product - the Repository/Product name
	 * @param stars - the number of stars it has
	 */
	public Repository(String vendor, String product, int stars) {
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
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (!(obj instanceof Repository))
			return false;
		if (obj == this)
			return true;
		return this.getVendor().equals(((Repository) obj).getVendor())
				&& this.getProduct().equals(((Repository) obj).getProduct())
				&& this.getStars() == ((Repository) obj).getStars();
	}

	@Override
	public String toString() {
		return this.vendor + " " + this.product + " " + this.stars;
	}

}
