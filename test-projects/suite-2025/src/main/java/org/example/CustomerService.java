package org.example;

public class CustomerService extends SuperClass {
    public void processCustomer(String name, int age, String address) {
        // Unused variable (PMD warns)
        int temp = 0;

        // Long method (PMD + DesigniteJava)
        System.out.println("Processing customer: " + name);
        if (age > 18) {
            System.out.println("Adult");
        } else {
            System.out.println("Minor");
        }

        // Duplicate code (PMD DuplicateCode)
        for (int i = 0; i < 3; i++) {
            System.out.println("Checking data...");
        }
        for (int i = 0; i < 3; i++) {
            System.out.println("Checking data...");
        }

        // Feature Envy smell (DesigniteJava)
        address.toLowerCase();
        address.trim();
        address.substring(0, 2);
    }
}

