package com.myfinanceapp.planning;

/**
 * 储蓄目标数据结构。
 * 对应 goals.json
 */
public class SavingsGoal {
    private String id; // UUID
    private String userId; // Should match the directory name
    private String name; // 目标名称
    private String description; // 可选，详细描述
    private double targetAmount; // 目标金额
    private double currentAmount; // 当前已存金额
    private String targetDate; // 目标日期，格式 yyyy-MM-dd
    private String createdDate; // 创建日期，格式 yyyy-MM-dd
    private String status; // 例如: "Active", "Achieved", "Cancelled"

    // Gson 需要无参构造函数
    public SavingsGoal() {
    }

    // Getters and Setters

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getTargetAmount() { return targetAmount; }
    public void setTargetAmount(double targetAmount) { this.targetAmount = targetAmount; }
    public double getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(double currentAmount) { this.currentAmount = currentAmount; }
    public String getTargetDate() { return targetDate; }
    public void setTargetDate(String targetDate) { this.targetDate = targetDate; }
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "SavingsGoal{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", name='" + name + '\'' +
                ", targetAmount=" + targetAmount +
                ", currentAmount=" + currentAmount +
                ", targetDate='" + targetDate + '\'' +
                ", createdDate='" + createdDate + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
    