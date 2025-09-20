import java.io.IOException;
import org.json.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Moderacao {

    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.parse("application/json");

    public static boolean checarModeracao(String texto) throws IOException {
        // Cria o JSON do body
        JSONObject json = new JSONObject();
        json.put("model", "text-moderation-latest");
        json.put("input", texto);

        // Monta a requisição
        RequestBody body = RequestBody.create(
                json.toString(),
                JSON
        );

        Request request = new Request.Builder()
                .url("https://api.openai.com/v1/moderations")
                .addHeader("Authorization", "Bearer " + API_KEY)
                .post(body)
                .build();

        // Executa a requisição
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Erro na chamada da API: " + response);
            }

            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);

            // Pega o primeiro resultado
            JSONObject resultado = jsonResponse
                    .getJSONArray("results")
                    .getJSONObject(0);

            boolean flagged = resultado.getBoolean("flagged");

            if (flagged) {
                System.out.println("⚠️ Texto não permitido: seu conteúdo foi sinalizado pela moderação.");
                return false;
            } else {
                System.out.println("✅ Texto permitido. Pode prosseguir.");
                return true;
            }
        }
    }

    public static void main(String[] args) throws IOException {
        String textoUsuario = "Voce e uma gostosa.";

        if (checarModeracao(textoUsuario)) {
            System.out.println("Conteúdo liberado para uso.");
        } else {
            System.out.println("Conteúdo bloqueado por conter material impróprio.");
        }
    }
}
