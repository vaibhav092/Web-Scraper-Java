package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.util.*;

public class WebScrapper {
    public static void main(String[] args) {
        String senateUrl = "https://senate.akleg.gov/";
        List<Map<String, String>> allMembers = new ArrayList<>();

        try {
            System.out.println("🌐 Fetching Senate data...");
            allMembers.addAll(fetchSenateMembers(senateUrl));

            ObjectMapper mapper = new ObjectMapper();
            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
            writer.writeValue(new File("data.json"), allMembers);

            System.out.println("✅ Saved " + allMembers.size() + " senators to data.json");

        } catch (Exception e) {
            System.err.println("🚨 Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<Map<String, String>> fetchSenateMembers(String url) throws Exception {
        List<Map<String, String>> senators = new ArrayList<>();

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .timeout(15000)
                .get();

        // The new site uses div.person under div.list
        Elements members = doc.select("div.list div.person");

        System.out.println("📊 Found " + members.size() + " senator entries");

        for (Element member : members) {
            Map<String, String> senator = new LinkedHashMap<>();

            String name = member.select("h3").text();
            String details = member.select("p").text();
            String href = member.select("a").attr("href");
            String img = member.select("img").attr("src");

            if (!href.startsWith("http"))
                href = "https://senate.akleg.gov" + href;
            if (!img.startsWith("http"))
                img = "https://senate.akleg.gov" + img;

            // Split details to find party
            String party = "";
            if (details.toLowerCase().contains("republican")) party = "Republican";
            else if (details.toLowerCase().contains("democrat")) party = "Democrat";

            senator.put("name", name);
            senator.put("title", "Senator");
            senator.put("party", party);
            senator.put("profile", img);
            senator.put("dob", "");
            senator.put("type", "Senator");
            senator.put("country", "USA");
            senator.put("url", href);
            senator.put("otherinfo", details);

            senators.add(senator);
            System.out.println("✅ Added: " + name);
        }

        return senators;
    }
}
