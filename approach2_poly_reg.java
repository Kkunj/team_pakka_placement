

import java.util.List;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
//import org.cloudbus.cloudsim.util.MathUtil;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

/**
 * The Polynomial Regression (PR) VM allocation policy.
 * Extends the Local Regression (LR) VM allocation policy to use polynomial regression for better predictions.
 */
public class PowerVmAllocationPolicyMigrationPolynomialRegression extends PowerVmAllocationPolicyMigrationAbstract {

    private double schedulingInterval;
    private double safetyParameter;
    private PowerVmAllocationPolicyMigrationAbstract fallbackVmAllocationPolicy;
    private int polynomialDegree;

    public PowerVmAllocationPolicyMigrationPolynomialRegression(
            List<? extends Host> hostList,
            PowerVmSelectionPolicy vmSelectionPolicy,
            double safetyParameter,
            double schedulingInterval,
            PowerVmAllocationPolicyMigrationAbstract fallbackVmAllocationPolicy,
            int polynomialDegree) {
//        super(hostList, vmSelectionPolicy);
        super(hostList, vmSelectionPolicy != null ? vmSelectionPolicy : new PowerVmSelectionPolicyDefault());
        setSafetyParameter(safetyParameter);
        setSchedulingInterval(schedulingInterval);
        setFallbackVmAllocationPolicy(fallbackVmAllocationPolicy);
        setPolynomialDegree(polynomialDegree);
    }

    public PowerVmAllocationPolicyMigrationPolynomialRegression(
            List<? extends Host> hostList,
            PowerVmSelectionPolicy vmSelectionPolicy,
            double safetyParameter,
            double schedulingInterval,
            PowerVmAllocationPolicyMigrationAbstract fallbackVmAllocationPolicy) {
        this(hostList, vmSelectionPolicy, safetyParameter, schedulingInterval, fallbackVmAllocationPolicy, 5); // Default degree is 2
    }

//    @Override
//    protected boolean isHostOverUtilized(PowerHost host) {
//        PowerHostUtilizationHistory _host = (PowerHostUtilizationHistory) host;
//        double[] utilizationHistory = _host.getUtilizationHistory();
//        int length = 10; // Number of past intervals to consider
//
//        if (utilizationHistory.length < length) {
//            return getFallbackVmAllocationPolicy().isHostOverUtilized(host);
//        }
//
//        double[] utilizationHistoryReversed = new double[length];
//        for (int i = 0; i < length; i++) {
//            utilizationHistoryReversed[i] = utilizationHistory[length - i - 1];
//        }
//
//        double[] coefficients;
//        try {
//            coefficients = getPolynomialCoefficients(utilizationHistoryReversed);
//        } catch (IllegalArgumentException e) {
//            return getFallbackVmAllocationPolicy().isHostOverUtilized(host);
//        }
//
//        double migrationIntervals = Math.ceil(getMaximumVmMigrationTime(_host) / getSchedulingInterval());
//        double predictedUtilization = 0;
//        for (int i = 0; i < coefficients.length; i++) {
//            predictedUtilization += coefficients[i] * Math.pow(length + migrationIntervals, i);
//        }
//        predictedUtilization *= getSafetyParameter();
//
//        addHistoryEntry(host, predictedUtilization);
//
//        return predictedUtilization >= 1;
//    }

    @Override
    protected boolean isHostOverUtilized(PowerHost host) {
        PowerHostUtilizationHistory _host = (PowerHostUtilizationHistory) host;
        double[] utilizationHistory = _host.getUtilizationHistory();
        int length = 10; // Number of past intervals to consider

        if (utilizationHistory.length < length) {
            return getFallbackVmAllocationPolicy().isHostOverUtilized(host);
        }

        double[] utilizationHistoryReversed = new double[length];
        for (int i = 0; i < length; i++) {
            utilizationHistoryReversed[i] = utilizationHistory[length - i - 1];
        }

        // Dynamic threshold based on workload variability
        double meanUtilization = 0;
        for (double util : utilizationHistoryReversed) {
            meanUtilization += util;
        }
        meanUtilization /= length;

        double stdDev = 0;
        for (double util : utilizationHistoryReversed) {
            stdDev += Math.pow(util - meanUtilization, 2);
        }
        stdDev = Math.sqrt(stdDev / length);

        double dynamicThreshold = Math.min(1.0, meanUtilization + (0.5 * stdDev));

        double[] coefficients;
        try {
            coefficients = getPolynomialCoefficients(utilizationHistoryReversed);
        } catch (IllegalArgumentException e) {
            return getFallbackVmAllocationPolicy().isHostOverUtilized(host);
        }

        double migrationIntervals = Math.ceil(getMaximumVmMigrationTime(_host) / getSchedulingInterval());
        double predictedUtilization = 0;
        for (int i = 0; i < coefficients.length; i++) {
            predictedUtilization += coefficients[i] * Math.pow(length + migrationIntervals, i);
        }
        predictedUtilization *= getSafetyParameter();

        addHistoryEntry(host, predictedUtilization);

        return predictedUtilization >= dynamicThreshold;
    }
    
    private double[] getPolynomialCoefficients(double[] utilizationHistoryReversed) {
        return calculatePolynomialCoefficients(utilizationHistoryReversed, polynomialDegree);
    }

    protected double getMaximumVmMigrationTime(PowerHost host) {
        int maxRam = Integer.MIN_VALUE;
        for (Vm vm : host.getVmList()) {
            int ram = vm.getRam();
            if (ram > maxRam) {
                maxRam = ram;
            }
        }
        return maxRam / ((double) host.getBw() / (2 * 8000));
    }

    protected void setSchedulingInterval(double schedulingInterval) {
        this.schedulingInterval = schedulingInterval;
    }

    protected double getSchedulingInterval() {
        return schedulingInterval;
    }

    public void setFallbackVmAllocationPolicy(PowerVmAllocationPolicyMigrationAbstract fallbackVmAllocationPolicy) {
        this.fallbackVmAllocationPolicy = fallbackVmAllocationPolicy;
    }

    public PowerVmAllocationPolicyMigrationAbstract getFallbackVmAllocationPolicy() {
        return fallbackVmAllocationPolicy;
    }

    public double getSafetyParameter() {
        return safetyParameter;
    }

    public void setSafetyParameter(double safetyParameter) {
        this.safetyParameter = safetyParameter;
    }

    public int getPolynomialDegree() {
        return polynomialDegree;
    }

    public void setPolynomialDegree(int polynomialDegree) {
        this.polynomialDegree = polynomialDegree;
    }

    public static OLSMultipleLinearRegression createWeightedPolynomialRegression(final double[][] x, final double[] y, final double[] weights) {
        double[][] xW = new double[x.length][x[0].length];
        double[] yW = new double[y.length];

        // Calculate effective weights and transform input data
        for (int i = 0; i < y.length; i++) {
            double weight = Math.sqrt(weights[i]);
            yW[i] = y[i] * weight;
            for (int j = 0; j < x[i].length; j++) {
                xW[i][j] = x[i][j] * weight;
            }
        }

        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.newSampleData(yW, xW);
        return regression;
    }

    public static double[] getTricubeWeights(final int n) {
        double[] weights = new double[n];
        double top = n - 1;
        double spread = top;
        for (int i = 2; i < n; i++) {
            double k = Math.pow(1 - Math.pow((top - i) / spread, 3), 3);
            if (k > 0) {
                weights[i] = 1 / k;
            } else {
                weights[i] = Double.MAX_VALUE;
            }
        }
        weights[0] = weights[1] = weights[2];
        return weights;
    }

    public static double[] calculatePolynomialCoefficients(final double[] y, int degree) {
        int n = y.length;
        double[][] x = new double[n][degree + 1];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= degree; j++) {
                x[i][j] = Math.pow(i + 1, j);
            }
        }
        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.newSampleData(y, x);
        return regression.estimateRegressionParameters();
    }
}


