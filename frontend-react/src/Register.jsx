import React, { useState } from "react";
import {
  Network,
  Fingerprint,
  User,
  Calendar,
  Users,
  ArrowRight,
  AlertCircle,
  CheckCircle,
} from "lucide-react";

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
        setMessage("Cont creat cu succes! Puteți să vă autentificați.");

        setForm({
          identifier: "",
          given: "",
          family: "",
          birthDate: "",
          gender: "",
        });
      } else {
        setMessage(text || "Eroare la înregistrare.");
      }
    } catch (err) {
      console.error(err);
      setMessage("Eroare de rețea. Verificați conexiunea.");
    } finally {
      setLoading(false);
    }
  };

  // ================= UI =================
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-br from-emerald-50 via-teal-50 to-sky-100 text-gray-900 font-sans p-4 sm:p-6">
      <div className="w-full max-w-lg flex flex-col items-center mt-8 mb-8">
        <form
          onSubmit={handleSubmit}
          className="bg-white/80 backdrop-blur-xl p-8 sm:p-10 rounded-3xl shadow-2xl border border-white/60 w-full flex flex-col transition-all"
        >
          <h2 className="text-3xl font-extrabold mb-2 text-center text-gray-800 tracking-tight">
            Înregistrare Pacient
          </h2>

          {/* IDENTIFIER */}
          <div className="w-full relative m-5 group">
            <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-gray-400 group-focus-within:text-teal-500 transition-colors">
              <Fingerprint className="w-5 h-5" />
            </div>
            <input
              type="text"
              placeholder="CNP / Identificator Unic"
              value={form.identifier}
              onChange={handleIdentifierChange}
              className="w-full pl-12 pr-4 py-3 border border-teal-200 rounded-xl bg-white/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500 transition-all text-gray-800 font-medium placeholder:text-gray-400"
              required
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 mb-5">
            {/* GIVEN NAME */}
            <div className="w-full relative group">
              <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-gray-400 group-focus-within:text-teal-500 transition-colors">
                <User className="w-5 h-5" />
              </div>
              <input
                type="text"
                name="given"
                placeholder="Prenume"
                value={form.given}
                onChange={handleChange}
                className="w-full pl-12 pr-4 py-3 border border-teal-200 rounded-xl bg-white/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500 transition-all text-gray-800 font-medium placeholder:text-gray-400"
                required
              />
            </div>

            {/* FAMILY NAME */}
            <div className="w-full relative group">
              <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-gray-400 group-focus-within:text-teal-500 transition-colors">
                <User className="w-5 h-5" />
              </div>
              <input
                type="text"
                name="family"
                placeholder="Nume de familie"
                value={form.family}
                onChange={handleChange}
                className="w-full pl-12 pr-4 py-3 border border-teal-200 rounded-xl bg-white/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500 transition-all text-gray-800 font-medium placeholder:text-gray-400"
                required
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 mb-8">
            {/* BIRTHDATE */}
            <div className="w-full relative group">
              <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-gray-400 group-focus-within:text-teal-500 transition-colors">
                <Calendar className="w-5 h-5" />
              </div>
              <input
                type="date"
                name="birthDate"
                value={form.birthDate}
                onChange={handleChange}
                className="w-full pl-12 pr-4 py-3 border border-teal-200 rounded-xl bg-white/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500 transition-all text-gray-800 font-medium text-sm"
                required
              />
            </div>

            {/* GENDER */}
            <div className="w-full relative group">
              <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-gray-400 group-focus-within:text-teal-500 transition-colors">
                <Users className="w-5 h-5" />
              </div>
              <select
                name="gender"
                value={form.gender}
                onChange={handleChange}
                className="w-full pl-12 pr-8 py-3 border border-teal-200 rounded-xl bg-white/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500 transition-all text-gray-800 font-medium appearance-none"
                required
              >
                <option value="" disabled>
                  Selectează Genul
                </option>
                <option value="male">Masculin</option>
                <option value="female">Feminin</option>
                <option value="other">Altul</option>
                <option value="unknown">Necunoscut</option>
              </select>
            </div>
          </div>

          {/* SUBMIT */}
          <button
            type="submit"
            disabled={loading}
            className="w-full flex items-center justify-center gap-2 bg-gradient-to-r from-teal-600 to-emerald-500 hover:from-teal-500 hover:to-emerald-400 text-white py-3.5 rounded-xl font-bold shadow-md hover:shadow-lg transition-all hover:-translate-y-0.5 active:scale-95 disabled:opacity-70 disabled:hover:translate-y-0"
          >
            {loading ? (
              "Se procesează..."
            ) : (
              <>
                Creare Cont <ArrowRight className="w-5 h-5" />
              </>
            )}
          </button>

          {/* MESSAGE */}
          {message && (
            <div
              className={`mt-6 w-full flex items-center gap-2 p-3.5 rounded-xl border text-sm font-medium animate-in fade-in slide-in-from-top-2 ${
                message.includes("succes")
                  ? "text-emerald-700 bg-emerald-50 border-emerald-200"
                  : "text-rose-700 bg-rose-50 border-rose-200"
              }`}
            >
              {message.includes("succes") ? (
                <CheckCircle className="w-5 h-5 shrink-0" />
              ) : (
                <AlertCircle className="w-5 h-5 shrink-0" />
              )}
              <span>{message}</span>
            </div>
          )}

          <div className="mt-8 text-center w-full pt-6 border-t border-gray-100">
            <span className="text-gray-500 text-sm font-medium">
              Aveți deja un cont?{" "}
            </span>
            <a
              href="/login"
              className="text-teal-600 hover:text-teal-700 hover:underline font-bold transition-colors"
            >
              Autentificare
            </a>
          </div>
        </form>
      </div>
    </div>
  );
};

export default Register;
