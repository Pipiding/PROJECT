package com.myfinanceapp.dashboard;

import com.google.gson.Gson; // Not strictly needed if using JsonUtil
import com.google.gson.GsonBuilder; // Not strictly needed if using JsonUtil
import com.myfinanceapp.planning.FinancialPlanningService; // Import Planning Service
import com.myfinanceapp.planning.SavingsGoal; // Import SavingsGoal class
import com.myfinanceapp.transaction.Transaction; // Import Transaction class
import com.myfinanceapp.transaction.TransactionService; // Import Transaction Service
import com.myfinanceapp.util.JsonUtil; // Using the shared Gson instance

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 财务洞察仪表板服务类。
 * 负责聚合用户的交易、预算和储蓄目标数据，生成用于仪表板展示的洞察。
 * 依赖于 TransactionService 和 FinancialPlanningService。
 */
public class DashboardService {

    private static final Logger LOGGER = Logger.getLogger(DashboardService.class.getName());
    // Unified date format for display in DTOs
    private static final String DISPLAY_DATE_FORMAT_PATTERN = "yyyy-MM-dd";
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat(DISPLAY_DATE_FORMAT_PATTERN);

    private final TransactionService transactionService;
    private final FinancialPlanningService financialPlanningService;
    // private final Gson gson; // Not needed if using JsonUtil for serialization in API layer

    /**
     * 构造函数。
     *
     * @param transactionService 交易服务实例。
     * @param financialPlanningService 金融规划服务实例。
     */
    public DashboardService(TransactionService transactionService, FinancialPlanningService financialPlanningService) {
        this.transactionService = transactionService;
        this.financialPlanningService = financialPlanningService;
        // SimpleDateFormat is not thread safe, so handle with synchronized blocks or ThreadLocal
    }

    /**
     * 获取指定用户在指定时间范围内的财务汇总数据。
     *
     * @param userId 用户 ID。
     * @param startDate 开始日期 (包含)。
     * @param endDate 结束日期 (包含)。
     * @param periodName 时间段名称 (例如: "当月", "上月")。
     * @return DashboardSummary 对象，包含该时间段的总收入、总支出和净储蓄。
     */
    private DashboardSummary getPeriodSummary(String userId, Date startDate, Date endDate, String periodName) {
        DashboardSummary summary = new DashboardSummary();
        summary.setPeriodName(periodName);
        // Format dates for DTO display
        synchronized (displayDateFormat) { // SimpleDateFormat non-thread-safe
            summary.setStartDate(displayDateFormat.format(startDate));
            summary.setEndDate(displayDateFormat.format(endDate));
        }


        try {
            List<Transaction> transactions = transactionService.getTransactionsByDateRange(userId, startDate, endDate);

            double totalIncome = 0;
            double totalExpense = 0;

            if (transactions != null) {
                for (Transaction transaction : transactions) {
                    if (transaction.isIncome()) {
                        totalIncome += transaction.getAmount();
                    } else {
                        // 支出金额在 TransactionService 中存储为负数，这里取绝对值作为总支出
                        totalExpense += Math.abs(transaction.getAmount());
                    }
                }
            }

            // Round values to 2 decimal places for display
            summary.setTotalIncome(Math.round(totalIncome * 100.0) / 100.0);
            summary.setTotalExpense(Math.round(totalExpense * 100.0) / 100.0);
            summary.setNetSavings(Math.round((totalIncome - totalExpense) * 100.0) / 100.0);

            LOGGER.log(Level.INFO, "用户 {0} 获取时间段 '{1}' ({2} - {3}) 的财务汇总：收入 {4}, 支出 {5}, 净储蓄 {6}",
                    new Object[]{userId, periodName, summary.getStartDate(), summary.getEndDate(), summary.getTotalIncome(), summary.getTotalExpense(), summary.getNetSavings()});

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "获取用户 {0} 时间段 '{1}' 的交易数据失败。", new Object[]{userId, periodName}, e);
            // Return a Summary with default values (0.0) on failure
            // The periodName and dates are already set.
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理用户 {0} 时间段 '{1}' 财务汇总时发生未知错误。", new Object[]{userId, periodName}, e);
            // Return a Summary with default values (0.0)
        }

        return summary;
    }

    /**
     * 计算指定时间范围内的各消费类别支出总额和百分比。
     *
     * @param userId 用户 ID。
     * @param startDate 开始日期 (包含)。
     * @param endDate 结束日期 (包含)。
     * @return CategorySpending 列表。
     */
    private List<CategorySpending> getCategorySpendingAnalysis(String userId, Date startDate, Date endDate) {
        List<CategorySpending> categorySpendingList = new ArrayList<>();

        try {
            List<Transaction> transactions = transactionService.getTransactionsByDateRange(userId, startDate, endDate);

            // Filter out expense transactions and group by category, summing up absolute amounts
            Map<String, Double> categoryTotalSpendingMap = transactions.stream()
                    .filter(txn -> !txn.isIncome()) // Filter out expense transactions
                    .collect(Collectors.groupingBy(
                            txn -> txn.getCategory() != null && !txn.getCategory().trim().isEmpty() ? txn.getCategory() : "未分类",
                            Collectors.summingDouble(txn -> Math.abs(txn.getAmount())) // Sum up absolute amounts
                    ));

            double totalExpenseForPeriod = categoryTotalSpendingMap.values().stream().mapToDouble(Double::doubleValue).sum();

            for (Map.Entry<String, Double> entry : categoryTotalSpendingMap.entrySet()) {
                CategorySpending cs = new CategorySpending();
                cs.setCategory(entry.getKey());
                // Round total spending for category
                cs.setTotalSpending(Math.round(entry.getValue() * 100.0) / 100.0);

                // Calculate percentage, avoid division by zero
                if (totalExpenseForPeriod > 0) {
                    // Round percentage to 1 decimal place
                    cs.setPercentageOfTotalExpense(Math.round((entry.getValue() / totalExpenseForPeriod) * 1000.0) / 10.0);
                } else {
                    cs.setPercentageOfTotalExpense(0.0);
                }
                categorySpendingList.add(cs);
            }

            // Sort by total spending in descending order
            categorySpendingList.sort(Comparator.comparingDouble(CategorySpending::getTotalSpending).reversed());


            LOGGER.log(Level.INFO, "用户 {0} 获取时间段 ({1} - {2}) 的分类支出分析，共 {3} 个类别。",
                    new Object[]{userId, displayDateFormat.format(startDate), displayDateFormat.format(endDate), categorySpendingList.size()});


        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "获取用户 {0} 时间段 ({1} - {2}) 的交易数据进行分类支出分析失败。",
                    new Object[]{userId, displayDateFormat.format(startDate), displayDateFormat.format(endDate), e});
            // Return empty list on failure
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理用户 {0} 时间段 ({1} - {2}) 分类支出分析时发生未知错误。",
                    new Object[]{userId, displayDateFormat.format(startDate), displayDateFormat.format(endDate), e});
            // Return empty list
        }

        return categorySpendingList;
    }

    /**
     * 获取指定用户所有储蓄目标的当前进度。
     *
     * @param userId 用户 ID。
     * @return SavingsGoalProgress 列表。
     */
    private List<SavingsGoalProgress> getSavingsGoalsProgress(String userId) {
        List<SavingsGoalProgress> progressList = new ArrayList<>();

        try {
            List<SavingsGoal> goals = financialPlanningService.getAllSavingsGoals(userId);

            if (goals != null) {
                for (SavingsGoal goal : goals) {
                    SavingsGoalProgress sgp = new SavingsGoalProgress();
                    sgp.setGoalId(goal.getId());
                    sgp.setName(goal.getName());
                    sgp.setTargetAmount(goal.getTargetAmount());
                    sgp.setCurrentAmount(goal.getCurrentAmount());
                    sgp.setStatus(goal.getStatus());
                    sgp.setTargetDate(goal.getTargetDate()); // Target date is already yyyy-MM-dd string

                    // Calculate progress percentage, avoid division by zero
                    if (goal.getTargetAmount() > 0) {
                        sgp.setProgressPercentage((goal.getCurrentAmount() / goal.getTargetAmount()) * 100.0);
                        // Ensure percentage does not exceed 100%
                        if (sgp.getProgressPercentage() > 100.0) {
                            sgp.setProgressPercentage(100.0);
                        }
                        // Round percentage to 1 decimal place
                        sgp.setProgressPercentage(Math.round(sgp.getProgressPercentage() * 10.0) / 10.0);
                    } else {
                        sgp.setProgressPercentage(0.0); // Progress is 0 if target amount is zero or negative
                    }
                    progressList.add(sgp);
                }
            }
            LOGGER.log(Level.INFO, "用户 {0} 获取储蓄目标进度，共 {1} 个目标。", new Object[]{userId, progressList.size()});

        } catch (Exception e) { // FinancialPlanningService method might throw exception
            LOGGER.log(Level.SEVERE, "获取用户 {0} 的储蓄目标失败。", new Object[]{userId}, e);
            // Return empty list on failure
        }

        return progressList;
    }

    /**
     * 生成基于财务数据的个性化提示。
     * 这是一个简化的示例，可以根据更复杂的规则或 AI 分析进行扩展。
     *
     * @param mainSummary 主要时间段的汇总数据。
     * @param previousSummary 上一个时间段的汇总数据。
     * @param categorySpendingList 分类支出列表。
     * @param goalsProgress 储蓄目标进度列表。
     * @return 提示信息列表。
     */
    private List<String> generateFinancialTips(DashboardSummary mainSummary, DashboardSummary previousSummary,
                                               List<CategorySpending> categorySpendingList, List<SavingsGoalProgress> goalsProgress) {
        List<String> tips = new ArrayList<>();

        if (mainSummary == null || previousSummary == null) {
            tips.add("暂无足够的历史数据生成详细的财务提示。请记录更多收支信息。");
            return tips;
        }

        // 示例提示：基于净储蓄趋势
        double currentNet = mainSummary.getNetSavings();
        double previousNet = previousSummary.getNetSavings();

        if (currentNet > previousNet) {
            if (previousNet < 0 && currentNet >= 0) {
                tips.add(String.format("恭喜！与上一期相比，您的财务状况有所改善，从赤字转为盈余！"));
            } else if (previousNet < 0 && currentNet < 0) {
                tips.add(String.format("与上一期相比，您的赤字有所减少。继续努力！"));
            }
            else { // both positive or previous 0
                tips.add(String.format("干得漂亮！与上一期相比，您的净储蓄增加了 %.2f 元。继续保持良好的储蓄习惯！", currentNet - previousNet));
            }
        } else if (currentNet < previousNet) {
            if (currentNet < 0 && previousNet >= 0) {
                tips.add(String.format("本期财务状况较上一期有所下降，出现赤字。请回顾支出明细，找出原因并尝试优化。"));
            } else if (currentNet < 0 && previousNet < 0) {
                tips.add(String.format("本期赤字较上一期有所增加。请检查是否有计划外的支出。"));
            }
            else { // both positive or current 0
                tips.add(String.format("本期净储蓄比上一期减少了 %.2f 元。请检查是否有计划外的支出导致储蓄下降。", previousNet - currentNet));
            }
        } else if (currentNet == previousNet) {
            tips.add("本期收支与上一期基本持平。");
        }

        // 示例提示：基于高额支出类别
        if (categorySpendingList != null && !categorySpendingList.isEmpty()) {
            // Find the top 1-3 spending categories
            List<CategorySpending> topSpendings = categorySpendingList.stream()
                    .sorted(Comparator.comparingDouble(CategorySpending::getTotalSpending).reversed())
                    .limit(3) // Get top 3
                    .collect(Collectors.toList());

            if (!topSpendings.isEmpty() && topSpendings.get(0).getTotalSpending() > 0) {
                String topCategories = topSpendings.stream()
                        .map(cs -> String.format("'%s' (%.1f%%)", cs.getCategory(), cs.getPercentageOfTotalExpense()))
                        .collect(Collectors.joining("、"));
                tips.add(String.format("本期支出较高的类别是 %s。您可以评估这些支出是否合理或有无节省空间。", topCategories));

                // Find categories with exceptionally high percentage (e.g., over 40% or 50%)
                List<CategorySpending> criticalHighPercentageSpending = categorySpendingList.stream()
                        .filter(cs -> cs.getPercentageOfTotalExpense() >= 40.0)
                        .collect(Collectors.toList());

                if (!criticalHighPercentageSpending.isEmpty()) {
                    String highSpendingCategories = criticalHighPercentageSpending.stream()
                            .map(cs -> String.format("'%s' (%.1f%%)", cs.getCategory(), cs.getPercentageOfTotalExpense()))
                            .collect(Collectors.joining("、"));
                    tips.add(String.format("重要提示：您的 %s 支出占比非常高，这可能是影响您财务状况的关键因素，请务必重点关注和控制。", highSpendingCategories));
                }
            }
        }

        // 示例提示：基于储蓄目标进度
        if (goalsProgress != null && !goalsProgress.isEmpty()) {
            List<SavingsGoalProgress> activeGoals = goalsProgress.stream()
                    .filter(g -> "Active".equalsIgnoreCase(g.getStatus()))
                    .collect(Collectors.toList());
            List<SavingsGoalProgress> achievedGoals = goalsProgress.stream()
                    .filter(g -> "Achieved".equalsIgnoreCase(g.getStatus()))
                    .collect(Collectors.toList());


            if (!activeGoals.isEmpty()) {
                tips.add(String.format("您当前有 %d 个活跃的储蓄目标。", activeGoals.size()));

                // Identify goals significantly behind schedule (simple check: less than 50% progress and target date within next 6 months)
                Calendar today = Calendar.getInstance();
                Calendar targetDateCal = Calendar.getInstance();
                Calendar sixMonthsLater = (Calendar) today.clone();
                sixMonthsLater.add(Calendar.MONTH, 6);


                List<SavingsGoalProgress> behindScheduleGoals = activeGoals.stream()
                        .filter(g -> {
                            try {
                                // Assuming target date string is parseable by default date format
                                synchronized (displayDateFormat) { // SimpleDateFormat non-thread-safe
                                    targetDateCal.setTime(displayDateFormat.parse(g.getTargetDate()));
                                }
                                // Check if target date is within next 6 months AND progress is less than 50%
                                return targetDateCal.after(today) && !targetDateCal.after(sixMonthsLater) && g.getProgressPercentage() < 50.0;
                            } catch (ParseException e) {
                                LOGGER.log(Level.WARNING, "解析储蓄目标日期失败: " + g.getTargetDate(), e);
                                return false; // Cannot determine if behind schedule
                            }
                        })
                        .collect(Collectors.toList());

                if (!behindScheduleGoals.isEmpty()) {
                    String behindGoalsNames = behindScheduleGoals.stream().map(SavingsGoalProgress::getName).collect(Collectors.joining("、"));
                    tips.add(String.format("提醒：您的储蓄目标 '%s' 可能落后于计划，距离目标日期不远但进度较低，请考虑增加储蓄额度或调整目标。", behindGoalsNames));
                } else {
                    tips.add("您的储蓄目标进展顺利！请继续保持。");
                }

            } else {
                tips.add("您当前没有设置活跃的储蓄目标。设定明确的储蓄目标有助于更好地管理资金。");
            }

            if (!achievedGoals.isEmpty()) {
                tips.add(String.format("恭喜！您已达成 %d 个储蓄目标！", achievedGoals.size()));
            }
        }

        // TODO: 可根据预算设置情况、特定节日临近等添加更多中国文化相关的财务提示
        tips.add("为了更精确地掌握财务状况，建议您定期分类和核对交易记录。");

        return tips;
    }


    /**
     * 获取指定用户在指定时间范围内的所有仪表板数据。
     *
     * @param userId 用户 ID。
     * @param mainPeriodStartDate 主要时间段开始日期。
     * @param mainPeriodEndDate 主要时间段结束日期。
     * @param mainPeriodName 主要时间段名称 (例如: "当月")。
     * @param previousPeriodStartDate 前一期时间段开始日期 (用于趋势分析)。
     * @param previousPeriodEndDate 前一期时间段结束日期 (用于趋势分析)。
     * @param previousPeriodName 前一期时间段名称 (例如: "上月")。
     * @return FinancialDashboardData 对象，包含所有聚合数据。
     */
    private FinancialDashboardData getFinancialDashboardData(String userId,
                                                             Date mainPeriodStartDate, Date mainPeriodEndDate, String mainPeriodName,
                                                             Date previousPeriodStartDate, Date previousPeriodEndDate, String previousPeriodName) {
        FinancialDashboardData dashboardData = new FinancialDashboardData();
        dashboardData.setUserId(userId);

        LOGGER.log(Level.INFO, "开始获取用户 {0} 的仪表板数据。", userId);

        // 1. 获取主要时间段的汇总数据
        dashboardData.setMainPeriodSummary(getPeriodSummary(userId, mainPeriodStartDate, mainPeriodEndDate, mainPeriodName));

        // 2. 获取前一期时间段的汇总数据 (用于趋势分析)
        dashboardData.setPreviousPeriodSummary(getPeriodSummary(userId, previousPeriodStartDate, previousPeriodEndDate, previousPeriodName));

        // 3. 获取主要时间段的分类支出分析
        dashboardData.setCategorySpendingList(getCategorySpendingAnalysis(userId, mainPeriodStartDate, mainPeriodEndDate));

        // 4. 获取储蓄目标进度
        dashboardData.setSavingsGoalsProgress(getSavingsGoalsProgress(userId));

        // 5. 生成财务提示 (基于已获取的数据)
        dashboardData.setFinancialTips(generateFinancialTips(
                dashboardData.getMainPeriodSummary(),
                dashboardData.getPreviousPeriodSummary(),
                dashboardData.getCategorySpendingList(),
                dashboardData.getSavingsGoalsProgress()
        ));

        LOGGER.log(Level.INFO, "用户 {0} 的仪表板数据获取完成。", userId);
        return dashboardData;
    }


    /**
     * 获取指定用户当月的财务仪表板数据 (包含与上月的比较)。
     *
     * @param userId 用户 ID。
     * @return FinancialDashboardData 对象。
     */
    public FinancialDashboardData getCurrentMonthDashboardData(String userId) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // 计算当月范围
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Date currentMonthStartDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 1);
        calendar.add(Calendar.SECOND, -1); // Last second of last day of current month
        Date currentMonthEndDate = calendar.getTime();

        // Calculate last month range
        calendar.setTime(currentMonthStartDate); // Go back to the 1st of current month
        calendar.add(Calendar.MONTH, -1); // Last month
        Date lastMonthStartDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 1); // Go back to the 1st of current month
        calendar.add(Calendar.SECOND, -1); // Last second of last day of last month
        Date lastMonthEndDate = calendar.getTime();

        LOGGER.log(Level.INFO, "准备获取用户 {0} 当月 ({1} - {2}) 及上月 ({3} - {4}) 的仪表板数据。",
                new Object[]{userId, displayDateFormat.format(currentMonthStartDate), displayDateFormat.format(currentMonthEndDate),
                        displayDateFormat.format(lastMonthStartDate), displayDateFormat.format(lastMonthEndDate)});


        return getFinancialDashboardData(userId,
                currentMonthStartDate, currentMonthEndDate, "当月",
                lastMonthStartDate, lastMonthEndDate, "上月");
    }

    /**
     * 获取指定用户上月的财务仪表板数据 (包含与上上月的比较)。
     *
     * @param userId 用户 ID。
     * @return FinancialDashboardData 对象。
     */
    public FinancialDashboardData getLastMonthDashboardData(String userId) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // Calculate last month range
        calendar.add(Calendar.MONTH, -1); // Last month
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Date lastMonthStartDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 1);
        calendar.add(Calendar.SECOND, -1); // Last second of last day of last month
        Date lastMonthEndDate = calendar.getTime();

        // Calculate two months ago range
        calendar.setTime(lastMonthStartDate); // Go back to the 1st of last month
        calendar.add(Calendar.MONTH, -1); // Two months ago
        Date twoMonthsAgoStartDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 1); // Go back to the 1st of last month
        calendar.add(Calendar.SECOND, -1); // Last second of last day of two months ago
        Date twoMonthsAgoEndDate = calendar.getTime();

        LOGGER.log(Level.INFO, "准备获取用户 {0} 上月 ({1} - {2}) 及上上月 ({3} - {4}) 的仪表板数据。",
                new Object[]{userId, displayDateFormat.format(lastMonthStartDate), displayDateFormat.format(lastMonthEndDate),
                        displayDateFormat.format(twoMonthsAgoStartDate), displayDateFormat.format(twoMonthsAgoEndDate)});


        return getFinancialDashboardData(userId,
                lastMonthStartDate, lastMonthEndDate, "上月",
                twoMonthsAgoStartDate, twoMonthsAgoEndDate, "上上月");
    }


    /**
     * 获取指定用户本年度至今 (Year-to-Date) 的财务仪表板数据 (包含与去年同期的比较)。
     *
     * @param userId 用户 ID。
     * @return FinancialDashboardData 对象。
     */
    public FinancialDashboardData getYearToDateDashboardData(String userId) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // Calculate Year-to-Date range
        int currentYear = calendar.get(Calendar.YEAR);
        calendar.set(currentYear, Calendar.JANUARY, 1);
        Date yearToDateStartDate = calendar.getTime();
        Date yearToDateEndDate = new Date(); // Today

        // Calculate same period last year range (from Jan 1st last year to the equivalent date last year)
        Calendar lastYearCalendar = (Calendar) calendar.clone();
        lastYearCalendar.add(Calendar.YEAR, -1); // Move to last year
        Date lastYearSamePeriodStartDate = lastYearCalendar.getTime();

        lastYearCalendar.setTime(yearToDateEndDate); // Set to current end date (today)
        lastYearCalendar.add(Calendar.YEAR, -1); // Move to last year
        Date lastYearSamePeriodEndDate = lastYearCalendar.getTime();


        LOGGER.log(Level.INFO, "准备获取用户 {0} 本年度至今 ({1} - {2}) 及去年同期 ({3} - {4}) 的仪表板数据。",
                new Object[]{userId, displayDateFormat.format(yearToDateStartDate), displayDateFormat.format(yearToDateEndDate),
                        displayDateFormat.format(lastYearSamePeriodStartDate), displayDateFormat.format(lastYearSamePeriodEndDate)});


        return getFinancialDashboardData(userId,
                yearToDateStartDate, yearToDateEndDate, "本年度至今",
                lastYearSamePeriodStartDate, lastYearSamePeriodEndDate, "去年同期");
    }

}
    