package com.roki.core.commands;

// Class to hold faction information
public class FactionInfo {
    private final String name;
    private final double vaultBalance;

    public FactionInfo(String name, double vaultBalance) {
        this.name = name;
        this.vaultBalance = vaultBalance;
    }

    public String getName() {
        return name;
    }

    public double getVaultBalance() {
        return vaultBalance;
    }
}