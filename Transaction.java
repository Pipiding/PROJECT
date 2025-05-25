package com.myfinanceapp.transaction;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 交易数据模型
 * 对应 transactions_YYYYMM.json 文件中的每个元素
 */
public class Transaction {
    private static final Logger LOGGER = Logger.getLogger(Transaction.class.getName());
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"; // ISO 8601 格式
    // SimpleDateFormat 不是线程安全的，在需要时创建新实例或使用synchronized
    private static final SimpleDateFormat TRANSACTION_DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_PATTERN);

    static {
        TRANSACTION_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC")); // 确保解析ISO 8601 Z时区正确
    }

    private String transactionId; // 唯一标识符
    private String date; // 交易日期 (ISO 8601 格式字符串)
    private double amount; // 金额 (正数收入，负数支出)
    private String description; // 描述
    private String category; // 类别 (例如: Food, Transport)
    private boolean isIncome; // 是否为收入 (true: 收入, false: 支出)
    private String source; // 交易来源 (例如: 手动输入, CSV导入)
    private boolean aiClassified; // 是否由AI分类
    private boolean manualCorrection; // 是否经过用户手动更正分类
    private String notes; // 备注

    // Gson 需要无参构造函数
    public Transaction() {}

    // Getters and Setters

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isIncome() { return isIncome; }
    public void setIncome(boolean income) { isIncome = income; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isAiClassified() { return aiClassified; }
    public void setAiClassified(boolean aiClassified) { this.aiClassified = aiClassified; }
    public boolean isManualCorrection() { return manualCorrection; }
    public void setManualCorrection(boolean manualCorrection) { this.manualCorrection = manualCorrection; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    /**
     * 解析 ISO 8601 格式的日期字符串到 Date 对象
     * @param dateString ISO 8601 格式日期字符串
     * @return Date 对象，解析失败返回 null
     */
    public Date parseDate() {
        if (this.date == null || this.date.trim().isEmpty()) {
            return null;
        }
        try {
            // 使用 synchronized 块确保 SimpleDateFormat 的线程安全
            synchronized (TRANSACTION_DATE_FORMAT) {
                return TRANSACTION_DATE_FORMAT.parse(this.date);
            }
        } catch (ParseException e) {
            LOGGER.log(Level.WARNING, "解析交易日期字符串失败: " + this.date, e);
            return null;
        }
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", date='" + date + '\'' +
                ", amount=" + amount +
                ", description='" + description + '\'' +
                ", category='" + category + '\'' +
                ", isIncome=" + isIncome +
                ", source='" + source + '\'' +
                '}';
    }
}
    