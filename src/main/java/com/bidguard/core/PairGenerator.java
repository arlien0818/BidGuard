package com.bidguard.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * 组合调度器（Pair Generator）
 * 
 * 功能：
 * - 从多个PDF文件列表中生成所有可能的配对组合
 * - 用于批量查重操作
 * 
 * 算法：
 * - 使用双层循环遍历，生成所有不重复的配对
 * - 例如：A,B,C,D → [A vs B], [A vs C], [A vs D], [B vs C], [B vs D], [C vs D]
 */
public class PairGenerator {
    private static final Logger LOGGER = Logger.getLogger(PairGenerator.class.getName());
    
    /**
     * 生成所有可能的文件配对组合
     * 
     * @param files PDF文件列表
     * @return 所有配对组合列表
     */
    public static List<FilePair> generatePairs(List<File> files) {
        List<FilePair> pairs = new ArrayList<>();
        
        if (files == null || files.size() < 2) {
            LOGGER.warning("文件列表为空或少于2个文件，无法生成配对");
            return pairs;
        }
        
        // 核心算法：双层循环
        for (int i = 0; i < files.size(); i++) {
            for (int j = i + 1; j < files.size(); j++) {
                FilePair pair = new FilePair(files.get(i), files.get(j));
                pairs.add(pair);
                LOGGER.info(() -> String.format("生成配对 #%d: %s", 
                    pairs.size(), pair.toString()));
            }
        }
        
        LOGGER.info(() -> String.format("总共生成 %d 个配对组合（从 %d 个文件）", 
            pairs.size(), files.size()));
        
        return pairs;
    }
    
    /**
     * 计算给定数量的文件可以生成多少个配对
     * 公式: C(n,2) = n * (n-1) / 2
     * 
     * @param fileCount 文件数量
     * @return 可生成的配对数量
     */
    public static int calculatePairCount(int fileCount) {
        if (fileCount < 2) {
            return 0;
        }
        return fileCount * (fileCount - 1) / 2;
    }
}
