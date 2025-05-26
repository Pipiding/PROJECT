package com.myfinanceapp.api;

import io.javalin.http.Context;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;

import com.myfinanceapp.dashboard.DashboardService;
import com.myfinanceapp.dashboard.FinancialDashboardData;
import com.myfinanceapp.auth.AuthService; // To access FileManager for user validation
import com.myfinanceapp.api.AuthApi; // To use validateUserExists helper


import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 财务洞察仪表板 API 控制器
 */
public class DashboardApi {

    private static final Logger LOGGER = Logger.getLogger(DashboardApi.class.getName());
    private final DashboardService dashboardService;
    private final AuthApi authApi; // To use validateUserExists helper


    public DashboardApi(DashboardService dashboardService, AuthApi authApi) {
        this.dashboardService = dashboardService;
        this.authApi = authApi; // Inject AuthApi to access user validation
    }

    /**
     * GET /users/{userId}/dashboard/currentMonth
     * 获取指定用户当月的财务仪表板数据 (包含与上月的比较)
     * Response Body: FinancialDashboardData object
     */
    public void getCurrentMonthDashboardData(Context ctx) {
        String userId = ctx.pathParam("userId");
        authApi.validateUserExists(ctx, userId); // Ensure user exists

        try {
            FinancialDashboardData data = dashboardService.getCurrentMonthDashboardData(userId);
            ctx.json(data);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理获取当月仪表板数据请求时发生异常 for user: " + userId, e);
            throw new InternalServerErrorResponse("处理获取当月仪表板数据请求时发生内部错误。");
        }
    }

    /**
     * GET /users/{userId}/dashboard/lastMonth
     * 获取指定用户上月的财务仪表板数据 (包含与上上月的比较)
     * Response Body: FinancialDashboardData object
     */
    public void getLastMonthDashboardData(Context ctx) {
        String userId = ctx.pathParam("userId");
        authApi.validateUserExists(ctx, userId); // Ensure user exists

        try {
            FinancialDashboardData data = dashboardService.getLastMonthDashboardData(userId);
            ctx.json(data);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理获取上月仪表板数据请求时发生异常 for user: " + userId, e);
            throw new InternalServerErrorResponse("处理获取上月仪表板数据请求时发生内部错误。");
        }
    }

    /**
     * GET /users/{userId}/dashboard/yearToDate
     * 获取指定用户本年度至今 (Year-to-Date) 的财务仪表板数据 (包含与去年同期的比较)
     * Response Body: FinancialDashboardData object
     */
    public void getYearToDateDashboardData(Context ctx) {
        String userId = ctx.pathParam("userId");
        authApi.validateUserExists(ctx, userId); // Ensure user exists

        try {
            FinancialDashboardData data = dashboardService.getYearToDateDashboardData(userId);
            ctx.json(data);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理获取年度至今仪表板数据请求时发生异常 for user: " + userId, e);
            throw new InternalServerErrorResponse("处理获取年度至今仪表板数据请求时发生内部错误。");
        }
    }
}
    