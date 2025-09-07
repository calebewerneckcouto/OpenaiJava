import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class GitHubReader {

    private static final String GITHUB_TOKEN = System.getenv("GITHUB_TOKEN");
    private static final String CHAT_GPT_API_KEY = System.getenv("OPENAI_API_KEY");

    private static final String GITHUB_API_URL = "https://api.github.com/repos/";
    private static final String CHAT_GPT_API_URL = "https://api.openai.com/v1/chat/completions";

    public static void main(String[] args) {
        try {
            String repoUrl = "calebewerneckcouto/FormADA";
           

            System.out.println("Lendo arquivos do repositório apenas na pasta src: " + repoUrl);
            String repositoryContent = obterConteudoRepositorio(repoUrl, "src");

            // Limitar conteúdo para evitar envio muito grande
            repositoryContent = repositoryContent.length() > 2000 ? repositoryContent.substring(0, 2000)
                    : repositoryContent;

            System.out.println("Gerando README...");
            String readme = gerarReadme(repositoryContent);
            System.out.println("README gerado:\n" + readme);

            System.out.println("Escrevendo README no GitHub...");
            escreverReadmeNoGitHub(repoUrl, readme);

        } catch (IOException e) {
            System.err.println("Erro ao acessar o repositório: " + e.getMessage());
        }
    }

    // ====== Função para obter conteúdo apenas do src ======
    public static String obterConteudoRepositorio(String repoUrl, String path) throws IOException {
        String urlStr = GITHUB_API_URL + repoUrl + "/contents/" + path;
        StringBuilder content = new StringBuilder();

        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "Bearer " + GITHUB_TOKEN);

        int responseCode = con.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Erro ao acessar API GitHub. Código de resposta: " + responseCode);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder responseData = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            responseData.append(inputLine);
        }
        in.close();

        JsonArray files = JsonParser.parseString(responseData.toString()).getAsJsonArray();

        for (int i = 0; i < files.size(); i++) {
            JsonObject file = files.get(i).getAsJsonObject();
            String fileName = file.get("name").getAsString();
            String fileType = file.get("type").getAsString();

            if ("file".equals(fileType) && fileName.endsWith(".java")) {
                // Pega apenas arquivos .java
                String fileContent = obterConteudoArquivo(file.get("download_url").getAsString());
                content.append("Arquivo: ").append(fileName).append("\n").append(fileContent).append("\n\n");
            } else if ("dir".equals(fileType)) {
                // chama recursivamente apenas se for diretório dentro de src
                content.append(obterConteudoRepositorio(repoUrl, path + "/" + fileName));
            }
        }

        return content.toString();
    }

    public static String obterConteudoArquivo(String fileUrl) throws IOException {
        StringBuilder content = new StringBuilder();
        URL url = new URL(fileUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine).append("\n");
        }
        in.close();

        return content.toString();
    }

    // ====== Função para gerar README usando ChatGPT ======
    public static String gerarReadme(String repositoryContent) {
        try {
            URL url = new URL(CHAT_GPT_API_URL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + CHAT_GPT_API_KEY);
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);

            JsonObject message = new JsonObject();
            message.addProperty("role", "user");
            message.addProperty("content", "Com base neste código fonte (apenas arquivos Java dentro de src), gere um README.md bem completo e explique tudo em português:\n"
                    + repositoryContent);

            JsonArray messages = new JsonArray();
            messages.add(message);

            JsonObject bodyJson = new JsonObject();
            bodyJson.addProperty("model", "gpt-4-turbo");
            bodyJson.add("messages", messages);

            OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream());
            writer.write(bodyJson.toString());
            writer.flush();
            writer.close();

            int responseCode = con.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Erro ao acessar API do ChatGPT. Código de resposta: " + responseCode);
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            StringBuilder responseData = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                responseData.append(inputLine);
            }
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

        return "Erro ao gerar o README.md";
    }

    // ====== Função para escrever README no GitHub ======
    public static void escreverReadmeNoGitHub(String repoUrl, String readmeContent) throws IOException {
        String urlStr = GITHUB_API_URL + repoUrl + "/contents/README.md";

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
            while ((inputLine = in.readLine()) != null) {
                responseData.append(inputLine);
            }
            in.close();

            JsonObject fileInfo = JsonParser.parseString(responseData.toString()).getAsJsonObject();
            if (fileInfo.has("sha")) {
                sha = fileInfo.get("sha").getAsString();
            }
        } else if (responseCode != 404) {
            throw new IOException("Erro ao acessar o repositório. Código de resposta: " + responseCode);
        }

        String encodedContent = Base64.getEncoder().encodeToString(readmeContent.getBytes());

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("message", "Atualizando README.md via API");
        bodyJson.addProperty("content", encodedContent);
        if (sha != null) {
            bodyJson.addProperty("sha", sha);
        }

        con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setRequestMethod("PUT");
        con.setRequestProperty("Authorization", "Bearer " + GITHUB_TOKEN);
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream());
        writer.write(bodyJson.toString());
        writer.flush();
        writer.close();

        responseCode = con.getResponseCode();
        if (responseCode != 201 && responseCode != 200) {
            throw new IOException("Erro ao escrever README no GitHub. Código de resposta: " + responseCode);
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        StringBuilder responseData = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            responseData.append(inputLine);
        }
        in.close();

        System.out.println("README.md atualizado com sucesso!");
    }
}
