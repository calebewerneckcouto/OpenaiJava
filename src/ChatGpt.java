import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class ChatGpt {

    public static void main(String[] args) {
        String resposta = chatGpt("O que é Java?");
        System.out.println(resposta);
    }

    public static String chatGpt(String message) {
        String url = "https://api.openai.com/v1/chat/completions";
        final String CHAT_GPT_API_KEY = System.getenv("OPENAI_API_KEY");
        String model =  "gpt-4-turbo";

        String response = "";

        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + CHAT_GPT_API_KEY);
            con.setRequestProperty("Content-Type", "application/json");

            String body = "{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\",\"content\":\"" + message + "\"}]}";
            con.setDoOutput(true);
            OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream());
            writer.write(body);
            writer.flush();
            writer.close();

            int responseCode = con.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer responseData = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                responseData.append(inputLine);
            }
            in.close();

            // Extrair o conteúdo da resposta JSON
            JsonParser parser = new JsonParser();
            JsonObject jsonResponse = parser.parse(responseData.toString()).getAsJsonObject();
            JsonArray choicesArray = jsonResponse.getAsJsonArray("choices");
            if (choicesArray.size() > 0) {
                JsonObject choice = choicesArray.get(0).getAsJsonObject();
                JsonObject messageObject = choice.getAsJsonObject("message");
                if (messageObject != null && messageObject.has("content")) {
                    response = messageObject.get("content").getAsString();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }
}
