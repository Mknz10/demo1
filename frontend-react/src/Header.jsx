import React from "react";
import { Network } from "lucide-react";

// Subcomponent for action buttons
function HeaderActions({ onProfile, onLogout }) {
  return (
    <div className="flex flex-col sm:flex-row items-center gap-2">
      <button
        className="bg-gradient-to-r from-teal-500 to-green-400 hover:from-teal-600 hover:to-green-500 text-white py-1 px-4 rounded-lg font-semibold transition border border-green-700 shadow-sm"
        onClick={onProfile}
      >
        Profil
      </button>
      <button
        className="bg-gradient-to-r from-red-500 to-pink-400 hover:from-red-600 hover:to-pink-500 text-white py-1 px-4 rounded-lg font-semibold transition border border-red-700 shadow-sm"
        onClick={onLogout}
      >
        Logout
      </button>
    </div>
  );
}

// Main Header component
export default function Header({ onProfile, onLogout, given }) {
  return (
    <header className="w-full bg-green-100 shadow-md border-b border-green-200">
      <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between py-4 px-4 mt-10">
        {/* Title Section */}
        <div className="flex items-center space-x-3 mb-2 sm:mb-0">
          <Network className="w-9 h-9 text-red-500" />
          <span className="text-3xl sm:text-4xl font-extrabold text-green-700 tracking-tight select-none">
            Salut{given ? `, ${given}` : ""}
          </span>
        </div>
        {/* Actions Section */}
        <HeaderActions onProfile={onProfile} onLogout={onLogout} />
      </div>
    </header>
  );
}
