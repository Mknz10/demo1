import React, { useState } from "react";

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
    <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-br from-green-100 via-teal-100 to-blue-100 text-gray-900 font-sans p-4">
      <form
        onSubmit={handleSubmit}
        className="bg-white/90 p-8 rounded-2xl shadow-2xl w-full max-w-sm border-2 border-teal-400 flex flex-col items-center"
      >
        <h2 className="text-3xl font-extrabold mb-2 text-center text-teal-700">
          Autentificare
        </h2>
        <p className="text-sm text-gray-500 mb-6 text-center">
          Introduceți identificatorul unic
        </p>

        <input
          type="text"
          value={identifierValue}
          onChange={(e) => setIdentifierValue(e.target.value)}
          placeholder="Introduceți identificatorul..."
          className="w-full p-3 border-2 border-green-400 rounded-lg mb-4 bg-green-50 text-gray-900 focus:outline-none focus:ring-2 focus:ring-teal-400 transition"
          required
        />

        <button
          type="submit"
          className="w-full bg-gradient-to-r from-teal-500 to-green-400 hover:from-teal-600 hover:to-green-500 text-white py-3 rounded-lg font-semibold shadow-lg transition-all active:scale-95 disabled:opacity-50"
          disabled={loading}
        >
          {loading ? "Se verifică..." : "Conectare"}
        </button>

        {message && (
          <div
            className={`mt-4 text-center text-sm p-3 rounded-lg border w-full ${
              message.includes("eșuată") ||
              message.includes("invalid") ||
              message.includes("Eroare")
                ? "text-red-700 bg-red-100 border-red-300"
                : "text-green-700 bg-green-100 border-green-300"
            }`}
          >
            {message}
          </div>
        )}

        <div className="mt-6 text-center w-full">
          <span className="text-gray-600 text-sm">Nu ai cont? </span>
          <a
            href="/register"
            className="text-teal-600 hover:underline font-bold"
          >
            Înregistrează-te
          </a>
        </div>
      </form>
    </div>
  );
}
