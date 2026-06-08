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
        (node.properties?.identifier === loggedIdentifier ||
          node.properties?.id === loggedIdentifier),
    );
  }

  // Extragere Nume și Prenume (suportă formatul cu name_family / name_given)
  let lastName =
    userNode?.properties?.lastName || userNode?.properties?.name_family || "";
  let firstName = userNode?.properties?.firstName || "";

  if (!firstName && Array.isArray(userNode?.properties?.name_given)) {
    firstName = userNode.properties.name_given.join(" ");
  }

  if (
    userNode?.properties?.name &&
    Array.isArray(userNode.properties.name) &&
    userNode.properties.name.length > 0
  ) {
    const nameObj =
      userNode.properties.name.find((n) => n.use === "official") ||
      userNode.properties.name[0];
    lastName = nameObj.family || lastName;
    if (Array.isArray(nameObj.given) && nameObj.given.length > 0) {
      firstName = nameObj.given.join(" ");
    }
  }
  if (!firstName) {
    firstName = "Pacient";
  }

  const rawGender = userNode?.properties?.gender || "-";
  const genderMap = {
    male: "Masculin",
    female: "Feminin",
    unknown: "Necunoscut",
    other: "Altul",
  };
  const gender = genderMap[rawGender?.toLowerCase()] || rawGender;

  const birthDate = userNode?.properties?.birthDate || "-";

  // Stare pentru câmpurile editabile
  const [judet, setJudet] = useState("");
  const [localitate, setLocalitate] = useState("");
  const [isSaving, setIsSaving] = useState(false);

  // Stare nouă pentru a controla modul de editare
  const [isEditing, setIsEditing] = useState(false);

  // Stare pentru permisiuni acces doctor
  const [accessEnabled, setAccessEnabled] = useState(true);
  const [isToggling, setIsToggling] = useState(false);

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
      if (years < 0) {
        age = "Date anonimizate (MIMIC)";
      } else {
        age = years + " ani";
      }
    }
  }

  // Fetch initial status
  useEffect(() => {
    const fetchStatus = async () => {
      try {
        const res = await fetch(
          `http://localhost:8080/auth/access-status?identifier=${loggedIdentifier}`,
        );
        const status = await res.json();
        setAccessEnabled(status);
      } catch (err) {
        console.error("Failed to fetch access status", err);
      }
    };
    if (loggedIdentifier) fetchStatus();
  }, [loggedIdentifier]);

  // Funcția de comutare acces doctor
  const handleToggleAccess = async () => {
    setIsToggling(true);
    const newStatus = !accessEnabled;

    try {
      const res = await fetch("http://localhost:8080/auth/toggle-access", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          identifier: loggedIdentifier,
          enable: newStatus,
        }),
      });

      if (res.ok) {
        setAccessEnabled(newStatus);
      } else {
        alert("Eroare la modificarea permisiunilor.");
      }
    } catch (err) {
      console.error("Toggle failed", err);
      alert("Eroare de rețea la conectarea cu serverul.");
    } finally {
      setIsToggling(false);
    }
  };

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
        <div className="h-20 bg-gradient-to-r from-teal-500 to-emerald-400"></div>

        <div className="px-6 sm:px-10 pb-10">
          {/* Avatar & Header Profile Info */}
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-end -mt-10 mb-8 gap-4">
            <div className="flex items-end gap-5">
              <div className="bg-white p-2 rounded-full shadow-lg">
                <div className="bg-gradient-to-br from-teal-100 to-emerald-100 text-teal-600 p-5 rounded-full border border-teal-200">
                  <User className="w-12 h-12" />
                </div>
              </div>
              <div className="mb-2">
                <h2 className="text-3xl font-extrabold text-gray-900 tracking-tight">
                  {firstName} {lastName}
                </h2>
                <p className="text-teal-600 font-semibold flex items-center gap-1.5 mt-1">
                  <Shield className="w-4 h-4" /> ID: {loggedIdentifier}
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
                    className="flex-1 sm:flex-none flex items-center justify-center gap-2 px-5 py-2.5 bg-teal-600 hover:bg-teal-700 text-white rounded-xl font-semibold shadow-md transition disabled:opacity-50"
                    onClick={handleSave}
                    disabled={isSaving}
                  >
                    <Save className="w-4 h-4" />{" "}
                    {isSaving ? "Se salvează..." : "Salvează"}
                  </button>
                </>
              ) : (
                <button
                  className="w-full sm:w-auto flex items-center justify-center gap-2 px-5 py-2.5 bg-white/60 hover:bg-white text-teal-700 border border-teal-200 rounded-xl font-semibold transition shadow-sm"
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
                <Activity className="w-5 h-5 text-teal-500" /> Date Biometrice
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
                    <Calendar className="w-4 h-4 text-teal-600" /> {birthDate}
                  </div>
                </div>
                <div>
                  <label className="text-sm font-medium text-gray-500">
                    Vârstă
                  </label>
                  <div className="text-gray-900 font-semibold mt-0.5">
                    {age}
                  </div>
                </div>
              </div>
            </div>

            {/* Domiciliu (Editabil) */}
            <div className="bg-gray-50/60 rounded-2xl p-6 border border-gray-100 shadow-sm">
              <h3 className="text-lg font-bold text-gray-800 mb-5 flex items-center gap-2">
                <MapPin className="w-5 h-5 text-teal-500" /> Domiciliu
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
                      className="mt-1 w-full px-4 py-2.5 border border-teal-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-teal-500 bg-white transition"
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
                      className="mt-1 w-full px-4 py-2.5 border border-teal-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-teal-500 bg-white transition"
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

          {/* Secțiune Acces Medic */}
          <div className="mt-8 bg-gradient-to-r from-teal-50 to-emerald-50 border border-teal-200 rounded-2xl p-6 flex flex-col sm:flex-row items-center justify-between gap-6 shadow-sm">
            <div>
              <h3 className="text-lg font-bold text-teal-900 flex items-center gap-2 mb-1">
                <Shield className="w-5 h-5 text-teal-600" /> Permisiuni Dosar
                Medical
              </h3>
              <p className="text-teal-700 text-sm">
                Gestionează dreptul medicului de a vizualiza analizele și
                istoricul tău medical.
              </p>
            </div>
            <button
              onClick={handleToggleAccess}
              disabled={isToggling}
              className={`px-6 py-3 rounded-xl font-bold text-white shadow-md transition-all whitespace-nowrap min-w-[180px] ${
                accessEnabled
                  ? "bg-rose-500 hover:bg-rose-600"
                  : "bg-emerald-500 hover:bg-emerald-600"
              } disabled:opacity-50`}
            >
              {isToggling
                ? "Se actualizează..."
                : accessEnabled
                  ? "Revocă Accesul"
                  : "Permite Accesul"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
