import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class RealisticVideoGenerator {

    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_TIMEOUT = 90000; // 90 segundos
    private static final int BACKOFF_MULTIPLIER = 2;

    public static void main(String[] args) {
        try {
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                System.out.println("Erro: Variável de ambiente OPENAI_API_KEY não encontrada.");
                System.out.println("Por favor, defina a variável: export OPENAI_API_KEY=sua_chave_aqui");
                return;
            }

            // Prompts realistas para diferentes cenas - CORRIGIDO
            List<String> realisticPrompts = Arrays.asList(
                "Fotografia realista de um gato robótico futurista decolando de um telhado em uma cidade à noite, " +
                "luzes neon, chuva fina, reflexos molhados nas ruas, estilo cinematográfico, 8K, ultra realista",
                
                "Fotografia realista do gato robótico voando sobre arranha-céus futuristas, " +
                "nuvens baixas, luzes da cidade cintilando através da neblina, estilo de filme de ficção científica",
                
                "Fotografia realista do gato robótico em voo sobre um rio urbano, " +
                "reflexos das luzes na água, arquitetura futurista, detalhes metálicos realistas",
                
                "Fotografia realista do gato robótico pairando sobre uma praça central, " +
                "pessoas olhando para cima, expressões de admiração, iluminação noturna dramática",
                
                "Fotografia realista do gato robótico aterrissando suavemente em um heliporto, " +
                "luzes piscando, detalhes mecânicos visíveis, atmosfera úmida noturna"
            );

            System.out.println("Gerando " + realisticPrompts.size() + " frames realistas com a OpenAI...");

            // Gerar frames em memória
            List<BufferedImage> frames = generateRealisticFrames(apiKey, realisticPrompts);
            
            if (frames.isEmpty()) {
                System.out.println("Nenhum frame foi gerado com sucesso.");
                return;
            }
            
            System.out.println(frames.size() + " frames realistas gerados com sucesso!");
            System.out.println("Criando vídeo realista...");

            // Criar vídeo em memória
            createAndDisplayRealisticVideo(frames, 1); // 1 FPS para melhor visualização

        } catch (Exception e) {
            System.err.println("Erro durante a execução: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static List<BufferedImage> generateRealisticFrames(String apiKey, List<String> prompts) throws Exception {
        List<BufferedImage> frames = new ArrayList<>();
        
        for (int i = 0; i < prompts.size(); i++) {
            System.out.println("Gerando frame realista " + (i + 1) + "/" + prompts.size() + "...");
            
            boolean success = false;
            int retryCount = 0;
            int currentTimeout = INITIAL_TIMEOUT;
            
            while (!success && retryCount < MAX_RETRIES) {
                try {
                    BufferedImage image = generateRealisticImage(apiKey, prompts.get(i), currentTimeout);
                    frames.add(image);
                    success = true;
                    
                    System.out.println("Frame realista " + (i + 1) + " gerado com sucesso!");
                    
                } catch (Exception e) {
                    retryCount++;
                    if (retryCount >= MAX_RETRIES) {
                        System.err.println("Falha após " + MAX_RETRIES + " tentativas para o frame " + (i + 1));
                        System.err.println("Erro: " + e.getMessage());
                        break;
                    }
                    
                    System.out.println("Tentativa " + retryCount + "/" + MAX_RETRIES + " falhou. Tentando novamente em " + (retryCount * 3) + " segundos...");
                    
                    // Backoff exponencial
                    Thread.sleep(retryCount * 3000);
                    currentTimeout *= BACKOFF_MULTIPLIER;
                }
            }
            
            if (success) {
                Thread.sleep(3000); // 3 segundos entre frames
            }
        }
        
        return frames;
    }

    public static BufferedImage generateRealisticImage(String apiKey, String prompt, int timeout) throws IOException {
        URL url = new URL("https://api.openai.com/v1/images/generations");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);

        JsonObject json = new JsonObject();
        json.addProperty("model", "dall-e-3");
        json.addProperty("prompt", prompt + " [ESTILO: Fotorealismo, Cinematográfico, 8K, Ultra Detalhado]");
        json.addProperty("size", "1024x1024");
        json.addProperty("quality", "hd"); // Alta qualidade para mais realismo
        json.addProperty("n", 1);
        json.addProperty("response_format", "b64_json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.toString().getBytes());
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            InputStream errorStream = conn.getErrorStream();
            String errorMessage = "Sem detalhes";
            if (errorStream != null) {
                errorMessage = readStream(errorStream);
            }
            throw new IOException("Erro na API OpenAI: " + responseCode + " - " + errorMessage);
        }

        String responseContent = readStream(conn.getInputStream());
        JsonObject response = JsonParser.parseString(responseContent).getAsJsonObject();
        JsonArray data = response.getAsJsonArray("data");
        
        if (data.size() == 0) {
            throw new IOException("Nenhuma imagem retornada pela API");
        }

        String base64Img = data.get(0).getAsJsonObject().get("b64_json").getAsString();

        byte[] imageBytes = Base64.getDecoder().decode(base64Img);
        try (InputStream imageStream = new ByteArrayInputStream(imageBytes)) {
            BufferedImage image = ImageIO.read(imageStream);
            if (image == null) {
                throw new IOException("Falha ao decodificar a imagem");
            }
            return image;
        }
    }

    private static String readStream(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "Stream nulo";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            return content.toString();
        }
    }

    public static void createAndDisplayRealisticVideo(List<BufferedImage> frames, int fps) {
        if (frames == null || frames.isEmpty()) {
            System.out.println("Nenhum frame para exibir.");
            return;
        }

        JFrame frame = new JFrame("Vídeo Realista - Gato Robótico Futurista");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);
        
        JPanel videoPanel = new JPanel() {
            private int currentFrame = 0;
            private long lastFrameTime = 0;
            private long frameInterval = 1000 / fps;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastFrameTime >= frameInterval) {
                    currentFrame = (currentFrame + 1) % frames.size();
                    lastFrameTime = currentTime;
                }
                
                BufferedImage currentImage = frames.get(currentFrame);
                int panelWidth = getWidth();
                int panelHeight = getHeight();
                
                // Manter proporção da imagem
                double scale = Math.min((double) panelWidth / currentImage.getWidth(), 
                                      (double) panelHeight / currentImage.getHeight());
                int scaledWidth = (int) (currentImage.getWidth() * scale);
                int scaledHeight = (int) (currentImage.getHeight() * scale);
                int x = (panelWidth - scaledWidth) / 2;
                int y = (panelHeight - scaledHeight) / 2;
                
                g2d.drawImage(currentImage, x, y, scaledWidth, scaledHeight, this);
                
                // Adicionar overlay informativo
                g2d.setColor(new Color(255, 255, 255, 180));
                g2d.fillRect(10, 10, 250, 40);
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("Arial", Font.BOLD, 12));
                g2d.drawString("Frame: " + (currentFrame + 1) + "/" + frames.size(), 20, 30);
                g2d.drawString("FPS: " + fps, 20, 50);
                
                repaint();
            }
        };
        
        frame.add(videoPanel);
        frame.setVisible(true);
        
        // Criar página HTML para exibição no navegador
        createBrowserVideo(frames, fps);
    }

    private static void createBrowserVideo(List<BufferedImage> frames, int fps) {
        try {
            String htmlContent = createRealisticHTMLPage(frames, fps);
            File tempHtmlFile = File.createTempFile("realistic_video", ".html");
            try (FileWriter writer = new FileWriter(tempHtmlFile)) {
                writer.write(htmlContent);
            }
            
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(tempHtmlFile.toURI());
                System.out.println("Abrindo vídeo realista no navegador...");
            }
            
            tempHtmlFile.deleteOnExit();
            
        } catch (Exception e) {
            System.out.println("Não foi possível abrir no navegador: " + e.getMessage());
            System.out.println("O vídeo está sendo exibido na janela do aplicativo.");
        }
    }

    private static String createRealisticHTMLPage(List<BufferedImage> frames, int fps) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Vídeo Realista</title>");
        html.append("<meta charset='UTF-8'>");
        html.append("<style>");
        html.append("body { margin: 0; background: #000; font-family: Arial, sans-serif; }");
        html.append(".container { text-align: center; padding: 20px; }");
        html.append("h1 { color: #fff; text-shadow: 2px 2px 4px rgba(0,0,0,0.5); margin-bottom: 20px; }");
        html.append(".video-container { position: relative; margin: 0 auto; max-width: 90vw; }");
        html.append("img { max-width: 100%; max-height: 80vh; border-radius: 10px; box-shadow: 0 0 20px rgba(255,255,255,0.1); }");
        html.append(".controls { margin-top: 20px; color: white; }");
        html.append("</style>");
        html.append("</head><body>");
        html.append("<div class='container'>");
        html.append("<h1>🎬 Vídeo Realista Gerado por IA</h1>");
        html.append("<div class='video-container'>");
        
        for (int i = 0; i < frames.size(); i++) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(frames.get(i), "png", baos);
                String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());
                
                html.append("<img id='frame").append(i).append("' ")
                    .append("src='data:image/png;base64,").append(base64Image).append("' ")
                    .append("style='display: ").append(i == 0 ? "block" : "none").append("; margin: 0 auto;'/>");
            } catch (IOException e) {
                System.err.println("Erro ao converter frame " + i + " para base64");
            }
        }
        
        html.append("</div>");
        html.append("<div class='controls'>");
        html.append("<p>Frames: ").append(frames.size()).append(" | FPS: ").append(fps).append(" | Qualidade: HD</p>");
        html.append("</div>");
        
        html.append("<script>");
        html.append("let currentFrame = 0;");
        html.append("const totalFrames = ").append(frames.size()).append(";");
        html.append("const frameInterval = ").append(1000 / fps).append(";");
        html.append("function animate() {");
        html.append("  document.getElementById('frame' + currentFrame).style.display = 'none';");
        html.append("  currentFrame = (currentFrame + 1) % totalFrames;");
        html.append("  document.getElementById('frame' + currentFrame).style.display = 'block';");
        html.append("  setTimeout(animate, frameInterval);");
        html.append("}");
        html.append("setTimeout(animate, frameInterval);");
        html.append("</script>");
        
        html.append("</div></body></html>");
        return html.toString();
    }
}