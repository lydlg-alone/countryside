(function () {
  function normalize(url) {
    return String(url || "").trim().replace(/\/+$/, "");
  }

  let configured = "";
  try {
    const params = new URLSearchParams(location.search);
    configured =
      params.get("api") ||
      localStorage.getItem("VILLAGE_API_BASE") ||
      window.VILLAGE_API_BASE ||
      "";
  } catch (_) {
    configured = window.VILLAGE_API_BASE || "";
  }

  if (configured) {
    window.API_BASE = normalize(configured);
    return;
  }

  const isLocal =
    location.hostname === "localhost" ||
    location.hostname === "127.0.0.1" ||
    location.hostname === "";

  window.API_BASE = isLocal ? "http://localhost:8080" : "";
})();

