import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { FileText, ArrowLeft, Eye, Search } from "lucide-react";
import { useCachedDocuments } from "./useCachedDocuments";
import { getOrDownloadDocument } from "./db";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import toast from "react-hot-toast";

export default function PractitionerDocuments() {
  const navigate = useNavigate();
  const loggedIdentifier = localStorage.getItem("loggedIdentifier");

  const [searchTerm, setSearchTerm] = useState("");

  // Folosim arhitectura hibridă: ia lista imediat din DB, verifică în fundal
  const { documents, setDocuments, isLoading, refetch } = useCachedDocuments(
    loggedIdentifier,
    "practitioner",
  );

  // WebSocket logic for real-time document list updates
  useEffect(() => {
    if (!loggedIdentifier) return;

    const client = new Client({
      webSocketFactory: () => new SockJS("http://localhost:8080/ws"),
      reconnectDelay: 5000,
      onConnect: () => {
        console.log("[WebSocket Docs] Connected.");
        client.subscribe(`/topic/doctor/${loggedIdentifier}`, (message) => {
          const notification = JSON.parse(message.body);
          console.log("[WebSocket Docs] Received:", notification);

          if (notification.type === "ACCESS_REVOKED") {
            const patientName =
              notification.details?.patientName ||
              `Pacient ID: ${notification.details.patientId}`;
            toast.error(
              `Acces revocat. Documentele pentru ${patientName} au fost eliminate.`,
            );

            // Eliminăm documentele pacientului din listă
            setDocuments((currentDocs) =>
              currentDocs.filter(
                (doc) => doc.patientId !== notification.details.patientId,
              ),
            );
          } else if (notification.type === "ACCESS_GRANTED") {
            const patientName =
              notification.patient?.name ||
              `Pacient ID: ${notification.patient.id}`;
            toast.success(
              `Acces permis de ${patientName}. Se actualizează lista de documente.`,
            );
            // Re-încărcăm lista de documente pentru a include și cele noi
            refetch();
          }
        });
      },
    });

    client.activate();

    return () => {
      if (client.active) {
        client.deactivate();
      }
    };
  }, [loggedIdentifier, refetch, setDocuments]);

  const filteredDocuments = documents.filter((doc) => {
    const searchLower = searchTerm.toLowerCase();
    return (
      doc.name?.toLowerCase().includes(searchLower) ||
      doc.patientFamily?.toLowerCase().includes(searchLower) ||
      doc.patientGiven?.toLowerCase().includes(searchLower) ||
      doc.patientId?.toLowerCase().includes(searchLower)
    );
  });

  // Funcția care aduce documentul fizic instantaneu (dacă există)
  const handleViewPDF = async (docId) => {
    try {
      const objectUrl = await getOrDownloadDocument(docId);
      window.open(objectUrl, "_blank");
    } catch (error) {
      console.error("Eroare la vizualizarea documentului PDF:", error);
      alert("Nu am putut deschide documentul. Serverul e indisponibil.");
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-emerald-50 via-teal-50 to-sky-100 p-8 font-sans text-gray-900">
      <div className="max-w-5xl mx-auto">
        <button
          onClick={() => navigate("/")}
          className="flex items-center gap-2 text-teal-700 hover:text-teal-900 font-semibold mb-6 transition"
        >
          <ArrowLeft className="w-5 h-5" /> Înapoi la Dashboard
        </button>

        <h1 className="text-3xl font-extrabold text-teal-800 mb-8">
          Management Documente Pacienți
        </h1>

        {/* Lista de documente */}
        <div className="bg-white/80 backdrop-blur-xl border border-white/40 shadow-xl rounded-3xl p-6">
          <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center mb-6 gap-4">
            <h2 className="text-xl font-bold text-teal-800">
              Lista Documentelor Extrași
            </h2>
            <div className="relative w-full sm:w-auto">
              <Search className="w-5 h-5 text-gray-400 absolute left-3 top-1/2 transform -translate-y-1/2" />
              <input
                type="text"
                placeholder="Caută pacient sau document..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="w-full sm:w-72 pl-10 pr-4 py-2 border border-teal-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-teal-500 bg-white"
              />
            </div>
          </div>

          {isLoading ? (
            <div className="text-center py-10 text-teal-600 animate-pulse">
              Se încarcă documentele...
            </div>
          ) : filteredDocuments.length === 0 ? (
            <div className="text-center py-10 text-gray-500">
              Nu s-au găsit documente.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="bg-teal-50 text-teal-800 border-b border-teal-200">
                    <th className="p-4 rounded-tl-xl font-semibold">
                      Fișier / Document
                    </th>
                    <th className="p-4 font-semibold">Aparține Pacientului</th>
                    <th className="p-4 rounded-tr-xl font-semibold">
                      Data Încărcării
                    </th>
                    <th className="p-4 rounded-tr-xl font-semibold text-center">
                      Acțiuni
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {filteredDocuments.map((doc, idx) => (
                    <tr
                      key={idx}
                      className="border-b border-gray-100 hover:bg-gray-50 transition"
                    >
                      <td className="p-4 flex items-center gap-3">
                        <FileText className="w-5 h-5 text-teal-500" />
                        <span className="font-medium text-gray-800">
                          {doc.name}
                        </span>
                      </td>
                      <td className="p-4 text-gray-700">
                        {doc.patientFamily} {doc.patientGiven}{" "}
                        <span className="text-xs text-gray-400 ml-1">
                          ({doc.patientId})
                        </span>
                      </td>
                      <td className="p-4 text-gray-600">
                        {doc.uploadDate
                          ? new Date(doc.uploadDate).toLocaleDateString("ro-RO")
                          : "-"}
                      </td>
                      <td className="p-4 text-center">
                        <button
                          onClick={() => handleViewPDF(doc.id)}
                          className="inline-flex items-center gap-2 px-3 py-1.5 bg-teal-100 text-teal-700 hover:bg-teal-200 rounded-lg text-sm font-semibold transition"
                        >
                          <Eye className="w-4 h-4" /> Vezi PDF
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
