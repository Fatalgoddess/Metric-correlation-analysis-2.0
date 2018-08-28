package utils;

import org.junit.Test;

/**
 * @author Antoniya Ivanova Calculates the Levenstein distance.
 *
 */

public class LevensteinDistanceCalculator {

	public static boolean fuzzyContains(String container, String containee, double threshold) {
		int lengthcontainer = container.length();
		int lengthcontainee = containee.length();
		
		// compare all substrings with a longer threshold.
		if (lengthcontainer < lengthcontainee)
			return false;

		int maxlength = (int) ((1.0 + threshold) * lengthcontainee);
		if (maxlength > lengthcontainer)
			maxlength = lengthcontainer;

		int shift = lengthcontainer - maxlength;
		for (int i = 0; i < shift; i++) {
			if (fuzzyContainsEnd2End(container.substring(i, i + maxlength - 1), containee, threshold))
				return true;
		}

		return false;
	}

	private static int minimum(int a, int b, int c) {
		return Math.min(Math.min(a, b), c);
	}

	// Compare Strings without substring before, the string can be scattered
	// anywhere in the container.
	public static boolean fuzzyContainsEnd2End(String container, String containee, double threshold) {
		int lengthcontainer = container.length();
		int lengthcontainee = containee.length();
		
		// A smaller String cannot contain a bigger String
		if (lengthcontainer < lengthcontainee)
			return false;
		//
		double lendiff = lengthcontainer - lengthcontainee;
		double dist = computeLevenshteinDistance(container, containee);
		
		if (((dist - lendiff) / lengthcontainee) < threshold)
			return true;

		return false;
	}

	public static int computeLevenshteinDistance(CharSequence lhs, CharSequence rhs) {
		int[][] distance = new int[lhs.length() + 1][rhs.length() + 1];

		for (int i = 0; i <= lhs.length(); i++)
			distance[i][0] = i;
		for (int j = 1; j <= rhs.length(); j++)
			distance[0][j] = j;

		for (int i = 1; i <= lhs.length(); i++) {

			for (int j = 1; j <= rhs.length(); j++) {
				distance[i][j] = minimum(distance[i - 1][j] + 1, distance[i][j - 1] + 1,
						distance[i - 1][j - 1] + ((lhs.charAt(i - 1) == rhs.charAt(j - 1)) ? 0 : 1));
				// System.out.print(distance[i][j]+"\t" ) ;
			}
			// System.out.println();
		}
		return distance[lhs.length()][rhs.length()];
	}
	
	@Test
	public void test() {
		System.out.println(fuzzyContains("brilliancectbigborefirmware", "frogcms", 0.15));
	}
}
