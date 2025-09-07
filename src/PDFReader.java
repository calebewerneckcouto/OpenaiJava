import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class PDFReader {

    private static final int MAX_TEXT_LENGTH = 500; // Tamanho máximo de cada parte do texto

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try {
            // Substitua pelo caminho do seu arquivo PDF
            String filePath = "E:/PROGRAMAÇÃO JAVA FULL STACK/certificação java/certificacaojava11.pdf";

            // Pergunta ao usuário qual método de extração deseja usar
            System.out.println("Escolha o método de extração:");
            System.out.println("1. Extrair um número fixo de páginas");
            System.out.println("2. Extrair um intervalo de páginas");
            int escolha = scanner.nextInt();
            scanner.nextLine(); // Limpar o buffer do scanner

            String textoPdf = "";

            switch (escolha) {
                case 1:
                    // Pergunta o número de páginas a serem extraídas
                    System.out.println("Digite o número de páginas para extrair:");
                    int numPaginas = scanner.nextInt();
                    scanner.nextLine(); // Limpar o buffer do scanner
                    textoPdf = extrairTextoPrimeirasPaginas(filePath, numPaginas);
                    break;

                case 2:
                    // Pergunta o intervalo de páginas
                    System.out.println("Digite a página inicial:");
                    int paginaInicial = scanner.nextInt();
                    System.out.println("Digite a página final:");
                    int paginaFinal = scanner.nextInt();
                    scanner.nextLine(); // Limpar o buffer do scanner
                    textoPdf = extrairTextoIntervaloPaginas(filePath, paginaInicial, paginaFinal);
                    break;

                default:
                    System.out.println("Escolha inválida.");
                    return;
            }

            // Dividir o texto em partes menores
            List<String> partesTexto = dividirTexto(textoPdf, MAX_TEXT_LENGTH);

            // Inicializa a resposta
            StringBuilder respostaCompleta = new StringBuilder();

            // Enviar cada parte para o ChatGPT e receber as respostas
            for (String parte : partesTexto) {
                String respostaParte = chatGpt(parte);
                respostaCompleta.append(respostaParte).append("\n"); // Adiciona cada resposta à resposta completa
            }

            System.out.println("Resposta do GPT:");
            System.out.println(respostaCompleta.toString());

            // Obter o número de páginas do PDF
            int numeroPaginas = contarPaginasPDF(filePath);
            System.out.println("Número de páginas do PDF: " + numeroPaginas);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String extrairTextoPrimeirasPaginas(String filePath, int numPaginas) throws IOException {
        PDDocument document = PDDocument.load(new File(filePath));
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(1);
        stripper.setEndPage(numPaginas);
        String texto = stripper.getText(document);
        document.close();
        return texto;
    }

    public static String extrairTextoIntervaloPaginas(String filePath, int paginaInicial, int paginaFinal) throws IOException {
        PDDocument document = PDDocument.load(new File(filePath));
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(paginaInicial);
        stripper.setEndPage(paginaFinal);
        String texto = stripper.getText(document);
        document.close();
        return texto;
    }

    public static int contarPaginasPDF(String filePath) throws IOException {
        PDDocument document = PDDocument.load(new File(filePath));
        int numeroPaginas = document.getNumberOfPages();
        document.close();
        return numeroPaginas;
    }

    public static List<String> dividirTexto(String texto, int maxLength) {
        List<String> partes = new ArrayList<>();
        int length = texto.length();
        for (int i = 0; i < length; i += maxLength) {
            partes.add(texto.substring(i, Math.min(length, i + maxLength)));
        }
        return partes;
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
            messageJson.addProperty("content", message + " em português e quero tudo explicado");
            messagesArray.add(messageJson);

            requestJson.add("messages", messagesArray);

            con.setDoOutput(true);
            OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream());
            writer.write(requestJson.toString());
            writer.flush();
            writer.close();

            int responseCode = con.getResponseCode();
            System.out.println("Response Code: " + responseCode);

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
}
