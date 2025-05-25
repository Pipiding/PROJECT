package com.myfinanceapp.transaction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder; // Not strictly needed if using JsonUtil
import com.google.gson.reflect.TypeToken;
import com.myfinanceapp.util.JsonUtil; // Using the shared Gson instance

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.DirectoryStream;
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
 * 交易管理模块服务类
 * 负责交易记录的手动输入、导入、存储、读取和查询
 * 所有文件操作通过FileManager或其扩展功能实现，遵循文件系统存储约束
 */
public class TransactionService {

    private static final Logger LOGGER = Logger.getLogger(TransactionService.class.getName());
    private static final String USER_DATA_DIR_NAME = "user_data";
    private static final String TRANSACTIONS_DIR_NAME = "transactions"; // 交易数据子目录
    private static final String TRANSACTIONS_FILE_PREFIX = "transactions_"; // 交易数据文件名前缀
    private static final String FILE_EXTENSION = ".json";
    private static final String DATE_FORMAT_PATTERN_FILE = "yyyyMM"; // 用于文件名中的日期格式
    private static final String DATE_FORMAT_PATTERN_CSV = "yyyy-MM-dd"; // 假设CSV日期格式为 yyyy-MM-dd 或其他常用格式

    private final Path baseDataDirectory;
    private final SimpleDateFormat fileDateFormat = new SimpleDateFormat(DATE_FORMAT_PATTERN_FILE);
    private final SimpleDateFormat csvDateFormat = new SimpleDateFormat(DATE_FORMAT_PATTERN_CSV); // For CSV parsing
    private final Gson gson; // Using shared Gson instance


    /**
     * 构造函数
     *
     * @param baseDataDirectoryPath 应用数据存储的根目录路径
     */
    public TransactionService(String baseDataDirectoryPath) {
        this.baseDataDirectory = Paths.get(baseDataDirectoryPath);
        this.gson = JsonUtil.gson; // Use the shared Gson
        // SimpleDateFormat is not thread-safe, but these instances are only used internally
        // within synchronized blocks or contexts where concurrency is not an issue in this single-user app.
        // For robustness in multi-threaded environment, create instances locally or use ThreadLocal.
    }

    /**
     * 根据用户ID和年份月份获取交易文件路径
     * 文件路径格式: baseDataDirectory/user_data/{userId}/transactions/transactions_YYYYMM.json
     * @param userId 用户ID
     * @param yearMonth 年份月份 (格式: YYYYMM)
     * @return 交易文件路径
     */
    private Path getTransactionFilePath(String userId, String yearMonth) {
        return baseDataDirectory.resolve(USER_DATA_DIR_NAME)
                .resolve(userId)
                .resolve(TRANSACTIONS_DIR_NAME)
                .resolve(TRANSACTIONS_FILE_PREFIX + yearMonth + FILE_EXTENSION);
    }

    /**
     * 根据日期的年和月获取交易文件路径
     * @param userId 用户ID
     * @param date 日期
     * @return 交易文件路径
     */
    private Path getTransactionFilePath(String userId, Date date) {
        String yearMonth;
        // Use synchronized for thread safety with SimpleDateFormat
        synchronized (fileDateFormat) {
            yearMonth = fileDateFormat.format(date);
        }
        return getTransactionFilePath(userId, yearMonth);
    }

    /**
     * 确保用户交易数据目录存在
     * @param userId 用户ID
     * @throws IOException 如果创建目录失败
     */
    private void ensureUserTransactionDirectoryExists(String userId) throws IOException {
        Path userTransactionDir = baseDataDirectory.resolve(USER_DATA_DIR_NAME)
                .resolve(userId)
                .resolve(TRANSACTIONS_DIR_NAME);
        if (!Files.exists(userTransactionDir)) {
            try {
                // 创建目录，并设置权限（如果操作系统支持 POSIX 文件权限）
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-x---"); // 示例权限
                FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
                Files.createDirectories(userTransactionDir, attr);
                LOGGER.info("用户交易数据目录创建成功: " + userTransactionDir);
            } catch (UnsupportedOperationException e) {
                // 如果操作系统不支持 POSIX 文件权限，则直接创建目录
                Files.createDirectories(userTransactionDir);
                LOGGER.warning("操作系统不支持 POSIX 文件权限，用户交易数据目录创建成功但权限未设置: " + userTransactionDir);
            }
        }
    }


    /**
     * 手动添加单笔交易记录
     *
     * @param userId      用户ID
     * @param date        交易日期
     * @param description 描述
     * @param amount      金额 (正负表示收入/支出)
     * @param type        交易类型 (例如: Income, Expense)
     * @param category    交易类别 (例如: Food, Transport)
     * @param notes       备注
     * @return 添加成功返回 true，失败返回 false
     */
    public boolean addTransaction(String userId, Date date, String description, double amount, String type, String category, String notes) {
        try {
            // 1. 验证输入数据
            if (!validateTransactionData(date, amount, type, category)) {
                LOGGER.warning("添加交易失败：数据验证未通过");
                return false;
            }

            // 2. 创建 Transaction 对象
            Transaction newTransaction = new Transaction();
            newTransaction.setTransactionId(UUID.randomUUID().toString()); // 生成唯一ID
            // Use shared Gson's date format for consistency
            newTransaction.setDate(JsonUtil.gson.toJsonTree(date).getAsString().replace("\"", "")); // Format Date to ISO 8601 String using Gson's configured format
            newTransaction.setAmount(amount);
            newTransaction.setDescription(description);
            newTransaction.setCategory(category);
            newTransaction.setIncome("Income".equalsIgnoreCase(type));
            newTransaction.setSource("手动输入"); // 标记为手动输入
            newTransaction.setAiClassified(false); // 手动输入的默认不是AI分类
            newTransaction.setManualCorrection(false); // 手动输入的默认不是更正
            newTransaction.setNotes(notes);

            // 3. 加载现有交易数据
            List<Transaction> transactions = loadTransactions(userId, date);

            // 4. 添加新交易到列表
            transactions.add(newTransaction);

            // 5. 保存交易数据回文件
            saveTransactions(userId, date, transactions);

            LOGGER.info("用户 '" + userId + "' 成功添加交易: " + description + ", " + amount);
            return true;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "添加交易失败：文件操作异常", e);
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "添加交易失败：未知异常", e);
            return false;
        }
    }

    /**
     * 从CSV文件导入交易数据
     *
     * @param userId      用户ID
     * @param csvContent  CSV文件内容的InputStream
     * @return 成功导入的交易记录数量，-1表示导入失败
     */
    public int importTransactionsFromCsv(String userId, InputStream csvContent) {
        int importedCount = 0;
        List<Transaction> parsedTransactions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvContent))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                if (!headerSkipped) {
                    // 假设第一行是标题，需要跳过
                    // 实际应用中，需要更智能地识别标题行或让用户指定是否跳过
                    headerSkipped = true;
                    continue;
                }

                // 解析CSV行
                String[] fields = line.split(","); // 简单的逗号分隔，实际应用中需要更健壮的CSV解析库
                if (fields.length < 3) {
                    LOGGER.warning("跳过无效的CSV行 (字段不足)：" + line);
                    continue;
                }

                try {
                    // 假设CSV列顺序为：日期, 描述, 金额
                    // 实际应用中，需要更灵活地处理列顺序，例如通过列名映射
                    Date date;
                    synchronized (csvDateFormat) { // SimpleDateFormat non-thread-safe
                        date = csvDateFormat.parse(fields[0].trim());
                    }
                    String description = fields[1].trim();
                    double amount = Double.parseDouble(fields[2].trim());

                    // 根据金额正负判断类型（这是一个简化的假设）
                    String type = amount >= 0 ? "Income" : "Expense";
                    String category = "未分类"; // 导入的默认类别
                    String notes = "来自CSV导入";


                    // 验证数据
                    if (!validateTransactionData(date, amount, type, category)) {
                        LOGGER.warning("跳过无效的CSV行 (数据验证未通过)：" + line);
                        continue;
                    }

                    // 创建 Transaction 对象
                    Transaction transaction = new Transaction();
                    transaction.setTransactionId(UUID.randomUUID().toString());
                    // Use shared Gson's date format for consistency
                    transaction.setDate(JsonUtil.gson.toJsonTree(date).getAsString().replace("\"", "")); // Format Date to ISO 8601 String using Gson's configured format
                    transaction.setAmount(amount);
                    transaction.setDescription(description);
                    transaction.setCategory(category);
                    transaction.setIncome("Income".equalsIgnoreCase(type));
                    transaction.setSource("CSV导入"); // 标记为CSV导入
                    transaction.setAiClassified(false); // 导入的默认不是AI分类
                    transaction.setManualCorrection(false); // 导入的默认不是更正
                    transaction.setNotes(notes);

                    parsedTransactions.add(transaction);
                    importedCount++;

                } catch (ParseException e) {
                    LOGGER.warning("跳过无效的CSV行 (日期格式错误)：" + line + " - " + e.getMessage());
                } catch (NumberFormatException e) {
                    LOGGER.warning("跳过无效的CSV行 (金额格式错误)：" + line + " - " + e.getMessage());
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "跳过处理失败的CSV行：" + line, e);
                }
            }

            // 按月份分组交易数据并保存
            Map<String, List<Transaction>> transactionsByMonth = parsedTransactions.stream()
                    .collect(Collectors.groupingBy(txn -> {
                        // Use the parseDate helper from Transaction class
                        Date txnDate = txn.parseDate();
                        if (txnDate != null) {
                            synchronized (fileDateFormat) { // SimpleDateFormat non-thread-safe
                                return fileDateFormat.format(txnDate);
                            }
                        }
                        return "invalid_date"; // Group invalid dates separately or handle error
                    }));

            // Remove any transactions grouped under "invalid_date"
            List<Transaction> invalidDateTxns = transactionsByMonth.remove("invalid_date");
            if (invalidDateTxns != null && !invalidDateTxns.isEmpty()) {
                LOGGER.warning("CSV导入中发现 " + invalidDateTxns.size() + " 笔无效日期交易，已跳过保存。");
                // Adjust importedCount if needed, or just log warning
            }


            for (Map.Entry<String, List<Transaction>> entry : transactionsByMonth.entrySet()) {
                String yearMonth = entry.getKey();
                List<Transaction> currentMonthTransactions = entry.getValue();

                // Load this month's existing transactions
                Date monthDate;
                try {
                    synchronized (fileDateFormat) { // SimpleDateFormat non-thread-safe
                        monthDate = fileDateFormat.parse(yearMonth);
                    }
                } catch (ParseException e) {
                    LOGGER.log(Level.SEVERE, "导入交易失败：无法解析月份字符串 '" + yearMonth + "'", e);
                    // This should not happen if grouping key is correctly formatted
                    continue; // Skip this month
                }
                List<Transaction> existingTransactions = loadTransactions(userId, monthDate);

                // 合并现有和新导入的交易
                existingTransactions.addAll(currentMonthTransactions);

                // 保存合并后的数据
                saveTransactions(userId, monthDate, existingTransactions);
            }

            LOGGER.info("用户 '" + userId + "' 成功导入 " + importedCount + " 笔交易记录");
            return importedCount;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "导入交易失败：读取CSV文件异常", e);
            return -1;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "导入交易失败：未知异常", e);
            return -1;
        }
    }

    /**
     * 数据验证核心逻辑
     * @param date 交易日期
     * @param amount 交易金额
     * @param type 交易类型
     * @param category 交易类别
     * @return 数据是否有效
     */
    private boolean validateTransactionData(Date date, double amount, String type, String category) {
        // 示例验证规则，可以根据需求扩展
        if (date == null) {
            LOGGER.warning("数据验证失败：日期为空");
            return false;
        }
        if (Double.isNaN(amount)) {
            LOGGER.warning("数据验证失败：金额无效");
            return false; // 金额必须是有效的数字
        }
        // Allow amount 0 for special cases? Let's allow for now.
        // if (amount == 0) {
        //     LOGGER.warning("数据验证失败：金额不能为零");
        //     return false;
        // }
        if (type == null || (!"Income".equalsIgnoreCase(type) && !"Expense".equalsIgnoreCase(type))) {
            LOGGER.warning("数据验证失败：类型无效 - " + type);
            return false; // 类型必须是 "Income" 或 "Expense" (大小写不敏感)
        }
        // Check consistency between amount sign and type
        if ("Income".equalsIgnoreCase(type) && amount < 0) {
            LOGGER.warning("数据验证失败：收入金额不能为负数");
            return false;
        }
        if ("Expense".equalsIgnoreCase(type) && amount > 0) {
            LOGGER.warning("数据验证失败：支出金额不能为正数 (请使用负数表示支出)");
            return false; // Assuming amount stores as signed value
        }

        // 类别验证可以进一步细化，例如检查是否在预定义类别列表中
        if (category == null || category.trim().isEmpty()) {
            LOGGER.warning("数据验证失败：类别为空");
            return false;
        }

        // 其他验证规则可以添加，例如：
        // - 描述是否为空等

        return true; // 所有验证通过
    }

    /**
     * 加载指定用户指定日期的月份的交易数据
     * 文件路径格式: baseDataDirectory/user_data/{userId}/transactions/transactions_YYYYMM.json
     *
     * @param userId 用户ID
     * @param date 日期 (用于确定加载哪个月份的文件)
     * @return 交易记录列表，如果文件不存在或读取失败返回空列表
     * @throws IOException 文件读取失败
     */
    public List<Transaction> loadTransactions(String userId, Date date) throws IOException {
        Path filePath = getTransactionFilePath(userId, date);
        return loadTransactionsFromFile(filePath);
    }

    /**
     * 加载指定用户指定年份月份的交易数据
     *
     * @param userId 用户ID
     * @param yearMonth 年份月份 (格式: YYYYMM)
     * @return 交易记录列表，如果文件不存在或读取失败返回空列表
     * @throws IOException 文件读取失败
     */
    public List<Transaction> loadTransactions(String userId, String yearMonth) throws IOException {
        Path filePath = getTransactionFilePath(userId, yearMonth);
        return loadTransactionsFromFile(filePath);
    }


    /**
     * 从指定文件路径加载交易记录
     * @param filePath 文件路径
     * @return 交易记录列表，如果文件不存在或读取失败返回空列表
     * @throws IOException 文件读取失败
     */
    private List<Transaction> loadTransactionsFromFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            // LOGGER.warning("交易数据文件不存在: " + filePath); // Too verbose for frequent checks
            return new ArrayList<>(); // 文件不存在，返回空列表
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            Type listType = new TypeToken<ArrayList<Transaction>>(){}.getType();
            List<Transaction> transactions = gson.fromJson(reader, listType);
            return transactions != null ? transactions : new ArrayList<>();
        } catch (Exception e) { // Catch potential JSON parsing errors
            LOGGER.log(Level.SEVERE, "读取或解析交易数据文件失败: " + filePath, e);
            throw new IOException("读取或解析交易数据文件失败", e);
        }
    }


    /**
     * 保存指定用户指定日期的月份的交易数据到文件
     *
     * @param userId 用户ID
     * @param date 日期 (用于确定保存到哪个月份的文件)
     * @param transactions 要保存的交易记录列表
     * @throws IOException 文件写入失败
     */
    public void saveTransactions(String userId, Date date, List<Transaction> transactions) throws IOException {
        ensureUserTransactionDirectoryExists(userId); // 确保用户交易目录存在
        Path filePath = getTransactionFilePath(userId, date);

        // 排序交易记录（可选，根据日期排序方便查看）
        transactions.sort(Comparator.comparing(t -> t.parseDate()));

        try (Writer writer = Files.newBufferedWriter(filePath)) {
            gson.toJson(transactions, writer);
            LOGGER.fine("用户 '" + userId + "' 的交易数据已保存到: " + filePath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "保存交易数据失败: " + filePath, e);
            throw e; // 抛出异常以便调用方处理
        }
    }


    /**
     * 查询指定用户在日期范围内的交易记录
     * 此方法会遍历日期范围内的所有月份文件进行查询
     *
     * @param userId    用户ID
     * @param startDate 开始日期 (包含)
     * @param endDate   结束日期 (包含)
     * @return 符合条件的交易记录列表
     * @throws IOException 文件读取失败
     */
    public List<Transaction> getTransactionsByDateRange(String userId, Date startDate, Date endDate) throws IOException {
        List<Transaction> result = new ArrayList<>();
        Calendar startCal = Calendar.getInstance();
        startCal.setTime(startDate);
        startCal.set(Calendar.DAY_OF_MONTH, 1); // 从开始日期的月份第一天开始
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);


        Calendar endCal = Calendar.getInstance();
        endCal.setTime(endDate);
        endCal.set(Calendar.HOUR_OF_DAY, 23); // Include transactions up to end of end date
        endCal.set(Calendar.MINUTE, 59);
        endCal.set(Calendar.SECOND, 59);
        endCal.set(Calendar.MILLISECOND, 999);

        // Adjust endCal to be the last day of the month of the end date
        // We iterate month by month
        Calendar iterateCal = Calendar.getInstance();
        iterateCal.setTime(startCal.getTime());


        // Iterate through months from startDate's month to endDate's month
        while (!iterateCal.getTime().after(endCal.getTime())) {
            Date currentMonthDate = iterateCal.getTime();
            List<Transaction> monthTransactions = loadTransactions(userId, currentMonthDate);

            // Filter current month's transactions by the actual date range
            for (Transaction transaction : monthTransactions) {
                Date transactionDate = transaction.parseDate(); // Use helper method
                if (transactionDate != null && !transactionDate.before(startDate) && !transactionDate.after(endDate)) {
                    result.add(transaction);
                }
            }

            // Move to the next month
            iterateCal.add(Calendar.MONTH, 1);
            // Set day to 1 to ensure correct month calculation
            iterateCal.set(Calendar.DAY_OF_MONTH, 1);
        }

        // Sort the result by date
        result.sort(Comparator.comparing(t -> t.parseDate()));

        LOGGER.info("用户 '" + userId + "' 在日期范围 " + startDate + " - " + endDate + " 内查询到 " + result.size() + " 笔交易记录");
        return result;
    }

    /**
     * 查询指定用户按交易类型筛选的交易记录 （筛选年度所有记录）
     * 注意：此方法会加载用户所有交易文件进行筛选，数据量大时可能影响性能
     *
     * @param userId 用户ID
     * @param type 交易类型 (例如: "Income", "Expense")
     * @return 符合条件的交易记录列表
     * @throws IOException 文件读取失败
     */
    public List<Transaction> getTransactionsByType(String userId, String type) throws IOException {
        List<Transaction> allTransactions = getAllTransactions(userId); // 获取所有交易

        boolean isIncome = "Income".equalsIgnoreCase(type);

        List<Transaction> filteredTransactions = allTransactions.stream()
                .filter(txn -> txn.isIncome() == isIncome)
                .collect(Collectors.toList());

        LOGGER.info("用户 '" + userId + "' 按类型 '" + type + "' 查询到 " + filteredTransactions.size() + " 笔交易记录");
        return filteredTransactions;
    }

    /**
     * 查询指定用户按交易类别筛选的交易记录 （筛选年度所有记录）
     * 注意：此方法会加载用户所有交易文件进行筛选，数据量大时可能影响性能
     *
     * @param userId 用户ID
     * @param category 交易类别 (例如: "餐饮", "交通")
     * @return 符合条件的交易记录列表
     * @throws IOException 文件读取失败
     */
    public List<Transaction> getTransactionsByCategory(String userId, String category) throws IOException {
        List<Transaction> allTransactions = getAllTransactions(userId); // 获取所有交易

        List<Transaction> filteredTransactions = allTransactions.stream()
                .filter(txn -> category.equalsIgnoreCase(txn.getCategory()))
                .collect(Collectors.toList());

        LOGGER.info("用户 '" + userId + "' 按类别 '" + category + "' 查询到 " + filteredTransactions.size() + " 笔交易记录");
        return filteredTransactions;
    }


    /**
     * 获取指定用户的所有交易记录 (加载所有月份的文件)
     * 注意：此方法会加载用户所有交易文件，数据量大时可能影响性能
     *
     * @param userId 用户ID
     * @return 所有交易记录列表
     * @throws IOException 文件读取失败
     */
    public List<Transaction> getAllTransactions(String userId) throws IOException {
        List<Transaction> allTransactions = new ArrayList<>();
        Path userTransactionDir = baseDataDirectory.resolve(USER_DATA_DIR_NAME)
                .resolve(userId)
                .resolve(TRANSACTIONS_DIR_NAME);

        if (!Files.exists(userTransactionDir)) {
            LOGGER.warning("用户交易目录不存在，无法加载所有交易: " + userTransactionDir);
            return allTransactions; // 返回空列表
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(userTransactionDir, TRANSACTIONS_FILE_PREFIX + "*" + FILE_EXTENSION)) {
            for (Path filePath : stream) {
                allTransactions.addAll(loadTransactionsFromFile(filePath)); // 合并各月份的交易
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "获取用户所有交易失败：扫描交易目录 '" + userTransactionDir + "' 异常", e);
            throw e;
        }

        // Sort the result
        allTransactions.sort(Comparator.comparing(t -> t.parseDate()));

        LOGGER.info("用户 '" + userId + "' 加载所有交易，共 " + allTransactions.size() + " 笔");
        return allTransactions;
    }

    // Helper to parse Date from ISO 8601 string for calculations if needed elsewhere
    // (Already defined in Transaction class)

}
    