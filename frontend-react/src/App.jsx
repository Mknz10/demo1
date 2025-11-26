import React, { useState, useEffect } from "react";
import {
  Network,
  Users,
  ArrowRight,
  Database,
  AlertTriangle,
  RefreshCw,
} from "lucide-react";

// --- NODE COMPONENT ---
const NodeCard = ({ node }) => {
  const { id, label, name, properties, color } = node;
  // Ensure a consistent look with rounded corners and shadows
  const bgColorClass = color || "bg-gray-600";

  return (
    <div
      className={`p-4 rounded-xl shadow-lg transform transition duration-300 hover:scale-[1.02] ${bgColorClass} text-white`}
    >
      <div className="flex items-center space-x-2 border-b border-white/30 pb-2 mb-2">
        {label === "Person" ? (
          <Users className="w-5 h-5" />
        ) : (
          <Database className="w-5 h-5" />
        )}
        <h3 className="font-bold text-lg truncate">{name || label}</h3>
      </div>
      <p className="text-sm font-medium opacity-80">
        {label} (ID: {id})
      </p>
      {Object.keys(properties).length > 0 && (
        <ul className="text-xs mt-1 space-y-0.5">
          {Object.entries(properties).map(([key, value]) => (
            <li key={key} className="truncate">
              <span className="font-semibold capitalize">{key}:</span>{" "}
              {String(value)}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

// --- EDGE COMPONENT ---
const EdgeDisplay = ({ edge, nodes }) => {
  // Find the source and target nodes based on their IDs
  const sourceNode = nodes.find((n) => String(n.id) === String(edge.source));
  const targetNode = nodes.find((n) => String(n.id) === String(edge.target));

  if (!sourceNode || !targetNode) {
    return (
      // Styling for error state
      <div className="flex items-center space-x-2 p-3 bg-red-900/50 text-red-300 rounded-lg text-xs">
        <AlertTriangle className="w-4 h-4" />
        <span>
          Relationship Error: Node ID missing ({edge.source} &rarr;{" "}
          {edge.target})
        </span>
      </div>
    );
  }

  return (
    // Styling for edge card: dark, blurred background, rounded, shadow
    <div className="flex items-center justify-start space-x-3 p-3 bg-white/10 backdrop-blur-sm rounded-lg shadow-inner text-white w-full">
      <span className="font-semibold text-sm truncate max-w-[calc(50%-70px)]">
        {sourceNode.name || sourceNode.label}
      </span>
      {/* Styling for the relationship type bubble */}
      <div className="flex items-center space-x-2 px-3 py-1 bg-white/20 rounded-full">
        <ArrowRight className="w-4 h-4" />
        <span className="text-xs font-bold uppercase tracking-wider">
          {edge.label}
        </span>
      </div>
      <span className="font-semibold text-sm truncate max-w-[calc(50%-70px)]">
        {targetNode.name || targetNode.label}
      </span>
      {Object.keys(edge.properties).length > 0 && (
        <span className="text-xs ml-auto opacity-70 hidden sm:block">
          (
          {Object.entries(edge.properties)
            .map(([key, value]) => `${key}: ${String(value)}`)
            .join(", ")}
          )
        </span>
      )}
    </div>
  );
};

// --- MAIN APPLICATION COMPONENT ---
export default function App() {
  const [graphData, setGraphData] = useState({ nodes: [], edges: [] });
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let isMounted = true;

    async function loadGraph() {
      try {
        setIsLoading(true);
        setError(null);

        const response = await fetch("http://localhost:8080/api/graph");
        if (!response.ok) {
          throw new Error(`API responded with status ${response.status}`);
        }

        const payload = await response.json();
        if (!isMounted) return;

        const nodes = Array.isArray(payload?.nodes) ? payload.nodes : [];
        const edges = Array.isArray(payload?.edges) ? payload.edges : [];
        setGraphData({ nodes, edges });
      } catch (fetchError) {
        if (!isMounted) return;
        setError(
          fetchError.message || "Unknown error while loading graph data"
        );
        setGraphData({ nodes: [], edges: [] });
      } finally {
        if (isMounted) {
          setIsLoading(false);
        }
      }
    }

    loadGraph();

    return () => {
      isMounted = false;
    };
  }, []);

  const { nodes, edges } = graphData;

  return (
    // Main layout: Dark background, min height to fill screen, padding, Inter font (default for Tailwind)
    <div className="min-h-screen bg-gray-900 p-4 sm:p-8 text-gray-100 font-sans">
      <header className="text-center mb-8">
        {/* Header styling: Gradient text, large font, center alignment */}
        <h1 className="text-4xl font-extrabold flex items-center justify-center space-x-3 text-transparent bg-clip-text bg-gradient-to-r from-teal-400 to-indigo-500">
          <Network className="w-8 h-8" />
          <span>Memgraph Project Viewer</span>
        </h1>
        <p className="text-gray-400 mt-2 text-lg">
          Visualizing live relationships stored in Memgraph
        </p>
      </header>

      {isLoading ? (
        <div className="flex justify-center items-center h-64">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-teal-400"></div>
          <p className="ml-4 text-xl text-teal-300">
            Loading graph from API...
          </p>
        </div>
      ) : (
        <main className="max-w-6xl mx-auto">
          {/* Responsive grid layout */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            {/* NODES SECTION CONTAINER */}
            <section className="lg:col-span-1 p-6 bg-gray-800 rounded-2xl shadow-2xl border border-gray-700">
              <h2 className="text-2xl font-bold mb-4 border-b pb-2 border-teal-500/50">
                Nodes ({nodes.length})
              </h2>
              {/* Added scroll bar for long lists */}
              <div className="space-y-4 max-h-[400px] lg:max-h-[60vh] overflow-y-auto pr-2">
                {nodes.length > 0 ? (
                  nodes.map((node) => <NodeCard key={node.id} node={node} />)
                ) : (
                  <p className="text-gray-400">
                    No nodes found. Check your mock data.
                  </p>
                )}
              </div>
            </section>

            {/* EDGES/RELATIONSHIPS SECTION CONTAINER */}
            <section className="lg:col-span-2 p-6 bg-gray-800 rounded-2xl shadow-2xl border border-gray-700">
              <h2 className="text-2xl font-bold mb-4 border-b pb-2 border-teal-500/50">
                Relationships ({edges.length})
              </h2>
              {/* Added scroll bar for long lists */}
              <div className="space-y-3 max-h-[400px] lg:max-h-[60vh] overflow-y-auto pr-2">
                {edges.length > 0 ? (
                  edges.map((edge) => (
                    <EdgeDisplay key={edge.id} edge={edge} nodes={nodes} />
                  ))
                ) : (
                  <p className="text-gray-400">
                    No relationships found. Check your mock data.
                  </p>
                )}
              </div>

              {/* Cypher Query Box - Now refers to the simulated query */}
              <div className="mt-8 p-4 bg-gray-700 rounded-xl text-sm space-y-3">
                <div className="flex items-center gap-2 text-teal-300">
                  <RefreshCw className="w-4 h-4" />
                  <h3 className="font-semibold">Data Source</h3>
                </div>
                <p className="text-gray-300">
                  Graph data is fetched from your Spring Boot API at
                  <code className="px-2 py-0.5 mx-1 bg-gray-900 rounded">
                    http://localhost:8080/api/graph
                  </code>
                  and reflects what is currently stored inside Memgraph.
                </p>
                <p className="opacity-80">
                  If nothing appears, make sure Memgraph is running with data
                  (for example via{" "}
                  <code className="px-2 py-0.5 bg-gray-900 rounded">
                    mgconsole
                  </code>
                  ) and that the backend is started with{" "}
                  <code className="px-2 py-0.5 bg-gray-900 rounded">
                    ./mvnw spring-boot:run
                  </code>{" "}
                  in the
                  <code className="px-2 py-0.5 bg-gray-900 rounded">
                    backend-java
                  </code>{" "}
                  folder.
                </p>
                {error && (
                  <p className="text-red-300 text-xs">Last error: {error}</p>
                )}
              </div>
            </section>
          </div>
        </main>
      )}

      <footer className="mt-12 text-center text-gray-500 text-xs">
        Built with React, Tailwind CSS, and live Memgraph data.
      </footer>
    </div>
  );
}
