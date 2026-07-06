import React, { useState, useEffect } from "react";
import Chat from "./Chat";
import {
  UploadCloud,
  Activity,
  MessageSquare,
  FileText,
  Eye,
  User,
} from "lucide-react";
import { useNavigate } from "react-router-dom";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import toast from "react-hot-toast";

export default function MainPage({ isUploading, handleUpload, role }) {
  const navigate = useNavigate();
  const loggedIdentifier = localStorage.getItem("loggedIdentifier");

  const [patientDocs, setPatientDocs] = useState([]);
  const [loadingDocs, setLoadingDocs] = useState(false);
  const [patients, setPatients] = useState([]);
  const [isLoading, setIsLoading] = useState(true);

  // Fetch patient's documents automatically
  useEffect(() => {
    if (role !== "Practitioner" && loggedIdentifier) {
      const fetchDocs = async () => {
        setLoadingDocs(true);
        try {
          const res = await fetch(
            `http://localhost:8080/api/documents/patient/${loggedIdentifier}`,
          );
          if (res.ok) {
            const data = await res.json();
            setPatientDocs(data);
          }
        } catch (e) {
          console.error("Failed to fetch docs", e);
        } finally {
          setLoadingDocs(false);
        }
      };
      fetchDocs();
    }
  }, [role, loggedIdentifier, isUploading]);

  // Combined useEffect for Practitioner logic
  useEffect(() => {
    // This effect should only run for Practitioners
    if (role !== "Practitioner" || !loggedIdentifier) {
      return;
    }

    // 1. Fetch initial list of patients
    const fetchDoctorPatients = async () => {
      setIsLoading(true);
      try {
        const res = await fetch(
          `http://localhost:8080/auth/doctor-patients?doctorId=${loggedIdentifier}`,
        );

        if (res.ok) {
          const data = await res.json();

          const formattedPatients = data.map((p) => ({
            label: "Patient",
            properties: {
              id: p.id,
              name: p.name,
              name_family: p.name_family,
              name_given: p.name_given,
            },
          }));

          setPatients(formattedPatients);
        }
      } catch (err) {
        console.error("Eroare la încărcarea pacienților medicului:", err);
      } finally {
        setIsLoading(false);
      }
    };

    fetchDoctorPatients();

    // 2. Setup WebSocket connection
    const client = new Client({
      webSocketFactory: () => new SockJS("http://localhost:8080/ws"),
      reconnectDelay: 5000,
      onConnect: () => {
        console.log("[WebSocket] Connected to server.");
        // Subscribe to the doctor-specific topic
        client.subscribe(`/topic/doctor/${loggedIdentifier}`, (message) => {
          const notification = JSON.parse(message.body);
          console.log("[WebSocket] Received notification:", notification);

          if (notification.type === "ACCESS_REVOKED") {
            const patientName =
              notification.details?.patientName ||
              `Pacient ID: ${notification.details.patientId}`;
            toast.error(`${patientName} a revocat accesul la date.`);

            // Remove the patient from the list in real-time
            setPatients((currentPatients) =>
              currentPatients.filter(
                (p) => p.properties.id !== notification.details.patientId,
              ),
            );
          } else if (notification.type === "ACCESS_GRANTED") {
            const patientName =
              notification.patient?.name ||
              `Pacient ID: ${notification.patient.id}`;
            toast.success(`${patientName} a permis accesul la date.`);

            // Add the new patient to the list in real-time
            const newPatient = {
              label: "Patient",
              properties: notification.patient,
            };
            setPatients((currentPatients) => [...currentPatients, newPatient]);
          }
        });
      },
      onStompError: (frame) => {
        console.error("Broker reported error: " + frame.headers["message"]);
        console.error("Additional details: " + frame.body);
      },
    });

    client.activate();

    // 3. Cleanup function
    return () => {
      if (client.active) {
        console.log("[WebSocket] Deactivating client on component unmount.");
        client.deactivate();
      }
    };
  }, [role, loggedIdentifier]); // This effect depends only on role and identifier

  const getPatientName = (props) => {
    if (!props) return "Pacient Necunoscut";
    let lastName = props.lastName || props.name_family || "";
    let firstName = props.firstName || "";
    if (!firstName && Array.isArray(props.name_given)) {
      firstName = props.name_given.join(" ");
    }
    if (props.name && Array.isArray(props.name)) {
      const nameObj =
        props.name.find((n) => n.use === "official") || props.name[0];
      lastName = nameObj.family || lastName;
      if (Array.isArray(nameObj.given)) firstName = nameObj.given.join(" ");
    }
    if (!firstName && !lastName) return props.id || "Pacient Necunoscut";
    return `${firstName} ${lastName}`.trim();
  };

  return (
    <div className="min-h-[calc(100vh-73px)] bg-gradient-to-br from-emerald-50 via-teal-50 to-sky-100 text-gray-900 font-sans flex flex-col">
      {/* Main Layout Grid */}
      <div className="flex-1 w-full max-w-7xl mx-auto px-4 py-8 sm:py-12 flex flex-col lg:flex-row gap-8 items-start lg:items-stretch">
        {/* Left Column: Upload or Practitioner Docs */}
        <div className="w-full lg:w-1/3 flex flex-col gap-6">
          {/* Upload Section */}
          {role !== "Practitioner" && (
            <>
              <div className="w-full bg-white/70 backdrop-blur-xl border border-white/40 shadow-xl rounded-3xl p-6 flex flex-col items-center hover:shadow-2xl transition">
                <label
                  className={`w-full flex items-center justify-center gap-3 bg-gradient-to-r from-teal-600 to-emerald-500 hover:from-teal-500 hover:to-emerald-400 text-white py-4 px-6 rounded-2xl font-semibold transition shadow-md ${
                    isUploading
                      ? "opacity-75 cursor-not-allowed"
                      : "cursor-pointer"
                  }`}
                >
                  <UploadCloud className="w-6 h-6" />
                  <span>
                    {isUploading
                      ? "Se procesează cu AI..."
                      : "Încarcă PDF Medical"}
                  </span>

                  <input
                    type="file"
                    accept=".pdf"
                    className="hidden"
                    disabled={isUploading}
                    onChange={handleUpload}
                  />
                </label>

                <p className="text-sm text-gray-500 mt-4 text-center">
                  AI extrage automat entități clinice din PDF și construiește
                  graful medical.
                </p>

                {isUploading && (
                  <div className="mt-4 text-teal-600 text-sm font-medium animate-pulse">
                    Se analizează documentul...
                  </div>
                )}
              </div>

              {/* Patient Documents List */}
              <div className="w-full bg-white/70 backdrop-blur-xl border border-white/40 shadow-xl rounded-3xl p-6 flex flex-col hover:shadow-2xl transition">
                <h3 className="text-lg font-bold text-teal-800 mb-4 flex items-center gap-2">
                  <FileText className="w-5 h-5 text-teal-600" />
                  Documente Încărcate
                </h3>

                {loadingDocs ? (
                  <div className="text-sm text-teal-600 font-medium animate-pulse text-center py-6">
                    Se încarcă documentele...
                  </div>
                ) : patientDocs.length === 0 ? (
                  <div className="text-sm text-gray-500 text-center py-6">
                    Niciun document încărcat încă.
                  </div>
                ) : (
                  <div className="flex flex-col gap-3 overflow-y-auto max-h-[300px] pr-1 [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:bg-teal-200 [&::-webkit-scrollbar-thumb]:rounded-full">
                    {patientDocs.map((doc, idx) => (
                      <div
                        key={idx}
                        className="flex items-center justify-between p-3 bg-white/60 border border-teal-100 rounded-2xl shadow-sm hover:border-teal-300 hover:bg-white transition-all group"
                      >
                        <div className="flex flex-col overflow-hidden mr-3">
                          <span className="text-sm font-bold text-gray-700 truncate group-hover:text-teal-700 transition-colors">
                            {doc.name}
                          </span>
                          <span className="text-xs text-gray-500 font-medium mt-0.5">
                            {doc.uploadDate
                              ? new Date(doc.uploadDate).toLocaleDateString(
                                  "ro-RO",
                                )
                              : "-"}
                          </span>
                        </div>
                        <button
                          onClick={() =>
                            window.open(
                              `http://localhost:8080/api/documents/download/${doc.id}`,
                              "_blank",
                            )
                          }
                          className="p-2.5 bg-teal-50 text-teal-600 hover:bg-teal-500 hover:text-white rounded-xl transition-colors shrink-0 shadow-sm"
                          title="Vezi PDF"
                        >
                          <Eye className="w-4 h-4" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}

          {/* Documents Section for Practitioner */}
          {role === "Practitioner" && (
            <div className="w-full flex flex-col gap-6">
              <div className="w-full bg-white/70 backdrop-blur-xl border border-white/40 shadow-xl rounded-3xl p-6 flex flex-col items-center hover:shadow-2xl transition">
                <button
                  onClick={() => navigate("/practitioner-documents")}
                  className="w-full flex items-center justify-center gap-3 bg-gradient-to-r from-blue-600 to-indigo-500 hover:from-blue-500 hover:to-indigo-400 text-white py-4 px-6 rounded-2xl font-semibold transition shadow-md"
                >
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    className="w-6 h-6"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    strokeWidth={2}
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
                    />
                  </svg>
                  Gestionează Documente Pacienți
                </button>
                <p className="text-sm text-gray-500 mt-4 text-center">
                  Vezi istoricul documentelor și fișierele PDF pentru pacienții
                  tăi.
                </p>
              </div>

              {/* Lista Pacienți pentru Medic */}
              <div className="w-full bg-white/70 backdrop-blur-xl border border-white/40 shadow-xl rounded-3xl p-6 flex flex-col hover:shadow-2xl transition">
                <h3 className="text-lg font-bold text-indigo-800 mb-4 flex items-center gap-2">
                  <User className="w-5 h-5 text-indigo-600" />
                  Pacienții Tăi ({patients.length})
                </h3>

                {isLoading ? (
                  <div className="text-sm text-gray-500 text-center py-6 animate-pulse">
                    Se încarcă pacienții...
                  </div>
                ) : patients.length === 0 ? (
                  <div className="text-sm text-gray-500 text-center py-6">
                    Nu aveți pacienți asociați momentan.
                  </div>
                ) : (
                  <div className="flex flex-col gap-3 overflow-y-auto max-h-[300px] pr-1 [&::-webkit-scrollbar]:w-1.5 [&::-webkit-scrollbar-thumb]:bg-indigo-200 [&::-webkit-scrollbar-thumb]:rounded-full">
                    {patients.map((patient, idx) => (
                      <div
                        key={idx}
                        className="flex items-center gap-3 p-3 bg-white/60 border border-indigo-100 rounded-2xl shadow-sm hover:border-indigo-300 hover:bg-white transition-all group"
                      >
                        <div className="bg-indigo-100 p-2.5 rounded-full text-indigo-600 shadow-sm shrink-0">
                          <User className="w-5 h-5" />
                        </div>
                        <div className="flex flex-col overflow-hidden">
                          <span className="text-sm font-bold text-gray-800 truncate group-hover:text-indigo-700 transition-colors">
                            {getPatientName(patient.properties)}
                          </span>
                          <span className="text-xs text-gray-500 font-medium truncate">
                            ID:{" "}
                            {patient.properties?.identifier ||
                              patient.properties?.id ||
                              "N/A"}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Right Column: CHAT SECTION */}
        <main className="w-full lg:w-2/3 flex flex-col h-[600px] lg:h-[calc(100vh-11rem)]">
          <div className="w-full h-full flex flex-col rounded-3xl border border-white/40 bg-white/70 backdrop-blur-xl shadow-2xl overflow-hidden transition hover:shadow-3xl">
            {/* Chat Header */}
            <div className="bg-gradient-to-r from-teal-600 via-emerald-500 to-sky-500 p-5 sm:px-6 flex items-center gap-4 text-white shadow-sm z-10">
              <div className="p-2.5 bg-white/20 rounded-xl backdrop-blur-md border border-white/30 shadow-inner">
                <MessageSquare className="w-6 h-6 text-white" />
              </div>
              <div>
                <h2 className="text-xl font-bold tracking-wide">
                  Health Graph Assistant
                </h2>
              </div>
            </div>

            {/* Chat */}
            <div className="flex-1 p-4 sm:p-6 bg-gray-50/50 flex flex-col min-h-0">
              <Chat />
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}
