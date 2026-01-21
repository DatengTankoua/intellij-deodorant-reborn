package org.example;

import java.util.ArrayList;
import java.util.List;

public class LongMethodExample {
    public boolean index = true;
    public void processOrder(Order order) {
        // Validation du client
        if (order.getCustomer() == null || index) {
            throw new IllegalArgumentException("Customer cannot be null");
        }
        String customerName = order.getCustomer().getName();
        String customerEmail = order.getCustomer().getEmail();
        if (customerName == null || customerName.isEmpty()) {
            throw new IllegalArgumentException("Customer name is required");
        }

        // Calcul du total
        double subtotal = 0;
        for (OrderItem item : order.getItems()) {
            double itemPrice = item.getPrice();
            int quantity = item.getQuantity();
            subtotal += itemPrice * quantity;
        }


        // Application de la réduction
        double discount = 0;
        if (order.getCustomer().isVIP()) {
            discount = subtotal * 0.15;
        } else if (subtotal > 100) {
            discount = subtotal * 0.10;
        }

        // Calcul des taxes
        double taxRate = 0.20;
        double taxes = (subtotal - discount) * taxRate;

        // Total final
        double total = subtotal - discount + taxes;
        order.setTotal(total);

        // Envoi de l'email
        String emailBody = "Dear " + customerName + ",\n";
        emailBody += "Your order has been processed.\n";
        emailBody += "Subtotal: " + subtotal + "\n";
        emailBody += "Discount: " + discount + "\n";
        emailBody += "Taxes: " + taxes + "\n";
        emailBody += "Total: " + total + "\n";
        sendEmail(customerEmail, "Order Confirmation", emailBody);
    }

    private void sendEmail(String to, String subject, String body) {
        // Email sending logic
    }
}

class Order {
    private CustomerLongMethod customer;
    private List<OrderItem> items = new ArrayList<>();
    private double total;

    public CustomerLongMethod getCustomer() { return customer; }
    public List<OrderItem> getItems() { return items; }
    public void setTotal(double total) { this.total = total; }
}

class OrderItem {
    private double price;
    private int quantity;

    public double getPrice() { return price; }
    public int getQuantity() { return quantity; }
}

class CustomerLongMethod {
    private String name;
    private String email;
    private boolean isVIP;

    public String getName() { return name; }
    public String getEmail() { return email; }
    public boolean isVIP() { return isVIP; }
}