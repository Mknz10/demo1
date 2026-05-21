import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";

export default function PractitionerProfile({ graphData }) {
  const loggedIdentifier = localStorage.getItem("loggedIdentifier");
  console.log("[PractitionerProfile] graphData:", graphData);
  console.log("[PractitionerProfile] loggedIdentifier:", loggedIdentifier);

  const navigate = useNavigate();

  let userNode = null;
  if (graphData && Array.isArray(graphData.nodes)) {
    // 🔴 Schimbat pentru a căuta nodul de tip "Practitioner"
    userNode = graphData.nodes.find(
      (node) =>
        node.label === "Practitioner" &&
        node.properties?.identifier === loggedIdentifier,
    );
  }

  // Extragere Nume, Prenume și Prefix (ex: "Dr.")
  let lastName = "-";
  let firstName = "-";
  let prefix = "";

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

    // Extragem titlul dacă există (specific medicilor)
    if (Array.isArray(nameObj.prefix) && nameObj.prefix.length > 0) {
      prefix = nameObj.prefix.join(" ") + " ";
    }
  }

  const gender = userNode?.properties?.gender || "-";
  const birthDate = userNode?.properties?.birthDate || "-";

  // Stare pentru câmpurile editabile (le poți schimba ulterior în specializare, cabinet, etc.)
  const [judet, setJudet] = useState("");
  const [localitate, setLocalitate] = useState("");
  const [isSaving, setIsSaving] = useState(false);
  const [isEditing, setIsEditing] = useState(false);

  useEffect(() => {
    if (userNode?.properties) {
      setJudet(userNode.properties.judet || "");
      setLocalitate(userNode.properties.localitate || "");
    }
  }, [userNode]);

  // Calcul Vârstă
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

  const handleSave = async () => {
    setIsSaving(true);
    try {
      // 🔴 Atenție: Poate vrei să schimbi acest endpoint în /api/practitioner/update mai târziu
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
        alert("Datele au fost salvate cu succes!");
        setIsEditing(false);
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

  const handleCancel = () => {
    setIsEditing(false);
    setJudet(userNode?.properties?.judet || "");
    setLocalitate(userNode?.properties?.localitate || "");
  };

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-br from-blue-100 via-indigo-100 to-purple-100 text-gray-900 font-sans">
      <div className="bg-white/90 p-10 rounded-3xl shadow-2xl w-96 border-2 border-indigo-400 flex flex-col items-center">
        <div className="flex items-center gap-2 mb-4">
          {/* Icoană specifică pentru medic (Cruce medicală/Plus) */}
          <svg
            xmlns="http://www.w3.org/2000/svg"
            fill="none"
            viewBox="0 0 24 24"
            strokeWidth={1.5}
            stroke="currentColor"
            className="w-8 h-8 text-indigo-600"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 4.5v15m7.5-7.5h-15"
            />
          </svg>

          <h2 className="text-3xl font-extrabold text-center text-indigo-800">
            Profil Medic
          </h2>
        </div>

        {/* Date Non-Editabile */}
        <div className="w-full mb-2">
          <span className="font-bold text-indigo-700">Nume:</span> {prefix}
          {lastName}
        </div>
        <div className="w-full mb-2">
          <span className="font-bold text-indigo-700">Prenume:</span>{" "}
          {firstName}
        </div>
        <div className="w-full mb-2">
          <span className="font-bold text-blue-700">Licență/ID:</span>{" "}
          {loggedIdentifier}
        </div>
        <div className="w-full mb-2">
          <span className="font-bold text-pink-700">Gen:</span> {gender}
        </div>
        <div className="w-full mb-6 border-b pb-4">
          <span className="font-bold text-purple-700">Vârstă:</span> {age}
        </div>

        {/* Date Editabile (pe care le vei schimba tu ulterior) */}
        <div className="w-full mb-3 flex flex-col">
          <label className="font-bold text-indigo-700 text-sm mb-1">
            Județ Cabinet:
          </label>
          {isEditing ? (
            <input
              type="text"
              value={judet}
              onChange={(e) => setJudet(e.target.value)}
              className="w-full px-3 py-2 border border-indigo-300 rounded focus:outline-none focus:ring-2 focus:ring-indigo-500"
              placeholder="Ex: Maramureș"
            />
          ) : (
            <span className="text-gray-800">{judet || "-"}</span>
          )}
        </div>

        <div className="w-full mb-6 flex flex-col">
          <label className="font-bold text-indigo-700 text-sm mb-1">
            Localitate Cabinet:
          </label>
          {isEditing ? (
            <input
              type="text"
              value={localitate}
              onChange={(e) => setLocalitate(e.target.value)}
              className="w-full px-3 py-2 border border-indigo-300 rounded focus:outline-none focus:ring-2 focus:ring-indigo-500"
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
                className="flex-1 py-2 bg-gradient-to-r from-indigo-500 to-blue-500 hover:from-indigo-600 hover:to-blue-600 text-white rounded-lg font-semibold shadow transition disabled:opacity-50"
                onClick={handleSave}
                disabled={isSaving}
              >
                {isSaving ? "Se salvează..." : "Salvează"}
              </button>
            </>
          ) : (
            <>
              <button
                className="flex-1 py-2 bg-gradient-to-r from-indigo-400 to-blue-400 hover:from-indigo-500 hover:to-blue-500 text-white rounded-lg font-semibold shadow transition"
                onClick={() => navigate("/")}
              >
                Înapoi
              </button>
              <button
                className="flex-1 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg font-semibold shadow transition"
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
