package ui;

import models.Transaction;
import services.CSVHelper;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.List;

/**
 * 主界面类
 * 实现用户交互界面和业务逻辑整合
 */
public class FinanceTrackerUI extends JFrame {
    // 输入组件
    private final JTextField amountField = new JTextField(10);
    private final JComboBox<String> categoryCombo = new JComboBox<>(
            new String[]{"餐饮", "交通", "住房", "娱乐", "医疗"}); // 预定义类别
    private final JFormattedTextField dateField = new JFormattedTextField();
    private final JTextArea descriptionArea = new JTextArea(3, 20);

    /**
     * 构造函数：初始化界面
     */
    public FinanceTrackerUI() {
        super("AI个人财务助手");
        setupUI();
    }

    /**
     * 初始化界面组件和布局
     */
    private void setupUI() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null); // 居中显示

        // 主面板布局
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 输入面板（4行2列网格布局）
        JPanel inputPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        inputPanel.add(new JLabel("金额 (CNY):"));
        inputPanel.add(amountField);
        inputPanel.add(new JLabel("类别:"));
        inputPanel.add(categoryCombo);
        inputPanel.add(new JLabel("日期:"));
        setupDateField(); // 初始化日期输入组件
        inputPanel.add(dateField);
        inputPanel.add(new JLabel("描述:"));
        inputPanel.add(new JScrollPane(descriptionArea)); // 带滚动条的文本域

        // 按钮面板（流式布局居中）
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton saveButton = new JButton("保存交易");
        saveButton.addActionListener(this::saveTransaction); // 绑定保存事件
        JButton importButton = new JButton("导入CSV");
        importButton.addActionListener(this::importCSV); // 绑定导入事件
        buttonPanel.add(saveButton);
        buttonPanel.add(importButton);

        mainPanel.add(inputPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    /**
     * 配置日期输入字段
     * 使用本地化的日期格式（自动适配操作系统设置）
     */
    private void setupDateField() {
        dateField.setValue(LocalDate.now()); // 默认当天
        dateField.setColumns(10);
        // 设置日期格式工厂（自动处理不同地区的日期格式）
        dateField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(
                new javax.swing.text.DateFormatter(
                        java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT))));
    }

    /**
     * 保存交易事件处理
     * @param e 动作事件
     */
    private void saveTransaction(ActionEvent e) {
        try {
            Transaction transaction = validateInput(); // 输入验证
            CSVHelper.saveTransaction(transaction);     // 持久化存储
            JOptionPane.showMessageDialog(this, "交易保存成功!");
            clearForm(); // 清空表单
        } catch (Exception ex) {
            showErrorDialog(ex.getMessage()); // 错误提示
        }
    }

    /**
     * 输入数据验证
     * @return 有效的交易对象
     * @throws Exception 包含错误信息的验证异常
     */
    private Transaction validateInput() throws Exception {
        // 验证金额
        double amount;
        try {
            amount = Double.parseDouble(amountField.getText());
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            throw new Exception("金额必须为有效正数");
        }

        // 验证日期（兼容不同分隔符）
        LocalDate date;
        try {
            String dateStr = dateField.getText().replace("/", "-"); // 统一分隔符
            date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new Exception("日期格式应为YYYY-MM-DD");
        }

        // 验证描述
        String description = descriptionArea.getText().trim();
        if (description.isEmpty()) {
            throw new Exception("描述不能为空");
        }

        return new Transaction(
                UUID.randomUUID().toString(),
                amount,
                (String) categoryCombo.getSelectedItem(),
                date,
                description
        );
    }

    /**
     * CSV导入事件处理
     * @param e 动作事件
     */
    private void importCSV(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter( // 文件类型过滤器
                new javax.swing.filechooser.FileNameExtensionFilter("CSV文件", "csv"));

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try {
                List<Transaction> imported = CSVHelper.importTransactions(selectedFile);
                JOptionPane.showMessageDialog(this,
                        "成功导入 " + imported.size() + " 条有效交易",
                        "导入完成",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                showErrorDialog("文件读取失败: " + ex.getMessage());
            }
        }
    }

    /**
     * 清空输入表单
     */
    private void clearForm() {
        amountField.setText("");
        dateField.setValue(LocalDate.now()); // 重置为当天
        descriptionArea.setText("");
    }

    /**
     * 显示错误提示对话框
     * @param message 错误信息
     */
    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(this,
                message,
                "输入错误",
                JOptionPane.ERROR_MESSAGE);
    }
}