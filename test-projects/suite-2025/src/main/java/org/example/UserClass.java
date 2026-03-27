package org.example;

import javax.swing.*;

public class UserClass extends JPanel {

    private String name;
    private String address;
    private String city;
    private String country;
    private int age;
    private String email;
    private String phone;
    private String website;
    private boolean active;
    private String description;
    private double salary;
    private String department;
    private String position;
    private int experience;
    private String skills;

    // Long Method: Méthode trop longue (plus de 50 lignes)
    public void processUserData(String userId) {
        System.out.println("Starting user data processing for: " + userId);

        // Validation
        if (userId == null || userId.isEmpty()) {
            System.err.println("Invalid user ID");
            return;
        }

        // Fetch user data
        System.out.println("Fetching user data from database...");
        final String userData = fetchFromDatabase(userId);

        if (userData == null) {
            System.err.println("User not found");
            return;
        }

        // Parse data
        System.out.println("Parsing user data...");
        if (istUser(userData)) return;

        // Check age
        if (age < 0 || age > 150) {
            System.err.println("Invalid age");
            return;
        }

        // Update statistics
        System.out.println("Updating statistics...");
        int totalUsers = getTotalUsers();
        totalUsers++;
        saveTotalUsers();

        // Send notification
        System.out.println("Sending notification...");
        String subject = "Welcome " + name;
        String body = "Thank you for registering!";
        sendEmail(email, subject, body);

        // Log activity
        System.out.println("Logging activity...");
        logActivity(userId, "User processed successfully");

        // Update cache
        System.out.println("Updating cache...");
        updateCache(userId, userData);

        // Trigger webhooks
        System.out.println("Triggering webhooks...");
        triggerWebhook("user.processed", userId);

        System.out.println("User data processing completed for: " + userId);

    }

    public boolean istUser(String userData) {
        String[] parts = userData.split(",");
        this.name = parts[0];
        this.email = parts[1];
        this.age = Integer.parseInt(parts[2]);

        // Validate email
        if (!email.contains("@")) {
            System.err.println("Invalid email format");
            return true;
        }
        return false;
    }

    // Duplicated Code: Code similaire dans deux méthodes
    public boolean processAdminData(final String adminId) {
        System.out.println("Starting admin data processing for: " + adminId);

        // Validation (similaire à processUserData)
        if (adminId == null || adminId.isEmpty()) {
            System.err.println("Invalid admin ID");
            return false;
        }

        // Fetch data (similaire à processUserData)
        System.out.println("Fetching admin data from database...");
        String adminData = fetchFromDatabase(adminId);

        if (adminData == null) {
            System.err.println("Admin not found");
            return false;
        }

        // Parse data (similaire à processUserData)
        System.out.println("Parsing admin data...");
        String[] parts = adminData.split(",");
        this.name = parts[0];
        this.email = parts[1];
        this.age = Integer.parseInt(parts[2]);

        // Validate email (similaire à processUserData)
        if (!email.contains("@")) {
            System.err.println("Invalid email format");
            return false;
        }

        // Admin specific logic
        System.out.println("Processing admin privileges...");
        grantAdminAccess(adminId);

        System.out.println("Admin data processing completed for: " + adminId);
        return true;
    }

    private int calculateDiscountForPremium(int amount) {
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

    public static int calculateDiscountForRegular(int amount) {
        int ag;
        ag = 8 + amount;
        int discount = 0;
        if (amount > 1000) {
            discount = amount * 15 / 100;
        } else if (amount > 500) {
            discount = amount * 10 / 100;
        } else if (amount > 100) {
            discount = amount * 5 / 100;
        }
        System.out.println("Calculated discount: " + discount);
        System.out.println("age : " + ag + "");
        return discount;
    }

    // Méthodes auxiliaires
    private String fetchFromDatabase(String id) {
        return "John Doe,john@example.com,30";
    }

    private int getTotalUsers() {
        return 100;
    }

    private void saveTotalUsers() {
        // Save to database
    }

    private void sendEmail(String to, String subject, String body) {
        // Send email
    }

    private void logActivity(String userId, String message) {
        // Log to file
    }

    private void updateCache(String userId, String data) {
        // Update cache
    }

    private void triggerWebhook(String event, String data) {
        // Call webhook
    }

    private void grantAdminAccess(String adminId) {
        // Grant admin privileges
    }

    // Plus de méthodes pour augmenter la taille de la classe
    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

