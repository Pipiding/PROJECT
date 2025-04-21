
package analysis;

import models.Transaction;
import java.time.LocalDate;
import java.util.List;

/**
 * AI分析引擎
 * 功能：
 * 1. 线性回归预测预算
 * 2. 季节性检测
 * 3. 异常消费警告
 */
public class AIAnalyzer {
    /**
     * 预测下月预算（线性回归算法）
     * @param history 最近6个月的历史数据
     * @return 预测预算值
     */
    public static <Transaction> double predictBudget(List<Transaction> history) {
        // 数据准备
        int n = history.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for(int i=0; i<n; i++) {
            double x = i+1; // 时间序列
            double y = history.get(i).amount();
            sumX += x;
            sumY += y;
            sumXY += x*y;
            sumX2 += x*x;
        }

        // 计算回归系数
        double slope = (n*sumXY - sumX*sumY)/(n*sumX2 - sumX*sumX);
        double intercept = (sumY - slope*sumX)/n;

        // 预测下个月值
        return slope*(n+1) + intercept;
    }

    /**
     * 生成预算建议报告
     * @param currentMonth 当前月份支出
     * @param predicted 预测值
     * @return 建议文本
     */
    public static String generateAdvice(double currentMonth, double predicted) {
        double diff = predicted - currentMonth;
        StringBuilder advice = new StringBuilder("AI预算建议：\n");

        if(diff > currentMonth*0.2) {
            advice.append("检测到消费增长趋势 (").append(String.format("%.2f", diff))
                    .append(")，建议：\n• 检查非必要开支\n• 设置消费提醒");
        } else if(diff < -currentMonth*0.1) {
            advice.append("检测到消费下降趋势，建议：\n• 保持当前消费模式\n• 考虑增加储蓄比例");
        } else {
            advice.append("消费模式稳定，建议：\n• 维持现有预算\n• 定期检查分类支出");
        }

        return advice.toString();
    }
}