package org.example;

public class GodClassExample {
    // Groupe 1 : Gestion des utilisateurs
    private String userName;
    private String userEmail;
    private int userAge;
    private String name;
    private int age;
    private String email;

    public void setUserName(String name) {
        this.userName = name;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserEmail(String email) {
        this.userEmail = email;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void validateUserAge() {
        if (userAge < 18) {
            System.out.println("User is minor");
        }
    }

    // Groupe 2 : Gestion des produits
    private String productName;
    private double productPrice;
    private int productStock;

    public void setProductName(String name) {
        this.productName = name;
    }

    public String getProductName() {
        return productName;
    }

    public void calculateProductDiscount() {
        productPrice = productPrice * 0.9;
    }

    public void updateStock(int quantity) {
        productStock += quantity;
    }

    public boolean isInStock() {
        return productStock > 0;
    }
}
