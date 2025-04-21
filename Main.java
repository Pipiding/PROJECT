//TIP 要<b>运行</b>代码，请按 <shortcut actionId="Run"/> 或
// 点击装订区域中的 <icon src="AllIcons.Actions.Execute"/> 图标。

import ui.FinanceTrackerUI;

import javax.swing.*;

/**
 * 程序入口类
 * 启动GUI应用程序
 */
public class Main {
    /**
     * 主方法
     * @param args 命令行参数（未使用）
     */
    public static void main(String[] args) {
        // 使用Swing事件分发线程保证线程安全
        SwingUtilities.invokeLater(() -> {
            FinanceTrackerUI app = new FinanceTrackerUI();
            app.setVisible(true); // 显示主窗口
        });
    }
}