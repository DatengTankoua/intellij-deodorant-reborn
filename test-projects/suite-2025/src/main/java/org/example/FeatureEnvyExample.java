package org.example;

public class FeatureEnvyExample {
    private Customer customer;

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