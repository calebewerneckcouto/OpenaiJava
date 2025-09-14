import java.io.*;
import java.util.*;
import java.net.HttpURLConnection;
import java.net.URL;
import com.google.gson.*;

public class CsvReaderOnDemand {

    private static List<Chunk> chunks = new ArrayList<>();

    static class Chunk {
        int inicioLinha;
        int fimLinha;
        String texto;
    }

    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);
        String filePath = "C:/Users/WINDOWS 11/git/chatgptJava/src/resources/relatorio_22_01_2025.csv";

        System.out.println("🔄 Carregando CSV em memória...");
        criarChunksCSV(filePath, 50); // lê todo CSV e cria chunks
        System.out.println("✅ CSV carregado! Pronto para perguntas.");

        while (true) {
            System.out.print("\nPergunta: ");
            String pergunta = scanner.nextLine();
            if (pergunta.equalsIgnoreCase("sair")) break;

            // Busca apenas os chunks relevantes
            List<Chunk> relevantes = buscarChunks(pergunta, 3); // top 3 chunks

            StringBuilder prompt = new StringBuilder();
            prompt.append("Aqui estão os dados relevantes do CSV:\n");
            for (Chunk c : relevantes) {
                prompt.append("Linhas ").append(c.inicioLinha).append("-").append(c.fimLinha).append(":\n");
                prompt.append(c.texto).append("\n");
            }
            prompt.append("Responda à pergunta com base nesses dados: ").append(pergunta);

            String resposta = chatGptRetry(prompt.toString());
            System.out.println("\n=== RESPOSTA DA IA ===");
            System.out.println(resposta);
        }

        scanner.close();
    }

    public static void criarChunksCSV(String filePath, int linhasPorChunk) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        String linha;
        int contador = 0;
        StringBuilder sb = new StringBuilder();
        int inicioChunk = 1;

        while ((linha = br.readLine()) != null) {
            contador++;
            sb.append(linha).append("\n");
            if (contador % linhasPorChunk == 0) {
                Chunk c = new Chunk();
                c.inicioLinha = inicioChunk;
                c.fimLinha = contador;
                c.texto = sb.toString();
                chunks.add(c);
                sb.setLength(0);
                inicioChunk = contador + 1;
            }
        }
        if (sb.length() > 0) {
            Chunk c = new Chunk();
            c.inicioLinha = inicioChunk;
            c.fimLinha = contador;
            c.texto = sb.toString();
            chunks.add(c);
        }
        br.close();
    }

    public static List<Chunk> buscarChunks(String pergunta, int topN) {
        Map<Chunk, Integer> scores = new HashMap<>();
        String[] palavras = pergunta.toLowerCase().split("\\s+");

        for (Chunk c : chunks) {
            int score = 0;
            String texto = c.texto.toLowerCase();
            for (String p : palavras) if (texto.contains(p)) score++;
            if (score > 0) scores.put(c, score);
        }

        List<Map.Entry<Chunk, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());

        List<Chunk> topChunks = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, sorted.size()); i++) topChunks.add(sorted.get(i).getKey());

        if (topChunks.isEmpty()) topChunks.addAll(chunks.subList(0, Math.min(topN, chunks.size())));

        return topChunks;
    }

    public static String chatGptRetry(String message) {
        int tentativas = 0;
        while (tentativas < 5) {
            try {
                return chatGpt(message);
            } catch (IOException e) {
                if (e.getMessage().contains("HTTP response code: 429")) {
                    tentativas++;
                    System.out.println("⚠️ Limite da API atingido. Tentando novamente em 5s... (" + tentativas + "/5)");
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                } else {
                    e.printStackTrace();
                    break;
                }
            }
        }
        return "Erro: não foi possível obter resposta da API.";
    }

    public static String chatGpt(String message) throws IOException {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String url = "https://api.openai.com/v1/chat/completions";
        String model = "gpt-4o-mini";

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Authorization", "Bearer " + apiKey);
        con.setRequestProperty("Content-Type", "application/json");

        JsonObject requestJson = new JsonObject();
        requestJson.addProperty("model", model);
        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", message);
        messages.add(msg);
        requestJson.add("messages", messages);

        con.setDoOutput(true);
        try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
            writer.write(requestJson.toString());
            writer.flush();
        }

        if (con.getResponseCode() != 200) throw new IOException("HTTP response code: " + con.getResponseCode());

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder response = new StringBuilder();
        String linha;
        while ((linha = in.readLine()) != null) response.append(linha);
        in.close();

        JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
        JsonArray choices = jsonResponse.getAsJsonArray("choices");
        if (choices.size() > 0) {
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject messageObj = choice.getAsJsonObject("message");
            if (messageObj != null && messageObj.has("content"))
                return messageObj.get("content").getAsString().trim();
        }
        return "Sem resposta.";
    }
}
