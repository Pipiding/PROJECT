package com.myfinanceapp.planning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 月度预算建议数据结构。
 */
public class BudgetSuggestion {
    private Map<String, Double> categorySpendingSuggestions; // 各类别的建议支出金额 (正数)
    private double estimatedIncome; // 估算的总收入 (正数)
    private double totalSuggestedSpending; // 建议的总支出 (正数)
    private double suggestedSavings; // 建议的储蓄金额 (正数)
    private List<String> notes; // 包含针对用户的个性化财务建议和文化背景相关的提示

    // 构造函数，Getter 和 Setter

    public BudgetSuggestion() {
        this.categorySpendingSuggestions = new HashMap<>();
        this.notes = new ArrayList<>();
    }

    public Map<String, Double> getCategorySpendingSuggestions() { return categorySpendingSuggestions; }
    public void setCategorySpendingSuggestions(Map<String, Double> categorySpendingSuggestions) { this.categorySpendingSuggestions = categorySpendingSuggestions; }
    public double getEstimatedIncome() { return estimatedIncome; }
    public void setEstimatedIncome(double estimatedIncome) { this.estimatedIncome = estimatedIncome; }
    public double getTotalSuggestedSpending() { return totalSuggestedSpending; }
    public void setTotalSuggestedSpending(double totalSuggestedSpending) { this.totalSuggestedSpending = totalSuggestedSpending; }
    public double getSuggestedSavings() { return suggestedSavings; }
    public void setSuggestedSavings(double suggestedSavings) { this.suggestedSavings = suggestedSavings; }
    public List<String> getNotes() { return notes; }
    public void setNotes(List<String> notes) { this.notes = notes; }

    public void addNote(String note) {
        if (this.notes == null) {
            this.notes = new ArrayList<>();
        }
        this.notes.add(note);
    }
}
    