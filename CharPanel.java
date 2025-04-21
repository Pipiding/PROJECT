// ChartPanel.java
package ui.components;

import org.jfree.chart.*;
import org.jfree.data.general.DefaultPieDataset;
import services.TransactionService;
import javax.swing.*;
import java.util.Map;

/**
 * 支出分析图表面板
 * 功能：
 * 1. 生成饼图显示支出分类比例
 * 2. 支持动态刷新数据
 * 3. 导出图表为PNG
 */
public class ChartPanel extends JPanel {
    private JFreeChart chart;
    
    public ChartPanel() {
        setLayout(new BorderLayout());
        refreshChart();
    }

    /**
     * 刷新图表数据
     */
    public void refreshChart() {
        removeAll();
        
        // 获取分类支出数据
        Map<String, Double> data = TransactionService.getCategoryExpenses();
        
        // 创建数据集
        DefaultPieDataset dataset = new DefaultPieDataset();
        data.forEach((category, amount) -> 
            dataset.setValue(category, amount));
        
        // 创建图表
        chart = ChartFactory.createPieChart(
            "月度支出分析",
            dataset,
            true, true, false
        );
        
        // 样式优化
        chart.getTitle().setFont(new Font("微软雅黑", Font.BOLD, 16));
        
        // 添加到面板
        add(new ChartPanel(chart), BorderLayout.CENTER);
        
        // 添加导出按钮
        JButton exportBtn = new JButton("导出图表");
        exportBtn.addActionListener(e -> exportAsImage());
        add(exportBtn, BorderLayout.SOUTH);
        
        revalidate();
        repaint();
    }

    private void exportAsImage() {
        try {
            File file = new File("chart_"+System.currentTimeMillis()+".png");
            ChartUtils.saveChartAsPNG(file, chart, 800, 600);
            JOptionPane.showMessageDialog(this, "图表已保存至："+file.getAbsolutePath());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "导出失败："+e.getMessage(), 
                "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}