import React from "react";

export default function NodeCard({ node }) {
  if (!node) return null;

  const props = node.properties || {};

  let displayTitle = props.name || node.name;

  if (!displayTitle && node.label === "Visit") {
    displayTitle = "Vizită medicală";
  }
  if (!displayTitle) {
    displayTitle = "Înregistrare medicală";
  }

  const excludedKeys = ["name", "cnp"];

  return (
    <div className="p-4 rounded-xl shadow-sm bg-white border-l-4 border-teal-500 hover:shadow-md transition-shadow mb-3">
      <div className="font-bold text-lg text-teal-800 mb-1">{displayTitle}</div>

      {props.value && (
        <div className="mt-2 mb-3">
          <span className="bg-teal-100 text-teal-800 font-bold px-3 py-1 rounded-md text-sm border border-teal-200">
            Rezultat: {props.value}
          </span>
        </div>
      )}

      {Object.keys(props).length > 0 && (
        <ul className="text-sm mt-2 space-y-1">
          {Object.entries(props)
            .filter(([key]) => !excludedKeys.includes(key) && key !== "value")
            // Remove duplicate values for the same property (e.g., repeated treatments)
            .reduce((acc, [key, value]) => {
              const valStr = String(value);
              if (!acc.some(([k, v]) => k === key && String(v) === valStr)) {
                acc.push([key, value]);
              }
              return acc;
            }, [])
            .map(([key, value]) => (
              <li key={key + String(value)} className="flex gap-2 items-center">
                <span className="font-semibold text-gray-500 capitalize">
                  {key}:
                </span>
                <span className="text-gray-900 font-medium">
                  {String(value)}
                </span>
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}
