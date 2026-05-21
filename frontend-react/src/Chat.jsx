import React, { useState, useRef, useEffect } from "react";

export default function Chat() {
  const [message, setMessage] = useState("");
  const [chatHistory, setChatHistory] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const chatEndRef = useRef(null);

  // Auto-scroll to the bottom when new messages arrive
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [chatHistory]);

  const handleSendMessage = async (e) => {
    e.preventDefault();
    if (!message.trim()) return;

    // 1. Fetch the user's identifier from localStorage
    const loggedIdentifier = localStorage.getItem("loggedIdentifier");

    // Safety check if the user somehow logged out
    if (!loggedIdentifier) {
      setChatHistory((prev) => [
        ...prev,
        { sender: "user", text: message },
        {
          sender: "bot",
          html: "<div style='color:red;'>Missing user identifier. Please log in.</div>",
        },
      ]);
      setMessage("");
      return;
    }

    const userMessage = message;
    setMessage("");

    // Add the user's message to the chat window
    setChatHistory((prev) => [...prev, { sender: "user", text: userMessage }]);
    setIsLoading(true);

    try {
      // 2. Send the message AND identifier to the backend
      const response = await fetch("http://localhost:8080/api/chat", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: userMessage,
          identifier: loggedIdentifier, // <-- This fixes the backend error!
        }),
      });

      const data = await response.json();

      // 3. Add the bot's HTML response to the chat window
      setChatHistory((prev) => [
        ...prev,
        {
          sender: "bot",
          html:
            data.reply_html ||
            `<div>${data.error || "Unknown error occurred"}</div>`,
        },
      ]);
    } catch (error) {
      console.error("Chat error:", error);
      setChatHistory((prev) => [
        ...prev,
        {
          sender: "bot",
          html: "<div style='color:red;'>Failed to connect to the server.</div>",
        },
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.chatBox}>
        {chatHistory.map((msg, index) => (
          <div
            key={index}
            style={{
              ...styles.messageWrapper,
              justifyContent: msg.sender === "user" ? "flex-end" : "flex-start",
            }}
          >
            <div
              style={{
                ...styles.messageBubble,
                backgroundColor: msg.sender === "user" ? "#007bff" : "#f8f9fa",
                color: msg.sender === "user" ? "#fff" : "#333",
                border: msg.sender === "user" ? "none" : "1px solid #e5e7eb",
              }}
            >
              {/* IMPORTANT: We use dangerouslySetInnerHTML to render the HTML table from the backend */}
              {msg.html ? (
                <div dangerouslySetInnerHTML={{ __html: msg.html }} />
              ) : (
                <div>{msg.text}</div>
              )}
            </div>
          </div>
        ))}
        {isLoading && (
          <div
            style={{ ...styles.messageWrapper, justifyContent: "flex-start" }}
          >
            <div
              style={{
                ...styles.messageBubble,
                backgroundColor: "#f8f9fa",
                color: "#666",
              }}
            >
              Analizează datele...
            </div>
          </div>
        )}
        <div ref={chatEndRef} />
      </div>

      <form onSubmit={handleSendMessage} style={styles.inputArea}>
        <input
          type="text"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          placeholder="Întreabă ceva despre analize sau documente..."
          style={styles.input}
          disabled={isLoading}
        />
        <button
          type="submit"
          style={styles.button}
          disabled={isLoading || !message.trim()}
        >
          Trimite
        </button>
      </form>
    </div>
  );
}

// Basic inline styling for a clean chat interface
const styles = {
  container: {
    display: "flex",
    flexDirection: "column",
    height: "500px",
    width: "100%",
    maxWidth: "800px",
    margin: "0 auto",
    border: "1px solid #e5e7eb",
    borderRadius: "12px",
    backgroundColor: "#fff",
    boxShadow: "0 4px 6px rgba(0, 0, 0, 0.05)",
  },
  chatBox: {
    flex: 1,
    padding: "16px",
    overflowY: "auto",
    display: "flex",
    flexDirection: "column",
    gap: "16px",
  },
  messageWrapper: {
    display: "flex",
    width: "100%",
  },
  messageBubble: {
    maxWidth: "85%",
    padding: "12px 16px",
    borderRadius: "16px",
    fontSize: "14px",
    lineHeight: "1.5",
    wordWrap: "break-word",
    overflowX: "auto", // Allows the table to scroll horizontally if it's too wide
  },
  inputArea: {
    display: "flex",
    borderTop: "1px solid #e5e7eb",
    padding: "12px",
    gap: "12px",
    backgroundColor: "#f9fafb",
    borderBottomLeftRadius: "12px",
    borderBottomRightRadius: "12px",
  },
  input: {
    flex: 1,
    padding: "12px 16px",
    borderRadius: "24px",
    border: "1px solid #d1d5db",
    outline: "none",
    fontSize: "14px",
  },
  button: {
    padding: "10px 24px",
    borderRadius: "24px",
    border: "none",
    backgroundColor: "#007bff",
    color: "white",
    cursor: "pointer",
    fontWeight: "600",
    transition: "background-color 0.2s",
  },
};
