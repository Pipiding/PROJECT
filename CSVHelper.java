package services;

import models.Transaction;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * CSV文件操作工具类
 * 实现交易记录的持久化存储和导入功能
 */
public class CSVHelper {
    // 日期格式化器（ISO标准格式）
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    // 数据文件存储路径
    private static final Path DATA_FILE = Paths.get("transactions.csv");

    // 静态初始化块：确保数据文件存在
    static {
        initializeDataFile();
    }

    /**
     * 初始化数据文件
     * 如果文件不存在则创建并写入表头
     */
    private static void initializeDataFile() {
        try {
            if (!Files.exists(DATA_FILE)) {
                Files.createFile(DATA_FILE);  // 创建新文件
                Files.writeString(DATA_FILE, "ID,Amount,Category,Date,Description\n"); // 写入CSV表头
            }
        } catch (IOException e) {
            System.err.println("[错误] 初始化数据文件失败: " + e.getMessage());
        }
    }

    /**
     * 保存单条交易记录到CSV文件
     * @param transaction 要保存的交易记录
     * @throws IOException 文件操作异常
     */
    public static void saveTransaction(Transaction transaction) throws IOException {
        // 格式化为CSV行：ID,金额（保留两位小数）,类别,ISO格式日期,描述
        String record = String.format("%s,%.2f,%s,%s,%s\n",
                transaction.id(),
                transaction.amount(),
                transaction.category(),
                transaction.date().format(DATE_FORMATTER),
                transaction.description());

        // 追加写入文件
        Files.writeString(DATA_FILE, record, StandardOpenOption.APPEND);
    }

    /**
     * 从CSV文件导入交易记录
     * @param file 要导入的CSV文件
     * @return 成功导入的有效交易列表
     * @throws IOException 文件读取异常
     */
    public static List<Transaction> importTransactions(File file) throws IOException {
        List<Transaction> validTransactions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    // 使用正则表达式分割字段，处理带引号的字段
                    String[] parts = line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                    if (parts.length >= 4) {
                        // 解析金额（必须为有效正数）
                        double amount = Double.parseDouble(parts[0].trim());

                        // 处理类别（去除首尾空格）
                        String category = parts[1].trim();

                        // 解析日期（必须符合ISO格式）
                        LocalDate date = LocalDate.parse(parts[2].trim(), DATE_FORMATTER);

                        // 处理描述（去除首尾引号）
                        String description = parts[3].trim().replaceAll("^\"|\"$", "");

                        // 创建交易对象
                        Transaction t = new Transaction(
                                UUID.randomUUID().toString(), // 生成唯一ID
                                amount,
                                category,
                                date,
                                description
                        );

                        // 保存有效记录
                        if (t.isValid()) {
                            validTransactions.add(t);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[警告] 跳过无效数据行: " + line);
                }
            }
        }

        // 批量保存有效记录
        for (Transaction t : validTransactions) {
            saveTransaction(t);
        }

        return validTransactions;
    }
}