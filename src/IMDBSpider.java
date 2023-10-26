import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.google.common.net.PercentEscaper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class IMDBSpider {

	public IMDBSpider() {
	}
	final static String BASE_URL_1 = "https://www.imdb.com/find?q=";
	final static String BASE_URL_2 = "&s=tt&ttype=ft";
	/**
	 * For each title in file movieListJSON:
	 *
	 * <pre>
	 * You should:
	 * - First, read a list of 500 movie titles from the JSON file in 'movieListJSON'.
	 *
	 * - Secondly, for each movie title, perform a web search on IMDB and retrieve
	 * movie’s URL: https://www.imdb.com/find?q=<MOVIE>&s=tt&ttype=ft
	 *
	 * - Thirdly, for each movie, extract metadata (actors, budget, description)
	 * from movie’s URL and store to a JSON file in directory 'outputDir':
	 *    https://www.imdb.com/title/tt0499549/?ref_=fn_al_tt_1 for Avatar - store
	 * </pre>
	 *
	 * @param movieListJSON JSON file containing movie titles
	 * @param outputDir     output directory for JSON files with metadata of movies.
	 * @throws IOException
	 */
	public static void fetchIMDBMovies(String movieListJSON, String outputDir) throws IOException {
		/*
		 * Read movie titles from JSON_File and store in "title-Class"
		 */
		
		String escapeCharsList = "[?\\:!.,/]";
		PercentEscaper escaper = new PercentEscaper("", false);

		// create Gson instance
		final Gson gson = new GsonBuilder().create();

		List<Titles> titles = null;
		
		// create a reader
		try (Reader reader = Files.newBufferedReader(Paths.get(movieListJSON))) {

			// convert JSON string to User object
			Type listType = new TypeToken<ArrayList<Titles>>() {
			}.getType();
			titles = gson.fromJson(reader, listType);
		}

		titles.parallelStream().forEach(titleLink -> {
			Movie movie = new Movie();

			/*
			 * Get the right HTML doc for each Movie
			 */

			Document doc;
			try {
				String url = BASE_URL_1 + escaper.escape(titleLink.movie_name) + BASE_URL_2;
				doc = Jsoup.connect(url)
						.userAgent("Mozilla/5.0 (Windows NT 10.0; WOW64; rv:77.0) Gecko/20100101 Firefox/77.0")
						.header("Accept-Language", "en")
						.timeout(20000)
						.get();
			} catch (IOException e) {
				System.err.println("ERROR: We couldn't obtain the film list for " + titleLink.movie_name);
				e.printStackTrace();
				return;
			}
			Elements link = doc
					.select("#main > div > div.findSection > table > tbody > tr:nth-child(1) > td.result_text > a");
			String url1 = "https://www.imdb.com/" + link.attr("href");
			try {
				doc = Jsoup.connect(url1).timeout(20000).get();
			} catch (IOException e) {
				System.err.println("ERROR: We couldn't obtain the film " + titleLink.movie_name);
				e.printStackTrace();
			}

			/*
			 * Set Movie attributes
			 */

			// get countries:
			List<String> countryList = doc.select("h4:contains(Country) ~ a").eachText();

			// get genreList:
			List<String> genreList = doc.select("h4:contains(Genre) ~ a").eachText();

			// get description:
			String description = doc.select("#titleStoryLine div > p > span").text();

			// get directors:
			List<String> directors = doc.select("h4:contains(Director) ~ a").eachText();

			// get cast- and characterList:
			List<String> pairsList = doc.select("h2 + table tr").eachText();
			List<String> castList = new ArrayList<>();
			List<String> characterList = new ArrayList<>();
			String[] split;
			String character = "";
			for (String pair : pairsList) {
				if (pair.contains("...")) {
					split = pair.split("\\.\\.\\.");
					castList.add(split[0].trim());
					character = split[1].replaceAll("[(].*[)]", "").trim();
					if (!character.equals(""))
						characterList.add(character); 
				}
			}

			// get release year:
			String year = doc.select("#titleYear a").text();

			// get budget:
			Elements element = doc.select("h4:contains(Budget)");
			String budget = "";
			if (!element.isEmpty()) {
				budget = element.parents().first().text().replace("Budget: ", "");
			}
			budget = checkDollar(budget);

			// get gross:
			String gross = "";
			element = doc.select("h4:contains(Cumulative Worldwide Gross)");
			if (!element.isEmpty()) {
				gross = element.parents().first().text().replace("Cumulative Worldwide Gross: ", "");
			} else {
				element = doc.select("h4:contains(Gross USA)");
				if (!element.isEmpty()) {
					gross = element.parents().first().text().replace("Gross USA: ", "");
				}
			}
			gross = checkDollar(gross);

			// get duration:
			element = doc.select("h4:contains(Runtime) + time");
			String duration = "";
			if (!element.isEmpty()) {
				Duration durationParser = Duration.parse("PT" + element.text().split(" ")[0] + "M");
				duration = durationParser.toHours() + "h " + (durationParser.toMinutesPart()) + "min";
			}

			// get rating value:
			String ratingValue = doc.select("[itemprop='ratingValue']").text();

			// get rating count:
			String ratingCount = doc.select("[itemprop='ratingCount']").text();

			// Build movie class:
			movie.setTitle(titleLink.movie_name);
			movie.setUrl(url1);
			movie.setCountryList(countryList);
			movie.setGenreList(genreList);
			movie.setDescription(description);
			movie.setDirectorList(directors);
			movie.setCastList(castList);
			movie.setCharacterList(characterList);
			movie.setYear(year);
			movie.setBudget(budget);
			movie.setGross(gross);
			movie.setDuration(duration);
			movie.setRatingValue(ratingValue);
			movie.setRatingCount(ratingCount);

			/*
			 * Make .json File for each Movie
			 */
	
			String fileName = titleLink.movie_name.replaceAll(escapeCharsList, "");
			Gson gson_ = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
			try (FileWriter fw = new FileWriter(outputDir + "\\" + fileName + ".json", true)) {
				List<Movie> movieAsArray = new ArrayList<Movie>();
				movieAsArray.add(movie);
				gson_.toJson(movieAsArray, fw);
				fw.close();
			} catch (IOException e) {
				System.err.println("ERROR: We couldn't open/close the file");
				e.printStackTrace();
			} catch (JsonIOException e) {
				System.err.println("ERROR: We couldn't write the json file");
				e.printStackTrace();
			}
		});
	}

	private static String checkDollar(String str) {
		if (!str.startsWith("$")) {
			str = "";
		}
		return str;
	}

	/**
	 * Helper method to remove html and formatting from text.
	 *
	 * @param text The text to be cleaned
	 * @return clean text
	 */
	protected static String cleanText(String text) { // Used?? for what??
		return text.replaceAll("\\<.*?>", "").replace("&nbsp;", " ").replace("\n", " ").replaceAll("\\s+", " ").trim();
	}

	public static void main(String[] argv) throws IOException {
		String moviesPath = "./data/movies.json";
		String outputDir = "./data/crawledMovies";

		if (argv.length == 2) {
			moviesPath = argv[0];
			outputDir = argv[1];
		} else if (argv.length != 0) {
			System.out.println("Call with: IMDBSpider.jar <moviesPath> <outputDir>");
			System.exit(0);
		}

		IMDBSpider.fetchIMDBMovies(moviesPath, outputDir);
	}

}

class Titles {
	String movie_name;

	Titles(String mn) {
		movie_name = mn;
	}
}
