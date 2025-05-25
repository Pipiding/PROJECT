package com.myfinanceapp.auth;

import com.google.gson.Gson;
import com.myfinanceapp.util.JsonUtil; // Using the shared Gson instance
import org.mindrot.jbcrypt.BCrypt;

import java.io.*;
import java.nio.file.Files; // For directory creation in FileManager
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 用户认证模块的主服务类
 * 负责用户注册、登录、密码修改和找回
 * 所有数据存储操作通过 FileManager 进行，严格遵循文件系统存储约束
 */
public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());
    private final Path baseDataDirectory;
    public final FileManager fileManager; // Keep public for other APIs to potentially validate user existence
    private final Gson gson; // Using shared Gson for consistency, though FileManager has its own


    /**
     * 构造函数
     *
     * @param baseDataDirectoryPath 应用数据存储的根目录路径
     */
    public AuthService(String baseDataDirectoryPath) {
        this.baseDataDirectory = Paths.get(baseDataDirectoryPath);
        this.fileManager = new FileManager(this.baseDataDirectory); // FileManager ensures user_data root exists
        this.gson = JsonUtil.gson; // Use the shared Gson
    }

    /**
     * 用户注册
     *
     * @param username          用户名
     * @param password          密码
     * @param securityQuestions 安全问题和答案列表 (答案为明文输入)
     * @return 注册成功返回 true，失败（如用户名已存在）返回 false
     */
    public boolean register(String username, String password, List<SecurityQuestion> securityQuestions) {
        // 1. 检查用户名唯一性
        if (fileManager.usernameExists(username)) {
            LOGGER.warning("注册失败：用户名已存在 - " + username);
            return false;
        }

        // 2. 生成唯一用户 ID 和用户专属目录路径
        String userId = UUID.randomUUID().toString();
        Path userDir = fileManager.getUserDirectory(userId); // Use FileManager to get user dir path

        // 3. 创建用户专属目录 (FileManager.save methods now ensure directory exists)
        // try { Files.createDirectories(userDir); ... } - Moved directory creation responsibility to FileManager save methods for robustness


        try {
            // 4. 安全地存储密码 (哈希加盐)
            String salt = BCrypt.gensalt(); // BCrypt 会生成并管理盐
            String hashedPassword = BCrypt.hashpw(password, salt);

            // 5. 创建并保存 SecurityData
            SecurityData securityData = new SecurityData(hashedPassword, salt);
            fileManager.saveSecurityData(userId, securityData);
            LOGGER.info("用户安全数据保存成功: " + userId);

            // 6. 创建并保存 UserProfile
            UserProfile userProfile = new UserProfile();
            userProfile.setUserId(userId);
            userProfile.setUsername(username); // 根据用户确认，username 作为登录标识符
            userProfile.setCreationDate(new Date());
            // 其他字段可以后续完善或在注册时收集
            fileManager.saveUserProfile(userId, userProfile);
            LOGGER.info("用户配置文件保存成功: " + userId);

            // 7. 安全存储安全问题和答案 (答案哈希处理)
            if (securityQuestions != null && !securityQuestions.isEmpty()) {
                List<SecurityQuestion> hashedQuestions = new ArrayList<>();
                for (SecurityQuestion sq : securityQuestions) {
                    // 对安全问题答案进行哈希处理，可以使用与密码相同的哈希算法和盐 (尽管不同盐更安全，此处简化)
                    // 注意：实际应用中，安全问题答案的哈希/盐应与密码区分开，以增加安全性
                    String hashedAnswer = BCrypt.hashpw(sq.getHashedAnswer(), salt); // sq.getHashedAnswer() is plaintext input here
                    hashedQuestions.add(new SecurityQuestion(sq.getQuestion(), hashedAnswer, true)); // Store hashed answer
                }
                fileManager.saveSecurityQuestions(userId, hashedQuestions);
                LOGGER.info("用户安全问题保存成功: " + userId);
            }

            LOGGER.info("用户注册成功: " + username + " (ID: " + userId + ")");
            return true;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "用户注册失败: 文件操作异常", e);
            // 注册失败时尝试清理已创建的部分文件
            try {
                fileManager.deleteUserDirectory(userId); // 注意：这个方法需要实现
                LOGGER.warning("用户注册失败后清理目录: " + userDir);
            } catch (IOException cleanupEx) {
                LOGGER.log(Level.SEVERE, "用户注册失败后清理目录也失败: " + userDir, cleanupEx);
            }
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "用户注册失败: 未知异常", e);
            return false;
        }
    }

    /**
     * 用户登录
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录成功返回 UserProfile 对象，失败返回 null
     */
    public UserProfile login(String username, String password) {
        // 1. 根据用户名查找用户 ID 和目录
        String userId = fileManager.findUserDirectoryByUsername(username);
        if (userId == null) {
            LOGGER.warning("登录失败：用户名不存在 - " + username);
            return null;
        }

        try {
            // 2. 加载安全数据 (包含哈希密码和盐)
            SecurityData securityData = fileManager.loadSecurityData(userId);
            if (securityData == null) {
                LOGGER.severe("登录失败：无法加载用户安全数据 - " + userId);
                return null; // 用户存在但安全数据丢失？异常情况
            }

            // 3. 验证密码
            boolean passwordMatch = BCrypt.checkpw(password, securityData.getHashedPassword());

            if (passwordMatch) {
                // 4. 密码匹配，加载并返回用户配置文件
                UserProfile userProfile = fileManager.loadUserProfile(userId);
                if (userProfile != null) {
                    LOGGER.info("用户登录成功: " + username + " (ID: " + userId + ")");
                    // 可选：更新最后登录时间
                    userProfile.setLastLoginDate(new Date());
                    fileManager.saveUserProfile(userId, userProfile); // Save updated profile
                    return userProfile;
                } else {
                    LOGGER.severe("登录失败：用户配置文件加载失败 - " + userId);
                    return null; // 安全数据存在但配置文件丢失？异常情况
                }
            } else {
                LOGGER.warning("登录失败：密码不匹配 - " + username);
                return null;
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "用户登录失败: 文件操作异常", e);
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "用户登录失败: 未知异常", e);
            return null;
        }
    }

    /**
     * 修改密码 (需要当前密码验证)
     *
     * @param userId      当前登录用户的 ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 修改成功返回 true，失败（如旧密码错误）返回 false
     */
    public boolean changePassword(String userId, String oldPassword, String newPassword) {
        if (!fileManager.userDirectoryExists(userId)) {
            LOGGER.warning("修改密码失败：用户 ID 不存在 - " + userId);
            return false;
        }

        try {
            // 1. 加载安全数据
            SecurityData securityData = fileManager.loadSecurityData(userId);
            if (securityData == null) {
                LOGGER.severe("修改密码失败：无法加载用户安全数据 - " + userId);
                return false;
            }

            // 2. 验证旧密码
            boolean oldPasswordMatch = BCrypt.checkpw(oldPassword, securityData.getHashedPassword());
            if (!oldPasswordMatch) {
                LOGGER.warning("修改密码失败：旧密码不匹配 - " + userId);
                return false;
            }

            // 3. 哈希新密码
            String salt = BCrypt.gensalt();
            String hashedNewPassword = BCrypt.hashpw(newPassword, salt);

            // 4. 更新并保存安全数据
            securityData.setHashedPassword(hashedNewPassword);
            // securityData.setSalt(salt); // BCrypt 在哈希时内部管理盐，盐已包含在 hashedNewPassword 中
            fileManager.saveSecurityData(userId, securityData);

            LOGGER.info("用户密码修改成功: " + userId);
            return true;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "修改密码失败: 文件操作异常", e);
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "修改密码失败: 未知异常", e);
            return false;
        }
    }

    /**
     * 重置密码（用于密码找回等无需旧密码的场景）
     * 此方法不验证旧密码，应仅在身份已通过其他方式（如安全问题）验证后调用。
     *
     * @param userId      需要重置密码的用户 ID
     * @param newPassword 新密码
     * @return 重置成功返回 true，失败返回 false
     */
    public boolean resetPassword(String userId, String newPassword) {
        if (!fileManager.userDirectoryExists(userId)) {
            LOGGER.warning("重置密码失败：用户 ID 不存在 - " + userId);
            return false;
        }

        try {
            // 1. 哈希新密码
            String salt = BCrypt.gensalt();
            String hashedNewPassword = BCrypt.hashpw(newPassword, salt);

            // 2. 加载并更新安全数据
            SecurityData securityData = fileManager.loadSecurityData(userId);
            if (securityData == null) {
                LOGGER.severe("重置密码失败：无法加载用户安全数据 - " + userId);
                return false; // 用户存在但安全数据丢失？异常情况
            }
            securityData.setHashedPassword(hashedNewPassword);
            // securityData.setSalt(salt); // BCrypt 在哈希时内部管理盐
            fileManager.saveSecurityData(userId, securityData);

            LOGGER.info("用户密码重置成功: " + userId);
            return true;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "重置密码失败: 文件操作异常", e);
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "重置密码失败: 未知异常", e);
            return false;
        }
    }


    /**
     * 开始密码找回流程：根据用户名获取安全问题列表
     *
     * @param username 用户名
     * @return 安全问题列表（不包含答案），如果用户不存在或没有安全问题则返回空列表
     */
    public List<String> startPasswordRecovery(String username) {
        // 1. 根据用户名查找用户 ID
        String userId = fileManager.findUserDirectoryByUsername(username);
        if (userId == null) {
            LOGGER.warning("密码找回开始失败：用户名不存在 - " + username);
            return Collections.emptyList();
        }

        try {
            // 2. 加载安全问题
            List<SecurityQuestion> questions = fileManager.loadSecurityQuestions(userId);
            if (questions == null || questions.isEmpty()) {
                LOGGER.warning("密码找回开始失败：用户没有设置安全问题 - " + userId);
                return Collections.emptyList();
            }

            // 3. 返回问题列表 (不包含答案)
            List<String> questionTexts = new ArrayList<>();
            for (SecurityQuestion sq : questions) {
                questionTexts.add(sq.getQuestion());
            }
            LOGGER.info("密码找回开始：为用户 " + username + " 获取到安全问题");
            return questionTexts;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "密码找回开始失败: 文件操作异常", e);
            return Collections.emptyList();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "密码找回开始失败: 未知异常", e);
            return Collections.emptyList();
        }
    }

    /**
     * 完成密码找回流程：验证安全问题答案
     * 如果答案正确，则允许用户设置新密码（此方法只验证身份，后续流程由调用方处理）
     *
     * @param username 用户名
     * @param providedAnswers 用户提供的安全问题答案列表 (顺序需与 startPasswordRecovery 返回的问题顺序一致)
     * @return 验证成功返回 true，失败返回 false
     */
    public boolean completePasswordRecovery(String username, List<String> providedAnswers) {
        // 1. 根据用户名查找用户 ID
        String userId = fileManager.findUserDirectoryByUsername(username);
        if (userId == null) {
            LOGGER.warning("密码找回完成失败：用户名不存在 - " + username);
            return false;
        }

        try {
            // 2. 加载存储的安全问题和答案
            List<SecurityQuestion> storedQuestions = fileManager.loadSecurityQuestions(userId);
            if (storedQuestions == null || storedQuestions.isEmpty() || storedQuestions.size() != providedAnswers.size()) {
                LOGGER.warning("密码找回完成失败：安全问题数量不匹配或未设置问题 - " + userId);
                return false; // 问题数量不匹配或用户没有设置问题
            }

            // 3. 加载用户的盐值，用于验证答案哈希 (假设答案哈希使用了与密码相同的盐)
            // 虽然BCrypt.checkpw不需要单独的盐，但确保用户安全数据可加载是前提
            SecurityData securityData = fileManager.loadSecurityData(userId);
            if (securityData == null) {
                LOGGER.severe("密码找回完成失败：无法加载用户安全数据 - " + userId);
                return false; // 用户存在但安全数据丢失？异常情况
            }

            // 4. 逐个验证答案
            boolean allAnswersMatch = true;
            for (int i = 0; i < storedQuestions.size(); i++) {
                String storedHashedAnswer = storedQuestions.get(i).getHashedAnswer();
                String providedAnswer = providedAnswers.get(i);

                // 使用 BCrypt 验证提供的答案和存储的哈希答案
                if (!BCrypt.checkpw(providedAnswer, storedHashedAnswer)) {
                    allAnswersMatch = false;
                    LOGGER.warning("密码找回完成：安全问题答案不匹配，问题索引: " + i + ", 用户: " + userId);
                    break; // 任意一个答案不匹配则失败
                }
            }

            if (allAnswersMatch) {
                LOGGER.info("密码找回完成：安全问题答案验证成功 - " + userId);
                // 验证成功后，调用方可以在 UI 中提示用户设置新密码，然后调用 resetPassword 方法
                return true;
            } else {
                return false;
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "密码找回完成失败: 文件操作异常", e);
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "密码找回完成失败: 未知异常", e);
            return false;
        }
    }
}
    