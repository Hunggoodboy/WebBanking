package com.bankingeconomy.config.database;

public class DbContextHolder {
    private static final ThreadLocal<DbType> contextHolder = new ThreadLocal<>();

    public static void setCurrentDb(DbType dbType) {
        contextHolder.set(dbType);
    }

    public static DbType getCurrentDb() {
        return contextHolder.get();
    }

    public static void clear() {
        contextHolder.remove();
    }
}