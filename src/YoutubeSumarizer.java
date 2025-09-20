import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class YoutubeSumarizer {

    // API Key do OpenAI (definida no .env ou variáveis do sistema)
    private static final String API_KEY = System.getenv("OPENAI_API_KEY");

    // Configura o OkHttpClient com timeouts maiores
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)  // conexão
            .writeTimeout(60, TimeUnit.SECONDS)    // envio
            .readTimeout(120, TimeUnit.SECONDS)    // resposta
            .build();

    // Tipo de mídia JSON
    private static final MediaType JSON = MediaType.parse("application/json");

    public static void main(String[] args) {
        try {
            // Caminho do transcript original
            Path transcriptPath = Paths.get("src/resources/transcript.txt");

            // Lê o conteúdo do arquivo
            String transcript = new String(Files.readAllBytes(transcriptPath), StandardCharsets.UTF_8);

            // Cria o prompt para tradução mantendo timestamps
            String prompt = """
                    Traduza o seguinte transcript do vídeo para português mantendo o tempo.
                    ⚠️ IMPORTANTE:
                    - Mantenha exatamente as marcações de tempo (ex: 00:00:00:00 - 00:00:02:05) SEM nenhuma alteração.
                    - Preserve o formato original (quebras de linha e espaçamentos).
                    - Traduza APENAS o texto entre as marcações de tempo, deixando as timestamps inalteradas.
                    - Não adicione nem remova nada além da tradução.

                    """ + transcript;

            // Monta o JSON da requisição
            JSONObject json = new JSONObject();
            json.put("model", "gpt-3.5-turbo");
            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            messages.put(message);
            json.put("messages", messages);

            // Cria o body da requisição
            RequestBody body = RequestBody.create(json.toString(), JSON);

            // Monta a requisição HTTP
            Request request = new Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            // Executa a requisição
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Erro na chamada da API: " + response);
                }

                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                // Extrai a tradução
                String translation = jsonResponse
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");

                // Exibe no console
                System.out.println(translation);

                // Caminho do arquivo de saída
                Path outputPath = Paths.get("src/resources/translation.txt");

                // Cria a pasta caso não exista
                Files.createDirectories(outputPath.getParent());

                // Salva a tradução
                Files.write(outputPath, translation.getBytes(StandardCharsets.UTF_8));

                System.out.println("✅ Tradução salva em src/resources/translation.txt");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
