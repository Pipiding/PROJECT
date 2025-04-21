
/**
 * 检测季节性消费模式
 * @param transactions 全年交易数据
 * @return 检测结果描述
 */
public static String detectSeasonality(List<Transaction> transactions) {
        // 按月份聚合数据
        double[] monthlyTotals = new double[12];
        transactions.forEach(t -> {
        int month = t.date().getMonthValue()-1;
        monthlyTotals[month] += t.amount();
        });

        // 计算平均值
        double avg = Arrays.stream(monthlyTotals).average().orElse(0);

        // 检测显著波动月份
        List<String> highMonths = new ArrayList<>();
        for(int i=0; i<12; i++) {
        if(monthlyTotals[i] > avg*1.5) {
        highMonths.add(getChineseMonthName(i+1));
        }
        }

        return highMonths.isEmpty() ?
        "未检测到显著季节性消费模式" :
        "检测到以下月份消费显著增加：" + String.join("、", highMonths);
        }

private static String getChineseMonthName(int month) {
        String[] names = {"一月","二月","三月","四月","五月","六月",
        "七月","八月","九月","十月","十一月","十二月"};
        return names[month-1];
        }