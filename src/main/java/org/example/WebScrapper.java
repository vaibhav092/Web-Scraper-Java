package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import java.io.File;
import java.util.*;

public class WebScrapper {

    public static void main(String[] args) {
        String baseUrl = "https://akleg.gov/senate.php";
        List<Map<String, String>> senators = new ArrayList<>();

        try (Playwright playwright = Playwright.create()) {
            // ---- Launch Browser (non-headless for Cloudflare bypass) ----
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(false)
                            .setArgs(List.of(
                                    "--no-sandbox",
                                    "--disable-blink-features=AutomationControlled"
                            ))
            );

            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setViewportSize(1920, 1080)
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                    "Chrome/131.0.0.0 Safari/537.36")
            );

            Page page = context.newPage();
            System.out.println("🌐 Opening: " + baseUrl);
            page.navigate(baseUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));

            // ---- Wait for senator cards or Cloudflare ----
            boolean loaded = waitForSenators(page);

            if (!loaded) {
                System.out.println("⚠️ Cloudflare challenge detected, waiting 10 seconds...");
                page.waitForTimeout(10_000);
                page.reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                loaded = waitForSenators(page);
            }

            if (!loaded) {
                System.err.println("🚨 Still no senators found (possible Cloudflare block). Exiting.");
                browser.close();
                return;
            }

            List<ElementHandle> members = page.querySelectorAll("div.member, div.col-md-3.member, div.member-card");
            System.out.println("📊 Found " + members.size() + " senator entries");

            for (ElementHandle member : members) {
                Map<String, String> senator = new LinkedHashMap<>();

                String name = textOrEmpty(member, ".member-name");
                String party = textOrEmpty(member, ".member-party");
                String profile = absolutize(attrOrEmpty(member, "img", "src"));
                String href = absolutize(attrOrEmpty(member, "a", "href"));

                senator.put("name", name);
                senator.put("title", "Senator");
                senator.put("party", party);
                senator.put("profile", profile);
                senator.put("dob", "");
                senator.put("type", "Senator");
                senator.put("country", "USA");
                senator.put("url", href);
                senator.put("otherinfo", "");

                senators.add(senator);
                System.out.println("✅ Added: " + name);
            }

            // ---- Write to data.json ----
            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
            writer.writeValue(new File("data.json"), senators);

            System.out.println("\n✅ Saved " + senators.size() + " senators to data.json");
            browser.close();
        } catch (Exception e) {
            System.err.println("🚨 Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ---------------- Helper Methods ----------------

    private static boolean waitForSenators(Page page) {
        try {
            Locator locator = page.locator("div.member, div.col-md-3.member, div.member-card");
            locator.first().waitFor(new Locator.WaitForOptions().setTimeout(45_000));
            return locator.count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String textOrEmpty(ElementHandle el, String selector) {
        try {
            ElementHandle sub = el.querySelector(selector);
            return sub != null ? sub.innerText().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String attrOrEmpty(ElementHandle el, String selector, String attr) {
        try {
            ElementHandle sub = el.querySelector(selector);
            return sub != null ? sub.getAttribute(attr) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static String absolutize(String href) {
        if (href == null || href.isEmpty()) return "";
        if (href.startsWith("http")) return href;
        if (href.startsWith("//")) return "https:" + href;
        if (!href.startsWith("/")) href = "/" + href;
        return "https://akleg.gov" + href;
    }
}
