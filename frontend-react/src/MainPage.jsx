import React from "react";
import Chat from "./Chat";
import { UploadCloud } from "lucide-react";

export default function MainPage({ isUploading, handleUpload }) {
  return (
    <div className="min-h-screen bg-gradient-to-br from-emerald-50 via-teal-50 to-sky-100 text-gray-900 font-sans">
      {/* Header */}
      <header className="w-full py-10 flex flex-col items-center">
        <h1 className="text-4xl font-extrabold tracking-tight text-teal-700">
          Health Graph AI
        </h1>
        <p className="text-sm text-gray-500 mt-2">
          Upload medical documents and explore structured clinical graphs
        </p>
      </header>

      {/* Upload Section */}
      <div className="px-4">
        <div className="max-w-7xl mx-auto bg-white/70 backdrop-blur-xl border border-white/40 shadow-xl rounded-3xl p-6 sm:p-8 flex flex-col items-center hover:shadow-2xl transition">
          <label className="w-full cursor-pointer flex flex-col items-center justify-center gap-3 bg-gradient-to-r from-teal-600 to-emerald-500 hover:from-teal-500 hover:to-emerald-400 text-white py-5 px-6 rounded-2xl font-semibold transition shadow-md">
            <UploadCloud className="w-7 h-7" />

            <span className="text-lg">
              {isUploading ? "Processing AI..." : "Upload Medical PDF"}
            </span>

            <input
              type="file"
              accept=".pdf"
              className="hidden"
              disabled={isUploading}
              onChange={handleUpload}
            />
          </label>

          <p className="text-sm text-gray-500 mt-4 text-center max-w-md">
            AI extracts clinical entities (Observations, Conditions,
            Medications) and builds a structured health graph automatically.
          </p>

          {isUploading && (
            <div className="mt-4 text-teal-600 text-sm animate-pulse">
              Analyzing document...
            </div>
          )}
        </div>
      </div>

      {/* CHAT SECTION (premium card style) */}
      <main className="flex-1 flex items-center justify-center px-4 pb-12 mt-12">
        <div className="w-full max-w-7xl">
          <div className="rounded-3xl border border-white/40 bg-white/70 backdrop-blur-xl shadow-2xl overflow-hidden transition hover:shadow-3xl">
            {/* Accent bar */}
            <div className="h-1 w-full bg-gradient-to-r from-teal-400 via-emerald-400 to-sky-400" />

            {/* Title strip (subtle improvement) */}
            <div className="px-6 pt-5 pb-2">
              <h2 className="text-lg font-semibold text-teal-700">
                Health Graph Chat
              </h2>
              <p className="text-xs text-gray-500">
                Ask questions about extracted medical data
              </p>
            </div>

            {/* Chat */}
            <div className="p-4 sm:p-6 pt-2">
              <Chat />
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
