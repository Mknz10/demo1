import React from "react";
import { Network, User, LogOut } from "lucide-react";

// Subcomponent for action buttons
function HeaderActions({ onProfile, onLogout }) {
  return (
    <div className="flex items-center gap-3">
      <button
        className="flex items-center gap-2 px-4 py-2 bg-white hover:bg-teal-50 text-gray-700 hover:text-teal-700 rounded-full font-medium transition-all shadow-sm border border-gray-200 hover:border-teal-200"
        onClick={onProfile}
      >
        <User className="w-4 h-4" />
        <span className="hidden sm:inline">Profil</span>
      </button>
      <button
        className="flex items-center gap-2 px-4 py-2 bg-white hover:bg-rose-50 text-gray-700 hover:text-rose-600 rounded-full font-medium transition-all shadow-sm border border-gray-200 hover:border-rose-200"
        onClick={onLogout}
      >
        <LogOut className="w-4 h-4" />
        <span className="hidden sm:inline">Logout</span>
      </button>
    </div>
  );
}

// Main Header component
export default function Header({ onProfile, onLogout, given }) {
  return (
    <header className="sticky top-0 z-50 w-full bg-white/70 backdrop-blur-lg border-b border-white/40 shadow-sm">
      <div className="max-w-7xl mx-auto flex items-center justify-between py-4 px-4 sm:px-6">
        {/* Title Section */}
        <div className="flex items-center gap-3">
          <div className="p-2 bg-gradient-to-br from-teal-400 to-emerald-500 rounded-xl shadow-sm">
            <Network className="w-5 h-5 text-white" />
          </div>
          <span className="text-xl sm:text-2xl font-bold text-gray-800 tracking-tight select-none">
            Salut{given ? `, ${given}` : ""}
          </span>
        </div>
        {/* Actions Section */}
        <HeaderActions onProfile={onProfile} onLogout={onLogout} />
      </div>
    </header>
  );
}
