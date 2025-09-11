import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class VideoGeneratorOpenAI {

    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_TIMEOUT = 60000; // 60 segundos
    private static final int BACKOFF_MULTIPLIER = 2;

    public static void main(String[] args) {
        try {
            String apiKey = System.getenv("OPENAI_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                System.out.println("Erro: Variável de ambiente OPENAI_API_KEY não encontrada.");
                System.out.println("Por favor, defina a variável: export OPENAI_API_KEY=sua_chave_aqui");
                return;
            }

            // Prompt para geração das imagens
            String prompt = "Um gato robótico voando sobre uma cidade futurista, estilo animação, céu ao pôr do sol";
            
            // Número de frames para o vídeo (reduzido para teste)
            int numFrames = 5;
            
            System.out.println("Gerando " + numFrames + " frames com a OpenAI...");

            // Gerar frames em memória
            List<BufferedImage> frames = generateFramesInMemory(apiKey, prompt, numFrames);
            
            if (frames.isEmpty()) {
                System.out.println("Nenhum frame foi gerado com sucesso.");
                return;
            }
            
            System.out.println(frames.size() + " frames gerados com sucesso!");
            System.out.println("Criando vídeo em memória...");

            // Criar vídeo em memória e abrir no navegador
            createAndDisplayVideo(frames, 2); // 2 FPS

        } catch (Exception e) {
            System.err.println("Erro durante a execução: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static List<BufferedImage> generateFramesInMemory(String apiKey, String prompt, int numFrames) throws Exception {
        List<BufferedImage> frames = new ArrayList<>();
        
        for (int i = 0; i < numFrames; i++) {
            System.out.println("Gerando frame " + (i + 1) + "/" + numFrames + "...");
            
            boolean success = false;
            int retryCount = 0;
            int currentTimeout = INITIAL_TIMEOUT;
            
            while (!success && retryCount < MAX_RETRIES) {
                try {
                    // Gerar imagem com variação no prompt para frames diferentes
                    String variedPrompt = prompt + ", cena " + (i + 1) + " de " + numFrames;
                    BufferedImage image = generateImageWithOpenAI(apiKey, variedPrompt, currentTimeout);
                    frames.add(image);
                    success = true;
                    
                    System.out.println("Frame " + (i + 1) + " gerado com sucesso!");
                    
                } catch (Exception e) {
                    retryCount++;
                    if (retryCount >= MAX_RETRIES) {
                        System.err.println("Falha após " + MAX_RETRIES + " tentativas para o frame " + (i + 1));
                        System.err.println("Erro: " + e.getMessage());
                        // Continuar para o próximo frame em vez de parar completamente
                        break;
                    }
                    
                    System.out.println("Tentativa " + retryCount + "/" + MAX_RETRIES + " falhou. Tentando novamente em " + (retryCount * 2) + " segundos...");
                    System.out.println("Erro: " + e.getMessage());
                    
                    // Backoff exponencial
                    Thread.sleep(retryCount * 2000); // Espera 2, 4, 6 segundos
                    currentTimeout *= BACKOFF_MULTIPLIER; // Aumenta o timeout
                }
            }
            
            // Pequena pausa entre frames mesmo quando bem-sucedido
            if (success) {
                Thread.sleep(2000); // 2 segundos entre frames
            }
        }
        
        return frames;
    }

    public static BufferedImage generateImageWithOpenAI(String apiKey, String prompt, int timeout) throws IOException {
        URL url = new URL("https://api.openai.com/v1/images/generations");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);

        // Montar JSON para a API
        JsonObject json = new JsonObject();
        json.addProperty("model", "dall-e-3");
        json.addProperty("prompt", prompt);
        json.addProperty("size", "1024x1024");
        json.addProperty("quality", "standard");
        json.addProperty("n", 1);
        json.addProperty("response_format", "b64_json");

        // Enviar requisição
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

        // Ler resposta
        String responseContent = readStream(conn.getInputStream());
        JsonObject response = JsonParser.parseString(responseContent).getAsJsonObject();
        JsonArray data = response.getAsJsonArray("data");
        
        if (data.size() == 0) {
            throw new IOException("Nenhuma imagem retornada pela API");
        }

        String base64Img = data.get(0).getAsJsonObject().get("b64_json").getAsString();

        // Converter base64 para BufferedImage
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

    public static void createAndDisplayVideo(List<BufferedImage> frames, int fps) {
        if (frames == null || frames.isEmpty()) {
            System.out.println("Nenhum frame para exibir.");
            return;
        }

        // Criar uma interface gráfica para exibir o vídeo
        JFrame frame = new JFrame("Vídeo Gerado - Gato Robótico");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 800);
        
        JLabel statusLabel = new JLabel("Exibindo " + frames.size() + " frames a " + fps + " FPS", SwingConstants.CENTER);
        statusLabel.setForeground(Color.BLUE);
        
        JPanel videoPanel = new JPanel() {
            private int currentFrame = 0;
            private long lastFrameTime = 0;
            private long frameInterval = 1000 / fps; // ms por frame

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastFrameTime >= frameInterval) {
                    currentFrame = (currentFrame + 1) % frames.size();
                    lastFrameTime = currentTime;
                }
                
                BufferedImage currentImage = frames.get(currentFrame);
                // Redimensionar para caber no painel mantendo a proporção
                int panelWidth = getWidth();
                int panelHeight = getHeight();
                int imgWidth = currentImage.getWidth();
                int imgHeight = currentImage.getHeight();
                
                double scale = Math.min((double) panelWidth / imgWidth, (double) panelHeight / imgHeight);
                int scaledWidth = (int) (imgWidth * scale);
                int scaledHeight = (int) (imgHeight * scale);
                int x = (panelWidth - scaledWidth) / 2;
                int y = (panelHeight - scaledHeight) / 2;
                
                g.drawImage(currentImage, x, y, scaledWidth, scaledHeight, this);
                
                // Forçar repaint para animação contínua
                repaint();
            }
        };
        
        frame.setLayout(new BorderLayout());
        frame.add(statusLabel, BorderLayout.NORTH);
        frame.add(videoPanel, BorderLayout.CENTER);
        frame.setVisible(true);
        
        // Também oferecer opção de abrir em navegador web
        try {
            // Criar uma página HTML simples com as imagens
            String htmlContent = createHTMLPage(frames, fps);
            File tempHtmlFile = File.createTempFile("video", ".html");
            try (FileWriter writer = new FileWriter(tempHtmlFile)) {
                writer.write(htmlContent);
            }
            
            // Abrir no navegador padrão
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(tempHtmlFile.toURI());
                System.out.println("Abrindo vídeo no navegador...");
            }
            
            // O arquivo será deletado quando o programa terminar
            tempHtmlFile.deleteOnExit();
            
        } catch (Exception e) {
            System.out.println("Não foi possível abrir no navegador: " + e.getMessage());
            System.out.println("O vídeo está sendo exibido na janela do aplicativo.");
        }
    }

    private static String createHTMLPage(List<BufferedImage> frames, int fps) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Vídeo Gerado</title>");
        html.append("<style>");
        html.append("body { margin: 0; background: #000; font-family: Arial, sans-serif; }");
        html.append(".container { text-align: center; padding: 20px; }");
        html.append("h1 { color: white; margin-bottom: 20px; }");
        html.append("img { max-width: 90vw; max-height: 70vh; border: 2px solid #333; }");
        html.append("</style>");
        html.append("</head><body>");
        html.append("<div class='container'>");
        html.append("<h1>Vídeo Gerado - Gato Robótico</h1>");
        
        // Adicionar todas as imagens com animação CSS
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
        
        // JavaScript para animar os frames
        html.append("<script>");
        html.append("let currentFrame = 0;");
        html.append("const totalFrames = ").append(frames.size()).append(";");
        html.append("const frameRate = ").append(fps).append(";");
        html.append("const frameInterval = 1000 / frameRate;");
        html.append("function animateFrames() {");
        html.append("  document.getElementById('frame' + currentFrame).style.display = 'none';");
        html.append("  currentFrame = (currentFrame + 1) % totalFrames;");
        html.append("  document.getElementById('frame' + currentFrame).style.display = 'block';");
        html.append("  setTimeout(animateFrames, frameInterval);");
        html.append("}");
        html.append("setTimeout(animateFrames, frameInterval);");
        html.append("</script>");
        
        html.append("</div></body></html>");
        return html.toString();
    }
}