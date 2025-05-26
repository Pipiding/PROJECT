package com.myfinanceapp.api;

import io.javalin.http.Context;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.ConflictResponse; // For username conflict

import com.myfinanceapp.auth.AuthService;
import com.myfinanceapp.auth.UserProfile;
import com.myfinanceapp.auth.SecurityQuestion; // Need SecurityQuestion model

import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 用户认证 API 控制器
 */
public class AuthApi {

    private static final Logger LOGGER = Logger.getLogger(AuthApi.class.getName());
    private final AuthService authService;

    public AuthApi(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Helper method to validate if a user directory exists for a given userId.
     * Used by other APIs requiring a user context.
     * @param ctx The Javalin context.
     * @param userId The user ID from path parameter.
     * @throws NotFoundResponse if user directory does not exist.
     */
    public void validateUserExists(Context ctx, String userId) {
        if (userId == null || userId.trim().isEmpty() || !authService.fileManager.userDirectoryExists(userId)) {
            LOGGER.warning("用户未找到或用户ID无效: " + userId + " for path " + ctx.path());
            throw new NotFoundResponse("用户未找到。");
        }
    }


    /**
     * POST /register
     * 用户注册接口
     * Request Body: { "username": "...", "password": "...", "securityQuestions": [{ "question": "...", "answer": "..." }] }
     */
    public void register(Context ctx) {
        try {
            // Define a simple input structure for registration data
            class RegistrationInput {
                public String username;
                public String password;
                public List<Map<String, String>> securityQuestions; // Input security questions as list of maps
            }

            RegistrationInput input = ctx.bodyAsClass(RegistrationInput.class);

            if (input.username == null || input.username.trim().isEmpty() || input.password == null || input.password.trim().isEmpty()) {
                throw new BadRequestResponse("用户名和密码不能为空。");
            }

            List<SecurityQuestion> securityQuestions = null;
            if (input.securityQuestions != null) {
                securityQuestions = new java.util.ArrayList<>();
                for (Map<String, String> sqMap : input.securityQuestions) {
                    String question = sqMap.get("question");
                    String answer = sqMap.get("answer");
                    if (question == null || question.trim().isEmpty() || answer == null || answer.trim().isEmpty()) {
                        LOGGER.warning("注册请求包含无效的安全问题/答案: " + sqMap);
                        throw new BadRequestResponse("安全问题和答案不能为空。");
                    }
                    // Create SecurityQuestion objects with plaintext answers for AuthService to hash
                    securityQuestions.add(new SecurityQuestion(question, answer));
                }
            }

            boolean success = authService.register(input.username, input.password, securityQuestions);

            if (success) {
                ctx.status(201); // Created
                ctx.json(Collections.singletonMap("message", "用户注册成功。"));
            } else {
                // AuthService.register returns false if username exists or other file ops fail
                if (authService.fileManager.usernameExists(input.username)) { // Check specifically for username conflict
                    ctx.status(409); // Conflict
                    ctx.json(Collections.singletonMap("message", "用户名已存在。"));
                } else {
                    ctx.status(500); // Internal Server Error for other failures
                    ctx.json(Collections.singletonMap("message", "用户注册失败，请稍后再试。"));
                }
            }
        } catch (BadRequestResponse | ConflictResponse e) {
            throw e; // Re-throw specific Javalin exceptions
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理注册请求时发生异常", e);
            throw new InternalServerErrorResponse("处理注册请求时发生内部错误。");
        }
    }

    /**
     * POST /login
     * 用户登录接口
     * Request Body: { "username": "...", "password": "..." }
     * Response Body: UserProfile object { "userId": "...", "username": "...", ...profile details... }
     */
    public void login(Context ctx) {
        try {
            // Define a simple input structure for login data
            class LoginInput {
                public String username;
                public String password;
            }
            LoginInput input = ctx.bodyAsClass(LoginInput.class);

            if (input.username == null || input.username.trim().isEmpty() || input.password == null || input.password.trim().isEmpty()) {
                throw new BadRequestResponse("用户名和密码不能为空。");
            }

            UserProfile userProfile = authService.login(input.username, input.password);

            if (userProfile != null) {
                ctx.json(userProfile); // Return UserProfile details (includes userId)
            } else {
                throw new UnauthorizedResponse("用户名或密码不正确。");
            }
        } catch (UnauthorizedResponse | BadRequestResponse e) {
            throw e; // Re-throw specific Javalin exceptions
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理登录请求时发生异常", e);
            throw new InternalServerErrorResponse("处理登录请求时发生内部错误。");
        }
    }

    /**
     * PUT /users/{userId}/password
     * 修改用户密码接口 (需要验证旧密码)
     * Request Body: { "oldPassword": "...", "newPassword": "..." }
     */
    public void changePassword(Context ctx) {
        String userId = ctx.pathParam("userId");
        validateUserExists(ctx, userId); // Ensure user exists

        try {
            // Define a simple input structure
            class ChangePasswordInput {
                public String oldPassword;
                public String newPassword;
            }
            ChangePasswordInput input = ctx.bodyAsClass(ChangePasswordInput.class);

            if (input.oldPassword == null || input.oldPassword.trim().isEmpty() || input.newPassword == null || input.newPassword.trim().isEmpty()) {
                throw new BadRequestResponse("旧密码和新密码不能为空。");
            }

            boolean success = authService.changePassword(userId, input.oldPassword, input.newPassword);

            if (success) {
                ctx.json(Collections.singletonMap("message", "密码修改成功。"));
            } else {
                // authService.changePassword returns false if old password doesn't match
                throw new UnauthorizedResponse("旧密码不正确。");
            }
        } catch (NotFoundResponse | BadRequestResponse | UnauthorizedResponse e) {
            throw e; // Re-throw specific Javalin exceptions
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理修改密码请求时发生异常 for user: " + userId, e);
            throw new InternalServerErrorResponse("处理修改密码请求时发生内部错误。");
        }
    }

    /**
     * POST /password/recover/start
     * 开始密码找回流程：获取安全问题
     * Request Body: { "username": "..." }
     * Response Body: List of question strings [question1, question2, ...]
     */
    public void startPasswordRecovery(Context ctx) {
        try {
            // Define a simple input structure
            class RecoveryStartInput {
                public String username;
            }
            RecoveryStartInput input = ctx.bodyAsClass(RecoveryStartInput.class);

            if (input.username == null || input.username.trim().isEmpty()) {
                throw new BadRequestResponse("用户名不能为空。");
            }

            List<String> questions = authService.startPasswordRecovery(input.username);

            if (questions.isEmpty()) {
                // Return 404 if user not found, 200 empty list if user found but no questions
                String userId = authService.fileManager.findUserDirectoryByUsername(input.username);
                if (userId == null) {
                    throw new NotFoundResponse("用户未找到或没有设置安全问题。"); // Ambiguous message for security
                } else {
                    // User found but no questions set
                    ctx.json(Collections.emptyList()); // Return empty list
                }

            } else {
                ctx.json(questions);
            }
        } catch (NotFoundResponse | BadRequestResponse e) {
            throw e; // Re-throw specific Javalin exceptions
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理开始密码找回请求时发生异常", e);
            throw new InternalServerErrorResponse("处理开始密码找回请求时发生内部错误。");
        }
    }

    /**
     * POST /password/recover/complete
     * 完成密码找回流程：验证安全问题答案
     * Request Body: { "username": "...", "answers": ["...", "..."] }
     * Response Body: { "success": true/false }
     * Note: A `true` response here means identity is verified. The client should then prompt for a new password and call the `/password/reset` endpoint.
     */
    public void completePasswordRecovery(Context ctx) {
        try {
            // Define a simple input structure
            class RecoveryCompleteInput {
                public String username;
                public List<String> answers;
            }
            RecoveryCompleteInput input = ctx.bodyAsClass(RecoveryCompleteInput.class);


            if (input.username == null || input.username.trim().isEmpty() || input.answers == null) {
                throw new BadRequestResponse("用户名和答案不能为空。");
            }

            boolean success = authService.completePasswordRecovery(input.username, input.answers);

            ctx.json(Collections.singletonMap("success", success));

        } catch (BadRequestResponse e) {
            throw e; // Re-throw specific Javalin exceptions
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理完成密码找回请求时发生异常", e);
            throw new InternalServerErrorResponse("处理完成密码找回请求时发生内部错误。");
        }
    }

    /**
     * PUT /password/reset
     * 重置密码接口 (用于密码找回等无需旧密码的场景)
     * Request Body: { "username": "...", "newPassword": "..." }
     * Note: This endpoint assumes identity has been verified out-of-band (e.g., via completePasswordRecovery).
     * A more secure approach would involve a temporary reset token verified by this endpoint.
     * For this project, we simplify and assume the client calls this only after successful identity verification.
     */
    public void resetPassword(Context ctx) {
        try {
            // Define a simple input structure
            class ResetPasswordInput {
                public String username;
                public String newPassword;
            }
            ResetPasswordInput input = ctx.bodyAsClass(ResetPasswordInput.class);

            if (input.username == null || input.username.trim().isEmpty() || input.newPassword == null || input.newPassword.trim().isEmpty()) {
                throw new BadRequestResponse("用户名和新密码不能为空。");
            }

            // Find userId by username
            String userId = authService.fileManager.findUserDirectoryByUsername(input.username);
            if (userId == null) {
                throw new NotFoundResponse("用户未找到。");
            }

            // Call the resetPassword method in AuthService
            boolean success = authService.resetPassword(userId, input.newPassword);

            if (success) {
                ctx.json(Collections.singletonMap("message", "密码重置成功。"));
            } else {
                throw new InternalServerErrorResponse("密码重置失败，请稍后再试。");
            }

        } catch (NotFoundResponse | BadRequestResponse e) {
            throw e; // Re-throw specific Javalin exceptions
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "处理密码重置请求时发生异常", e);
            throw new InternalServerErrorResponse("处理密码重置请求时发生内部错误。");
        }
    }
}
    