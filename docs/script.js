// APRSdroid 9M2PJU Mod — download page logic
// Fetches release info from the public GitHub API and renders download
// buttons + live download counters. No backend; the GitHub API exposes
// download_count per asset for unauthenticated requests (CORS-enabled,
// 60 req/hour per IP — plenty for a landing page).

const OWNER = "9M2PJU";
const REPO  = "APRSdroid-9M2PJU-Mod";
const API   = `https://api.github.com/repos/${OWNER}/${REPO}/releases`;

function fmtSize(bytes) {
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024*1024) return (bytes/1024).toFixed(1) + " KB";
  return (bytes/1024/1024).toFixed(1) + " MB";
}

function fmtDate(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  return d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

function fmtNum(n) {
  return n.toLocaleString();
}

function assetDownloadCount(asset) {
  // GitHub API field is download_count
  return asset.download_count || 0;
}

function releaseDownloadCount(release) {
  return release.assets.reduce((sum, a) => sum + assetDownloadCount(a), 0);
}

function totalDownloadCount(releases) {
  return releases.reduce((sum, r) => sum + releaseDownloadCount(r), 0);
}

function assetButton(asset) {
  const a = document.createElement("a");
  a.href = asset.browser_download_url;
  a.className = "btn btn-primary";
  a.textContent = "Download .apk";
  return a;
}

function renderAsset(asset) {
  const row = document.createElement("div");
  row.className = "asset-row";

  row.appendChild(assetButton(asset));

  const name = document.createElement("span");
  name.className = "asset-name";
  name.textContent = asset.name;
  row.appendChild(name);

  const size = document.createElement("span");
  size.className = "asset-size";
  size.textContent = fmtSize(asset.size);
  row.appendChild(size);

  const count = document.createElement("span");
  count.className = "asset-count";
  count.textContent = "↓ " + fmtNum(assetDownloadCount(asset));
  row.appendChild(count);

  return row;
}

function renderLatest(release) {
  const card = document.getElementById("latest-release");
  document.getElementById("latest-name").textContent = release.name || release.tag_name;
  document.getElementById("latest-date").textContent = fmtDate(release.published_at);

  const assetsEl = document.getElementById("latest-assets");
  assetsEl.innerHTML = "";
  if (release.assets.length === 0) {
    assetsEl.textContent = "No APK assets attached to this release.";
  } else {
    release.assets.forEach(a => assetsEl.appendChild(renderAsset(a)));
  }

  document.getElementById("latest-downloads").textContent = fmtNum(releaseDownloadCount(release));
  card.hidden = false;
}

function renderAllReleases(releases) {
  const list = document.getElementById("all-releases");
  list.innerHTML = "";
  releases.forEach(r => {
    const item = document.createElement("div");
    item.className = "release-item";

    const left = document.createElement("div");
    const name = document.createElement("div");
    name.className = "ri-name";
    name.textContent = r.name || r.tag_name;
    left.appendChild(name);
    const meta = document.createElement("div");
    meta.className = "ri-meta";
    meta.textContent = `${fmtDate(r.published_at)} · ${r.assets.length} asset(s)`;
    left.appendChild(meta);
    item.appendChild(left);

    const count = document.createElement("span");
    count.className = "ri-count";
    count.textContent = "↓ " + fmtNum(releaseDownloadCount(r));
    item.appendChild(count);

    list.appendChild(item);
  });
}

function renderError(msg) {
  const status = document.getElementById("download-status");
  status.innerHTML = `<p>Could not load releases from GitHub. ` +
    `<a href="https://github.com/${OWNER}/${REPO}/releases" target="_blank" rel="noopener">View releases on GitHub →</a></p>` +
    `<p style="font-size:0.8rem;color:var(--muted)">${msg}</p>`;
}

async function load() {
  try {
    const res = await fetch(API, { headers: { Accept: "application/vnd.github+json" } });
    if (!res.ok) throw new Error(`GitHub API responded ${res.status}`);
    const releases = await res.json();
    if (!Array.isArray(releases) || releases.length === 0) {
      renderError("No releases published yet.");
      return;
    }

    // GitHub returns latest-first; the API endpoint is /releases (not /latest)
    // so the first entry is the most recent published release.
    document.getElementById("download-status").hidden = true;
    renderLatest(releases[0]);
    renderAllReleases(releases);
    document.getElementById("total-downloads").textContent = fmtNum(totalDownloadCount(releases));
  } catch (e) {
    console.error(e);
    renderError(e.message || "Unknown error");
  }
}

document.addEventListener("DOMContentLoaded", load);
