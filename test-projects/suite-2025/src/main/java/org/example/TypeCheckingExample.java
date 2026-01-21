package org.example;

/**
 * EXEMPLE DE CODE SMELL: TYPE CHECKING
 *
 * Ce code utilise des if/else pour vérifier le type d'un objet
 * et exécuter différentes logiques selon le type.
 *
 * PROBLÈME:
 * - Violation du principe Open/Closed (OCP)
 * - Code difficile à maintenir
 * - Ajout d'un nouveau type nécessite modification partout
 *
 * SOLUTION: Replace Conditional with Polymorphism
 * - Créer une hiérarchie de classes avec polymorphisme
 * - Chaque type implémente son propre comportement
 */
public class TypeCheckingExample {

    // Constantes pour les types d'employés
    public static final int ENGINEER = 0;
    public static final int SALESMAN = 1;
    public static final int MANAGER = 2;

    /**
     * EXEMPLE 1: Type Checking avec constantes
     * IntelliJDeodorant devrait détecter ce pattern et suggérer
     * de remplacer par une hiérarchie avec polymorphisme
     */
    public double calculateSalary(Employee employee) {
        double salary = 0;

        // Type checking avec switch - CODE SMELL!
        switch (employee.getType()) {
            case ENGINEER:
                salary = employee.getBaseSalary() * 1.2;
                if (employee.getExperience() > 5) {
                    salary += 5000;
                }
                break;

            case SALESMAN:
                salary = employee.getBaseSalary() * 1.1;
                salary += employee.getCommission();
                break;

            case MANAGER:
                salary = employee.getBaseSalary() * 1.5;
                salary += employee.getBonus();
                if (employee.hasTeam()) {
                    salary += 10000;
                }
                break;

            default:
                salary = employee.getBaseSalary();
        }

        return salary;
    }

    /**
     * EXEMPLE 2: Type Checking avec if/else
     * Calcul des congés selon le type d'employé
     */
    public int calculateVacationDays(Employee employee) {
        int vacationDays = 0;

        // Type checking avec if/else - CODE SMELL!
        if (employee.getType() == ENGINEER) {
            vacationDays = 20;
            if (employee.getExperience() > 5) {
                vacationDays += 5;
            }
        } else if (employee.getType() == SALESMAN) {
            vacationDays = 15;
        } else if (employee.getType() == MANAGER) {
            vacationDays = 25;
            if (employee.hasTeam()) {
                vacationDays += 5;
            }
        }

        return vacationDays;
    }

    /**
     * EXEMPLE 3: Type Checking dans le calcul de bonus
     */
    public double calculateBonus(Employee employee, double revenue) {
        double bonus = 0;

        switch (employee.getType()) {
            case ENGINEER:
                // Les ingénieurs reçoivent un bonus fixe
                bonus = 2000;
                break;

            case SALESMAN:
                // Les vendeurs reçoivent un pourcentage du revenu
                bonus = revenue * 0.05;
                break;

            case MANAGER:
                // Les managers reçoivent un pourcentage plus élevé
                bonus = revenue * 0.10;
                if (employee.hasTeam()) {
                    bonus += 5000;
                }
                break;
        }

        return bonus;
    }

    /**
     * EXEMPLE 4: Type Checking pour la description
     */
    public String getEmployeeDescription(Employee employee) {
        String description = "";

        if (employee.getType() == ENGINEER) {
            description = "Ingénieur: " + employee.getName();
            description += "\nExpérience: " + employee.getExperience() + " ans";
        } else if (employee.getType() == SALESMAN) {
            description = "Commercial: " + employee.getName();
            description += "\nCommission: " + employee.getCommission() + "€";
        } else if (employee.getType() == MANAGER) {
            description = "Manager: " + employee.getName();
            if (employee.hasTeam()) {
                description += "\nGère une équipe";
            }
        }

        return description;
    }
}

/**
 * Classe Employee avec un champ "type" qui détermine le comportement
 * C'est un anti-pattern classique!
 */
class Employee {
    private String name;
    private int type;
    private double baseSalary;
    private int experience;
    private double commission;
    private double bonus;
    private boolean hasTeam;

    public Employee(String name, int type, double baseSalary) {
        this.name = name;
        this.type = type;
        this.baseSalary = baseSalary;
    }

    // Getters et setters
    public String getName() { return name; }
    public int getType() { return type; }
    public double getBaseSalary() { return baseSalary; }
    public int getExperience() { return experience; }
    public double getCommission() { return commission; }
    public double getBonus() { return bonus; }
    public boolean hasTeam() { return hasTeam; }

    public void setExperience(int experience) { this.experience = experience; }
    public void setCommission(double commission) { this.commission = commission; }
    public void setBonus(double bonus) { this.bonus = bonus; }
    public void setHasTeam(boolean hasTeam) { this.hasTeam = hasTeam; }
}
