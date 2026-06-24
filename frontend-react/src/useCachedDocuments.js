import { useState, useEffect } from "react";
import { db } from "./db";

export function useCachedDocuments(identifier, role = "patient") {
  const [documents, setDocuments] = useState([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!identifier) return;
    let isMounted = true;

    const fetchDocuments = async () => {
      // ETAPA 1: CACHE (Latență 0)
      // Extragem instant lista veche din memoria browser-ului
      const cachedDocs = await db.documentsList
        .where("ownerId")
        .equals(identifier)
        .toArray();
      if (isMounted && cachedDocs.length > 0) {
        setDocuments(cachedDocs);
        setIsLoading(false); // Dezactivăm "loading-ul", interfața se încarcă instant
      }

      // ETAPA 2: REVALIDARE ÎN FUNDAL
      // Facem request pe ascuns pentru a vedea dacă au apărut documente noi pe server
      try {
        const endpoint =
          role === "practitioner"
            ? `http://localhost:8080/api/documents/practitioner/${identifier}`
            : `http://localhost:8080/api/documents/patient/${identifier}`;

        const res = await fetch(endpoint);
        if (res.ok) {
          const freshDocs = await res.json();

          if (isMounted) {
            setDocuments(freshDocs);
            setIsLoading(false);
          }

          // ETAPA 3: SINCRONIZARE CACHE
          // Adăugăm 'ownerId' pentru a ști ale cui sunt documentele și actualizăm baza de date locală
          const docsWithOwner = freshDocs.map((doc) => ({
            ...doc,
            ownerId: identifier,
          }));
          await db.documentsList.bulkPut(docsWithOwner);
        }
      } catch (err) {
        console.error("Mod Offline / Server indisponibil:", err);
        if (isMounted) setIsLoading(false);
      }
    };

    fetchDocuments();
    return () => {
      isMounted = false;
    };
  }, [identifier, role]);

  return { documents, isLoading };
}
