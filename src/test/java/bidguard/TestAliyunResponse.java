package bidguard;

import com.bidguard.RecognizeCharacter;
import com.aliyun.ocr_api20210707.models.*;
import com.aliyun.teautil.models.RuntimeOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 测试类：获取阿里云OCR完整返回结构
 */
public class TestAliyunResponse {
    public static void main(String[] args) {
        try {
            // 读取测试PDF的第一页
            File pdfFile = new File("testfiles/test1.pdf");
            if (!pdfFile.exists()) {
                System.out.println("测试文件不存在: " + pdfFile.getAbsolutePath());
                return;
            }
            
            System.out.println("正在读取PDF文件: " + pdfFile.getName());
            
            // 渲染第一页为图片
            PDDocument document = PDDocument.load(pdfFile);
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, 200);
            document.close();
            
            System.out.println("已渲染第一页，图片尺寸: " + image.getWidth() + "x" + image.getHeight());
            
            // 转换为字节流
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();
            InputStream imageStream = new ByteArrayInputStream(imageBytes);
            
            // 创建阿里云客户端
            com.aliyun.ocr_api20210707.Client client = RecognizeCharacter.createClient();
            
            // 调用OCR API
            RecognizeAdvancedRequest request = new RecognizeAdvancedRequest();
            request.setBody(imageStream);
            request.setNeedRotate(true);
            request.setNeedSortPage(true);
            
            System.out.println("正在调用阿里云OCR API...");
            
            RuntimeOptions runtime = new RuntimeOptions();
            RecognizeAdvancedResponse response = client.recognizeAdvancedWithOptions(request, runtime);
            
            // 序列化为JSON
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String responseJson = gson.toJson(response);
            
            // 保存到文件
            File outputFile = new File("output/aliyun_ocr_response_structure.json");
            outputFile.getParentFile().mkdirs();
            Files.write(Paths.get(outputFile.getPath()), responseJson.getBytes("UTF-8"));
            
            System.out.println("\n" + "=".repeat(80));
            System.out.println("阿里云OCR完整返回结构已保存到:");
            System.out.println(outputFile.getAbsolutePath());
            System.out.println("=".repeat(80));
            
            // 同时打印到控制台
            System.out.println("\n返回结构预览:");
            System.out.println(responseJson);
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
