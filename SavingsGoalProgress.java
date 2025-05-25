package com.myfinanceapp.dashboard;

/**
 * 数据传输对象 (DTO)
 * 表示单个储蓄目标的当前进度。
 */
public class SavingsGoalProgress {
    private String goalId; // 目标唯一ID
    private String name; // 目标名称
    private double targetAmount; // 目标金额
    private double currentAmount; // 当前已存金额
    private double progressPercentage; // 进度百分比
    private String status; // 目标状态 (例如: "Active", "Achieved")
    private String targetDate; // 目标完成日期 (格式化字符串 yyyy-MM-dd)

    // Gson 需要无参构造函数
    public SavingsGoalProgress() {
    }

    // Getters and Setters
    public String getGoalId() { return goalId; }
    public void setGoalId(String goalId) { this.goalId = goalId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(double targetAmount) { this.targetAmount = targetAmount; }
    public double getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(double currentAmount) { this.currentAmount = currentAmount; }
    public double getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(double progressPercentage) { this.progressPercentage = progressPercentage; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTargetDate() { return targetDate; }
    public void setTargetDate(String targetDate) { this.targetDate = targetDate; }
}
    