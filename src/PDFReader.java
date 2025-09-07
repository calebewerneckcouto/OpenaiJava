import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class PDFReader {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try {
            // Caminho do PDF original
            String filePath = "E:/PROGRAMAÇÃO JAVA FULL STACK/AulaIngles/English Conversation - Intermediate English (2).pdf";

            // Pergunta a página que deseja extrair
            System.out.println("Digite o número da página que deseja extrair:");
            int pagina = scanner.nextInt();
            scanner.nextLine(); // limpar buffer

            // Extrair texto da página escolhida
            String textoPagina = extrairTextoPagina(filePath, pagina);

            // Enviar para ChatGPT pedindo resolução
            String prompt = "Resolva as atividades desta página de inglês e explique tudo em português:\n" + textoPagina;
            String respostaGPT = chatGpt(prompt);

            // Criar PDF com a resolução
            String arquivoResolvido = "E:/PROGRAMAÇÃO JAVA FULL STACK/AulaIngles/Atividade_Resolvida_Pagina_" + pagina + ".pdf";
            criarPDF(respostaGPT, arquivoResolvido);

            System.out.println("Atividade resolvida gerada em: " + arquivoResolvido);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Extrai texto de uma página específica
    public static String extrairTextoPagina(String filePath, int pagina) throws IOException {
        PDDocument document = PDDocument.load(new File(filePath));
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pagina);
        stripper.setEndPage(pagina);
        String texto = stripper.getText(document);
        document.close();
        return texto;
    }

    // Envia texto para ChatGPT
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
                    response = messageObject.get("content").getAsString();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }

    // Cria PDF com o conteúdo resolvido
    public static void criarPDF(String texto, String caminhoArquivo) throws IOException {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        PDPageContentStream contentStream = new PDPageContentStream(document, page);
        contentStream.beginText();
        contentStream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
        contentStream.setLeading(14f);
        contentStream.newLineAtOffset(50, 750);

        String[] linhas = texto.split("\n");
        for (String linha : linhas) {
            contentStream.showText(linha);
            contentStream.newLine();
        }

        contentStream.endText();
        contentStream.close();

        document.save(caminhoArquivo);
        document.close();
    }
}
