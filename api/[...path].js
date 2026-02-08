function joinPaths(a, b) {
  const left = (a || "/").replace(/\/+$/, "");
  const right = (b || "/").replace(/^\/+/, "");
  return `${left}/${right}`.replace(/\/+$/, "") || "/";
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on("data", (c) => chunks.push(Buffer.from(c)));
    req.on("end", () => resolve(Buffer.concat(chunks)));
    req.on("error", reject);
  });
}

module.exports = async (req, res) => {
  const backend = process.env.BACKEND_URL;
  if (!backend) {
    res.statusCode = 500;
    res.setHeader("content-type", "application/json; charset=utf-8");
    res.end(
      JSON.stringify({
        error: "BACKEND_URL is not set",
        hint: "Set BACKEND_URL in Vercel Environment Variables (e.g. https://your-backend.example.com or http://x.x.x.x:8080)."
      })
    );
    return;
  }

  let target;
  try {
    const backendUrl = new URL(backend);
    const incomingUrl = new URL(req.url || "/", "http://local");
    target = new URL(backendUrl.toString());
    target.pathname = joinPaths(backendUrl.pathname, incomingUrl.pathname);
    target.search = incomingUrl.search;
  } catch (e) {
    res.statusCode = 500;
    res.setHeader("content-type", "application/json; charset=utf-8");
    res.end(
      JSON.stringify({
        error: "Invalid BACKEND_URL",
        backend: backend
      })
    );
    return;
  }

  const headers = { ...req.headers };
  delete headers.host;
  delete headers.connection;
  delete headers["content-length"];
  // Avoid upstream compression to keep proxying simple/safe.
  headers["accept-encoding"] = "identity";

  let body;
  const method = (req.method || "GET").toUpperCase();
  if (method !== "GET" && method !== "HEAD") {
    body = await readBody(req);
  }

  let upstream;
  try {
    upstream = await fetch(target.toString(), {
      method,
      headers,
      body: body && body.length ? body : undefined,
      redirect: "manual"
    });
  } catch (e) {
    res.statusCode = 502;
    res.setHeader("content-type", "application/json; charset=utf-8");
    res.end(
      JSON.stringify({
        error: "Failed to reach backend",
        target: target.toString()
      })
    );
    return;
  }

  res.statusCode = upstream.status;

  const setCookies = [];
  upstream.headers.forEach((value, key) => {
    const k = key.toLowerCase();
    if (k === "set-cookie") {
      setCookies.push(value);
      return;
    }
    if (k === "transfer-encoding") return;
    if (k === "content-encoding") return;
    if (k === "content-length") return;
    res.setHeader(key, value);
  });
  if (setCookies.length) res.setHeader("set-cookie", setCookies);

  const ab = await upstream.arrayBuffer();
  res.end(Buffer.from(ab));
};
