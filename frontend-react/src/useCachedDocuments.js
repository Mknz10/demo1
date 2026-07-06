import { useState, useEffect, useCallback } from "react";
import { db } from "./db";

export function useCachedDocuments(identifier, role = "patient") {
  const [documents, setDocuments] = useState([]);
  const [isLoading, setIsLoading] = useState(true);

  // Funcție separată pentru revalidarea datelor în fundal (folosită de `refetch`)
  const revalidate = useCallback(async () => {
    if (!identifier) return;
    console.log("[Cache] Revalidating documents in background...");
    try {
      const endpoint =
        role === "practitioner"
          ? `http://localhost:8080/api/documents/practitioner/${identifier}`
          : `http://localhost:8080/api/documents/patient/${identifier}`;

      const res = await fetch(endpoint);
      if (res.ok) {
        const freshDocs = await res.json();
        const docsWithOwner = freshDocs.map((doc) => ({
          ...doc,
          ownerId: identifier,
        }));
        await db.documentsList.bulkPut(docsWithOwner);
        setDocuments(freshDocs); // Actualizăm starea cu datele proaspete
      }
    } catch (err) {
      console.error("Mod Offline / Server indisponibil la revalidare:", err);
    }
  }, [identifier, role]);

  // Hook pentru încărcarea inițială (Stale-While-Revalidate)
  useEffect(() => {
    const initialFetch = async () => {
      // Mutăm verificarea aici. Toate setările de stare se fac în acest bloc asincron.
      if (!identifier) {
        setDocuments([]);
        setIsLoading(false);
        return;
      }

      // ETAPA 1: Încercăm să încărcăm din cache
      try {
        setIsLoading(true); // Setăm loading doar dacă avem un identifier
        const cachedDocs = await db.documentsList
          .where("ownerId")
          .equals(identifier)
          .toArray();
        if (cachedDocs.length > 0) {
          console.log("[Cache] Loaded documents from IndexedDB.");
          setDocuments(cachedDocs);
        }
      } catch (err) {
        console.error("Eroare la citirea din cache:", err);
      }

      // ETAPA 2: Revalidăm (sau facem primul fetch) cu serverul
      await revalidate();

      setIsLoading(false);
    };

    initialFetch();
  }, [identifier, role, revalidate]);

  return {
    documents,
    setDocuments,
    isLoading,
    refetch: revalidate,
  };
}
