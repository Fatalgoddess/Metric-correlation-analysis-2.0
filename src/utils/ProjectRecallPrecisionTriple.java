package utils;

public class ProjectRecallPrecisionTriple<projectName, recall, precision> {
	
	/**
	 * @author Antoniya Ivanova A class to store a triple of project, recall,
	 *         precision
	 *
	 */

	private String projectName;
	private Float recall;
	private Float precision;

	public ProjectRecallPrecisionTriple(String projectName, Float recall, Float precision) {
		this.projectName = projectName;
		this.recall = recall;
		this.precision = precision;
	}

	public void setRecall(Float recall) {
		this.recall = recall;
	}

	public void setPrecision(Float precision) {
		this.precision = precision;
	}

	public String getProjectName() {
		return projectName;
	}

	public Float getRecall() {
		return recall;
	}

	public Float getPrecision() {
		return precision;
	}

}
