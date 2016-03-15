
/*
* 	@Author:  Danqi Chen
* 	@Email:  danqi@cs.stanford.edu
*	@Created:  2014-08-25
* 	@Last Modified:  2014-10-05
*/

package ilcc.ccgparser.nnparser;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;
import java.util.*;


/**
 *
 *  Some utility functions
 *
 *  @author Danqi Chen
 *  @author Jon Gauthier
 */

class Util {

  private Util() {} // static methods

  private static Random random;

  // return strings sorted by frequency, and filter out those with freq. less than cutOff.

  /**
   * Build a dictionary of words collected from a corpus.
   * <p>
   * Filters out words with a frequency below the given {@code cutOff}.
   *
   * @return Words sorted by decreasing frequency, filtered to remove
   *         any words with a frequency below {@code cutOff}
   */
  public static List<String> generateDict(List<String> str, int cutOff)
  {
    Counter<String> freq = new IntCounter<>();
    for (String aStr : str)
      freq.incrementCount(aStr);

    List<String> keys = Counters.toSortedList(freq, false);
    List<String> dict = new ArrayList<>();
    for (String word : keys) {
      if (freq.getCount(word) >= cutOff)
        dict.add(word);
    }
    return dict;
  }

  public static List<String> generateDict(List<String> str)
  {
    return generateDict(str, 1);
  }

  /**
   * @return Shared random generator used in this package
   */
  static Random getRandom() {
    if (random != null)
      return random;
    else
      return getRandom(System.currentTimeMillis());
  }

  /**
   * Set up shared random generator to use the given seed.
   *
   * @return Shared random generator object
   */
  static Random getRandom(long seed) {
    random = new Random(seed);
    System.err.printf("Random generator initialized with seed %d%n", seed);

    return random;
  }

  public static <T> List<T> getRandomSubList(List<T> input, int subsetSize)
  {
    int inputSize = input.size();
    if (subsetSize > inputSize)
      subsetSize = inputSize;

    Random random = getRandom();
    for (int i = 0; i < subsetSize; i++)
    {
      int indexToSwap = i + random.nextInt(inputSize - i);
      T temp = input.get(i);
      input.set(i, input.get(indexToSwap));
      input.set(indexToSwap, temp);
    }
    return input.subList(0, subsetSize);
  }
}
