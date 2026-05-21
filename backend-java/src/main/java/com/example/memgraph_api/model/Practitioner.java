package com.example.memgraph_api.model;

import java.util.List;

/**
 * FHIR-compliant Practitioner resource representing a Doctor or other healthcare provider.
 */
public class Practitioner {
    private final String resourceType = "Practitioner";
    private List<Identifier> identifier;
    private List<HumanName> name;
    private String gender;
    private String birthDate;
    private String qualification; // e.g., "MD", "RN", etc.
    private String role; // e.g., "doctor", "nurse", etc.

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
        private String use;
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

    // ================= GETTERS/SETTERS =================
    public String getResourceType() { return resourceType; }
    public List<Identifier> getIdentifier() { return identifier; }
    public void setIdentifier(List<Identifier> identifier) { this.identifier = identifier; }
    public List<HumanName> getName() { return name; }
    public void setName(List<HumanName> name) { this.name = name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
