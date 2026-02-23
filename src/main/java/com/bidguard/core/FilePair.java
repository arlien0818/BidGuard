package com.bidguard.core;

import java.io.File;

/**
 * 文件对 - 用于表示两个需要进行对比的PDF文件
 */
public class FilePair {
    private final File fileA;
    private final File fileB;
    
    public FilePair(File fileA, File fileB) {
        this.fileA = fileA;
        this.fileB = fileB;
    }
    
    public File getFileA() {
        return fileA;
    }
    
    public File getFileB() {
        return fileB;
    }
    
    @Override
    public String toString() {
        return String.format("[%s] vs [%s]", 
            fileA != null ? fileA.getName() : "null",
            fileB != null ? fileB.getName() : "null");
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        FilePair other = (FilePair) obj;
        return (fileA != null ? fileA.equals(other.fileA) : other.fileA == null) &&
               (fileB != null ? fileB.equals(other.fileB) : other.fileB == null);
    }
    
    @Override
    public int hashCode() {
        int result = fileA != null ? fileA.hashCode() : 0;
        result = 31 * result + (fileB != null ? fileB.hashCode() : 0);
        return result;
    }
}
