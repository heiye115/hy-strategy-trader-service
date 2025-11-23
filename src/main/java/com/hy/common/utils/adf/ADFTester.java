package com.hy.common.utils.adf;

import org.apache.commons.math3.linear.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * ADFTester - 优化版 ADF（Augmented Dickey-Fuller）检验 Java 实现
 * 说明：
 * - 使用 Apache Commons Math 执行 OLS 与矩阵求逆，确保数值稳定性
 * - 支持三种检验类型：无常数、有常数、有常数和趋势
 * - 自动选择滞后阶数（基于 AIC）
 * - 根据样本量动态确定临界值
 */
public class ADFTester {

    /**
     * ADF 检验类型
     */
    public enum ADFTestType {
        NO_CONSTANT,      // 无常数项无趋势项：Δy_t = γ * y_{t-1} + Σφ_i Δy_{t-i} + ε_t
        CONSTANT,         // 有常数项无趋势项：Δy_t = α + γ * y_{t-1} + Σφ_i Δy_{t-i} + ε_t
        CONSTANT_TREND    // 有常数项和趋势项：Δy_t = α + β*t + γ * y_{t-1} + Σφ_i Δy_{t-i} + ε_t
    }

    /**
     * ADF 检验结果
     */
    public static class ADFResult {
        public final double tStat;              // γ 的 t-statistic
        public final int lags;                 // 最优滞后阶数
        public final boolean rejectAt1Percent; // 1% 显著性水平下拒绝原假设
        public final boolean rejectAt5Percent; // 5% 显著性水平下拒绝原假设
        public final boolean rejectAt10Percent; // 10% 显著性水平下拒绝原假设
        public final double aicValue;          // AIC 值
        public final int sampleSize;           // 有效样本量
        public final ADFTestType testType;     // 检验类型

        public ADFResult(double tStat, int lags, boolean rejectAt1Percent,
                         boolean rejectAt5Percent, boolean rejectAt10Percent,
                         double aicValue, int sampleSize, ADFTestType testType) {
            this.tStat = tStat;
            this.lags = lags;
            this.rejectAt1Percent = rejectAt1Percent;
            this.rejectAt5Percent = rejectAt5Percent;
            this.rejectAt10Percent = rejectAt10Percent;
            this.aicValue = aicValue;
            this.sampleSize = sampleSize;
            this.testType = testType;
        }

        /**
         * 判断序列是否平稳（5%显著性水平）
         */
        public boolean isStationary() {
            return rejectAt5Percent;
        }

        @Override
        public String toString() {
            return String.format("ADFResult{tStat=%.4f, lags=%d, stationary=%s, AIC=%.2f, n=%d}",
                    tStat, lags, isStationary(), aicValue, sampleSize);
        }
    }

    /**
     * 运行 ADF 检验（默认使用带常数项的检验）
     */
    public static ADFResult runADF(List<BigDecimal> series, int maxLag) {
        return runADF(series, maxLag, ADFTestType.CONSTANT);
    }

    /**
     * 运行 ADF 检验（自动选择滞后阶数）
     */
    public static ADFResult runADF(List<BigDecimal> series, ADFTestType testType) {
        return runADF(series, 0, testType);
    }

    /**
     * 运行完整的 ADF 检验
     *
     * @param series   时间序列数据（按时间顺序）
     * @param maxLag   最大滞后阶数（如果≤0则自动选择）
     * @param testType 检验类型
     * @return ADF 检验结果
     */
    public static ADFResult runADF(List<BigDecimal> series, int maxLag, ADFTestType testType) {
        // 输入验证
        if (series == null || series.size() < 20) {
            throw new IllegalArgumentException("序列需要至少20个观测值");
        }

        // 检查是否为常数序列
        if (isConstantSequence(series)) {
            throw new IllegalArgumentException("序列不能为常数序列");
        }

        int n = series.size();
        double[] y = convertToDoubleArray(series);

        // 自动选择最大滞后阶数
        if (maxLag <= 0) {
            maxLag = autoSelectMaxLag(n);
        }
        maxLag = Math.max(0, Math.min(20, maxLag));

        // 选择最优滞后阶数
        LagSelectionResult lagResult = selectBestLag(y, maxLag, testType);
        int bestLag = lagResult.lag;
        double bestAIC = lagResult.aic;

        // 计算 ADF 检验统计量
        double tStat = computeADFtStat(y, bestLag, testType);

        // 获取临界值并判断显著性
        double critical1Pct = getCriticalValue(n, "1%", testType);
        double critical5Pct = getCriticalValue(n, "5%", testType);
        double critical10Pct = getCriticalValue(n, "10%", testType);

        boolean rejectAt1Percent = tStat < critical1Pct;
        boolean rejectAt5Percent = tStat < critical5Pct;
        boolean rejectAt10Percent = tStat < critical10Pct;

        return new ADFResult(tStat, bestLag, rejectAt1Percent, rejectAt5Percent,
                rejectAt10Percent, bestAIC, n, testType);
    }

    // ==================== 核心计算逻辑 ====================

    /**
     * 计算 ADF 检验的 t 统计量
     */
    private static double computeADFtStat(double[] y, int p, ADFTestType testType) {
        int n = y.length;
        if (n < getMinSampleSize(p, testType)) {
            return Double.NaN;
        }

        // 构造设计矩阵和因变量
        MatrixData matrixData = buildADFMatrix(y, p, testType);
        double[][] X = matrixData.X;
        double[] dep = matrixData.dep;
        int T = matrixData.T;
        int k = matrixData.k;

        // 使用 OLS 估计参数
        double[] beta = ols(X, dep);

        // 计算残差和标准误
        int Tdf = T - k; // 自由度
        if (Tdf <= 0) return Double.NaN;

        double[] residuals = calculateResiduals(X, dep, beta);
        double sse = sumSquared(residuals);
        double sigma2 = sse / Tdf;

        // 计算 γ 系数（对应 y_{t-1}）的方差
        double[][] XtXinv = calculateXtXInverse(X);
        int gammaIndex = getGammaIndex(testType); // γ 系数在 beta 中的位置

        double varGamma = sigma2 * XtXinv[gammaIndex][gammaIndex];

        // 返回 t 统计量
        return beta[gammaIndex] / Math.sqrt(varGamma);
    }

    /**
     * 构建 ADF 检验的设计矩阵
     */
    private static MatrixData buildADFMatrix(double[] y, int p, ADFTestType testType) {
        int n = y.length;
        int T = n - p - 1;

        // 确定回归变量个数
        int baseVars = 1; // γ * y_{t-1}
        if (testType != ADFTestType.NO_CONSTANT) baseVars++; // 常数项
        if (testType == ADFTestType.CONSTANT_TREND) baseVars++; // 趋势项

        int k = baseVars + p; // 总变量数
        double[][] X = new double[T][k];
        double[] dep = new double[T];

        for (int t = p + 1; t < n; t++) {
            int row = t - p - 1;
            int col = 0;

            // 因变量：Δy_t
            dep[row] = y[t] - y[t - 1];

            // 常数项（如果需要）
            if (testType != ADFTestType.NO_CONSTANT) {
                X[row][col++] = 1.0;
            }

            // 趋势项（如果需要）
            if (testType == ADFTestType.CONSTANT_TREND) {
                X[row][col++] = t; // 时间趋势
            }

            // γ * y_{t-1}
            X[row][col++] = y[t - 1];

            // 滞后差分项：Δy_{t-1}, ..., Δy_{t-p}
            for (int j = 1; j <= p; j++) {
                X[row][col++] = y[t - j] - y[t - j - 1];
            }
        }

        return new MatrixData(X, dep, T, k);
    }

    /**
     * 选择最优滞后阶数
     */
    private static LagSelectionResult selectBestLag(double[] y, int maxLag, ADFTestType testType) {
        int bestLag = 0;
        double bestAIC = Double.POSITIVE_INFINITY;

        for (int p = 0; p <= maxLag; p++) {
            double aic = computeAIC(y, p, testType);
            if (!Double.isNaN(aic) && aic < bestAIC) {
                bestAIC = aic;
                bestLag = p;
            }
        }

        return new LagSelectionResult(bestLag, bestAIC);
    }

    /**
     * 计算 AIC 值
     */
    private static double computeAIC(double[] y, int p, ADFTestType testType) {
        int n = y.length;
        if (n < getMinSampleSize(p, testType)) {
            return Double.NaN;
        }

        MatrixData matrixData = buildADFMatrix(y, p, testType);
        double[][] X = matrixData.X;
        double[] dep = matrixData.dep;
        int T = matrixData.T;
        int k = matrixData.k;

        double[] beta = ols(X, dep);
        double[] residuals = calculateResiduals(X, dep, beta);
        double sse = sumSquared(residuals);

        // 使用无偏估计计算 sigma^2
        double sigma2 = sse / (T - k);
        return T * Math.log(sigma2) + 2 * k;
    }

    // ==================== 工具方法 ====================

    /**
     * 根据样本量自动选择最大滞后阶数
     */
    private static int autoSelectMaxLag(int sampleSize) {
        return (int) Math.floor(4 * Math.pow(sampleSize / 100.0, 1.0 / 4.0));
    }

    /**
     * 获取最小样本量要求
     */
    private static int getMinSampleSize(int p, ADFTestType testType) {
        int baseVars = 1; // γ * y_{t-1}
        if (testType != ADFTestType.NO_CONSTANT) baseVars++;
        if (testType == ADFTestType.CONSTANT_TREND) baseVars++;

        return p + baseVars + 5; // 至少比变量数多5个观测值
    }

    /**
     * 获取 γ 系数在参数向量中的位置
     */
    private static int getGammaIndex(ADFTestType testType) {
        int index = 0;
        if (testType != ADFTestType.NO_CONSTANT) index++;
        if (testType == ADFTestType.CONSTANT_TREND) index++;
        return index;
    }

    /**
     * 根据样本量和检验类型获取临界值
     */
    private static double getCriticalValue(int sampleSize, String level, ADFTestType testType) {
        // 简化的 MacKinnon 临界值表（实际应用中应该使用更精确的表）
        if (testType == ADFTestType.NO_CONSTANT) {
            if (sampleSize <= 25) {
                return "1%".equals(level) ? -2.66 : "5%".equals(level) ? -1.95 : -1.60;
            } else if (sampleSize <= 50) {
                return "1%".equals(level) ? -2.62 : "5%".equals(level) ? -1.95 : -1.61;
            } else if (sampleSize <= 100) {
                return "1%".equals(level) ? -2.60 : "5%".equals(level) ? -1.95 : -1.61;
            } else {
                return "1%".equals(level) ? -2.58 : "5%".equals(level) ? -1.95 : -1.62;
            }
        } else if (testType == ADFTestType.CONSTANT) {
            if (sampleSize <= 25) {
                return "1%".equals(level) ? -3.75 : "5%".equals(level) ? -3.00 : -2.63;
            } else if (sampleSize <= 50) {
                return "1%".equals(level) ? -3.58 : "5%".equals(level) ? -2.93 : -2.60;
            } else if (sampleSize <= 100) {
                return "1%".equals(level) ? -3.51 : "5%".equals(level) ? -2.89 : -2.58;
            } else {
                return "1%".equals(level) ? -3.46 : "5%".equals(level) ? -2.88 : -2.57;
            }
        } else { // CONSTANT_TREND
            if (sampleSize <= 25) {
                return "1%".equals(level) ? -4.38 : "5%".equals(level) ? -3.60 : -3.24;
            } else if (sampleSize <= 50) {
                return "1%".equals(level) ? -4.15 : "5%".equals(level) ? -3.50 : -3.18;
            } else if (sampleSize <= 100) {
                return "1%".equals(level) ? -4.04 : "5%".equals(level) ? -3.45 : -3.15;
            } else {
                return "1%".equals(level) ? -3.99 : "5%".equals(level) ? -3.43 : -3.13;
            }
        }
    }

    /**
     * 检查序列是否为常数序列
     */
    private static boolean isConstantSequence(List<BigDecimal> series) {
        if (series.isEmpty()) return true;
        BigDecimal first = series.getFirst();
        for (BigDecimal value : series) {
            if (value.compareTo(first) != 0) {
                return false;
            }
        }
        return true;
    }

    private static double[] convertToDoubleArray(List<BigDecimal> series) {
        double[] result = new double[series.size()];
        for (int i = 0; i < series.size(); i++) {
            result[i] = series.get(i).doubleValue();
        }
        return result;
    }

    private static double[] calculateResiduals(double[][] X, double[] y, double[] beta) {
        int n = y.length;
        double[] residuals = new double[n];
        for (int i = 0; i < n; i++) {
            double fitted = 0.0;
            for (int j = 0; j < beta.length; j++) {
                fitted += X[i][j] * beta[j];
            }
            residuals[i] = y[i] - fitted;
        }
        return residuals;
    }

    private static double sumSquared(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value * value;
        }
        return sum;
    }

    // ==================== 线性代数工具方法 ====================

    /**
     * OLS 回归（使用 Apache Commons Math）
     */
    private static double[] ols(double[][] X, double[] y) {
        try {
            RealMatrix XM = new Array2DRowRealMatrix(X, false);
            RealMatrix Xt = XM.transpose();
            RealMatrix XtX = Xt.multiply(XM);

            DecompositionSolver solver;
            try {
                solver = new LUDecomposition(XtX).getSolver();
            } catch (SingularMatrixException e) {
                // 如果 LU 分解失败，尝试 QR 分解
                solver = new QRDecomposition(XtX).getSolver();
            }

            RealVector yV = new ArrayRealVector(y, false);
            RealVector bv = solver.solve(Xt.operate(yV));
            return bv.toArray();

        } catch (Exception e) {
            throw new RuntimeException("OLS 回归失败: " + e.getMessage(), e);
        }
    }

    /**
     * 计算 X'X 的逆矩阵
     */
    private static double[][] calculateXtXInverse(double[][] X) {
        RealMatrix XM = new Array2DRowRealMatrix(X, false);
        RealMatrix XtX = XM.transpose().multiply(XM);

        try {
            RealMatrix inv = new LUDecomposition(XtX).getSolver().getInverse();
            return inv.getData();
        } catch (SingularMatrixException e) {
            // 如果矩阵奇异，使用伪逆
            RealMatrix inv = new QRDecomposition(XtX).getSolver().getInverse();
            return inv.getData();
        }
    }

    /**
     * 计算 X'X 矩阵（对称优化）
     */
    private static double[][] multiplyXtX(double[][] X) {
        int T = X.length, k = X[0].length;
        double[][] r = new double[k][k];

        for (int i = 0; i < k; i++) {
            for (int j = i; j < k; j++) {
                double sum = 0;
                for (double[] x : X) {
                    sum += x[i] * x[j];
                }
                r[i][j] = sum;
                r[j][i] = sum;
            }
        }
        return r;
    }

    // ==================== 内部辅助类 ====================

    private static class MatrixData {
        final double[][] X;
        final double[] dep;
        final int T;
        final int k;

        MatrixData(double[][] X, double[] dep, int T, int k) {
            this.X = X;
            this.dep = dep;
            this.T = T;
            this.k = k;
        }
    }

    private static class LagSelectionResult {
        final int lag;
        final double aic;

        LagSelectionResult(int lag, double aic) {
            this.lag = lag;
            this.aic = aic;
        }
    }
}