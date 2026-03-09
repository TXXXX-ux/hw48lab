import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static List<Candidate> candidates = new ArrayList<>();
    private static final String WEB_DIR = "data";
    private static final String JSON_PATH = "candidates.json";

    public static void main(String[] args) throws IOException {
        loadData();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", Main::handleIndex);
        server.createContext("/vote", Main::handleVote);
        server.createContext("/thankyou", Main::handleThankYou);
        server.createContext("/votes", Main::handleResults);
        server.createContext("/css/", Main::handleStatic);
        server.createContext("/images/", Main::handleStatic);

        server.start();
        System.out.println("Сервер запущен: http://localhost:8080");
    }

    private static void handleIndex(HttpExchange exchange) throws IOException {
        String html = readFile("candidates.html");

        String template = extractTag(html, "div", "class=\"card\"");

        StringBuilder allCards = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            String card = template
                    .replace("images/1.jpeg", "images/" + c.photo)
                    .replace("name surname", c.name)
                    .replace("candidate_id", String.valueOf(i));
            allCards.append(card);
        }

        String finalHtml = html.replaceFirst("(?s)<main.*?>.*?</main>",
                "<main class=\"flex flex-wrap align-evenly\">" + allCards.toString() + "</main>");
        sendResponse(exchange, finalHtml);
    }

    private static void handleResults(HttpExchange exchange) throws IOException {
        String html = readFile("votes.html");
        String template = extractTag(html, "div", "class=\"card\"");

        int total = candidates.stream().mapToInt(v -> v.votes).sum();
        StringBuilder allCards = new StringBuilder();

        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> Integer.compare(b.votes, a.votes));

        for (Candidate c : sorted) {
            int p = (total == 0) ? 0 : (c.votes * 100 / total);
            String card = template
                    .replace("images/1.jpeg", "images/" + c.photo)
                    .replace("Votes: 75%", c.name + ": " + p + "%");
            allCards.append(card);
        }

        String finalHtml = html.replaceFirst("(?s)<main.*?>.*?</main>",
                "<main class=\"flex flex-wrap align-center\">" + allCards.toString() +
                        "<a class=\"back flex align-center\" href=\"/\">back to main</a></main>");
        sendResponse(exchange, finalHtml);
    }

    private static void handleThankYou(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        int id = Integer.parseInt(query.split("=")[1]);
        Candidate c = candidates.get(id);

        String html = readFile("thankyou.html");
        String finalHtml = html.replace("images/1.jpeg", "images/" + c.photo)
                .replace("Thank you for your vote!", "Вы выбрали: " + c.name);
        sendResponse(exchange, finalHtml);
    }

    private static String extractTag(String html, String tag, String attr) {
        Pattern p = Pattern.compile("(?s)<" + tag + "[^>]*" + attr + ".*?</" + tag + ">");
        Matcher m = p.matcher(html);
        return m.find() ? m.group() : "";
    }

    private static void handleVote(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int id = Integer.parseInt(body.split("=")[1]);
            candidates.get(id).votes++;
            saveData();
            exchange.getResponseHeaders().set("Location", "/thankyou?id=" + id);
            exchange.sendResponseHeaders(303, -1);
        }
        exchange.close();
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String readFile(String name) throws IOException {
        return Files.readString(Path.of(WEB_DIR, name));
    }

    private static void handleStatic(HttpExchange exchange) throws IOException {
        Path path = Path.of(WEB_DIR, exchange.getRequestURI().getPath().substring(1));
        if (Files.exists(path)) {
            String mime = path.toString().endsWith(".css") ? "text/css" : "image/jpeg";
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.sendResponseHeaders(200, Files.size(path));
            Files.copy(path, exchange.getResponseBody());
        } else {
            exchange.sendResponseHeaders(404, -1);
        }
        exchange.close();
    }

    private static void loadData() throws IOException {
        File f = new File(JSON_PATH);
        if (f.exists()) {
            candidates = new Gson().fromJson(new FileReader(f), new TypeToken<List<Candidate>>(){}.getType());
        }
    }

    private static void saveData() throws IOException {
        try (Writer w = new FileWriter(JSON_PATH)) {
            new Gson().toJson(candidates, w);
        }
    }

    static class Candidate {
        String name;
        String photo;
        int votes;
    }
}