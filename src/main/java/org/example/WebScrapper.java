package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.NoSuchElementException;

public class WebScrapper {

    public static void main(String[] args) {
        String baseUrl = "https://akleg.gov/senate.php";
        List<Map<String, String>> senatorsData = new ArrayList<>();

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-gpu", "--no-sandbox", "--window-size=1400,900");

        // use visible mode (headless = false)
        options.addArguments("--headless=new");
        WebDriver driver = new ChromeDriver(options);

        try {
            System.out.println("🌐 Loading: " + baseUrl);
            driver.get(baseUrl);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(40));
            // wait for senator cards to appear
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".member, .col-md-3.member, .member-info")));

            List<WebElement> members = driver.findElements(By.cssSelector(".member, .col-md-3.member, .member-info"));
            System.out.println("📊 Found " + members.size() + " senator entries");

            if (members.isEmpty()) {
                System.out.println("⚠️ No members found — page may still be loading. Trying again after 10s...");
                Thread.sleep(10000);
                members = driver.findElements(By.cssSelector(".member, .col-md-3.member, .member-info"));
            }

            for (WebElement member : members) {
                try {
                    Map<String, String> senator = new LinkedHashMap<>();

                    String name = safeText(member, By.cssSelector(".member-name, h3, h4"));
                    String party = safeText(member, By.cssSelector(".member-party, p:matchesOwn(Republican|Democrat|Independent)"));
                    String profile = safeAttr(member, By.cssSelector("img"), "src");
                    String href = safeAttr(member, By.cssSelector("a"), "href");

                    if (!profile.isEmpty() && !profile.startsWith("http"))
                        profile = "https://akleg.gov" + (profile.startsWith("/") ? profile : "/" + profile);
                    if (!href.isEmpty() && !href.startsWith("http"))
                        href = "https://akleg.gov" + (href.startsWith("/") ? href : "/" + href);

                    senator.put("name", name);
                    senator.put("title", "Senator");
                    senator.put("party", party);
                    senator.put("profile", profile);
                    senator.put("dob", "");
                    senator.put("type", "Senator");
                    senator.put("country", "USA");
                    senator.put("url", href);
                    senator.put("otherinfo", "");

                    senatorsData.add(senator);
                    System.out.println("✅ Added: " + name);

                } catch (Exception e) {
                    System.err.println("⚠️ Skipping one entry: " + e.getMessage());
                }
            }

            // Save JSON
            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
            writer.writeValue(new File("data.json"), senatorsData);

            System.out.println("✅ Successfully saved " + senatorsData.size() + " senators to data.json");

        } catch (TimeoutException e) {
            System.err.println("🚨 Timeout waiting for senator elements. Cloudflare or slow load.");
        } catch (Exception e) {
            System.err.println("🚨 Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            driver.quit();
            System.out.println("🧹 Browser closed.");
        }
    }

    private static String safeText(WebElement parent, By selector) {
        try {
            return parent.findElement(selector).getText().trim();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    private static String safeAttr(WebElement parent, By selector, String attr) {
        try {
            return parent.findElement(selector).getAttribute(attr);
        } catch (NoSuchElementException e) {
            return "";
        }
    }
}
