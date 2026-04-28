import React, { useState, useRef, useEffect } from "react";

export default function Chat() {
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const appendMessage = (msg, isUser, isHtml = false) => {
    setMessages((prev) => [...prev, { text: msg, isUser, isHtml }]);
  };

  const sendMessage = async () => {
    const text = input.trim();
    if (!text) return;

    appendMessage(text, true);
    setInput("");
    setLoading(true);
    appendMessage("Gândesc și generez răspuns...", false);

    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: text }),
      });

      let data;
      try {
        data = await res.json();
      } catch {
        setMessages((prev) => prev.slice(0, -1));
        appendMessage("Eroare la parsarea răspunsului serverului.", false);
        return;
      }

      setMessages((prev) => prev.slice(0, -1)); // remove loading

      if (data.error) {
        appendMessage(`Eroare server: ${data.error}`, false);
      } else {
        appendMessage(data.reply_html, false, true);
      }
    } catch (e) {
      setMessages((prev) => prev.slice(0, -1));
      appendMessage(`Eroare rețea: ${e.message}`, false);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col h-screen bg-gray-100">
      {/* CHAT AREA */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3">
        {messages.map((msg, idx) => (
          <div
            key={idx}
            className={`flex ${msg.isUser ? "justify-end" : "justify-start"}`}
          >
            <div
              className={`
                max-w-[85%] p-3 rounded-xl shadow
                ${msg.isUser ? "bg-blue-600 text-white" : "bg-white text-gray-800"}
              `}
            >
              {msg.isHtml ? (
                <div
                  className="chat-html overflow-x-auto"
                  dangerouslySetInnerHTML={{ __html: msg.text }}
                />
              ) : (
                <div className="whitespace-pre-wrap">{msg.text}</div>
              )}
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      {/* INPUT AREA */}
      <div className="p-3 border-t bg-white flex gap-2">
        <input
          className="flex-1 p-2 border rounded-lg focus:outline-none"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && !loading && sendMessage()}
          placeholder="Întrebare..."
          disabled={loading}
        />
        <button
          className="px-4 py-2 bg-green-600 text-white rounded-lg disabled:opacity-50"
          onClick={sendMessage}
          disabled={loading || !input.trim()}
        >
          Trimite
        </button>
      </div>
    </div>
  );
}
