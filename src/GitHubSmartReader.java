import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GitHubSmartReader {

    private static final String GITHUB_TOKEN = System.getenv("GITHUB_TOKEN");
    private static final String CHAT_GPT_API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String GITHUB_API_URL = "https://api.github.com/repos/";
    private static final String CHAT_GPT_API_URL = "https://api.openai.com/v1/chat/completions";

    public static void main(String[] args) {
        try {
            String repoUrl = "calebewerneckcouto/GerarPixSpring"; // Repositório alvo

            // Map de arquivos importantes (caminho -> download_url)
            Map<String, String> arquivos = new LinkedHashMap<>();
            coletarArquivosImportantes(repoUrl, "", arquivos);

            if (arquivos.isEmpty()) {
                System.out.println("Nenhum arquivo relevante encontrado.");
                return;
            }

            System.out.println("Arquivos importantes encontrados:");
            arquivos.keySet().forEach(System.out::println);

            // Processar chunks
            List<String> resumosChunks = new ArrayList<>();
            for (Map.Entry<String, String> entry : arquivos.entrySet()) {
                String codigo = obterConteudoArquivo(entry.getValue());
                // Divide em chunks de 100 linhas
                List<String> chunks = dividirEmChunks(codigo, 100);
                for (String chunk : chunks) {
                    String resumo = gerarResumoChunk(chunk, entry.getKey());
                    resumosChunks.add(resumo);
                }
            }

            // Combinar resumos para gerar README completo
            String resumoFinal = String.join("\n\n", resumosChunks);
            String promptFinal = """
                Você é um desenvolvedor sênior especialista. Gere um README.md completo para este repositório, considerando:
                1. Estrutura do projeto e pastas.
                2. Linguagens de programação usadas.
                3. Dependências e instruções de instalação.
                4. Como rodar o projeto e executar testes.
                5. Explicação detalhada dos arquivos de código (funções, classes, principais responsabilidades).
                6. Exemplos de uso.
                7. Boas práticas e dicas para contribuir.
                
                Aqui estão os resumos dos arquivos analisados:
                %s
                """.formatted(resumoFinal);

            String readmeCompleto = chamarChatGPT(promptFinal, "gpt-4-turbo");

            System.out.println("\n=== README Gerado ===\n");
            System.out.println(readmeCompleto);

            escreverReadmeNoGitHub(repoUrl, readmeCompleto);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ===== Coleta arquivos importantes automaticamente =====
    public static void coletarArquivosImportantes(String repoUrl, String path, Map<String, String> arquivos) throws IOException {
        String urlStr = GITHUB_API_URL + repoUrl + "/contents/" + path;
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "Bearer " + GITHUB_TOKEN);

        int responseCode = con.getResponseCode();
        if (responseCode != 200) {
            System.err.println("Erro GitHub API: " + responseCode);
            return;
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder responseData = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) responseData.append(inputLine);
        in.close();

        JsonArray files = JsonParser.parseString(responseData.toString()).getAsJsonArray();
        for (int i = 0; i < files.size(); i++) {
            JsonObject file = files.get(i).getAsJsonObject();
            String type = file.get("type").getAsString();
            String name = file.get("name").getAsString();
            String filePath = path.isEmpty() ? name : path + "/" + name;

            if ("file".equals(type)) {
                String ext = name.contains(".") ? name.substring(name.lastIndexOf(".") + 1) : "";
                // Pega apenas linguagens importantes
                if (Arrays.asList("java","py","js","ts","go").contains(ext)) {
                    arquivos.put(filePath, file.get("download_url").getAsString());
                }
            } else if ("dir".equals(type)) {
                // recursivo
                coletarArquivosImportantes(repoUrl, filePath, arquivos);
            }
        }
    }

    // ===== Obter conteúdo do arquivo =====
    public static String obterConteudoArquivo(String fileUrl) throws IOException {
        StringBuilder content = new StringBuilder();
        URL url = new URL(fileUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        while ((inputLine = in.readLine()) != null) content.append(inputLine).append("\n");
        in.close();

        return content.toString();
    }

    // ===== Dividir código em chunks =====
    public static List<String> dividirEmChunks(String codigo, int linhasPorChunk) {
        String[] linhas = codigo.split("\n");
        List<String> chunks = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < linhas.length; i++) {
            sb.append(linhas[i]).append("\n");
            if ((i + 1) % linhasPorChunk == 0 || i == linhas.length - 1) {
                chunks.add(sb.toString());
                sb.setLength(0);
            }
        }
        return chunks;
    }

    // ===== Gerar resumo do chunk =====
    public static String gerarResumoChunk(String chunk, String arquivo) {
        String prompt = "Resuma este arquivo Java chamado " + arquivo + " em português, destacando funções, classes e responsabilidades principais:\n" + chunk;
        return chamarChatGPT(prompt, "gpt-4o-mini");
    }

    // ===== Chamada genérica ao ChatGPT =====
    public static String chamarChatGPT(String message, String model) {
        try {
            URL url = new URL(CHAT_GPT_API_URL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + CHAT_GPT_API_KEY);
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);

            JsonObject messageJson = new JsonObject();
            messageJson.addProperty("role", "user");
            messageJson.addProperty("content", message);

            JsonArray messagesArray = new JsonArray();
            messagesArray.add(messageJson);

            JsonObject bodyJson = new JsonObject();
            bodyJson.addProperty("model", model);
            bodyJson.add("messages", messagesArray);

            try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
                writer.write(bodyJson.toString());
                writer.flush();
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder responseData = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) responseData.append(inputLine);
            in.close();

            JsonObject jsonResponse = JsonParser.parseString(responseData.toString()).getAsJsonObject();
            JsonArray choicesArray = jsonResponse.getAsJsonArray("choices");
            if (choicesArray.size() > 0) {
                JsonObject choice = choicesArray.get(0).getAsJsonObject();
                JsonObject messageObject = choice.getAsJsonObject("message");
                if (messageObject != null && messageObject.has("content")) {
                    return messageObject.get("content").getAsString();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return "Erro ao gerar resposta do ChatGPT";
    }

    // ===== Escrever README no GitHub =====
    public static void escreverReadmeNoGitHub(String repoUrl, String readmeContent) throws IOException {
        String urlStr = GITHUB_API_URL + repoUrl + "/contents/README.md";

        // Verifica SHA existente
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "Bearer " + GITHUB_TOKEN);

        int responseCode = con.getResponseCode();
        String sha = null;
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder responseData = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) responseData.append(inputLine);
            in.close();

            JsonObject fileInfo = JsonParser.parseString(responseData.toString()).getAsJsonObject();
            if (fileInfo.has("sha")) sha = fileInfo.get("sha").getAsString();
        }

        // Atualiza README
        String encodedContent = Base64.getEncoder().encodeToString(readmeContent.getBytes());
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("message", "Atualizando README.md via API inteligente");
        bodyJson.addProperty("content", encodedContent);
        if (sha != null) bodyJson.addProperty("sha", sha);

        con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setRequestMethod("PUT");
        con.setRequestProperty("Authorization", "Bearer " + GITHUB_TOKEN);
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
            writer.write(bodyJson.toString());
            writer.flush();
        }

        responseCode = con.getResponseCode();
        if (responseCode == 200 || responseCode == 201) {
            System.out.println("✅ README.md atualizado com sucesso!");
        } else {
            System.err.println("Erro ao atualizar README.md: " + responseCode);
        }
    }
}
