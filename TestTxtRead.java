import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

public class TestTxtRead {
    public static void main(String[] args) throws Exception {
        String filePath = "testfiles\\T1_Original.txt（基准长文本）.txt";
        File file = new File(filePath);
        
        System.out.println("=== 测试文件读取 ===");
        System.out.println("文件路径: " + file.getAbsolutePath());
        System.out.println("文件存在: " + file.exists());
        System.out.println("文件大小(字节): " + file.length());
        
        // 方法1：不指定编码（旧代码）
        byte[] bytes1 = Files.readAllBytes(file.toPath());
        String text1 = new String(bytes1);
        System.out.println("\n【不指定编码】");
        System.out.println("字节数: " + bytes1.length);
        System.out.println("字符数: " + text1.length());
        System.out.println("前100字符: " + text1.substring(0, Math.min(100, text1.length())));
        
        // 方法2：指定UTF-8编码（新代码）
        byte[] bytes2 = Files.readAllBytes(file.toPath());
        String text2 = new String(bytes2, StandardCharsets.UTF_8);
        System.out.println("\n【指定UTF-8编码】");
        System.out.println("字节数: " + bytes2.length);
        System.out.println("字符数: " + text2.length());
        System.out.println("前100字符: " + text2.substring(0, Math.min(100, text2.length())));
    }
}
