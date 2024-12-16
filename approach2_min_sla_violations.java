{\rtf1\ansi\ansicpg1252\cocoartf2820
\cocoatextscaling0\cocoaplatform0{\fonttbl\f0\fswiss\fcharset0 Helvetica;}
{\colortbl;\red255\green255\blue255;}
{\*\expandedcolortbl;;}
\paperw11900\paperh16840\margl1440\margr1440\vieww11520\viewh8400\viewkind0
\pard\tx720\tx1440\tx2160\tx2880\tx3600\tx4320\tx5040\tx5760\tx6480\tx7200\tx7920\tx8640\pardirnatural\partightenfactor0

\f0\fs24 \cf0 package org.cloudbus.cloudsim.power;\
\
import java.util.List;\
import java.util.Set; \
\
import org.cloudbus.cloudsim.Host;\
import org.cloudbus.cloudsim.Vm;\
import org.cloudbus.cloudsim.util.MathUtil;\
\
/**\
 * Advanced Local Regression (LR) VM allocation policy to optimize SLA and energy consumption.\
 */\
public class AdvancedPowerVmAllocationPolicy extends PowerVmAllocationPolicyMigrationLocalRegression \{\
\
    private double utilizationThreshold;\
\
    public AdvancedPowerVmAllocationPolicy(\
            List<? extends Host> hostList,\
            PowerVmSelectionPolicy vmSelectionPolicy,\
            double safetyParameter,\
            double schedulingInterval,\
            PowerVmAllocationPolicyMigrationAbstract fallbackVmAllocationPolicy,\
            double utilizationThreshold) \{\
        super(hostList, vmSelectionPolicy, safetyParameter, schedulingInterval, fallbackVmAllocationPolicy);\
        this.utilizationThreshold = utilizationThreshold;\
    \}\
\
    @Override\
    protected boolean isHostOverUtilized(PowerHost host) \{\
        PowerHostUtilizationHistory _host = (PowerHostUtilizationHistory) host;\
        double[] utilizationHistory = _host.getUtilizationHistory();\
        int length = 10; // Use last 10 data points for regression\
\
        if (utilizationHistory.length < length) \{\
            return getFallbackVmAllocationPolicy().isHostOverUtilized(host);\
        \}\
\
        double[] utilizationHistoryReversed = new double[length];\
        for (int i = 0; i < length; i++) \{\
            utilizationHistoryReversed[i] = utilizationHistory[utilizationHistory.length - i - 1];\
        \}\
\
        double[] estimates = null;\
        try \{\
            estimates = MathUtil.getLoessParameterEstimates(utilizationHistoryReversed);\
        \} catch (IllegalArgumentException e) \{\
            return getFallbackVmAllocationPolicy().isHostOverUtilized(host);\
        \}\
\
        double migrationIntervals = Math.ceil(getMaximumVmMigrationTime(_host) / getSchedulingInterval());\
        double predictedUtilization = estimates[0] + estimates[1] * (length + migrationIntervals);\
        predictedUtilization *= getSafetyParameter();\
\
        addHistoryEntry(host, predictedUtilization);\
\
        // Use stricter threshold to minimize SLA violations\
        return predictedUtilization >= utilizationThreshold;\
    \}\
\
    @Override\
    public PowerHost findHostForVm(Vm vm, Set<? extends Host> excludedHosts) \{\
        PowerHost selectedHost = null;\
        double bestPowerEfficiency = Double.MIN_VALUE;\
\
        for (PowerHost host : this.<PowerHost>getHostList()) \{\
            if (excludedHosts.contains(host)) \{\
                continue;\
            \}\
\
            if (!host.isSuitableForVm(vm)) \{\
                continue;\
            \}\
\
            if (isHostOverUtilizedAfterAllocation(host, vm)) \{\
                continue;\
            \}\
\
            double powerEfficiency = host.getTotalMips() / host.getMaxPower();\
\
            if (powerEfficiency > bestPowerEfficiency) \{\
                bestPowerEfficiency = powerEfficiency;\
                selectedHost = host;\
            \}\
        \}\
\
        return selectedHost;\
    \}\
\
    protected double getMaximumVmMigrationTime(PowerHost host) \{\
        int maxRam = Integer.MIN_VALUE;\
        for (Vm vm : host.getVmList()) \{\
            int ram = vm.getRam();\
            if (ram > maxRam) \{\
                maxRam = ram;\
            \}\
        \}\
        return maxRam / ((double) host.getBw() / (2 * 8000));\
    \}\
\
    public void setUtilizationThreshold(double utilizationThreshold) \{\
        this.utilizationThreshold = utilizationThreshold;\
    \}\
\}}