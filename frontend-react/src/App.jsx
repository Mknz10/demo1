import React, { useState, useEffect, useCallback } from "react";
import { Routes, Route, useNavigate, Navigate } from "react-router-dom";
import Login from "./Login";
import Profile from "./Profile";
import PractitionerProfile from "./PractitionerProfile";
import Header from "./Header";
import MainPage from "./MainPage";
import Register from "./Register";
import Chat from "./Chat";
import PractitionerDocuments from "./PractitionerDocuments";

export default function App() {
  const navigate = useNavigate();
  const loggedIdentifier = localStorage.getItem("loggedIdentifier");

  // --- STATE ---
  const [isLoggedIn, setIsLoggedIn] = useState(
    () => localStorage.getItem("isLoggedIn") === "true",
  );

  const [isLoading, setIsLoading] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [graphData, setGraphData] = useState({ nodes: [], edges: [] });

  // sync login
  useEffect(() => {
    localStorage.setItem("isLoggedIn", isLoggedIn ? "true" : "false");
  }, [isLoggedIn]);

  // --- FETCH GRAPH ---
  const fetchGraphData = useCallback(async () => {
    if (!loggedIdentifier) return;

    setIsLoading(true);
    try {
      const response = await fetch(
        `http://localhost:8080/api/graph?identifier=${loggedIdentifier}`,
      );

      if (!response.ok) throw new Error(response.status);

      const payload = await response.json();

      setGraphData({
        nodes: Array.isArray(payload?.nodes) ? payload.nodes : [],
        edges: Array.isArray(payload?.edges) ? payload.edges : [],
      });
    } catch (err) {
      console.error("Graph load failed:", err);
      setGraphData({ nodes: [], edges: [] });
    } finally {
      setIsLoading(false);
    }
  }, [loggedIdentifier]);

  useEffect(() => {
    if (isLoggedIn) fetchGraphData();
  }, [isLoggedIn, fetchGraphData]);

  // --- HANDLERS ---
  const handleLoginSuccess = () => {
    // This forces the app to recognize the newly saved loggedIdentifier
    setIsLoggedIn(true);
  };

  const handleLogout = () => {
    localStorage.removeItem("isLoggedIn");
    localStorage.removeItem("loggedIdentifier");
    setIsLoggedIn(false);
    setGraphData({ nodes: [], edges: [] });
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

      await fetchGraphData();
    } catch (err) {
      console.error("Upload failed:", err);
      alert("Upload error");
    } finally {
      setIsUploading(false);
    }
  };

  // --- AUTH GATE ---
  if (!isLoggedIn) {
    return (
      <Routes>
        <Route path="/register" element={<Register />} />
        <Route
          path="*"
          element={<Login onLoginSuccess={handleLoginSuccess} />}
        />
      </Routes>
    );
  }

  // --- USER NODE ---
  const userNode =
    loggedIdentifier && Array.isArray(graphData.nodes)
      ? graphData.nodes.find(
          (node) =>
            (node.label === "Patient" || node.label === "Practitioner") &&
            node.properties?.identifier === loggedIdentifier,
        )
      : null;

  const isPractitioner = userNode?.label === "Practitioner";

  // --- NAME ---
  let given = "";

  if (userNode?.properties?.name?.length > 0) {
    const nameObj =
      userNode.properties.name.find((n) => n.use === "official") ||
      userNode.properties.name[0];

    if (Array.isArray(nameObj.given)) {
      given = nameObj.given.join(" ");
    }
  }

  return (
    <>
      {/* DEBUG */}
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

      {/* HEADER */}
      <Header
        onProfile={() =>
          navigate(isPractitioner ? "/practitioner-profile" : "/profile")
        }
        onLogout={handleLogout}
        given={given}
      />

      {/* ROUTES */}
      <Routes>
        <Route
          path="/"
          element={
            <MainPage
              isUploading={isUploading}
              isLoading={isLoading}
              graphData={graphData}
              handleUpload={handleUpload}
              role={isPractitioner ? "Practitioner" : "Patient"}
            />
          }
        />

        <Route
          path="/profile"
          element={
            !isPractitioner ? (
              <Profile graphData={graphData} />
            ) : (
              <Navigate to="/practitioner-profile" replace />
            )
          }
        />

        <Route
          path="/practitioner-profile"
          element={
            isPractitioner ? (
              <PractitionerProfile graphData={graphData} />
            ) : (
              <Navigate to="/profile" replace />
            )
          }
        />

        <Route
          path="/practitioner-documents"
          element={
            isPractitioner ? (
              <PractitionerDocuments />
            ) : (
              <Navigate to="/" replace />
            )
          }
        />

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
