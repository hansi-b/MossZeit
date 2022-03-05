package moss_zeit;

import java.time.Duration;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hansi_b.moss.Errors;
import org.hansi_b.moss.Sudoku;
import org.hansi_b.moss.draw.AsciiPainter;
import org.hansi_b.moss.explain.Solver;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class App {

	private static final Logger log = LogManager.getLogger();

	private enum Level {
		LEICHT, MITTEL, SCHWER
	}

	private final WebDriver driver;

	App(WebDriver driver) {
		this.driver = driver;
	}

	static App create() {
		WebDriverManager.firefoxdriver().setup();
		var driver = new FirefoxDriver();
		driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500));
		return new App(driver);
	}

	private String findSudokuHtml() throws InterruptedException {

		driver.get("https://sudoku.zeit.de");

		WebElement consentFrame = waitForXpath("//*[@title='SP Consent Message']");
		driver.switchTo().frame(consentFrame);

		WebElement acceptButton = waitForXpath("//*[@title='EINVERSTANDEN']");
		doRepeat(() -> new Actions(driver).moveToElement(acceptButton).perform());
		acceptButton.click();
		driver.switchTo().defaultContent();

		Level l = Level.SCHWER;
		log.info("Using Sudoku of level {} ...", l);
		WebElement levelButton = waitForXpath(String.format("//button[text()='%s']", l));
		new Actions(driver).moveToElement(levelButton).perform();
		levelButton.click();

		String sudokuGridHtml = driver.findElement(By.className("sodokoGrid")).getAttribute("innerHTML");
		driver.quit();
		return sudokuGridHtml;
	}

	private WebElement waitForXpath(String xpath) {
		WebElement result = new WebDriverWait(driver, Duration.ofSeconds(3)).until(d -> d.findElement(By.xpath(xpath)));
		if (result == null)
			throw Errors.illegalState("Could not get hold of element for %s", xpath);
		return result;
	}

	private static void doRepeat(Runnable todo) throws InterruptedException {
		int i = 10;
		while (i > 0) {
			i -= 1;
			try {
				todo.run();
				break;
			} catch (WebDriverException ex) {
				log.debug("Retry ({} to go) on exception: {}", i, ex);
				Thread.sleep(200);
			}
		}
	}

	private static Sudoku parseSudoku(String sudokuGridHtml) {
		Elements rows = Jsoup.parse(sudokuGridHtml).getElementsByClass("sodokoRow");
		if (rows.size() != 9)
			throw Errors.illegalState("Excepted 9 rows, found %d", rows.size());

		List<List<Integer>> values = rows.stream().map(r -> {
			Elements cells = r.children();
			if (cells.size() != 9) {
				throw Errors.illegalState("Expected 9 cells, got %d in %s", cells.size(), r);
			}
			return cells.stream().map(c -> {
				Elements filled = c.getElementsByClass("fixed-value");
				if (filled.size() > 1) {
					throw Errors.illegalState("More than one filled element in %s", filled);
				}
				return filled.isEmpty() ? 0 : Integer.parseInt(filled.get(0).text());
			}).toList();
		}).toList();
		return Sudoku.filled(values);
	}

	public static void main(String[] args) throws InterruptedException {

		String sudokuGridHtml = create().findSudokuHtml();
		Sudoku sudoku = parseSudoku(sudokuGridHtml);
		log.info("Found Sudoku:\n{}", () -> new AsciiPainter().draw(sudoku));

		Sudoku solved = new Solver().solve(sudoku);
		var resultMsg = String.format("%s Sudoku:%n", solved.isSolved() ? "Solved" : "Could not completely solve");
		log.info("{}{}", () -> resultMsg, () -> new AsciiPainter().draw(solved));
	}
}
