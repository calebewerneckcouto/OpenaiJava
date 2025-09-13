import com.google.gson.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class ExcelReaderChunks {

    private static List<Chunk> chunks = new ArrayList<>();
    private static String resumoPlanilha = "";

    static class Chunk {
        int inicioLinha;
        int fimLinha;
        String texto;
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try {
            String filePath = "C:/Users/WINDOWS 11/git/chatgptJava/src/resources/animais_60.xlsx";

            System.out.println("🔄 Extraindo conteúdo da planilha e criando chunks...");
            criarChunksExcel(filePath, 50); // chunk de 50 linhas por bloco

            System.out.println("🔄 Gerando resumo inicial da planilha com IA...");
            StringBuilder todosChunks = new StringBuilder();
            for (Chunk c : chunks) todosChunks.append(c.texto).append("\n");
            resumoPlanilha = chatGpt("Resuma os dados desta planilha em português, destacando informações principais:\n\n" + todosChunks);

            System.out.println("\n✅ Resumo pronto!");
            System.out.println(resumoPlanilha);

            // Loop de perguntas
            while (true) {
                System.out.print("\nPergunta: ");
                String pergunta = scanner.nextLine();

                if (pergunta.equalsIgnoreCase("sair")) {
                    System.out.println("Encerrando o programa. Até mais!");
                    break;
                }

                // Busca chunks relevantes
                List<Chunk> relevantes = buscarChunks(pergunta);

                StringBuilder promptBuilder = new StringBuilder();
                promptBuilder.append("Aqui estão os dados relevantes da planilha:\n");
                for (Chunk c : relevantes) {
                    promptBuilder.append("Linhas ").append(c.inicioLinha).append("-").append(c.fimLinha).append(":\n");
                    promptBuilder.append(c.texto).append("\n\n");
                }
                promptBuilder.append("Com base nesses dados, responda à seguinte pergunta:\n").append(pergunta);

                String respostaGPT = chatGpt(promptBuilder.toString());
                System.out.println("\n=== RESPOSTA DA IA ===");
                System.out.println(respostaGPT);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    // Cria chunks da planilha
    public static void criarChunksExcel(String filePath, int linhasPorChunk) throws IOException {
        FileInputStream fis = new FileInputStream(new File(filePath));
        Workbook workbook = new XSSFWorkbook(fis);

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            int totalLinhas = sheet.getLastRowNum() + 1;

            for (int inicio = 0; inicio < totalLinhas; inicio += linhasPorChunk) {
                int fim = Math.min(inicio + linhasPorChunk - 1, totalLinhas - 1);
                StringBuilder sb = new StringBuilder();

                for (int r = inicio; r <= fim; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    for (Cell cell : row) sb.append(getCellStringValue(cell)).append("\t");
                    sb.append("\n");
                }

                Chunk chunk = new Chunk();
                chunk.inicioLinha = inicio + 1;
                chunk.fimLinha = fim + 1;
                chunk.texto = sb.toString();
                chunks.add(chunk);
            }
        }

        workbook.close();
        fis.close();
    }

    // Busca chunks relevantes baseado em palavras-chave
    public static List<Chunk> buscarChunks(String pergunta) {
        List<Chunk> relevantes = new ArrayList<>();
        String[] palavras = pergunta.toLowerCase().split("\\s+");

        for (Chunk c : chunks) {
            String textoLower = c.texto.toLowerCase();
            for (String p : palavras) {
                if (textoLower.contains(p)) {
                    relevantes.add(c);
                    break;
                }
            }
        }

        if (relevantes.isEmpty()) return chunks; // se nada for encontrado, usa todos
        return relevantes;
    }

    // Pega valor da célula
    public static String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING: return cell.getStringCellValue();
                case NUMERIC: return new DataFormatter().formatCellValue(cell);
                case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
                case FORMULA: return new DataFormatter().formatCellValue(cell);
                default: return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    // Chamada ao ChatGPT
    public static String chatGpt(String message) {
        String url = "https://api.openai.com/v1/chat/completions";
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = "gpt-4o-mini";
        String response = "";

        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");

            JsonObject requestJson = new JsonObject();
            requestJson.addProperty("model", model);

            JsonArray messagesArray = new JsonArray();
            JsonObject messageJson = new JsonObject();
            messageJson.addProperty("role", "user");
            messageJson.addProperty("content", message);
            messagesArray.add(messageJson);
            requestJson.add("messages", messagesArray);

            con.setDoOutput(true);
            try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
                writer.write(requestJson.toString());
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
                    response = messageObject.get("content").getAsString().trim();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }
}
