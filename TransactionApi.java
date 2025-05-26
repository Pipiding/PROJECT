package myfinanceapp;

import io.javalin.http.Context;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UploadedFile;

import com.myfinanceapp.transaction.Transaction;
import com.myfinanceapp.transaction.TransactionService;
import com.myfinanceapp.util.JsonUtil; // For parsing Date strings

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 交易管理 API 控制器
 */
public class TransactionApi {

    private static final Logger LOGGER = Logger.getLogger(TransactionApi.class.getName());
    private final TransactionService transactionService;
    private final AuthApi authApi; // To use validateUserExists helper

    public TransactionApi(TransactionService transactionService, AuthApi authApi) {
        this.transactionService = transactionService;
        this.authApi = authApi; // Inject AuthApi to access user validation
    }

    /**
     * POST /users/{userId}/transactions
     * 手动添加交易记录
     * Request Body: { "date": "yyyy-MM-dd", "description": "...", "amount": 123.45, "type": "Income/Expense", "category": "...", "notes": "..." }
     */
    public void addTransaction(Context ctx) {
        String userId = ctx.pathParam("userId");
        authApi.validateUserExists(ctx, userId); // Ensure user exists

        try {
            // Define a simple input structure
            class AddTransactionInput {
                public String date; // Input as yyyy-MM-dd string
                public String description;
                public double amount;
                public String type; // "Income" or "Expense"
                public String category;
                public String notes;
            }
            AddTransactionInput input = ctx.bodyAsClass(AddTransactionInput.class);

            if (input.date == null || input.date.trim().isEmpty() ||
                    input.description == null || input.description.trim().isEmpty() ||
                    Double.isNaN(input.amount) || input.type == null || input.type.trim().isEmpty() ||
                    input.category == null || input.category.trim().isEmpty())
            {
                throw new BadRequestResponse("日期, 描述, 金额, 类型, 类别不能为空。");
            }

            // Parse the input date string (assuming yyyy-MM-dd from example)
            SimpleDateFormat inputDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date transactionDate;
            try {
                synchronized (inputDateFormat) { // SimpleDateFormat non-thread-safe
                    transactionDate = inputDateFormat.parse(input.date.trim());
                }
            } catch (ParseException e) {
                throw new BadRequestResponse("无效的日期格式。请使用 YYYY-MM-DD。");
            }


            boolean success = transactionService.addTransaction(
                    userId,
                    transactionDate,
                    input.description.trim(),
                    input.amount,
                    input.type.trim(),
                    input.category.trim(),
                    input.notes != null ? input.notes.trim() : ""
            );

            if (success) {
                ctx.status(201); // Created
                ctx.json(Collections.singletonMap("message", "交易记录添加成功。"));
            } else {
                // addTransaction returns false on internal failure (e.g., file IO) after validation
                throw new InternalServerErrorResponse("交易记录添加失败，请稍后再试。");
            }

        } catch (BadRequestResponse e) {
            throw e; // Re-throw specific Javalin exception
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理添加交易请求时发生异常 for user: " + userId, e);
            throw new InternalServerErrorResponse("处理添加交易请求时发生内部错误。");
        }
    }

    /**
     * POST /users/{userId}/transactions/import/csv
     * 从CSV文件导入交易记录
     * Request Body: multipart form-data with a file named "csvFile"
     * Response Body: { "importedCount": N }
     */
    public void importTransactionsFromCsv(Context ctx) {
        String userId = ctx.pathParam("userId");
        authApi.validateUserExists(ctx, userId); // Ensure user exists

        // Assuming file upload via multipart form-data with parameter name "csvFile"
        ctx.uploadedFile("csvFile").ifPresentOrElse(uploadedFile -> {
            try (InputStream csvContent = uploadedFile.getContent()) {
                int importedCount = transactionService.importTransactionsFromCsv(userId, csvContent);
                if (importedCount != -1) {
                    ctx.json(Collections.singletonMap("importedCount", importedCount));
                } else {
                    // importTransactionsFromCsv returns -1 on failure
                    throw new InternalServerErrorResponse("导入CSV交易失败。请检查文件格式或内容。");
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "读取上传的CSV文件失败 for user: " + userId, e);
                throw new InternalServerErrorResponse("读取上传的CSV文件失败。");
            } catch (InternalServerErrorResponse e) {
                throw e; // Re-throw specific Javalin exception
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "处理CSV导入请求时发生异常 for user: " + userId, e);
                throw new InternalServerErrorResponse("处理CSV导入请求时发生内部错误。");
            }
        }, () -> {
            throw new BadRequestResponse("未找到上传的CSV文件。请确保文件参数名为 'csvFile'。");
        });
    }


    /**
     * GET /users/{userId}/transactions
     * 查询交易记录，支持按日期范围、类型、类别筛选
     * Query Params:
     *   - startDate (optional): yyyy-MM-dd
     *   - endDate (optional): yyyy-MM-dd
     *   - type (optional): "income" or "expense"
     *   - category (optional): string
     * If no query params, returns all transactions.
     * Response Body: List of Transaction objects
     */
    public void getTransactions(Context ctx) {
        String userId = ctx.pathParam("userId");
        authApi.validateUserExists(ctx, userId); // Ensure user exists

        String startDateStr = ctx.queryParam("startDate");
        String endDateStr = ctx.queryParam("endDate");
        String type = ctx.queryParam("type"); // "income" or "expense"
        String category = ctx.queryParam("category");

        try {
            List<Transaction> transactions;
            SimpleDateFormat queryDateFormat = new SimpleDateFormat("yyyy-MM-dd"); // For parsing query params

            Date startDate = null;
            Date endDate = null;

            // 1. Parse date range parameters
            if (startDateStr != null && !startDateStr.trim().isEmpty()) {
                try {
                    synchronized (queryDateFormat) { // SimpleDateFormat non-thread-safe
                        startDate = queryDateFormat.parse(startDateStr);
                    }
                    // Set time to start of day
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(startDate);
                    calendar.set(Calendar.HOUR_OF_DAY, 0);
                    calendar.set(Calendar.MINUTE, 0);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    startDate = calendar.getTime();

                } catch (ParseException e) {
                    throw new BadRequestResponse("无效的 startDate 格式。请使用 YYYY-MM-DD。");
                }
            }
            if (endDateStr != null && !endDateStr.trim().isEmpty()) {
                try {
                    synchronized (queryDateFormat) { // SimpleDateFormat non-thread-safe
                        endDate = queryDateFormat.parse(endDateStr);
                    }
                    // Set time to end of day for inclusive range
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTime(endDate);
                    calendar.set(Calendar.HOUR_OF_DAY, 23);
                    calendar.set(Calendar.MINUTE, 59);
                    calendar.set(Calendar.SECOND, 59);
                    calendar.set(Calendar.MILLISECOND, 999);
                    endDate = calendar.getTime();

                } catch (ParseException e) {
                    throw new BadRequestResponse("无效的 endDate 格式。请使用 YYYY-MM-DD。");
                }
            }

            // 2. Fetch base transaction data (by date range or all)
            if (startDate != null && endDate != null) {
                // Fetch by date range using the efficient service method
                transactions = transactionService.getTransactionsByDateRange(userId, startDate, endDate);
            } else if (startDate == null && endDate == null) {
                // If no date range specified, get all transactions (less efficient for large data)
                transactions = transactionService.getAllTransactions(userId);
            } else {
                // Cannot specify only start or only end date range efficiently with current service
                throw new BadRequestResponse("必须同时提供 startDate 和 endDate 进行日期范围查询。");
            }


            // 3. Apply type and category filters in memory
            List<Transaction> filteredTransactions = transactions.stream()
                    .filter(txn -> {
                        if (type != null && !type.trim().isEmpty()) {
                            boolean isIncomeFilter = "income".equalsIgnoreCase(type.trim());
                            if (txn.isIncome() != isIncomeFilter) return false;
                        }
                        if (category != null && !category.trim().isEmpty()) {
                            if (txn.getCategory() == null || !txn.getCategory().trim().equalsIgnoreCase(category.trim())) return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            // 4. Return filtered results
            ctx.json(filteredTransactions);

        } catch (NotFoundResponse | BadRequestResponse e) {
            throw e; // Re-throw specific Javalin exceptions
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "文件读取失败获取用户交易: " + userId, e);
            throw new InternalServerErrorResponse("无法加载交易数据。");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理获取交易请求时发生异常 for user: " + userId, e);
            throw new InternalServerErrorResponse("处理获取交易请求时发生内部错误。");
        }
    }
}
    