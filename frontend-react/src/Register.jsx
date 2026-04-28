import React, { useState } from "react";

// ================= CNP EXTRACTOR =================
function extractDataFromCNP(cnp) {
  if (!/^\d{13}$/.test(cnp)) return null;

  const genderDigit = parseInt(cnp[0], 10);

  let gender = "unknown";
  if ([1, 3, 5, 7].includes(genderDigit)) gender = "male";
  else if ([2, 4, 6, 8].includes(genderDigit)) gender = "female";

  let year = parseInt(cnp.substring(1, 3), 10);
  const month = parseInt(cnp.substring(3, 5), 10);
  const day = parseInt(cnp.substring(5, 7), 10);

  if ([1, 2].includes(genderDigit)) year += 1900;
  else if ([3, 4].includes(genderDigit)) year += 1800;
  else if ([5, 6, 7, 8].includes(genderDigit)) year += 2000;

  const birthDate = `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;

  return { gender, birthDate };
}

// ================= REGISTER COMPONENT =================
const Register = () => {
  const [form, setForm] = useState({
    identifier: "",
    given: "",
    family: "",
    birthDate: "",
    gender: "",
  });

  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  // ================= AUTO FILL FROM CNP =================
  const handleIdentifierChange = (e) => {
    const identifier = e.target.value;
    const extracted = extractDataFromCNP(identifier);

    setForm((prev) => ({
      ...prev,
      identifier,
      birthDate: extracted?.birthDate || prev.birthDate,
      gender: extracted?.gender || prev.gender,
    }));
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  };

  // ================= SUBMIT (FHIR PATIENT) =================
  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage("");

    try {
      const res = await fetch("http://localhost:8080/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },

        body: JSON.stringify({
          resourceType: "Patient",

          identifier: [
            {
              system: "national-id",
              value: form.identifier,
            },
          ],

          name: [
            {
              use: "official",
              family: form.family,
              given: [form.given],
            },
          ],

          gender: form.gender,
          birthDate: form.birthDate,
        }),
      });

      const text = await res.text();

      if (res.ok) {
        setMessage("Success! Patient registered in FHIR format.");

        setForm({
          identifier: "",
          given: "",
          family: "",
          birthDate: "",
          gender: "",
        });
      } else {
        setMessage(text || "Registration error.");
      }
    } catch (err) {
      console.error(err);
      setMessage("Network error.");
    } finally {
      setLoading(false);
    }
  };

  // ================= UI =================
  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 p-6">
      <form
        onSubmit={handleSubmit}
        className="bg-white p-8 rounded-2xl shadow-lg w-full max-w-md"
      >
        <h2 className="text-2xl font-bold mb-6 text-center">
          FHIR Patient Registration
        </h2>

        {/* IDENTIFIER */}
        <input
          type="text"
          placeholder="CNP / Identifier"
          value={form.identifier}
          onChange={handleIdentifierChange}
          className="w-full p-3 border rounded mb-4"
          required
        />

        {/* GIVEN + FAMILY */}
        <input
          type="text"
          name="given"
          placeholder="Given Name"
          value={form.given}
          onChange={handleChange}
          className="w-full p-3 border rounded mb-4"
          required
        />

        <input
          type="text"
          name="family"
          placeholder="Family Name"
          value={form.family}
          onChange={handleChange}
          className="w-full p-3 border rounded mb-4"
          required
        />

        {/* BIRTHDATE */}
        <input
          type="date"
          name="birthDate"
          value={form.birthDate}
          onChange={handleChange}
          className="w-full p-3 border rounded mb-4"
          required
        />

        {/* GENDER */}
        <select
          name="gender"
          value={form.gender}
          onChange={handleChange}
          className="w-full p-3 border rounded mb-4"
          required
        >
          <option value="">Select gender</option>
          <option value="male">male</option>
          <option value="female">female</option>
          <option value="other">other</option>
          <option value="unknown">unknown</option>
        </select>

        {/* SUBMIT */}
        <button
          type="submit"
          disabled={loading}
          className="w-full bg-blue-600 text-white p-3 rounded"
        >
          {loading ? "Registering..." : "Register Patient"}
        </button>

        {/* MESSAGE */}
        {message && <p className="mt-4 text-center text-sm">{message}</p>}
      </form>
    </div>
  );
};

export default Register;
