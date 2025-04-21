package models;

import java.time.LocalDate;

/**
 * 交易记录数据模型
 * 使用Java Record实现不可变数据对象
 * @param id          唯一标识（UUID）
 * @param amount      金额（正数）
 * @param category    分类（非空）
 * @param date        日期（非空）
 * @param description 描述（非空）
 */
public record Transaction(
        String id,
        double amount,
        String category,
        LocalDate date,
        String description
) {
    /**
     * 数据有效性验证
     * @return 是否有效
     */
    public boolean isValid() {
        return amount > 0
                && !category.isBlank()
                && date != null
                && !description.isBlank();
    }
}