import React from "react";

export default function EdgeDisplay({ edge, nodes }) {
  if (!edge || !nodes) return null;
  const sourceNode = nodes.find((n) => String(n.id) === String(edge.source));
  const targetNode = nodes.find((n) => String(n.id) === String(edge.target));
  return (
    <div className="flex items-center space-x-3 p-3 bg-gradient-to-r from-green-100 via-blue-100 to-teal-100 rounded-xl mb-3 border-2 border-blue-300 shadow text-gray-900">
      <span className="font-semibold text-green-700">
        {sourceNode ? sourceNode.name || sourceNode.label : edge.source}
      </span>
      <span className="text-blue-500">&rarr;</span>
      <span className="font-semibold text-green-700">
        {targetNode ? targetNode.name || targetNode.label : edge.target}
      </span>
      <span className="ml-2 text-xs text-blue-700 bg-blue-100 px-2 py-1 rounded-full border border-blue-400">
        {edge.label}
      </span>
    </div>
  );
}
