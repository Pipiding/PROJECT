package com.myfinanceapp.dashboard;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据传输对象 (DTO)
 * 表示财务洞察仪表板的整体数据结构。
 * 此类用于将后端聚合的数据传输到前端展示。
 */
public class FinancialDashboardData {
    private String userId;
    private DashboardSummary mainPeriodSummary; // 主要选定时间段的汇总
    private DashboardSummary previousPeriodSummary; // 主要时间段前一期（用于趋势比较）的汇总
    private List<CategorySpending> categorySpendingList; // 主要时间段的分类支出列表
    private List<SavingsGoalProgress> savingsGoalsProgress; // 储蓄目标进度列表
    private List<String> financialTips; // 个性化财务提示/观察

    // Gson 需要无参构造函数
    public FinancialDashboardData() {
        this.categorySpendingList = new ArrayList<>();
        this.savingsGoalsProgress = new ArrayList<>();
        this.financialTips = new ArrayList<>();
    }

    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public DashboardSummary getMainPeriodSummary() { return mainPeriodSummary; }
    public void setMainPeriodSummary(DashboardSummary mainPeriodSummary) { this.mainPeriodSummary = mainPeriodSummary; }
    public DashboardSummary getPreviousPeriodSummary() { return previousPeriodSummary; }
    public void setPreviousPeriodSummary(DashboardSummary previousPeriodSummary) { this.previousPeriodSummary = previousPeriodSummary; }
    public List<CategorySpending> getCategorySpendingList() { return categorySpendingList; }
    public void setCategorySpendingList(List<CategorySpending> categorySpendingList) { this.categorySpendingList = categorySpendingList; }
    public List<SavingsGoalProgress> getSavingsGoalsProgress() { return savingsGoalsProgress; }
    public void setSavingsGoalsProgress(List<SavingsGoalProgress> savingsGoalsProgress) { this.savingsGoalsProgress = savingsGoalsProgress; }
    public List<String> getFinancialTips() { return financialTips; }
    public void setFinancialTips(List<String> financialTips) { this.financialTips = financialTips; }
}
    