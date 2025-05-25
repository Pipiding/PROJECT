package com.myfinanceapp.planning;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder; // Not strictly needed if using JsonUtil
import com.google.gson.reflect.TypeToken;
import com.myfinanceapp.transaction.Transaction; // Import Transaction class
import com.myfinanceapp.transaction.TransactionService; // Import TransactionService
import com.myfinanceapp.util.JsonUtil; // Using the shared Gson instance

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute; // Needed for Posix permissions
import java.nio.file.attribute.PosixFilePermission; // Needed for Posix permissions
import java.nio.file.attribute.PosixFilePermissions; // Needed for Posix permissions
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 金融规划服务类，负责财务分析、预算建议和储蓄目标管理。
 * 此服务依赖于 {@link TransactionService} 来获取用户的交易数据，
 * 并使用文件系统存储储蓄目标数据。
 */
public class FinancialPlanningService {

    private static final Logger LOGGER = Logger.getLogger(FinancialPlanningService.class.getName());
    private static final String USER_DATA_DIR_NAME = "user_data";
    private static final String SAVINGS_GOALS_FILE = "goals.json"; // 储蓄目标文件，遵循 SvD7o
    private static final String DATE_FORMAT_GOAL = "yyyy-MM-dd"; // 储蓄目标日期格式，遵循 SvD7o 示例
    // Transaction date format pattern is defined in Transaction class, using Transaction.parseDate()

    private final Path baseDataDirectory;
    private final TransactionService transactionService; // 依赖注入
    private final Gson gson; // Using shared Gson instance
    private final SimpleDateFormat goalDateFormat = new SimpleDateFormat(DATE_FORMAT_GOAL);

    /**
     * 构造函数。
     *
     * @param baseDataDirectoryPath 应用数据存储的根目录路径。
     * @param transactionService 交易服务实例，用于获取交易数据。
     */
    public FinancialPlanningService(String baseDataDirectoryPath, TransactionService transactionService) {
        this.baseDataDirectory = Paths.get(baseDataDirectoryPath);
        this.transactionService = transactionService;
        this.gson = JsonUtil.gson; // Use the shared Gson
        // SimpleDateFormat is not thread-safe, but this instance is only used internally
        // within synchronized blocks or contexts where concurrency is not an issue.
    }

    // --- 月度预算建议功能 (US5 & US8) ---

    /**
     * 生成指定用户指定月份的月度预算建议。
     * 根据用户过去 3-6 个月的交易数据进行分析，并结合中国文化背景提供建议。
     *
     * @param userId 用户ID。
     * @param year 目标年份。
     * @param month 目标月份 (1-12)。
     * @return BudgetSuggestion 对象，包含预算建议和相关提示。如果分析失败或无数据，返回包含默认建议或提示的对象。
     */
    public BudgetSuggestion generateMonthlyBudgetSuggestion(String userId, int year, int month) {
        BudgetSuggestion suggestion = new BudgetSuggestion();
        List<Transaction> transactionsForAnalysis;
        int analysisMonths = 6; // 尝试分析过去6个月的数据

        try {
            // 计算分析的日期范围：目标月份前的过去 analysisMonths 个月
            Calendar endCal = Calendar.getInstance();
            endCal.set(year, month - 1, 1, 0, 0, 0); // 目标月份的第一天
            endCal.add(Calendar.SECOND, -1); // 目标月份前一个月的最后一天结束

            Calendar startCal = (Calendar) endCal.clone();
            startCal.add(Calendar.MONTH, -analysisMonths + 1); // 分析 periodMonths 个月的范围

            Date endDate = endCal.getTime();
            Date startDate = startCal.getTime();

            LOGGER.log(Level.INFO, "为用户 {0} 生成 {1}-{2} 预算建议，分析范围从 {3} 到 {4}",
                    new Object[]{userId, year, month, startDate, endDate});

            // Use TransactionService to get data for the date range
            transactionsForAnalysis = transactionService.getTransactionsByDateRange(userId, startDate, endDate);

            if (transactionsForAnalysis == null || transactionsForAnalysis.isEmpty()) {
                suggestion.addNote("暂无足够的历史交易数据（过去 " + analysisMonths + " 个月）进行详细分析，以下为通用建议。");
                // 提供一些默认建议或提示
                suggestion.setEstimatedIncome(0.0);
                suggestion.setTotalSuggestedSpending(0.0);
                suggestion.setSuggestedSavings(0.0);
                suggestion.addNote("请手动输入一些交易记录以帮助系统更好地了解您的收支情况。");
                addChinaCulturalNotes(suggestion, year, month); // 即使无数据，也提供文化背景提示
                return suggestion;
            }

            LOGGER.log(Level.INFO, "用户 {0} 在分析范围 {1} 到 {2} 内有 {3} 笔交易记录",
                    new Object[]{userId, startDate, endDate, transactionsForAnalysis.size()});


            // 1. 计算总收入和按类别汇总支出
            double totalIncome = 0;
            Map<String, Double> categoryTotalSpending = new HashMap<>();
            Set<String> uniqueMonths = new HashSet<>();

            for (Transaction transaction : transactionsForAnalysis) {
                Date txnDate = transaction.parseDate(); // Use helper method from Transaction
                if (txnDate == null) {
                    LOGGER.log(Level.WARNING, "跳过无效交易日期: {0}", transaction.getDate());
                    continue; // Skip invalid date
                }

                // 统计参与分析的独立月份数量
                Calendar txnCal = Calendar.getInstance();
                txnCal.setTime(txnDate);
                uniqueMonths.add(txnCal.get(Calendar.YEAR) + "-" + (txnCal.get(Calendar.MONTH) + 1));


                if (transaction.isIncome()) {
                    totalIncome += transaction.getAmount(); // 假设收入金额为正数
                } else {
                    // Ensure expense amount is negative for calculation consistency, then take absolute for sum
                    double expenseAmount = transaction.getAmount(); // This is already stored as negative
                    String category = transaction.getCategory() != null && !transaction.getCategory().trim().isEmpty() ? transaction.getCategory() : "未分类支出";
                    categoryTotalSpending.merge(category, expenseAmount, Double::sum); // Merge sums negative values
                }
            }

            int actualAnalysisMonths = uniqueMonths.size();
            if (actualAnalysisMonths == 0) { // If no valid transaction dates within the range
                suggestion.addNote("获取到交易记录但日期无效，无法进行详细分析，以下为通用建议。");
                suggestion.setEstimatedIncome(0.0);
                suggestion.setTotalSuggestedSpending(0.0);
                suggestion.setSuggestedSavings(0.0);
                addChinaCulturalNotes(suggestion, year, month);
                return suggestion;
            }


            // 2. 计算平均月收入和支出
            suggestion.setEstimatedIncome(totalIncome / actualAnalysisMonths);

            Map<String, Double> categoryAvgSpending = new HashMap<>();
            double totalAvgSpending = 0;
            for (Map.Entry<String, Double> entry : categoryTotalSpending.entrySet()) {
                // 支出金额为负，取绝对值计算平均支出 (and round to 2 decimal places)
                double avgSpending = Math.round(Math.abs(entry.getValue()) / actualAnalysisMonths * 100.0) / 100.0;
                categoryAvgSpending.put(entry.getKey(), avgSpending);
                totalAvgSpending += avgSpending;
            }

            suggestion.setCategorySpendingSuggestions(categoryAvgSpending);
            suggestion.setTotalSuggestedSpending(Math.round(totalAvgSpending * 100.0) / 100.0); // Round total spending

            // 3. 计算建议储蓄 (中国文化背景适应)
            // 默认建议税后收入的 20-30%。此处简化为总收入。
            double suggestedSavingsRate = 0.25; // 例如，取 25%
            suggestion.setSuggestedSavings(Math.round(suggestion.getEstimatedIncome() * suggestedSavingsRate * 100.0) / 100.0); // Round savings

            // 4. 添加文化背景相关的提示和通用财务建议 (US8)
            addChinaCulturalNotes(suggestion, year, month);
            suggestion.addNote("这是基于您过去约 " + actualAnalysisMonths + " 个月的平均收支情况生成的预算建议。");
            suggestion.addNote("建议储蓄金额约占估算月收入的 " + (int)(suggestedSavingsRate * 100) + "%，符合较高的储蓄习惯。");
            suggestion.addNote("请根据您的实际情况和即将到来的特殊支出（如节日、人情往来）调整预算。");
            suggestion.addNote("财务规划应以稳健为原则，避免过度消费和高风险投资。");


        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "生成月度预算建议失败：无法获取交易数据", e);
            suggestion.addNote("系统发生错误，无法加载您的交易数据以生成预算建议。请稍后再试。");
            // 提供默认建议或提示作为回退
            suggestion.setEstimatedIncome(0.0);
            suggestion.setTotalSuggestedSpending(0.0);
            suggestion.setSuggestedSavings(0.0);
            addChinaCulturalNotes(suggestion, year, month); // Even on failure, try to add cultural tips
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "生成月度预算建议失败：未知异常", e);
            suggestion.addNote("系统发生未知错误，无法生成预算建议。请联系支持。");
            // 提供默认建议或提示作为回退
            suggestion.setEstimatedIncome(0.0);
            suggestion.setTotalSuggestedSpending(0.0);
            suggestion.setSuggestedSavings(0.0);
            addChinaCulturalNotes(suggestion, year, month); // Even on failure, try to add cultural tips
        }

        return suggestion;
    }

    /**
     * 添加针对中国文化背景的预算提示。
     * @param suggestion 预算建议对象。
     * @param year 目标年份。
     * @param month 目标月份 (1-12)。
     */
    private void addChinaCulturalNotes(BudgetSuggestion suggestion, int year, int month) {
        // 提示考虑家庭与人情支出类别
        suggestion.addNote("提示：请考虑添加或审阅与家庭责任和人情往来相关的预算类别，例如 '子女教育'、'赡养父母'、'人情红包/礼金'。");

        // 提示重要节日规划
        Calendar targetCal = Calendar.getInstance();
        targetCal.set(year, month - 1, 1); // Month is 0-indexed

        // 检查春节 (通常在公历1月下旬至2月)
        // 这是一个简化的检查，精确日期需要复杂的农历计算或外部库
        // Check if target month is Jan/Feb/Mar, which are common months for Lunar New Year
        if (month == 1 || month == 2 || month == 3) {
            // Further check if the current year's Lunar New Year falls into these months
            // (requires actual Lunar calendar logic, skipping complex implementation)
            suggestion.addNote("注意：目标月份或临近月份可能包含春节，通常会有较多一次性支出，如年货、红包、聚餐、旅行等，请提前做好预算规划。");
        }

        // 检查国庆节 (公历10月1日)
        if (month == 9 || month == 10) {
            suggestion.addNote("注意：目标月份或临近月份包含国庆节，长假期间旅行、购物、聚会等支出可能增加，请预留预算。");
        }

        // 其他潜在节日：中秋节 (农历八月十五，公历通常在9月或10月)，端午节 (农历五月初五，公历通常在5月或6月) 等
        // 可以在未来的版本中增加更多节日的精确判断和提示。
        if (month == 5 || month == 6) {
            suggestion.addNote("提示：端午节（农历五月初五，公历通常在5月或6月）可能涉及粽子、礼品等支出，请根据您的习惯适度规划。");
        }
        if (month == 9 || month == 10) {
            // 国庆节提示已包含
            suggestion.addNote("提示：中秋节（农历八月十五，公历通常在9月或10月）可能涉及月饼、家庭聚餐等支出，请根据您的习惯适度规划。");
        }
        if (month == 12 || month == 1) {
            suggestion.addNote("提示：年底可能面临年终奖、置办年货、送礼、返乡等集中支出，请提前盘点并做好详细预算。");
        }
    }


    // --- 储蓄目标管理功能 (US6) ---

    /**
     * 根据用户 ID 获取储蓄目标文件路径。
     * @param userId 用户 ID。
     * @return 储蓄目标文件路径。
     */
    private Path getSavingsGoalsFilePath(String userId) {
        return baseDataDirectory.resolve(USER_DATA_DIR_NAME)
                .resolve(userId)
                .resolve(SAVINGS_GOALS_FILE);
    }

    /**
     * 确保用户数据目录存在。
     * @param userId 用户 ID。
     * @throws IOException 如果创建目录失败。
     */
    private void ensureUserDirectoryExists(String userId) throws IOException {
        Path userDir = baseDataDirectory.resolve(USER_DATA_DIR_NAME).resolve(userId);
        if (!Files.exists(userDir)) {
            try {
                // 创建目录，并设置权限（如果操作系统支持 POSIX 文件权限）
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-x---"); // 示例权限
                FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
                Files.createDirectories(userDir, attr);
                LOGGER.info("用户数据目录创建成功: " + userDir);
            } catch (UnsupportedOperationException e) {
                // 如果操作系统不支持 POSIX 文件权限，则直接创建目录
                Files.createDirectories(userDir);
                LOGGER.warning("操作系统不支持 POSIX 文件权限，用户数据目录创建成功但权限未设置: " + userDir);
            }
        }
    }


    /**
     * 加载指定用户的储蓄目标列表。
     *
     * @param userId 用户 ID。
     * @return 储蓄目标列表，如果文件不存在或读取失败返回空列表。
     */
    private List<SavingsGoal> loadSavingsGoals(String userId) {
        Path filePath = getSavingsGoalsFilePath(userId);
        if (!Files.exists(filePath)) {
            LOGGER.log(Level.INFO, "用户 {0} 的储蓄目标文件不存在，返回空列表。", userId);
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            Type listType = new TypeToken<ArrayList<SavingsGoal>>(){}.getType();
            List<SavingsGoal> goals = gson.fromJson(reader, listType);
            LOGGER.log(Level.INFO, "用户 {0} 加载了 {1} 个储蓄目标。", new Object[]{userId, goals != null ? goals.size() : 0});
            return goals != null ? goals : new ArrayList<>();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "加载用户储蓄目标文件失败: " + filePath, e);
            return new ArrayList<>(); // Return empty list on read failure to avoid crash
        } catch (Exception e) { // Catch potential JSON parsing errors
            LOGGER.log(Level.SEVERE, "解析用户储蓄目标文件失败: " + filePath, e);
            return new ArrayList<>(); // Return empty list on parse failure
        }
    }

    /**
     * 保存指定用户的储蓄目标列表。
     *
     * @param userId 用户 ID。
     * @param goals 要保存的储蓄目标列表。
     * @throws IOException 文件写入失败。
     */
    private void saveSavingsGoals(String userId, List<SavingsGoal> goals) throws IOException {
        ensureUserDirectoryExists(userId); // Ensure user directory exists
        Path filePath = getSavingsGoalsFilePath(userId);

        try (Writer writer = Files.newBufferedWriter(filePath)) {
            gson.toJson(goals, writer);
            LOGGER.log(Level.INFO, "用户 {0} 的储蓄目标已保存到 {1}。", new Object[]{userId, filePath});
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "保存用户储蓄目标文件失败: " + filePath, e);
            throw e; // Propagate exception to caller
        }
    }

    /**
     * 创建并保存新的储蓄目标。
     *
     * @param userId 用户 ID。
     * @param goalDetails 包含新储蓄目标详细信息（名称、目标金额、目标日期、描述）的 SavingsGoal 对象。
     *                    此对象不需要设置 id, userId, currentAmount, createdDate, status。
     * @return 创建并保存成功的 SavingsGoal 对象，如果保存失败返回 null。
     */
    public SavingsGoal createSavingsGoal(String userId, SavingsGoal goalDetails) {
        if (goalDetails == null || userId == null || userId.trim().isEmpty()) {
            LOGGER.warning("创建储蓄目标失败：输入参数无效。");
            return null;
        }
        if (goalDetails.getName() == null || goalDetails.getName().trim().isEmpty() || goalDetails.getTargetAmount() <= 0 || goalDetails.getTargetDate() == null || goalDetails.getTargetDate().trim().isEmpty()) {
            LOGGER.warning("创建储蓄目标失败：目标名称、金额或日期无效。");
            return null;
        }
        // Validate targetDate format (assuming yyyy-MM-dd)
        try {
            synchronized (goalDateFormat) { // SimpleDateFormat non-thread-safe
                goalDateFormat.parse(goalDetails.getTargetDate());
            }
        } catch (ParseException e) {
            LOGGER.warning("创建储蓄目标失败：目标日期格式无效 - " + goalDetails.getTargetDate());
            return null;
        }


        List<SavingsGoal> goals = loadSavingsGoals(userId);

        // Generate unique ID and creation date
        SavingsGoal newGoal = new SavingsGoal();
        newGoal.setId(UUID.randomUUID().toString());
        newGoal.setUserId(userId);
        newGoal.setName(goalDetails.getName());
        newGoal.setDescription(goalDetails.getDescription());
        newGoal.setTargetAmount(goalDetails.getTargetAmount());
        newGoal.setCurrentAmount(0.0); // New goal starts at 0
        newGoal.setTargetDate(goalDetails.getTargetDate()); // Use provided target date string
        synchronized (goalDateFormat) { // SimpleDateFormat non-thread-safe
            newGoal.setCreatedDate(goalDateFormat.format(new Date())); // Set current creation date
        }
        newGoal.setStatus("Active"); // New goal status is Active

        goals.add(newGoal);

        try {
            saveSavingsGoals(userId, goals);
            LOGGER.log(Level.INFO, "用户 {0} 成功创建储蓄目标: {1}", new Object[]{userId, newGoal.getName()});
            return newGoal;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "创建并保存储蓄目标失败。", e);
            // Remove the failed goal from the list before returning null
            goals.remove(newGoal);
            return null;
        }
    }

    /**
     * 获取指定 ID 的储蓄目标。
     *
     * @param userId 用户 ID。
     * @param goalId 储蓄目标的 ID。
     * @return 匹配的 SavingsGoal 对象，如果未找到返回 null。
     */
    public SavingsGoal getSavingsGoal(String userId, String goalId) {
        if (userId == null || userId.trim().isEmpty() || goalId == null || goalId.trim().isEmpty()) {
            LOGGER.warning("获取储蓄目标失败：输入参数无效。");
            return null;
        }
        List<SavingsGoal> goals = loadSavingsGoals(userId);
        return goals.stream()
                .filter(goal -> goalId.equals(goal.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取用户所有的储蓄目标。
     *
     * @param userId 用户 ID。
     * @return 用户所有的储蓄目标列表。
     */
    public List<SavingsGoal> getAllSavingsGoals(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            LOGGER.warning("获取所有储蓄目标失败：用户ID无效。");
            return Collections.emptyList();
        }
        return loadSavingsGoals(userId);
    }

    /**
     * 更新储蓄目标。
     * 客户端应提供包含更新字段的 SavingsGoal 对象，其中 ID 必须存在。
     *
     * @param userId 用户 ID。
     * @param goal 包含更新后详细信息的 SavingsGoal 对象（必须包含有效的 ID）。
     * @return 更新后的 SavingsGoal 对象，如果未找到或保存失败返回 null。
     */
    public SavingsGoal updateSavingsGoal(String userId, SavingsGoal goal) {
        if (goal == null || userId == null || userId.trim().isEmpty() || goal.getId() == null || goal.getId().trim().isEmpty()) {
            LOGGER.warning("更新储蓄目标失败：输入参数无效。");
            return null;
        }

        List<SavingsGoal> goals = loadSavingsGoals(userId);
        Optional<SavingsGoal> existingGoalOpt = goals.stream()
                .filter(g -> goal.getId().equals(g.getId()))
                .findFirst();

        if (existingGoalOpt.isPresent()) {
            SavingsGoal existingGoal = existingGoalOpt.get();
            // Update existing goal fields from the provided goal object
            // Only update fields if they are not null or have valid values in the input object
            if (goal.getName() != null && !goal.getName().trim().isEmpty()) existingGoal.setName(goal.getName());
            if (goal.getDescription() != null) existingGoal.setDescription(goal.getDescription()); // Allow null to clear description
            if (goal.getTargetAmount() > 0) existingGoal.setTargetAmount(goal.getTargetAmount());
            if (goal.getCurrentAmount() >= 0) existingGoal.setCurrentAmount(goal.getCurrentAmount()); // Allow 0
            if (goal.getTargetDate() != null && !goal.getTargetDate().trim().isEmpty()) {
                // Validate updated targetDate format
                try {
                    synchronized (goalDateFormat) { // SimpleDateFormat non-thread-safe
                        goalDateFormat.parse(goal.getTargetDate());
                    }
                    existingGoal.setTargetDate(goal.getTargetDate());
                } catch (ParseException e) {
                    LOGGER.warning("更新储蓄目标失败：目标日期格式无效 - " + goal.getTargetDate());
                    // Don't update the date if invalid
                }
            }
            if (goal.getStatus() != null && !goal.getStatus().trim().isEmpty()) existingGoal.setStatus(goal.getStatus());

            // Optional: Check and update status to "Achieved" based on currentAmount vs targetAmount
            if ("Active".equalsIgnoreCase(existingGoal.getStatus()) && existingGoal.getCurrentAmount() >= existingGoal.getTargetAmount()) {
                existingGoal.setStatus("Achieved");
                LOGGER.log(Level.INFO, "储蓄目标 '{0}' ({1}) 已达成！自动更新状态。", new Object[]{existingGoal.getName(), existingGoal.getId()});
            }


            try {
                saveSavingsGoals(userId, goals);
                LOGGER.log(Level.INFO, "用户 {0} 成功更新储蓄目标: {1}", new Object[]{userId, existingGoal.getName()});
                return existingGoal;
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "更新并保存储蓄目标失败。", e);
                return null;
            }
        } else {
            LOGGER.log(Level.WARNING, "更新储蓄目标失败：未找到 ID 为 {0} 的目标。", goal.getId());
            return null; // Target not found
        }
    }

    /**
     * 删除储蓄目标。
     *
     * @param userId 用户 ID。
     * @param goalId 要删除的储蓄目标的 ID。
     * @return 删除成功返回 true，如果未找到或保存失败返回 false。
     */
    public boolean deleteSavingsGoal(String userId, String goalId) {
        if (userId == null || userId.trim().isEmpty() || goalId == null || goalId.trim().isEmpty()) {
            LOGGER.warning("删除储蓄目标失败：输入参数无效。");
            return false;
        }

        List<SavingsGoal> goals = loadSavingsGoals(userId);
        boolean removed = goals.removeIf(goal -> goalId.equals(goal.getId()));

        if (removed) {
            try {
                saveSavingsGoals(userId, goals);
                LOGGER.log(Level.INFO, "用户 {0} 成功删除储蓄目标 ID: {1}", new Object[]{userId, goalId});
                return true;
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "删除储蓄目标后保存文件失败。", e);
                return false;
            }
        } else {
            LOGGER.log(Level.WARNING, "删除储蓄目标失败：未找到 ID 为 {0} 的目标。", goalId);
            return false; // Target not found
        }
    }
}
    