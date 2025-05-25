package com.myfinanceapp.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 文件管理器
 * 负责所有用户相关文件的读写操作 (Auth 模块专用)
 * Note: This FileManager is specific to Auth files. Other services handle their files separately
 * based on the overall file structure defined in SvD7o.
 */
public class FileManager {
    private static final Logger LOGGER = Logger.getLogger(FileManager.class.getName());
    private static final String USER_DATA_DIR_NAME = "user_data";
    private static final String USER_PROFILE_FILE = "user_profile.json";
    private static final String SECURITY_DATA_FILE = "security.dat";
    private static final String SECURITY_QUESTIONS_FILE = "security_questions.json"; // 用于密码找回的安全问题

    private final Path baseDataDirectory;
    private final Path userDataDirectory;
    private final Gson gson;
    private final Properties properties; // 用于处理 .dat 文件

    public FileManager(Path baseDataDirectory) {
        this.baseDataDirectory = baseDataDirectory;
        this.userDataDirectory = baseDataDirectory.resolve(USER_DATA_DIR_NAME);
        // Use a separate Gson instance or the shared one if needed.
        // Using a dedicated one here for potential specific configurations later.
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.properties = new Properties();
        ensureBaseDataDirectoryExists(); // Ensure the root user data directory exists
    }

    /**
     * 确保基础用户数据根目录存在
     */
    private void ensureBaseDataDirectoryExists() {
        if (!Files.exists(userDataDirectory)) {
            try {
                // 创建目录，并设置权限（如果操作系统支持 POSIX 文件权限）
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-x---");
                FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);
                Files.createDirectories(userDataDirectory, attr);
                LOGGER.info("用户数据根目录创建成功: " + userDataDirectory);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "创建用户数据根目录失败: " + userDataDirectory, e);
                // 在实际应用中，这里应该抛出运行时异常或更友好的错误处理
                throw new UncheckedIOException("无法创建用户数据根目录", e);
            } catch (UnsupportedOperationException e) {
                // 如果操作系统不支持 POSIX 文件权限，则直接创建目录
                try {
                    Files.createDirectories(userDataDirectory);
                    LOGGER.warning("操作系统不支持 POSIX 文件权限，用户数据根目录创建成功但权限未设置: " + userDataDirectory);
                } catch (IOException ioException) {
                    LOGGER.log(Level.SEVERE, "创建用户数据根目录失败 (无 POSIX 支持): " + userDataDirectory, ioException);
                    throw new UncheckedIOException("无法创建用户数据根目录", ioException);
                }
            }
        }
    }


    /**
     * 根据用户 ID 获取用户专属目录路径
     * @param userId 用户 ID
     * @return 用户专属目录路径
     */
    public Path getUserDirectory(String userId) {
        return userDataDirectory.resolve(userId);
    }

    /**
     * 检查用户目录是否存在
     * @param userId 用户 ID
     * @return 用户目录是否存在
     */
    public boolean userDirectoryExists(String userId) {
        // Check if the user's specific directory exists within the user_data root
        return Files.exists(getUserDirectory(userId));
    }

    /**
     * 查找用户名对应的用户 ID
     * 注意：这是一种低效的查找方式，因为它需要扫描所有用户目录并读取 user_profile.json 文件。
     * 在用户数量增多时性能会急剧下降。
     * 更好的方式是维护一个独立的用户名到用户ID的映射文件（如 username_to_userid.json），
     * 但这需要额外的管理和同步机制，且超出了当前基于文件系统的极简约束，故此处采用扫描方式。
     *
     * @param username 用户名
     * @return 用户 ID，如果不存在则返回 null
     */
    public String findUserDirectoryByUsername(String username) {
        if (!Files.exists(userDataDirectory)) {
            LOGGER.warning("用户数据根目录不存在: " + userDataDirectory);
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(userDataDirectory)) {
            for (Path userDir : stream) {
                if (Files.isDirectory(userDir)) {
                    Path userProfilePath = userDir.resolve(USER_PROFILE_FILE);
                    if (Files.exists(userProfilePath)) {
                        try {
                            UserProfile profile = loadUserProfileFromFile(userProfilePath);
                            if (profile != null && username.equals(profile.getUsername())) {
                                return userDir.getFileName().toString(); // 返回目录名，即 userId
                            }
                        } catch (IOException e) {
                            // 忽略读取出错的文件，可能是损坏或权限问题
                            LOGGER.log(Level.WARNING, "读取用户配置文件失败: " + userProfilePath, e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "扫描用户数据目录失败: " + userDataDirectory, e);
        }
        return null; // 未找到匹配的用户名
    }

    /**
     * 检查用户名是否已存在
     * @param username 用户名
     * @return 用户名是否存在
     */
    public boolean usernameExists(String username) {
        return findUserDirectoryByUsername(username) != null;
    }


    /**
     * 删除用户专属目录 (用于注册失败清理等)
     * @param userId 用户 ID
     * @throws IOException 如果删除失败
     */
    public void deleteUserDirectory(String userId) throws IOException {
        Path userDir = getUserDirectory(userId);
        if (Files.exists(userDir)) {
            // 递归删除目录内容
            Files.walk(userDir)
                    .sorted(Comparator.reverseOrder()) // 从最深层文件开始删除
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            LOGGER.finest("已删除: " + path);
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "删除文件/目录失败: " + path, e);
                            // 实际应用中可能需要更复杂的错误处理或重试机制
                        }
                    });
            LOGGER.info("用户目录已删除: " + userDir);
        }
    }


    // --- UserProfile 文件操作 ---

    /**
     * 保存用户配置文件 user_profile.json
     * @param userId 用户 ID
     * @param profile UserProfile 对象
     * @throws IOException 文件写入失败
     */
    public void saveUserProfile(String userId, UserProfile profile) throws IOException {
        Path filePath = getUserDirectory(userId).resolve(USER_PROFILE_FILE);
        // Ensure user directory exists before saving files
        Files.createDirectories(filePath.getParent());
        try (Writer writer = Files.newBufferedWriter(filePath)) {
            gson.toJson(profile, writer);
        }
    }

    /**
     * 加载用户配置文件 user_profile.json
     * @param userId 用户 ID
     * @return UserProfile 对象，如果文件不存在或读取失败则返回 null
     * @throws IOException 文件读取失败
     */
    public UserProfile loadUserProfile(String userId) throws IOException {
        Path filePath = getUserDirectory(userId).resolve(USER_PROFILE_FILE);
        return loadUserProfileFromFile(filePath);
    }

    /**
     * 从指定路径加载用户配置文件
     * @param filePath 文件路径
     * @return UserProfile 对象，如果文件不存在或读取失败则返回 null
     * @throws IOException 文件读取失败
     */
    private UserProfile loadUserProfileFromFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            // LOGGER.warning("用户配置文件不存在: " + filePath); // Too verbose for every check
            return null;
        }
        try (Reader reader = Files.newBufferedReader(filePath)) {
            return gson.fromJson(reader, UserProfile.class);
        } catch (Exception e) { // Catch potential JSON parsing errors
            LOGGER.log(Level.SEVERE, "读取或解析用户配置文件失败: " + filePath, e);
            throw new IOException("读取或解析用户配置文件失败", e);
        }
    }


    // --- SecurityData 文件操作 ---

    /**
     * 保存安全数据 security.dat (属性文件格式)
     * @param userId 用户 ID
     * @param securityData SecurityData 对象
     * @throws IOException 文件写入失败
     */
    public void saveSecurityData(String userId, SecurityData securityData) throws IOException {
        Path filePath = getUserDirectory(userId).resolve(SECURITY_DATA_FILE);
        Files.createDirectories(filePath.getParent()); // Ensure user directory exists

        // Using a temporary Properties instance to avoid concurrency issues if FileManager was used across threads
        Properties currentProperties = new Properties();
        currentProperties.setProperty("hashedPassword", securityData.getHashedPassword());
        currentProperties.setProperty("salt", securityData.getSalt()); // 存储盐值

        try (OutputStream os = Files.newOutputStream(filePath)) {
            currentProperties.store(os, "User Security Data");
        }
    }

    /**
     * 加载安全数据 security.dat
     * @param userId 用户 ID
     * @return SecurityData 对象，如果文件不存在或读取失败则返回 null
     * @throws IOException 文件读取失败
     */
    public SecurityData loadSecurityData(String userId) throws IOException {
        Path filePath = getUserDirectory(userId).resolve(SECURITY_DATA_FILE);
        if (!Files.exists(filePath)) {
            // LOGGER.warning("用户安全数据文件不存在: " + filePath); // Too verbose
            return null;
        }
        // Using a temporary Properties instance for thread safety and fresh load
        Properties currentProperties = new Properties();
        try (InputStream is = Files.newInputStream(filePath)) {
            currentProperties.load(is);
            String hashedPassword = currentProperties.getProperty("hashedPassword");
            String salt = currentProperties.getProperty("salt");
            if (hashedPassword != null && salt != null) {
                return new SecurityData(hashedPassword, salt);
            } else {
                LOGGER.severe("安全数据文件内容不完整: " + filePath);
                // 文件存在但内容不完整，也视为读取失败
                throw new IOException("安全数据文件内容不完整");
            }
        } catch (Exception e) { // Catch potential file reading/parsing errors
            LOGGER.log(Level.SEVERE, "读取或解析用户安全数据文件失败: " + filePath, e);
            throw new IOException("读取或解析用户安全数据文件失败", e);
        }
    }

    // --- SecurityQuestions 文件操作 ---

    /**
     * 保存安全问题列表 security_questions.json
     * @param userId 用户 ID
     * @param questions SecurityQuestion 对象列表 (这里的answer已经是哈希过的)
     * @throws IOException 文件写入失败
     */
    public void saveSecurityQuestions(String userId, List<SecurityQuestion> questions) throws IOException {
        Path filePath = getUserDirectory(userId).resolve(SECURITY_QUESTIONS_FILE);
        Files.createDirectories(filePath.getParent()); // Ensure user directory exists
        try (Writer writer = Files.newBufferedWriter(filePath)) {
            gson.toJson(questions, writer);
        }
    }

    /**
     * 加载安全问题列表 security_questions.json
     * @param userId 用户 ID
     * @return SecurityQuestion 对象列表 (包含哈希答案)，如果文件不存在或读取失败则返回空列表
     * @throws IOException 文件读取失败
     */
    public List<SecurityQuestion> loadSecurityQuestions(String userId) throws IOException {
        Path filePath = getUserDirectory(userId).resolve(SECURITY_QUESTIONS_FILE);
        if (!Files.exists(filePath)) {
            // LOGGER.warning("用户安全问题文件不存在: " + filePath); // Too verbose
            return Collections.emptyList();
        }
        try (Reader reader = Files.newBufferedReader(filePath)) {
            Type listType = new TypeToken<ArrayList<SecurityQuestion>>(){}.getType();
            List<SecurityQuestion> questions = gson.fromJson(reader, listType);
            return questions != null ? questions : Collections.emptyList();
        } catch (Exception e) { // Catch potential JSON parsing errors
            LOGGER.log(Level.SEVERE, "读取或解析用户安全问题文件失败: " + filePath, e);
            throw new IOException("读取或解析用户安全问题文件失败", e);
        }
    }
}
    