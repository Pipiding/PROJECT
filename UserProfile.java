package com.myfinanceapp.auth;

import java.util.Date;

/**
 * 用户配置文件数据模型
 * 对应 user_profile.json
 */
public class UserProfile {
    private String userId;
    private String username; // 根据用户确认，username 作为登录标识符
    private String preferredLocale;
    private Date creationDate; // 使用 Date 类型方便 Gson 处理 ISO 8601
    private Date lastLoginDate; // 使用 Date 类型方便 Gson 处理 ISO 8601
    // ... 其他用户偏好设置字段

    // Getters and Setters

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPreferredLocale() { return preferredLocale; }
    public void setPreferredLocale(String preferredLocale) { this.preferredLocale = preferredLocale; }
    public Date getCreationDate() { return creationDate; }
    public void setCreationDate(Date creationDate) { this.creationDate = creationDate; }
    public Date getLastLoginDate() { return lastLoginDate; }
    public void setLastLoginDate(Date lastLoginDate) { this.lastLoginDate = lastLoginDate; }
}
    