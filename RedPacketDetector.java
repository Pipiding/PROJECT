// RedPacketDetector.java
package services;

import models.Transaction;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 红包交易处理器
 * 功能：
 * 1. 自动检测红包交易
 * 2. 特殊分类处理
 * 3. 生成红包报告
 */
public class RedPacketDetector {
    
    /**
     * 识别红包交易（基于描述关键词）
     * @param transactions 原始交易数据
     * @return 红包交易列表
     */
    public static List<Transaction> detectRedPackets(List<Transaction> transactions) {
        return transactions.stream()
            .filter(t -> t.description().matches(".*(红包|压岁钱|利是).*"))
            .map(t -> new Transaction(
                t.id(),
                t.amount(),
                "红包",  // 强制分类
                t.date(),
                t.description()
            ))
            .collect(Collectors.toList());
    }

    /**
     * 生成红包分析报告
     * @param redPackets 红包交易列表
     * @return 报告文本
     */
    public static String generateReport(List<Transaction> redPackets) {
        double total = redPackets.stream()
            .mapToDouble(Transaction::amount)
            .sum();
        
        long sendCount = redPackets.stream()
            .filter(t -> t.amount() < 0)
            .count();
        
        long receiveCount = redPackets.size() - sendCount;
        
        return String.format(
            "红包活动分析报告：\n" +
            "• 总交易次数：%d次\n" +
            "• 发送红包：%d次\n" +
            "• 接收红包：%d次\n" +
            "• 净收入：%.2f元",
            redPackets.size(), sendCount, receiveCount, total
        );
    }
}