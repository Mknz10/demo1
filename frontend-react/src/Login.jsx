import React, { useState } from "react";
import { Network, Key, ArrowRight, AlertCircle } from "lucide-react";

export default function Login({ onLoginSuccess }) {
  const [identifierValue, setIdentifierValue] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!identifierValue.trim()) {
      setMessage("Introduceți identificatorul");
      return;
    }
    setLoading(true);
    setMessage("");
    // Trimitem identificatorul ca array de obiecte cu doar value
    const loginPayload = {
      identifier: [
        {
          value: identifierValue.trim(),
        },
      ],
    };

    try {
      const res = await fetch("http://localhost:8080/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(loginPayload),
      });

      const data = await res.json();

      if (res.ok && onLoginSuccess) {
        localStorage.setItem("loggedIdentifier", identifierValue.trim());
        onLoginSuccess();
      } else {
        setMessage(data.error || "Autentificare eșuată");
      }
    } catch (error) {
      console.error("Eroare:", error);
      setMessage("Eroare de rețea. Verificați conexiunea.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-br from-emerald-50 via-teal-50 to-sky-100 text-gray-900 font-sans p-4 sm:p-6">
      <div className="w-full max-w-md flex flex-col items-center">
        {/* Login Card */}
        <form
          onSubmit={handleSubmit}
          className="bg-white/80 backdrop-blur-xl p-8 sm:p-10 rounded-3xl shadow-2xl border border-white/60 w-full flex flex-col items-center transition-all"
        >
          <h2 className="text-3xl font-extrabold mb-2 text-center text-gray-800 tracking-tight">
            Autentificare
          </h2>

          <div className="w-full relative m-6 group">
            <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none text-gray-400 group-focus-within:text-teal-500 transition-colors">
              <Key className="w-5 h-5" />
            </div>
            <input
              type="text"
              value={identifierValue}
              onChange={(e) => setIdentifierValue(e.target.value)}
              placeholder="ID Unic / Licență..."
              className="w-full pl-12 pr-4 py-3.5 border border-teal-200 rounded-xl bg-white/50 focus:bg-white focus:outline-none focus:ring-2 focus:ring-teal-500 transition-all text-gray-800 font-medium placeholder:text-gray-400"
              required
            />
          </div>

          <button
            type="submit"
            className="w-full flex items-center justify-center gap-2 bg-gradient-to-r from-teal-600 to-emerald-500 hover:from-teal-500 hover:to-emerald-400 text-white py-3.5 rounded-xl font-bold shadow-md hover:shadow-lg transition-all hover:-translate-y-0.5 active:scale-95 disabled:opacity-70 disabled:hover:translate-y-0"
            disabled={loading}
          >
            {loading ? (
              "Se verifică..."
            ) : (
              <>
                Conectare <ArrowRight className="w-5 h-5" />
              </>
            )}
          </button>

          {message && (
            <div
              className={`mt-6 w-full flex items-center gap-2 p-3.5 rounded-xl border text-sm font-medium animate-in fade-in slide-in-from-top-2 ${
                message.includes("eșuată") ||
                message.includes("invalid") ||
                message.includes("Eroare")
                  ? "text-rose-700 bg-rose-50 border-rose-200"
                  : "text-emerald-700 bg-emerald-50 border-emerald-200"
              }`}
            >
              <AlertCircle className="w-5 h-5 shrink-0" />
              <span>{message}</span>
            </div>
          )}

          <div className="mt-8 text-center w-full pt-6 border-t border-gray-100">
            <span className="text-gray-500 text-sm font-medium">
              Nu aveți un cont încă?{" "}
            </span>
            <a
              href="/register"
              className="text-teal-600 hover:text-teal-700 hover:underline font-bold transition-colors"
            >
              Înregistrare
            </a>
          </div>
        </form>
      </div>
    </div>
  );
}
