package com.bidguard;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        System.out.println("BidGuard engine ready");
        
        // 在事件分发线程中启动GUI
        SwingUtilities.invokeLater(() -> {
            new BidCheckerGUI().setVisible(true);
        });
    }
}
