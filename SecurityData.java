package com.myfinanceapp.auth;

/**
 * 安全数据模型
 * 对应 security.dat (属性文件格式)
 * 注意：尽管定义了 salt 字段，jBCrypt 实际将盐集成在 hashedPasword 中
 */
public class SecurityData {
    private String hashedPassword;
    private String salt; // jBCrypt 集成盐，此字段可能冗余，但为数据结构兼容性保留

    public SecurityData(String hashedPassword, String salt) {
        this.hashedPassword = hashedPassword;
        this.salt = salt;
    }

    // Getters and Setters

    public String getHashedPassword() { return hashedPassword; }
    public void setHashedPassword(String hashedPassword) { this.hashedPassword = hashedPassword; }
    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
}
    