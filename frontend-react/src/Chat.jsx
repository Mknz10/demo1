import React, { useState, useRef, useEffect } from "react";
import { Send, Bot, Database, Globe } from "lucide-react";

export default function Chat() {
  const [message, setMessage] = useState("");
  const [chatHistory, setChatHistory] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [searchMode, setSearchMode] = useState("db"); // "db" sau "web"
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
      if (searchMode === "db") {
        // Căutare în baza de date (Graful Medical)
        const response = await fetch("http://localhost:8080/api/chat", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            message: userMessage,
            identifier: loggedIdentifier,
          }),
        });
        const data = await response.json();
        setChatHistory((prev) => [
          ...prev,
          {
            sender: "bot",
            html:
              data.reply_html ||
              `<div>${data.error || "Eroare la procesarea răspunsului"}</div>`,
          },
        ]);
      } else {
        // Căutare pe Web
        const response = await fetch("http://localhost:8080/api/chat/ask", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ question: userMessage }),
        });
        const data = await response.json();
        setChatHistory((prev) => [
          ...prev,
          {
            sender: "bot",
            text: data.answer || "Eroare la primirea răspunsului.",
          },
        ]);
      }
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
    <div className="flex flex-col h-full w-full bg-transparent min-h-0">
      {/* Chat Messages Area */}
      <div className="flex-1 overflow-y-auto p-2 sm:p-4 space-y-6 scroll-smooth">
        {chatHistory.map((msg, index) => (
          <div
            key={index}
            className={`flex ${msg.sender === "user" ? "justify-end" : "justify-start"}`}
          >
            <div
              className={`px-5 py-3 max-w-[90%] sm:max-w-[80%] shadow-sm overflow-x-auto ${
                msg.sender === "user"
                  ? "bg-gradient-to-br from-teal-500 to-emerald-500 text-white rounded-2xl rounded-tr-sm"
                  : "bg-white text-gray-800 border border-gray-100 rounded-2xl rounded-tl-sm"
              }`}
            >
              {/* IMPORTANT: We use dangerouslySetInnerHTML to render the HTML table from the backend */}
              {msg.html ? (
                <div
                  className="prose prose-sm max-w-none"
                  dangerouslySetInnerHTML={{ __html: msg.html }}
                />
              ) : (
                <div className="leading-relaxed">{msg.text}</div>
              )}
            </div>
          </div>
        ))}

        {isLoading && (
          <div className="flex justify-start">
            <div className="bg-white text-gray-500 border border-gray-100 rounded-2xl rounded-tl-sm px-5 py-4 max-w-[85%] shadow-sm flex items-center gap-3">
              <Bot className="w-5 h-5 animate-bounce text-teal-500" />
              <div className="flex gap-1.5">
                <span className="w-2 h-2 bg-teal-400 rounded-full animate-pulse"></span>
                <span className="w-2 h-2 bg-teal-400 rounded-full animate-pulse delay-75"></span>
                <span className="w-2 h-2 bg-teal-400 rounded-full animate-pulse delay-150"></span>
              </div>
            </div>
          </div>
        )}
        <div ref={chatEndRef} />
      </div>

      {/* Input Area cu Selector de Mod */}
      <div className="mt-2 sm:mt-4 flex flex-col gap-2">
        <div className="flex gap-2 px-1">
          <button
            type="button"
            onClick={() => setSearchMode("db")}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold transition-colors ${searchMode === "db" ? "bg-teal-100 text-teal-700 shadow-sm" : "text-gray-500 hover:bg-gray-100"}`}
          >
            <Database className="w-4 h-4" /> Dosar Medical
          </button>
          <button
            type="button"
            onClick={() => setSearchMode("web")}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-semibold transition-colors ${searchMode === "web" ? "bg-emerald-100 text-emerald-700 shadow-sm" : "text-gray-500 hover:bg-gray-100"}`}
          >
            <Globe className="w-4 h-4" /> Asistent Web
          </button>
        </div>

        <form
          onSubmit={handleSendMessage}
          className="relative flex items-center"
        >
          <input
            type="text"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder={
              searchMode === "db"
                ? "Întreabă ceva despre analizele din dosar..."
                : "Caută informații medicale generale pe web..."
            }
            className="w-full pl-5 pr-14 py-4 bg-white border border-teal-200 rounded-2xl focus:outline-none focus:ring-2 focus:ring-teal-500 shadow-sm transition-all text-gray-700 placeholder:text-gray-400"
            disabled={isLoading}
          />
          <button
            type="submit"
            className="absolute right-2 p-2.5 bg-teal-500 hover:bg-teal-600 text-white rounded-xl transition-all disabled:opacity-50 disabled:hover:bg-teal-500 shadow-md group"
            disabled={isLoading || !message.trim()}
          >
            <Send className="w-5 h-5 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 transition-transform" />
          </button>
        </form>
      </div>
    </div>
  );
}
