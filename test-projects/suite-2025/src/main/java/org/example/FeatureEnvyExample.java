package org.example;

import javax.swing.*;

public class FeatureEnvyExample extends JPanel {
    private Customer customer;
    private int age;

    // Cette méthode a Feature Envy - elle utilise trop Customer
    public String getCustomerFullInfo() {
        StringBuilder info = new StringBuilder();
        info.append(customer.getFirstName());
        info.append(" ");
        info.append(customer.getLastName());
        info.append(" - ");
        info.append(customer.getEmail());
        info.append(" (");
        info.append(customer.getPhone());
        info.append(")");
        return info.toString();
    }

    public int calculateDiscountForPremium(int amount) {
        int discount = 10;
        if (amount > 1000) {
            discount = amount * 15 / 100;
        } else if (amount > 500) {
            discount = amount * 10 / 100;
        } else if (amount > 100) {
            discount = amount * 5 / 100;
        }
        System.out.println("Calculated discount: " + discount);
        return discount;
    }

}

class Customer {
    private String firstName;
    private String lastName;
    private String email;
    private String phone;

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
}