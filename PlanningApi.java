package myfinanceapp;

import io.javalin.http.Context;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;

import com.myfinanceapp.planning.FinancialPlanningService;
import com.myfinanceapp.planning.BudgetSuggestion;
import com.myfinanceapp.planning.SavingsGoal;
import com.myfinanceapp.auth.AuthService; // To access FileManager for user validation
import com.myfinanceapp.api.AuthApi; // To use validateUserExists helper


import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 财务规划 API 控制器
 */
public class PlanningApi {

    private static final Logger LOGGER = Logger.getLogger(PlanningApi.class.getName());
    private final FinancialPlanningService planningService;
    private final AuthApi authApi; // To use validateUserExists helper

    public PlanningApi(FinancialPlanningService planningService, AuthApi authApi) {
        this.planningService = planningService;
        this.authApi = authApi; // Inject AuthApi to access user validation
    }

    /**
     * GET /users/{userId}/planning/budget/suggestion
     * 获取月度预算建议
     * Query Params: year={year}, month={month}
     * Response Body: BudgetSuggestion object
     */
    public void getMonthlyBudgetSuggestion(Context ctx) {
        String userId = ctx.pathParam("userId");
        authApi.validateUserExists(ctx, userId); // Ensure user exists

        String yearStr = ctx.queryParam("year");
        String monthStr = ctx.queryParam("month");

        if (yearStr == null || yearStr.trim().isEmpty() || monthStr == null || monthStr.trim().isEmpty()) {
            throw new BadRequestResponse("年份和月份参数不能为空。");
        }

        try {
            int year = Integer.parseInt(yearStr);
            int month = Integer.parseInt(monthStr);

            if (month < 1 || month > 12) {
                throw new BadRequestResponse("月份参数无效，必须是 1-12 之间的整数。");
            }
            // Simple year validation
            if (year < 1900 || year > 2100) { // Arbitrary range
                throw new BadRequestResponse("年份参数无效。");
            }


            BudgetSuggestion suggestion = planningService.generateMonthlyBudgetSuggestion(userId, year, month);

            ctx.json(suggestion);

        } catch (NumberFormatException e) {
            throw new BadRequestResponse("年份或月份参数必须是有效的整数。");
        } catch (BadRequestResponse e) {
            throw e; // Re-throw specific Javalin exception
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理获取预算建议请求时发生异常 for user: " + userId, e);
            throw new InternalServerErrorResponse("处理获取预算建议请求时发生内部错误。");
        }
    }

    /**
     * POST /users/{userId}/planning/goals
     * 创建新的储蓄目标
     * Request Body: SavingsGoal object (excluding id, userId, currentAmount, createdDate, status)
     * { "name": "...", "targetAmount": 10000.0, "targetDate": "yyyy-MM-dd", "description": "..." }
     * Response Body: Created SavingsGoal object with ID and default values
     */
    public void createSavingsGoal(Context ctx) {
        String userId = ctx.pathParam("userId");
        authApi.validateUserExists(ctx, userId); // Ensure user exists

        try {
            // Define a simple input structure for goal creation
            class CreateGoalInput {
                public String name;
                public String description;
                public double targetAmount;
                public String targetDate; // yyyy-MM-dd
            }
            CreateGoalInput input = ctx.bodyAsClass(CreateGoalInput.class);

            if (input.name == null || input.name.trim().isEmpty() || input.targetAmount <= 0 || input.targetDate == null || input.targetDate.trim().isEmpty()) {
                throw new BadRequestResponse("目标名称、目标金额和目标日期不能为空，且目标金额必须大于零。");
            }

            // Create a SavingsGoal object from input. Service will populate ID, userId, etc.
            SavingsGoal newGoalDetails = new SavingsGoal();
            newGoalDetails.setName(input.name.trim());
            newGoalDetails.setDescription(input.description != null ? input.description.trim() : null);
            newGoalDetails.setTargetAmount(input.targetAmount);
            newGoalDetails.setTargetDate(input.targetDate.trim()); // Ensure format is yyyy-MM-dd, service will validate

            SavingsGoal createdGoal = planningService.createSavingsGoal(userId, newGoalDetails);

            if (createdGoal != null) {
                ctx.status(201); // Created
                ctx.json(createdGoal);
            } else {
                // createSavingsGoal returns null on validation or file save failure
                throw new InternalServerErrorResponse("创建储蓄目标失败，请稍后再试。");
            }

        } catch (BadRequestResponse e) {
            throw e; // Re-throw specific Javalin exception
        } catch (InternalServerErrorResponse e) {
            throw e; // Re-throw specific Javalin exception
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理创建储蓄目标请求时发生异常 for user: " + userId, e);
            throw new InternalServerErrorResponse("处理创建储蓄目标请求时发生内部错误。");
        }
    }

    /**
     * GET /users/{userId}/planning/goals
     * 获取用户所有储蓄目标
     * Response Body: List of SavingsGoal objects
     */
    public void getAllSavingsGoals(Context ctx) {
        String userId = ctx.pathParam("userId");
        authApi.validateUserExists(ctx, userId); // Ensure user exists

        try {
            List<SavingsGoal> goals = planningService.getAllSavingsGoals(userId);
            ctx.json(goals);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理获取所有储蓄目标请求时发生异常 for user: " + userId, e);
            throw new InternalServerErrorResponse("处理获取所有储蓄目标请求时发生内部错误。");
        }
    }

    /**
     * GET /users/{userId}/planning/goals/{goalId}
     * 获取指定ID的储蓄目标
     * Path Param: goalId
     * Response Body: SavingsGoal object or 404 Not Found
     */
    public void getSavingsGoal(Context ctx) {
        String userId = ctx.pathParam("userId");
        String goalId = ctx.pathParam("goalId");
        authApi.validateUserExists(ctx, userId); // Ensure user exists

        if (goalId == null || goalId.trim().isEmpty()) {
            throw new BadRequestResponse("目标ID不能为空。");
        }

        try {
            SavingsGoal goal = planningService.getSavingsGoal(userId, goalId);
            if (goal != null) {
                ctx.json(goal);
            } else {
                throw new NotFoundResponse("未找到指定ID的储蓄目标。");
            }
        } catch (BadRequestResponse e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理获取单个储蓄目标请求时发生异常 for user: " + userId + ", goalId: " + goalId, e);
            throw new InternalServerErrorResponse("处理获取储蓄目标请求时发生内部错误。");
        }
    }


    /**
     * PUT /users/{userId}/planning/goals/{goalId}
     * 更新储蓄目标
     * Path Param: goalId
     * Request Body: SavingsGoal object with updated fields (ID field in body is optional but will be ignored, use path param ID)
     * { "name": "...", "targetAmount": 10000.0, "currentAmount": 1000.0, "targetDate": "yyyy-MM-dd", "description": "...", "status": "..." }
     * Response Body: Updated SavingsGoal object
     */
    public void updateSavingsGoal(Context ctx) {
        String userId = ctx.pathParam("userId");
        String goalId = ctx.pathParam("goalId");
        authApi.validateUserExists(ctx, userId); // Ensure user exists


        if (goalId == null || goalId.trim().isEmpty()) {
            throw new BadRequestResponse("目标ID不能为空。");
        }

        try {
            SavingsGoal updatedGoalDetails = ctx.bodyAsClass(SavingsGoal.class);
            updatedGoalDetails.setId(goalId); // Ensure the goal ID from path is used

            SavingsGoal updatedGoal = planningService.updateSavingsGoal(userId, updatedGoalDetails);

            if (updatedGoal != null) {
                ctx.json(updatedGoal);
            } else {
                // updateSavingsGoal returns null if goal not found or save failed
                if (planningService.getSavingsGoal(userId, goalId) == null) { // Check if goal exists
                    throw new NotFoundResponse("未找到指定ID的储蓄目标。");
                } else {
                    throw new InternalServerErrorResponse("更新储蓄目标失败，请稍后再试。");
                }
            }

        } catch (NotFoundResponse | BadRequestResponse e) {
            throw e; // Re-throw specific Javalin exceptions
        } catch (InternalServerErrorResponse e) {
            throw e; // Re-throw specific Javalin exception
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理更新储蓄目标请求时发生异常 for user: " + userId + ", goalId: " + goalId, e);
            throw new InternalServerErrorResponse("处理更新储蓄目标请求时发生内部错误。");
        }
    }

    /**
     * DELETE /users/{userId}/planning/goals/{goalId}
     * 删除储蓄目标
     * Path Param: goalId
     * Response Body: { "success": true } or 404 Not Found
     */
    public void deleteSavingsGoal(Context ctx) {
        String userId = ctx.pathParam("userId");
        String goalId = ctx.pathParam("goalId");
        authApi.validateUserExists(ctx, userId); // Ensure user exists


        if (goalId == null || goalId.trim().isEmpty()) {
            throw new BadRequestResponse("目标ID不能为空。");
        }

        try {
            boolean success = planningService.deleteSavingsGoal(userId, goalId);

            if (success) {
                ctx.json(Collections.singletonMap("success", true));
            } else {
                // deleteSavingsGoal returns false if goal not found or save failed
                if (planningService.getSavingsGoal(userId, goalId) == null) { // Check if goal exists BEFORE attempting delete
                    throw new NotFoundResponse("未找到指定ID的储蓄目标。");
                } else {
                    throw new InternalServerErrorResponse("删除储蓄目标失败，请稍后再试。");
                }
            }
        } catch (NotFoundResponse | BadRequestResponse e) {
            throw e; // Re-throw specific Javalin exceptions
        } catch (InternalServerErrorResponse e) {
            throw e; // Re-throw specific Javalin exceptions
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理删除储蓄目标请求时发生异常 for user: " + userId + ", goalId: " + goalId, e);
            throw new InternalServerErrorResponse("处理删除储蓄目标请求时发生内部错误。");
        }
    }
}
    