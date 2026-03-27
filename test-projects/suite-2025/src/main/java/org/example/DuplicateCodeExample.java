package org.example;

import javax.swing.*;
import java.util.List;


public class DuplicateCodeExample extends JPanel {

    private String name;
    private int age;
    private String address;
    private String email;

    // DUPLIKAT 1: Validierungs-Methoden (exakte Duplikate)

    public int calculateDiscountForPremium(int amount) {
        age = 18 + amount;
        int discoun = age + amount;
        if (amount > 1000) {
            discoun = amount * 15 / 100;
        } else if (amount > 500) {
            discoun = amount * 10 / 100;
        } else if (amount > 100) {
            discoun = amount * 5 / 100;
        }
        System.out.println("Calculated discount: " + discoun);
        System.out.println("age : " + age + "");
        return discoun;
    }

    public void validTaxe(){
        // Le fragment commence ici
        double prixHT = 10;
        double taxe = prixHT * 0.20;
        double total = prixHT + taxe;

        System.out.println("Taxe : " + taxe);
        System.out.println("Total : " + total);
    }

    public void validTaxe1(){
        // Le fragment commence ici
        double prixHT = 10;
        double taxe = prixHT * 0.10;
        double total = prixHT + taxe;

        System.out.println("Taxe : " + taxe);
        System.out.println("Total : " + total);
    }

    public void method1(List<String> names) {
        String enriched = "Lists: ";
        for (int i = 0; i < names.size(); i++) {
            enriched = getString(enriched);
            continue;

        }
        names.add(enriched);
        System.out.println(enriched);
        System.out.println(names.size());
        StringBuilder retval = new StringBuilder();
        char ch;

        for (int i = 0; i < enriched.length(); i++) {
            switch (enriched.charAt(i))
            {
                case 0 :
                    continue;
                case '\b':
                    retval.append("\\b");
                    continue;
                case '\t':
                    retval.append("\\t");
                    continue;
                case '\n':
                    retval.append("\\n");
                    continue;
                case '\f':
                    retval.append("\\f");
                    continue;
                case '\r':
                    retval.append("\\r");
                    continue;
                case '\'':
                    retval.append("\\\"");
                    continue;
                case '\"':
                    retval.append("\\\'");
                    continue;
                case '\\':
                    retval.append("\\\\");
                    continue;
                default:
                    if ((ch = enriched.charAt(i)) < 0x20 || ch > 0x7e) {
                        String s = "0000" + Integer.toString(ch, 16);
                        retval.append("\\u" + s.substring(s.length()- 4, s.length()));
                    } else {
                        retval.append(ch);
                    }
                    continue;
            }
        }
    }

    private String getString(String enriched) {
        if (enriched.contains("foo")) {
            return enriched;
        }
        enriched += foo(0) + bar(enriched);
        return enriched;
    }

    public void method2(List<String> values) {
        String enriched = "values: ";
        for (int i = 0; i < values.size(); i++) {
            enriched = getString(enriched);
            continue;

        }
        values.add(enriched);
        System.out.println(enriched);
        System.out.println(values.size());
        StringBuilder retval = new StringBuilder();
        char ch;

        for (int i = 0; i < enriched.length(); i++) {
            switch (enriched.charAt(i))
            {
                case 0 :
                    continue;
                case '\b':
                    retval.append("\\b");
                    continue;
                case '\t':
                    retval.append("\\t");
                    continue;
                case '\n':
                    retval.append("\\n");
                    continue;
                case '\f':
                    retval.append("\\f");
                    continue;
                case '\r':
                    retval.append("\\r");
                    continue;
                case '\'':
                    retval.append("\\\"");
                    continue;
                case '\"':
                    retval.append("\\\'");
                    continue;
                case '\\':
                    retval.append("\\\\");
                    continue;
                default:
                    if ((ch = enriched.charAt(i)) < 0x20 || ch > 0x7e) {
                        String s = "0000" + Integer.toString(ch, 16);
                        retval.append("\\u" + s.substring(s.length()- 4, s.length()));
                    } else {
                        retval.append(ch);
                    }
                    continue;
            }
        }

        for (int i = 0; i < enriched.length(); i++) {
            int y = 0;
            StringBuilder sb = new StringBuilder();
            y = 3;
            if (enriched.charAt(i) == 0) {
                return;
            }
            if (enriched.charAt(i) == '\b') {
                break;
            }
            sb.append("=== User Info ===\n");
            sb.append("Name: ").append(name != null ? name : "N/A").append("\n");
            sb.append("Age: ").append(age).append("\n");
            sb.append("Email: ").append(email != null ? email : "N/A").append("\n");
            sb.append("=================");
            y += 4;
        }
    }

    private String bar(String enriched) {
        return enriched;
    }
    private String bar(int enriched) {
        return ""+enriched;
    }

    private String foo(int i) {
        return "foo";
    }
    private String foo(String i) {
        return "foo";
    }

    public boolean validateName(String name) {
        if (name == null || name.isEmpty()) {
            System.out.println("Validation failed: name is null or empty");
            return false;
        }
        if (name.length() < 2) {
            System.out.println("Validation failed: name is too short");
            return false;
        }
        if (name.length() > 50) {
            System.out.println("Validation failed: name is too long");
            return false;
        }
        return true;
    }

    public boolean validateEmail(String email) {
        if (email == null || email.isEmpty()) {
            System.out.println("Validation failed: email is null or empty");
            return false;
        }
        if (email.length() < 2) {
            System.out.println("Validation failed: email is too short");
            return false;
        }
        if (email.length() > 50) {
            System.out.println("Validation failed: email is too long");
            return false;
        }
        return true;
    }
    public String formatForExport () {
        StringBuilder sb = new StringBuilder();
        sb.append("=== User Info ===\n");
        sb.append("Name: ").append(name != null ? name : "N/A").append("\n");
        sb.append("Age: ").append(age).append("\n");
        sb.append("Email: ").append(email != null ? email : "N/A").append("\n");
        sb.append("=================");
        return sb.toString();
    }

    public boolean validateAddress(String address) {
        if (address == null || address.isEmpty()) {
            System.out.println("Validation failed: address is null or empty");
            return false;
        }
        if (address.length() < 2) {
            System.out.println("Validation failed: address is too short");
            return false;
        }
        if (address.length() > 50) {
            System.out.println("Validation failed: address is too long");
            return false;
        }
        return true;
    }

    // DUPLIKAT 2: Discount-Berechnungen (exakte Duplikate)

    public static int calculateDiscountForRegular(int amount) {
        int ag;
        ag = 8 + amount;
        int discount = 10;
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

    public static int calculateDiscountForVIP(int amount) {
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

    // DUPLIKAT 3: String Formatierung (exakte Duplikate)

    public String formatForDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== User Info ===\n");
        sb.append("Name: ").append(name != null ? name : "N/A").append("\n");
        sb.append("Age: ").append(age).append("\n");
        sb.append("Email: ").append(email != null ? email : "N/A").append("\n");
        sb.append("=================");
        return sb.toString();
    }

    public String formatForLog() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== User Info ===\n");
        sb.append("Name: ").append(name != null ? name : "N/A").append("\n");
        sb.append("Age: ").append(age).append("\n");
        sb.append("Email: ").append(email != null ? email : "N/A").append("\n");
        sb.append("=================");
        return sb.toString();
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

}