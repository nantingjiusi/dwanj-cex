package com.remus.dwanjcex.config.jwt;

/**
 * 一个基于ThreadLocal的用户上下文持有者。
 * 用于在单个请求线程中存储和获取当前登录用户的ID。
 *
 * @author Remus
 * @version 1.0
 * @since 2024/7/18 18:10
 */
public class UserContextHolder {

    private static final ThreadLocal<Long> userContext = new ThreadLocal<>();

    /**
     * 设置当前线程的用户ID。
     * @param userId 用户ID
     */
    public static void setCurrentUserId(Long userId) {
        userContext.set(userId);
    }

    /**
     * 获取当前线程的用户ID。
     * @return 用户ID，如果未设置则返回null
     */
    public static Long getCurrentUserId() {
        return userContext.get();
    }

    /**
     * 清理当前线程的用户ID。
     * 必须在请求处理完成后调用，以防内存泄漏。
     */
    public static void clear() {
        userContext.remove();
    }
}
