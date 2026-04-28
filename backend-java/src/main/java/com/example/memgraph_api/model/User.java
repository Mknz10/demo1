package com.example.memgraph_api.model;

import java.util.List;

public class User {

    // 🔴 FORCE FHIR COMPLIANCE
    private final String resourceType = "Patient";

    private List<Identifier> identifier;
    private List<HumanName> name;
    private String gender;
    private String birthDate;
    private List<Address> address;

    // ================= IDENTIFIER =================
    public static class Identifier {
        private String system;
        private String value;

        public String getSystem() { return system; }
        public void setSystem(String system) { this.system = system; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    // ================= HUMAN NAME (FHIR STANDARD) =================
    public static class HumanName {
        private String use; // official | usual | temp
        private String family;
        private List<String> given;
        private String text;

        public String getUse() { return use; }
        public void setUse(String use) { this.use = use; }

        public String getFamily() { return family; }
        public void setFamily(String family) { this.family = family; }

        public List<String> getGiven() { return given; }
        public void setGiven(List<String> given) { this.given = given; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    // ================= ADDRESS =================
    public static class Address {
        private List<String> line;
        private String city;
        private String state;
        private String postalCode;
        private String country;

        public List<String> getLine() { return line; }
        public void setLine(List<String> line) { this.line = line; }

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        public String getPostalCode() { return postalCode; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
    }

    // ================= GETTERS =================

    public String getResourceType() {
        return resourceType;
    }

    public List<Identifier> getIdentifier() {
        return identifier;
    }

    public void setIdentifier(List<Identifier> identifier) {
        this.identifier = identifier;
    }

    public List<HumanName> getName() {
        return name;
    }

    public void setName(List<HumanName> name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public List<Address> getAddress() {
        return address;
    }

    public void setAddress(List<Address> address) {
        this.address = address;
    }
}