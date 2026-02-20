package bidguard;

import com.bidguard.FilePair;
import com.bidguard.PairGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试配对生成器
 */
public class TestPairGenerator {
    
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("配对生成器测试");
        System.out.println("=".repeat(60));
        System.out.println();
        
        // 创建测试文件列表
        List<File> testFiles = new ArrayList<>();
        testFiles.add(new File("testfiles/文档A.pdf"));
        testFiles.add(new File("testfiles/文档B.pdf"));
        testFiles.add(new File("testfiles/文档C.pdf"));
        testFiles.add(new File("testfiles/文档D.pdf"));
        
        System.out.println("📁 输入文件列表 (" + testFiles.size() + " 个):");
        for (int i = 0; i < testFiles.size(); i++) {
            System.out.println("  [" + (i+1) + "] " + testFiles.get(i).getName());
        }
        System.out.println();
        
        // 计算预期配对数
        int expectedCount = PairGenerator.calculatePairCount(testFiles.size());
        System.out.println("📊 预期生成配对数: C(" + testFiles.size() + ",2) = " + expectedCount);
        System.out.println();
        
        // 生成配对
        List<FilePair> pairs = PairGenerator.generatePairs(testFiles);
        
        // 显示结果
        System.out.println("=".repeat(60));
        System.out.println("✅ 配对生成结果");
        System.out.println("=".repeat(60));
        System.out.println();
        System.out.println("实际生成配对数: " + pairs.size());
        System.out.println();
        
        // 显示所有配对
        System.out.println("配对详情:");
        System.out.println("-".repeat(60));
        for (int i = 0; i < pairs.size(); i++) {
            FilePair pair = pairs.get(i);
            System.out.printf("配对 #%-2d: %s\n", i + 1, pair.toString());
            System.out.printf("          ↳ [%s]\n", pair.getFileA().getName());
            System.out.printf("          ↳ [%s]\n", pair.getFileB().getName());
            if (i < pairs.size() - 1) {
                System.out.println();
            }
        }
        System.out.println("-".repeat(60));
        System.out.println();
        
        // 验证
        boolean testPassed = (pairs.size() == expectedCount);
        if (testPassed) {
            System.out.println("✅ 测试通过！配对数量正确。");
        } else {
            System.out.println("❌ 测试失败！预期 " + expectedCount + " 个配对，实际生成 " + pairs.size() + " 个。");
        }
        System.out.println();
        
        // 算法说明
        System.out.println("=".repeat(60));
        System.out.println("算法说明");
        System.out.println("=".repeat(60));
        System.out.println("核心逻辑：双层循环");
        System.out.println();
        System.out.println("for (int i = 0; i < files.size(); i++) {");
        System.out.println("    for (int j = i + 1; j < files.size(); j++) {");
        System.out.println("        pairs.add(new FilePair(files.get(i), files.get(j)));");
        System.out.println("    }");
        System.out.println("}");
        System.out.println();
        System.out.println("特点：");
        System.out.println("  • 避免重复（不会生成 A vs B 又生成 B vs A）");
        System.out.println("  • 避免自我配对（不会生成 A vs A）");
        System.out.println("  • 保证顺序（i < j）");
        System.out.println();
    }
}
