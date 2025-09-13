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
import java.util.*;

public class PDFReaderChunks {

    // Lista para armazenar os chunks
    private static List<Chunk> chunks = new ArrayList<>();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try {
            String filePath = "E:/PROGRAMAÇÃO JAVA FULL STACK/AulaIngles/English Conversation - Intermediate English (2).pdf";

            System.out.println("🔄 Extraindo conteúdo do PDF e criando chunks...");
            criarChunksPDF(filePath, 5); 

            System.out.println("✅ Chunks prontos para consulta rápida!");

            // Loop de perguntas
            while (true) {
                System.out.print("\nDigite sua pergunta sobre o PDF (ou 'sair' para encerrar): ");
                String pergunta = scanner.nextLine();

                if (pergunta.equalsIgnoreCase("sair")) {
                    System.out.println("Encerrando...");
                    break;
                }

                // Seleciona chunks relevantes usando busca simples por palavra-chave
                List<Chunk> chunksRelevantes = buscarChunks(pergunta);

                // Monta o prompt apenas com os chunks relevantes
                StringBuilder promptBuilder = new StringBuilder();
                promptBuilder.append("Aqui estão os trechos relevantes do PDF:\n");
                for (Chunk c : chunksRelevantes) {
                    promptBuilder.append("Páginas ").append(c.paginaInicio).append("-").append(c.paginaFim)
                            .append(":\n").append(c.texto).append("\n\n");
                }
                promptBuilder.append("Com base nesses trechos, responda à pergunta em português:\n").append(pergunta);

                String respostaGPT = chatGpt(promptBuilder.toString());
                System.out.println("\n=== RESPOSTA DA IA ===");
                System.out.println(respostaGPT);

                // Opcional: salvar resposta em PDF
                String arquivoResolvido = "E:/PROGRAMAÇÃO JAVA FULL STACK/AulaIngles/Resposta_" + pergunta.hashCode() + ".pdf";
                criarPDF(respostaGPT, arquivoResolvido);
                System.out.println("📄 Resposta salva em: " + arquivoResolvido);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Fecha o scanner para liberar o recurso
            scanner.close();
        }
    }

    // Classe para armazenar chunks
    static class Chunk {
        int paginaInicio;
        int paginaFim;
        String texto;
    }

    // Cria chunks do PDF
    public static void criarChunksPDF(String filePath, int paginasPorChunk) throws IOException {
        PDDocument document = PDDocument.load(new File(filePath));
        PDFTextStripper stripper = new PDFTextStripper();
        int totalPaginas = document.getNumberOfPages();

        for (int i = 1; i <= totalPaginas; i += paginasPorChunk) {
            stripper.setStartPage(i);
            stripper.setEndPage(Math.min(i + paginasPorChunk - 1, totalPaginas));
            String textoChunk = stripper.getText(document);

            Chunk chunk = new Chunk();
            chunk.paginaInicio = i;
            chunk.paginaFim = Math.min(i + paginasPorChunk - 1, totalPaginas);
            chunk.texto = textoChunk;
            chunks.add(chunk);
        }
        document.close();
    }

    // Busca chunks relevantes (palavras-chave simples)
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

        // Se nada for encontrado, retorna todos para não perder informação
        if (relevantes.isEmpty()) {
            return chunks;
        }
        return relevantes;
    }

    // Chamar ChatGPT
    public static String chatGpt(String message) {
        String url = "https://api.openai.com/v1/chat/completions";
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = "gpt-4o-mini"; // mais rápido e barato
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
                    response = messageObject.get("content").getAsString().trim();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }

    // Criar PDF com resposta
    public static void criarPDF(String texto, String caminhoArquivo) throws IOException {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);

        PDPageContentStream contentStream = new PDPageContentStream(document, page);
        contentStream.beginText();
        contentStream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
        contentStream.setLeading(14f);
        contentStream.newLineAtOffset(50, 750);

        float maxY = 50;
        float startY = 750;

        String[] linhas = texto.split("\n");
        for (String linha : linhas) {
            while (linha.length() > 95) {
                String subLinha = linha.substring(0, 95);
                contentStream.showText(subLinha);
                contentStream.newLine();
                linha = linha.substring(95);
                startY -= 14;
            }

            if (startY <= maxY) {
                contentStream.endText();
                contentStream.close();
                page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                contentStream = new PDPageContentStream(document, page);
                contentStream.beginText();
                contentStream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12);
                contentStream.setLeading(14f);
                contentStream.newLineAtOffset(50, 750);
                startY = 750;
            }

            contentStream.showText(linha);
            contentStream.newLine();
            startY -= 14;
        }

        contentStream.endText();
        contentStream.close();

        document.save(caminhoArquivo);
        document.close();
       
    }
}
