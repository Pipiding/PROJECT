package com.myfinanceapp.dashboard;

/**
 * 数据传输对象 (DTO)
 * 表示某个特定时间段的财务汇总数据。
 */
public class DashboardSummary {
    private String periodName; // 时间段名称 (例如: "当月", "上月", "年初至今")
    private String startDate; // 时间段开始日期 (格式化字符串 yyyy-MM-dd)
    private String endDate; // 时间段结束日期 (格式化字符串 yyyy-MM-dd)
    private double totalIncome; // 总收入
    private double totalExpense; // 总支出 (正数)
    private double netSavings; // 净储蓄 (收入 - 支出)

    // Gson 需要无参构造函数
    public DashboardSummary() {
    }

    // Getters and Setters
    public String getPeriodName() { return periodName; }
    public void setPeriodName(String periodName) { this.periodName = periodName; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public double getTotalIncome() { return totalIncome; }
    public void setTotalIncome(double totalIncome) { this.totalIncome = totalIncome; }
    public double getTotalExpense() { return totalExpense; }
    public void setTotalExpense(double totalExpense) { this.totalExpense = totalExpense; }
    public double getNetSavings() { return netSavings; }
    public void setNetSavings(double netSavings) { this.netSavings = netSavings; }
}
    