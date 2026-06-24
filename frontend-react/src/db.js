import Dexie from "dexie";

export const db = new Dexie("HealthAppDB");

// 1. Definim structura bazei de date (tabelele)
db.version(1).stores({
  // Metadate documente (lista de fișiere). 'id' este cheia primară, 'ownerId' pentru filtrare rapidă.
  documentsList: "id, ownerId, name, uploadDate",
  // Fișiere fizice (Blob-uri/PDF-uri descărcate)
  documentBlobs: "id, timestamp",
});

// ==============================================
// CACHING PENTRU FIȘIERE (BLOB-URI)
// ==============================================
export const getOrDownloadDocument = async (docId) => {
  // Verificăm dacă fișierul PDF există deja salvat local în browser
  const cachedBlob = await db.documentBlobs.get(docId);

  if (cachedBlob) {
    console.log(
      "[IndexedDB] Document încărcat instantaneu din cache-ul local ⚡",
    );
    return URL.createObjectURL(cachedBlob.blob);
  }

  // Dacă nu este în cache, îl descărcăm clasic de la server
  console.log("[Network] Se descarcă documentul de la server 🌐...");
  const response = await fetch(
    `http://localhost:8080/api/documents/download/${docId}`,
  );
  if (!response.ok) throw new Error("Eroare la descărcarea documentului");

  const blob = await response.blob();

  // Salvăm fișierul fizic (Blob-ul) în IndexedDB pentru viitor
  await db.documentBlobs.put({
    id: docId,
    blob: blob,
    timestamp: new Date().getTime(),
  });

  return URL.createObjectURL(blob);
};

// ==============================================
// SECURITATE / CONFIDENȚIALITATE (La Logout)
// ==============================================
export const clearLocalFirstData = async () => {
  try {
    await db.documentsList.clear();
    await db.documentBlobs.clear();
    console.log("[Privacy] Datele cache au fost șterse din browser.");
  } catch (error) {
    console.error("Eroare la curățarea cache-ului:", error);
  }
};
