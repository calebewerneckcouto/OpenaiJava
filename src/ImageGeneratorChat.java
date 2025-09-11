import com.google.gson.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.awt.Desktop;

public class ImageGeneratorChat {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Gerador de Imagens com IA ===");
        System.out.println("Digite a descrição da imagem ou 'sair' para encerrar.");

        while (true) {
            System.out.print("\nPrompt: ");
            String prompt = scanner.nextLine();

            if (prompt.equalsIgnoreCase("sair")) {
                System.out.println("Encerrando o programa. Até mais!");
                break;
            }

            String imageUrl = gerarImagem(prompt);

            if (imageUrl != null && !imageUrl.isEmpty()) {
                System.out.println("\n=== IMAGEM GERADA ===");
                System.out.println(imageUrl);

                // Tenta abrir automaticamente no navegador
                try {
                    Desktop.getDesktop().browse(new URL(imageUrl).toURI());
                } catch (Exception e) {
                    System.out.println("Não foi possível abrir no navegador automaticamente.");
                }
            } else {
                System.out.println("Não foi possível gerar a imagem.");
            }
        }

        scanner.close();
    }

    public static String gerarImagem(String prompt) {
        String url = "https://api.openai.com/v1/images/generations";
        String apiKey = System.getenv("OPENAI_API_KEY");
        String imageUrl = "";

        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("User-Agent", "Java-OpenAI-Client");

            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("model", "dall-e-3");
            requestJson.addProperty("prompt", prompt);
            requestJson.addProperty("size", "1024x1024");

            requestJson.addProperty("n", 1);

            String payload = requestJson.toString();
            System.out.println("\n>>> JSON enviado: " + payload);

            con.setDoOutput(true);
            try (OutputStream os = con.getOutputStream()) {
                os.write(payload.getBytes("UTF-8"));
            }

            int responseCode = con.getResponseCode();
            InputStream is = (responseCode == 200) ? con.getInputStream() : con.getErrorStream();

            BufferedReader in = new BufferedReader(new InputStreamReader(is));
            StringBuilder responseData = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null) responseData.append(inputLine);
            in.close();

            System.out.println("\n>>> RESPOSTA DA API: " + responseData);

            if (responseCode == 200) {
                JsonObject jsonResponse = JsonParser.parseString(responseData.toString()).getAsJsonObject();
                JsonArray dataArray = jsonResponse.getAsJsonArray("data");
                if (dataArray.size() > 0) {
                    JsonObject dataObj = dataArray.get(0).getAsJsonObject();
                    if (dataObj.has("url")) {
                        imageUrl = dataObj.get("url").getAsString();
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return imageUrl;
    }
}