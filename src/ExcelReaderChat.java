import com.google.gson.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class ExcelReaderChat {

    private static final String FILE_PATH = "C:/Users/WINDOWS 11/git/chatgptJava/src/resources/dados_100.xlsx";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try {
            System.out.println("=== Chat com IA sobre sua planilha ===");
            System.out.println("Digite sua pergunta ou 'sair' para encerrar.");

            // Lê a planilha inteira **uma vez**
            String conteudoExcel = lerExcel(FILE_PATH);

            while (true) {
                System.out.print("\nPergunta: ");
                String pergunta = scanner.nextLine();

                if (pergunta.equalsIgnoreCase("sair")) {
                    System.out.println("Encerrando o programa. Até mais!");
                    break;
                }

                // Prompt completo para a IA
                String prompt = "Aqui estão os dados da planilha:\n\n" + conteudoExcel +
                        "\n\nResponda normalmente em português à seguinte pergunta sobre a planilha:\n" + pergunta;

                String respostaGPT = chatGpt(prompt);

                System.out.println("\n=== RESPOSTA DA IA ===");
                System.out.println(respostaGPT);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String lerExcel(String filePath) throws IOException {
        FileInputStream fis = new FileInputStream(new File(filePath));
        Workbook workbook = new XSSFWorkbook(fis);
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            sb.append("Planilha: ").append(sheet.getSheetName()).append("\n");

            for (Row row : sheet) {
                for (Cell cell : row) {
                    sb.append(getCellStringValue(cell)).append("\t");
                }
                sb.append("\n");
            }
            sb.append("\n=======================\n");
        }

        workbook.close();
        fis.close();
        return sb.toString();
    }

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

    public static String chatGpt(String message) {
        String url = "https://api.openai.com/v1/chat/completions";
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = "gpt-4-turbo";
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
            OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream());
            writer.write(requestJson.toString());
            writer.flush();
            writer.close();

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
                    response = messageObject.get("content").getAsString();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }
}
