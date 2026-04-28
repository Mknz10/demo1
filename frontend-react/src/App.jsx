import React, { useState, useEffect, useCallback } from "react";
import { Routes, Route, useNavigate, Navigate } from "react-router-dom";
import Login from "./Login";
import Profile from "./Profile";
import Header from "./Header";
import MainPage from "./MainPage";
import Register from "./Register";
import Chat from "./Chat";

export default function App() {
  const navigate = useNavigate();
  const loggedIdentifier = localStorage.getItem("loggedIdentifier");

  // --- STĂRI (STATE) ---
  const [isLoggedIn, setIsLoggedIn] = useState(
    () => localStorage.getItem("isLoggedIn") === "true",
  );
  const [isLoading, setIsLoading] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [graphData, setGraphData] = useState({ nodes: [], edges: [] });

  // Sincronizare login cu localStorage
  useEffect(() => {
    localStorage.setItem("isLoggedIn", isLoggedIn ? "true" : "false");
  }, [isLoggedIn]);

  // --- LOGICĂ PRELUARE DATE (API) ---
  const fetchGraphData = useCallback(async () => {
    if (!loggedIdentifier) return;

    setIsLoading(true);
    try {
      const response = await fetch(
        `http://localhost:8080/api/graph?identifier=${loggedIdentifier}`,
      );
      if (!response.ok) {
        throw new Error(`API responded with status ${response.status}`);
      }

      const payload = await response.json();
      console.log("Fetched graph data:", payload);

      setGraphData({
        nodes: Array.isArray(payload?.nodes) ? payload.nodes : [],
        edges: Array.isArray(payload?.edges) ? payload.edges : [],
      });
    } catch (error) {
      console.error("Failed to load graph:", error);
      setGraphData({ nodes: [], edges: [] });
    } finally {
      setIsLoading(false);
    }
  }, [loggedIdentifier]);

  // Se execută automat când utilizatorul se loghează
  useEffect(() => {
    if (isLoggedIn) {
      fetchGraphData();
    }
  }, [isLoggedIn, fetchGraphData]);

  // --- HANDLERE ---
  const handleLoginSuccess = () => setIsLoggedIn(true);

  const handleLogout = () => {
    localStorage.removeItem("isLoggedIn");
    localStorage.removeItem("loggedIdentifier");
    setIsLoggedIn(false);
    setGraphData({ nodes: [], edges: [] }); // Curățăm datele medicale din memorie
    navigate("/");
  };

  const handleUpload = async (e) => {
    const file = e.target.files[0];
    if (!file || !loggedIdentifier) return;

    setIsUploading(true);
    const formData = new FormData();
    formData.append("file", file);
    formData.append("identifier", loggedIdentifier);

    try {
      await fetch("http://localhost:8080/api/graph/upload", {
        method: "POST",
        body: formData,
      });
      // După upload, preluăm noile date FĂRĂ să dăm refresh la pagină
      await fetchGraphData();
    } catch (error) {
      console.error("Upload failed:", error);
      alert("A apărut o eroare la procesarea fișierului medical.");
    } finally {
      setIsUploading(false);
    }
  };

  // --- RANDARE (RENDER) ---

  // Dacă utilizatorul NU este logat, are acces doar la Login și Register
  if (!isLoggedIn) {
    return (
      <Routes>
        <Route path="/register" element={<Register />} />
        {/* Orice altă rută publică duce la Login */}
        <Route
          path="*"
          element={<Login onLoginSuccess={handleLoginSuccess} />}
        />
      </Routes>
    );
  }

  // Dacă utilizatorul ESTE logat, vede meniul (Header) și paginile private
  // Extrage prenumele din graphData pentru header
  // Extract prenume (first name) from FHIR-style 'name' array for Header
  let given = "";
  if (loggedIdentifier && graphData && Array.isArray(graphData.nodes)) {
    const userNode = graphData.nodes.find(
      (node) =>
        node.label === "Patient" &&
        node.properties?.identifier === loggedIdentifier,
    );
    if (
      userNode?.properties?.name &&
      Array.isArray(userNode.properties.name) &&
      userNode.properties.name.length > 0
    ) {
      const nameObj =
        userNode.properties.name.find((n) => n.use === "official") ||
        userNode.properties.name[0];
      if (Array.isArray(nameObj.given) && nameObj.given.length > 0) {
        given = nameObj.given.join(" ");
      }
    }
  }

  return (
    <>
      {/* Debug: show current loggedIdentifier */}
      <div
        style={{
          position: "fixed",
          top: 0,
          right: 0,
          background: "#e0f7fa",
          color: "#006064",
          padding: "4px 12px",
          zIndex: 9999,
          fontSize: 13,
          borderBottomLeftRadius: 8,
        }}
      >
        <strong>Logged in as:</strong>{" "}
        {loggedIdentifier || <span style={{ color: "red" }}>none</span>}
      </div>
      <Header
        onProfile={() => navigate("/profile")}
        onLogout={handleLogout}
        given={given}
      />
      <Routes>
        <Route
          path="/"
          element={
            <MainPage
              isUploading={isUploading}
              isLoading={isLoading}
              graphData={graphData}
              handleUpload={handleUpload}
            />
          }
        />
        <Route path="/profile" element={<Profile graphData={graphData} />} />
        {/* Dacă introduce o rută greșită, îl trimitem pe pagina principală */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
