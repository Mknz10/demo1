import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  User,
  Calendar,
  MapPin,
  Shield,
  Edit2,
  ArrowLeft,
  Save,
  X,
  Activity,
} from "lucide-react";

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
    <div className="min-h-screen bg-gradient-to-br from-emerald-50 via-teal-50 to-sky-100 flex flex-col items-center justify-start font-sans text-gray-900 pt-6 pb-12 px-4 sm:px-6">
      <div className="w-full max-w-7xl">
        <button
          onClick={() => navigate("/")}
          className="flex items-center gap-2 text-teal-700 hover:text-teal-900 font-semibold mb-6 transition"
        >
          <ArrowLeft className="w-5 h-5" /> Înapoi la Dashboard
        </button>
      </div>
      <div className="w-full max-w-7xl bg-white/80 backdrop-blur-xl border border-white/40 shadow-2xl rounded-3xl overflow-hidden transition-all">
        {/* Top Banner */}
        <div className="h-20 bg-gradient-to-r from-indigo-500 to-blue-400"></div>

        <div className="px-6 sm:px-10 pb-10">
          {/* Avatar & Header Profile Info */}
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-end -mt-10 mb-8 gap-4 w-full">
            <div className="flex flex-col sm:flex-row items-start sm:items-end gap-5 min-w-0 w-full sm:w-auto">
              <div className="bg-white p-2 rounded-full shadow-lg shrink-0">
                <div className="bg-gradient-to-br from-indigo-100 to-blue-100 text-indigo-600 p-5 rounded-full border border-indigo-200">
                  <User className="w-12 h-12" />
                </div>
              </div>
              <div className="mb-2 min-w-0 flex-1">
                <h2 className="text-3xl font-extrabold text-gray-900 tracking-tight break-words">
                  {prefix}
                  {firstName} {lastName}
                </h2>
                <p className="text-indigo-600 font-semibold flex items-center gap-1.5 mt-1">
                  <Shield className="w-4 h-4" /> Licență/ID: {loggedIdentifier}
                </p>
              </div>
            </div>

            {/* Edit / Save Buttons */}
            <div className="flex gap-3 w-full sm:w-auto mt-4 sm:mt-0">
              {isEditing ? (
                <>
                  <button
                    className="flex-1 sm:flex-none flex items-center justify-center gap-2 px-5 py-2.5 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-xl font-semibold transition"
                    onClick={handleCancel}
                    disabled={isSaving}
                  >
                    <X className="w-4 h-4" /> Anulează
                  </button>
                  <button
                    className="flex-1 sm:flex-none flex items-center justify-center gap-2 px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold shadow-md transition disabled:opacity-50"
                    onClick={handleSave}
                    disabled={isSaving}
                  >
                    <Save className="w-4 h-4" />{" "}
                    {isSaving ? "Se salvează..." : "Salvează"}
                  </button>
                </>
              ) : (
                <button
                  className="w-full sm:w-auto flex items-center justify-center gap-2 px-5 py-2.5 bg-white/60 hover:bg-white text-indigo-700 border border-indigo-200 rounded-xl font-semibold transition shadow-sm"
                  onClick={() => setIsEditing(true)}
                >
                  <Edit2 className="w-4 h-4" /> Editează Profilul
                </button>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            {/* Date Biometrice */}
            <div className="bg-gray-50/60 rounded-2xl p-6 border border-gray-100 shadow-sm">
              <h3 className="text-lg font-bold text-gray-800 mb-5 flex items-center gap-2">
                <Activity className="w-5 h-5 text-indigo-500" /> Date Biometrice
              </h3>
              <div className="space-y-4">
                <div>
                  <label className="text-sm font-medium text-gray-500">
                    Gen
                  </label>
                  <div className="text-gray-900 font-semibold mt-0.5 capitalize">
                    {gender}
                  </div>
                </div>
                <div>
                  <label className="text-sm font-medium text-gray-500">
                    Data Nașterii
                  </label>
                  <div className="text-gray-900 font-semibold mt-0.5 flex items-center gap-2">
                    <Calendar className="w-4 h-4 text-indigo-600" /> {birthDate}
                  </div>
                </div>
                <div>
                  <label className="text-sm font-medium text-gray-500">
                    Vârstă
                  </label>
                  <div className="text-gray-900 font-semibold mt-0.5">
                    {age} ani
                  </div>
                </div>
              </div>
            </div>

            {/* Domiciliu (Editabil) */}
            <div className="bg-gray-50/60 rounded-2xl p-6 border border-gray-100 shadow-sm">
              <h3 className="text-lg font-bold text-gray-800 mb-5 flex items-center gap-2">
                <MapPin className="w-5 h-5 text-indigo-500" /> Cabinet
              </h3>
              <div className="space-y-4">
                <div>
                  <label className="text-sm font-medium text-gray-500">
                    Județ
                  </label>
                  {isEditing ? (
                    <input
                      type="text"
                      value={judet}
                      onChange={(e) => setJudet(e.target.value)}
                      className="mt-1 w-full px-4 py-2.5 border border-indigo-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white transition"
                      placeholder="Ex: Maramureș"
                    />
                  ) : (
                    <div className="text-gray-900 font-semibold mt-0.5">
                      {judet || "Necompletat"}
                    </div>
                  )}
                </div>
                <div>
                  <label className="text-sm font-medium text-gray-500">
                    Localitate
                  </label>
                  {isEditing ? (
                    <input
                      type="text"
                      value={localitate}
                      onChange={(e) => setLocalitate(e.target.value)}
                      className="mt-1 w-full px-4 py-2.5 border border-indigo-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white transition"
                      placeholder="Ex: Baia Mare"
                    />
                  ) : (
                    <div className="text-gray-900 font-semibold mt-0.5">
                      {localitate || "Necompletat"}
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
