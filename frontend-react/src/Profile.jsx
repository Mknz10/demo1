import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";

export default function Profile({ graphData }) {
  // Debug log to help diagnose missing data
  const loggedIdentifier = localStorage.getItem("loggedIdentifier");
  console.log("[Profile] graphData:", graphData);
  console.log("[Profile] loggedIdentifier:", loggedIdentifier);

  // ...existing code...
  const navigate = useNavigate();

  let userNode = null;
  if (graphData && Array.isArray(graphData.nodes)) {
    userNode = graphData.nodes.find(
      (node) =>
        node.label === "Patient" &&
        node.properties?.identifier === loggedIdentifier,
    );
  }

  // Extract lastName and firstName from FHIR-style 'name' array
  let lastName = "-";
  let firstName = "-";
  if (
    userNode?.properties?.name &&
    Array.isArray(userNode.properties.name) &&
    userNode.properties.name.length > 0
  ) {
    const nameObj =
      userNode.properties.name.find((n) => n.use === "official") ||
      userNode.properties.name[0];
    lastName = nameObj.family || "-";
    if (Array.isArray(nameObj.given) && nameObj.given.length > 0) {
      firstName = nameObj.given.join(" ");
    }
  }
  const gender = userNode?.properties?.gender || "-";
  const birthDate = userNode?.properties?.birthDate || "-";

  // Stare pentru câmpurile editabile
  const [judet, setJudet] = useState("");
  const [localitate, setLocalitate] = useState("");
  const [isSaving, setIsSaving] = useState(false);

  // Stare nouă pentru a controla modul de editare
  const [isEditing, setIsEditing] = useState(false);

  // Când datele vin din graf, precompletăm câmpurile dacă ele există deja
  useEffect(() => {
    if (userNode?.properties) {
      setJudet(userNode.properties.judet || "");
      setLocalitate(userNode.properties.localitate || "");
    }
  }, [userNode]);

  let age = "-";
  if (birthDate && birthDate !== "-") {
    let birth;
    if (/^\d{4}-\d{2}-\d{2}$/.test(birthDate)) {
      birth = new Date(birthDate);
    } else if (/^\d{2}\.\d{2}\.\d{4}$/.test(birthDate)) {
      const [day, month, year] = birthDate.split(".");
      birth = new Date(`${year}-${month}-${day}`);
    }
    if (birth && !isNaN(birth.getTime())) {
      const today = new Date();
      let years = today.getFullYear() - birth.getFullYear();
      const m = today.getMonth() - birth.getMonth();
      if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) {
        years--;
      }
      age = years;
    }
  }

  // Funcția de salvare către Backend
  const handleSave = async () => {
    setIsSaving(true);
    try {
      const response = await fetch("http://localhost:8080/api/profile/update", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          identifier: loggedIdentifier,
          judet,
          localitate,
        }),
      });

      if (response.ok) {
        alert("Domiciliul a fost salvat cu succes!");
        setIsEditing(false); // Ieșim din modul de editare după salvarea cu succes
      } else {
        alert("A apărut o eroare la salvare.");
      }
    } catch (error) {
      console.error("Eroare:", error);
      alert("Nu ne-am putut conecta la server.");
    } finally {
      setIsSaving(false);
    }
  };

  // Funcția pentru anularea editării (revine la datele inițiale din baza de date)
  const handleCancel = () => {
    setIsEditing(false);
    setJudet(userNode?.properties?.judet || "");
    setLocalitate(userNode?.properties?.localitate || "");
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-br from-green-100 via-teal-100 to-blue-100 text-gray-900 font-sans">
      <div className="bg-white/90 p-10 rounded-3xl shadow-2xl w-96 border-2 border-teal-400 flex flex-col items-center">
        <div className="flex items-center gap-2 mb-4">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={1.5}
            stroke="currentColor"
            className="w-8 h-8 text-red-500"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 6v6l4 2"
            />
            <circle
              cx="12"
              cy="12"
              r="9"
              stroke="currentColor"
              strokeWidth="1.5"
              fill="none"
            />
          </svg>
          <h2 className="text-3xl font-extrabold text-center text-teal-700">
            Profil pacient
          </h2>
        </div>

        {/* Date Non-Editabile */}
        <div className="w-full mb-2">
          <span className="font-bold text-green-700">Nume:</span> {lastName}
        </div>
        <div className="w-full mb-2">
          <span className="font-bold text-green-700">Prenume:</span> {firstName}
        </div>
        <div className="w-full mb-2">
          <span className="font-bold text-blue-700">Identifier:</span>{" "}
          {loggedIdentifier}
        </div>
        <div className="w-full mb-2">
          <span className="font-bold text-pink-700">Gen:</span> {gender}
        </div>
        <div className="w-full mb-2">
          <span className="font-bold text-indigo-700">Data nașterii:</span>{" "}
          {birthDate}
        </div>
        <div className="w-full mb-6 border-b pb-4">
          <span className="font-bold text-purple-700">Vârstă:</span> {age}
        </div>

        {/* Date Editabile (Domiciliu) */}
        <div className="w-full mb-3 flex flex-col">
          <label className="font-bold text-teal-700 text-sm mb-1">Județ:</label>
          {isEditing ? (
            <input
              type="text"
              value={judet}
              onChange={(e) => setJudet(e.target.value)}
              className="w-full px-3 py-2 border border-teal-300 rounded focus:outline-none focus:ring-2 focus:ring-teal-500"
              placeholder="Ex: Maramureș"
            />
          ) : (
            <span className="text-gray-800">{judet || "-"}</span>
          )}
        </div>

        <div className="w-full mb-6 flex flex-col">
          <label className="font-bold text-teal-700 text-sm mb-1">
            Localitate:
          </label>
          {isEditing ? (
            <input
              type="text"
              value={localitate}
              onChange={(e) => setLocalitate(e.target.value)}
              className="w-full px-3 py-2 border border-teal-300 rounded focus:outline-none focus:ring-2 focus:ring-teal-500"
              placeholder="Ex: Baia Mare"
            />
          ) : (
            <span className="text-gray-800">{localitate || "-"}</span>
          )}
        </div>

        {/* Butoane condiționate */}
        <div className="flex gap-4 w-full">
          {isEditing ? (
            <>
              <button
                className="flex-1 py-2 bg-gray-400 hover:bg-gray-500 text-white rounded-lg font-semibold shadow transition"
                onClick={handleCancel}
                disabled={isSaving}
              >
                Anulează
              </button>
              <button
                className="flex-1 py-2 bg-gradient-to-r from-teal-500 to-green-500 hover:from-teal-600 hover:to-green-600 text-white rounded-lg font-semibold shadow transition disabled:opacity-50"
                onClick={handleSave}
                disabled={isSaving}
              >
                {isSaving ? "Se salvează..." : "Salvează"}
              </button>
            </>
          ) : (
            <>
              <button
                className="flex-1 py-2 bg-gradient-to-r from-teal-500 to-green-400 hover:from-teal-600 hover:to-green-500 text-white rounded-lg font-semibold shadow transition"
                onClick={() => navigate("/")}
              >
                Înapoi
              </button>
              <button
                className="flex-1 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg font-semibold shadow transition"
                onClick={() => setIsEditing(true)}
              >
                Editează
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
