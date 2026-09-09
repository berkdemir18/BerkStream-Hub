// @plugin-info {"id":"berkstream-desktop","name":"BerkStream Desktop","version":"2.0.0","description":"BerkStream Hub'ın masaüstü motoru: Türkçe dublaj/altyazılı kaynakları paralel tarar, oynatılabilir akış döndürür.","author":"berkdemir18","icon_url":"https://raw.githubusercontent.com/berkdemir18/BerkStream-Hub/main/assets/berkstream-icon.png","supported_types":["movie","show"],"is_builtin":false}
//
// BerkStream Hub - masaüstü (JavaScript) sürümü.
//
// Neden bu dosya var: Hub'ın kataloğu (repo.json / plugins.json) CloudStream'in
// Android eklenti formatı; .cs3 dosyaları PC'de açılmıyor. Bu dosya Hub'daki
// Kotlin sağlayıcıların çalışan JS karşılığı - tek dosya, bağımlılık yok,
// sadece global fetch kullanıyor. BerkStream PC bunu doğrudan URL'den çekip
// çalıştırıyor, böylece kaynak mantığı tek yerde (Hub'da) duruyor.
//
// Sözleşme (host bu fonksiyonları çağırır):
//   search(query)              -> JSON: [{id,title,poster_url,media_type,year,...}]
//   getEpisodes(mediaId)       -> JSON: [{id,title,season,episode_number,...}]
//   getStreams(mediaId)        -> JSON: [{url,quality,format,provider,language,headers}]
//   resolve(queryJson)         -> JSON: {sources:[...],report:[...]}   (BerkStream PC bunu kullanır)

const TMDB_KEY = "e6333b32409e02a4a6eba6fb7ff866bb";
const TMDB = "https://api.themoviedb.org/3";
const IMAGE = "https://image.tmdb.org/t/p/w500";

const UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

const BROWSER_HEADERS = {
  "User-Agent": UA,
  Accept:
    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
  "Accept-Language": "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
  "sec-ch-ua": '"Chromium";v="131", "Not_A Brand";v="24", "Google Chrome";v="131"',
  "sec-ch-ua-mobile": "?0",
  "sec-ch-ua-platform": '"Windows"',
  "Upgrade-Insecure-Requests": "1",
};

// ───────────────────────────── HTTP ─────────────────────────────

// Cloudflare'in ara sayfası HTML olarak geliyor; içeriği ondan ayırmak gerekiyor.
function isChallenge(body) {
  return /Just a moment|cf-browser-verification|challenge-platform|__cf_chl/i.test(body || "");
}

async function req(url, { method = "GET", headers = {}, body, referer, timeout = 12000 } = {}) {
  const origin = new URL(url).origin;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeout);
  try {
    const res = await fetch(url, {
      method,
      body,
      redirect: "follow",
      signal: controller.signal,
      headers: {
        ...BROWSER_HEADERS,
        Referer: referer || `${origin}/`,
        ...(method === "POST"
          ? {
              Origin: origin,
              "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
              "X-Requested-With": "XMLHttpRequest",
              Accept: "application/json, text/plain, */*",
            }
          : {}),
        ...headers,
      },
    });
    const text = await res.text();
    if (isChallenge(text)) throw new Error(`cloudflare: ${url}`);
    if (!res.ok) throw new Error(`http${res.status}: ${url}`);
    return text;
  } finally {
    clearTimeout(timer);
  }
}

const getJson = async (url, opts) => JSON.parse(await req(url, opts));

function absolute(base, href) {
  if (!href) return null;
  if (href.startsWith("//")) return `https:${href}`;
  try {
    return new URL(href, base).href;
  } catch {
    return null;
  }
}

// ─────────────────────── Başlık eşleştirme ───────────────────────
// BerkStreamProvider.kt'deki looseTitle/titleMatches portu. Gevşek "içeren"
// karşılaştırması "Dexter" aramasını "Dexter's Laboratory" ile eşleştiriyordu.

const NOISE =
  /\b(izle|seyret|full|hd|fullhd|4k|1080p|720p|480p|turkce|turkiye|dublaj|altyazili|altyazi|filmi|film|dizisi|dizi|online|tek|parca|part|sezon|bolum|yerli|yabanci|hdfilm|tr)\b/g;

function deaccent(v) {
  return String(v || "")
    .replace(/ı/g, "i")
    .replace(/İ/g, "i")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase();
}

function looseTitle(v) {
  return deaccent(v)
    .replace(/[^a-z0-9]+/g, " ")
    .replace(NOISE, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function tokenSetRatio(a, b) {
  const A = new Set(a.split(" ").filter(Boolean));
  const B = new Set(b.split(" ").filter(Boolean));
  if (!A.size || !B.size) return 0;
  let shared = 0;
  for (const t of A) if (B.has(t)) shared++;
  return Math.round((2 * shared * 100) / (A.size + B.size));
}

function titleMatches(candidate, target) {
  const a = looseTitle(candidate);
  const b = looseTitle(target);
  if (!a || !b) return false;
  if (a === b) return true;
  if (deaccent(candidate).replace(/[^a-z0-9]/g, "") === deaccent(target).replace(/[^a-z0-9]/g, ""))
    return true;
  const targetWords = new Set(b.split(" ").filter(Boolean));
  if (a.split(" ").some((w) => w.length > 2 && !targetWords.has(w))) return false;
  return tokenSetRatio(a, b) >= 92;
}

// ───────────────────── packed-eval çözücü ─────────────────────
// Türk oynatıcı sayfalarının çoğu m3u8'i "p,a,c,k,e,d" sıkıştırmasında saklıyor.

const PACKED = /eval\(function\(p,a,c,k,e,(?:r|d)\)\{[\s\S]*?\}\((.*?)\)\)/;

function baseN(value, base) {
  const digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  let out = "";
  let n = value;
  do {
    out = digits[n % base] + out;
    n = Math.floor(n / base);
  } while (n > 0);
  return out;
}

function unpack(source) {
  if (!source || !source.includes("p,a,c,k,e,")) return source;
  let out = source;
  const seen = new Set();
  for (let guard = 0; guard < 8; guard++) {
    const match = PACKED.exec(out);
    if (!match || seen.has(match[0])) break;
    seen.add(match[0]);
    const m =
      /^\s*'([\s\S]*?)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'([\s\S]*?)'\.split\('\|'\)/.exec(match[1]) ||
      /^\s*"([\s\S]*?)"\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*"([\s\S]*?)"\.split\('\|'\)/.exec(match[1]);
    if (!m) break;
    const payload = m[1].replace(/\\'/g, "'").replace(/\\"/g, '"').replace(/\\\\/g, "\\");
    const base = parseInt(m[2], 10);
    const count = parseInt(m[3], 10);
    const words = m[4].split("|");
    if (words.length !== count) break;
    const dict = new Map();
    for (let i = 0; i < count; i++) if (words[i]) dict.set(baseN(i, base), words[i]);
    out = out.replace(match[0], payload.replace(/\b\w+\b/g, (t) => dict.get(t) ?? t));
  }
  return out;
}

// ─────────────────────────── Çözücüler ───────────────────────────

function unescapeUrl(v) {
  return String(v || "")
    .replace(/\\u0026/g, "&")
    .replace(/\\\//g, "/")
    .replace(/\\/g, "");
}

/** ContentX ailesi: pichive.online, contentx.me, hotlinger.com, playru.net */
async function contentX(url, referer) {
  const origin = new URL(url).origin;
  const page = await req(url, { referer });
  const streams = [];
  const subtitles = [];
  const seen = new Set();

  for (const m of page.matchAll(/"file":"((?:\\"|[^"])+)","label":"((?:\\"|[^"])+)"/g)) {
    const subUrl = unescapeUrl(m[1]);
    if (!subUrl || seen.has(subUrl)) continue;
    seen.add(subUrl);
    subtitles.push({ lang: m[2], url: absolute(origin, subUrl) });
  }

  const targets = [];
  const main = /window\.openPlayer\('([^']+)'/.exec(page);
  if (main) targets.push({ id: main[1], label: "Türkçe Altyazı" });
  const dub = /,"([^"']+?)","Türkçe/.exec(page);
  if (dub) targets.push({ id: dub[1], label: "Türkçe Dublaj" });

  for (const t of targets) {
    try {
      const src = await req(`${origin}/source2.php?v=${t.id}`, { referer });
      const file = /file":"([^"]+)/.exec(src);
      if (!file) continue;
      let link = unescapeUrl(file[1]);
      if (/hotlinger|dplayer82\.site/.test(link)) link = link.replace("m.php", "master.m3u8");
      streams.push({
        url: link,
        format: "hls",
        language: t.label,
        headers: { Referer: url, "User-Agent": UA },
      });
    } catch {}
  }
  return { streams, subtitles };
}

/** VidRame: m3u8 ters çevrilmiş + ROT13 + base64 sarılı. */
function rot13(s) {
  return s.replace(/[a-zA-Z]/g, (c) => {
    const base = c <= "Z" ? 90 : 122;
    const next = c.charCodeAt(0) + 13;
    return String.fromCharCode(base >= next ? next : next - 26);
  });
}

function b64decode(value) {
  if (typeof Buffer !== "undefined") return Buffer.from(value, "base64").toString("binary");
  return atob(value);
}

async function vidRame(url, referer) {
  const page = await req(url, { referer });
  const packed = /\.dd\("([^"]+)"\)/.exec(page);
  if (!packed) return { streams: [], subtitles: [] };
  let raw = packed[1].replace(/-/g, "+").replace(/_/g, "/");
  while (raw.length % 4) raw += "=";
  const link = [...rot13(b64decode(raw))].reverse().join("");
  return {
    streams: [
      { url: link, format: "hls", language: "Türkçe", headers: { Referer: `${new URL(url).origin}/` } },
    ],
    subtitles: [],
  };
}

/** PlayerJS / FirePlayer şablonu - Türk sitelerinin ortak oynatıcısı. */
async function playerJs(url, referer) {
  const page = unpack(await req(url, { referer }));
  const streams = [];
  const subtitles = [];
  const seen = new Set();

  const subs = /playerjsSubtitle\s*=\s*"(.+?)"/.exec(page);
  if (subs) {
    for (const m of subs[1].matchAll(/\[(.*?)\](https?:\/\/[^\s",]+)/g)) {
      subtitles.push({ lang: m[1], url: m[2] });
    }
  }
  const push = (raw) => {
    const link = unescapeUrl(raw);
    if (!link || seen.has(link)) return;
    seen.add(link);
    streams.push({ url: link, format: "hls", language: "Türkçe", headers: { Referer: url } });
  };
  for (const m of page.matchAll(/["']?file["']?\s*:\s*["']([^"']+\.m3u8[^"']*)["']/g)) push(m[1]);
  if (!streams.length) for (const m of page.matchAll(/(https?:[^\s"'<>]*?\.m3u8[^\s"'<>]*)/g)) push(m[1]);
  return { streams, subtitles };
}

const HOSTS = [
  [/contentx\.me|pichive\.online|playru\.net|hotlinger\.com|dplayer82\.site/i, contentX],
  [/vidrame\./i, vidRame],
];

/**
 * İframe'i akışa çevirir; çeviremezse "embed" olarak geri verir.
 *
 * Oynatıcı sunucularının çoğu Cloudflare arkasında (2026-09-09 ölçümü: 54
 * kaynağın 15'i). CloudStream Android'de bunu WebView ile aşıyor; masaüstünde
 * karşılığı kullanıcının kendi tarayıcısı - embed iframe'i açıldığında
 * Cloudflare kendiliğinden geçiliyor.
 */
async function resolveFrame(frame, referer, language) {
  // Sayfa şablonlarında çözülmemiş `${...}` kalıntısı iframe olarak geliyor.
  if (!frame || frame.includes("${") || frame.includes("%7B")) {
    return { streams: [], subtitles: [] };
  }
  try {
    for (const [pattern, fn] of HOSTS) {
      if (pattern.test(frame)) {
        const r = await fn(frame, referer);
        if (r.streams.length) return r;
      }
    }
    if (!/youtube|youtu\.be|fragman|trailer/i.test(frame)) {
      const r = await playerJs(frame, referer);
      if (r.streams.length) return r;
    }
  } catch {}
  return {
    streams: [{ url: frame, format: "embed", language: language || "Türkçe", headers: { Referer: referer } }],
    subtitles: [],
  };
}

// ─────────────────────────── Sağlayıcılar ───────────────────────────

/** Site base64'ü ISO-8859-1 baytları olarak kodluyor. */
function decodePayload(b64) {
  const binary = b64decode(b64);
  if (typeof Buffer !== "undefined") return JSON.parse(Buffer.from(binary, "binary").toString("utf8"));
  return JSON.parse(decodeURIComponent(escape(binary)));
}

/** RoketDizi ve SelcukFlix'in ortak altyapısı (Hub: RoketDizi.kt). */
function macellan({ name, mainUrl, types }) {
  const secureData = async (url) => {
    const html = await req(url);
    const script = /<script id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/.exec(html);
    if (!script) return null;
    const node = JSON.parse(script[1])?.props?.pageProps?.secureData;
    return node ? decodePayload(node) : null;
  };

  return {
    name,
    types,
    mainUrl,

    async search(query) {
      const raw = await req(
        `${mainUrl}/api/bg/searchcontent?searchterm=${encodeURIComponent(query)}`,
        { method: "POST", body: "", referer: `${mainUrl}/` },
      );
      const data = decodePayload(JSON.parse(raw).response);
      if (data.state !== true) return [];
      return (data.result || [])
        .filter((r) => !String(r.used_slug || "").includes("/seri-filmler/"))
        .map((r) => ({
          title: r.object_name,
          url: absolute(mainUrl, `/${r.used_slug}`),
          year: r.object_release_year || null,
          isSeries: /seri/i.test(r.used_type || ""),
        }))
        .filter((x) => x.title && x.url);
    },

    async episodeUrl(item, season, episode) {
      const root = await secureData(item.url);
      const related = root?.RelatedResults || root?.relatedResults || {};
      for (const s of related.getSerieSeasonAndEpisodes?.result || []) {
        if (Number(s.season_no) !== Number(season)) continue;
        for (const e of s.episodes || []) {
          if (Number(e.episode_no) === Number(episode)) return absolute(mainUrl, `/${e.used_slug}`);
        }
      }
      return null;
    },

    async links(url) {
      const root = await secureData(url);
      const out = { streams: [], subtitles: [] };
      if (!root) return out;
      const related = root.RelatedResults || root.relatedResults || {};

      // source_content bir URL değil, komple <iframe ...> HTML'i.
      const frames = [];
      const push = (list) => {
        for (const s of list || []) {
          const raw = s.source_content || "";
          const src = /src=["']([^"']+)["']/.exec(raw)?.[1];
          if (src) frames.push({ url: absolute(mainUrl, src), quality: s.quality_name, language: s.language_name });
        }
      };
      push(related.getEpisodeSources?.result);
      for (const [key, value] of Object.entries(related)) {
        if (key.startsWith("getMoviePartSourcesById")) push(value?.result);
      }

      const seen = new Set();
      for (const frame of frames.slice(0, 6)) {
        if (!frame.url || seen.has(frame.url)) continue;
        seen.add(frame.url);
        const r = await resolveFrame(frame.url, `${mainUrl}/`, frame.language);
        for (const s of r.streams) out.streams.push({ ...s, quality: frame.quality || s.quality });
        out.subtitles.push(...r.subtitles);
      }
      return out;
    },
  };
}

/** HDFilmİzle - arama JSON döndürüyor (Hub: HDFilmIzle.kt). */
const hdFilmIzle = {
  name: "HDFilmİzle",
  types: ["movie"],
  mainUrl: "https://www.hdfilmizle.vip",

  async search(query) {
    const raw = await req(`${this.mainUrl}/search/`, {
      method: "POST",
      body: new URLSearchParams({ query }).toString(),
      referer: `${this.mainUrl}/`,
    });
    let list;
    try {
      list = JSON.parse(raw);
    } catch {
      return [];
    }
    return (Array.isArray(list) ? list : [])
      .map((v) => ({ title: v.name, url: absolute(this.mainUrl, v.slug), year: parseInt(v.year, 10) || null }))
      .filter((x) => x.title && x.url);
  },

  async links(url) {
    const html = await req(url);
    const out = { streams: [], subtitles: [] };
    const frames = [...html.matchAll(/<iframe[^>]+data-src=["']([^"']+)["']/gi)].map((m) => m[1]);
    for (const raw of frames.slice(0, 3)) {
      const r = await resolveFrame(absolute(this.mainUrl, raw), `${this.mainUrl}/`);
      out.streams.push(...r.streams);
      out.subtitles.push(...r.subtitles);
    }
    return out;
  },
};

/** SinemaCX (Hub: SinemaCX.kt). */
const sinemaCx = {
  name: "SinemaCX",
  types: ["movie"],
  mainUrl: "https://www.sinema.gg",

  async search(query) {
    const html = await req(`${this.mainUrl}/?s=${encodeURIComponent(query)}`);
    const out = [];
    for (const m of html.matchAll(/<a[^>]+href=["']([^"']+)["'][^>]*title=["']([^"']+)["']/gi)) {
      const url = absolute(this.mainUrl, m[1]);
      if (!url || !/\/[a-z0-9-]+-izle/i.test(url)) continue;
      if (out.some((x) => x.url === url)) continue;
      out.push({ title: m[2].trim(), url, year: null });
    }
    return out;
  },

  async links(url) {
    const grab = (html) => [...html.matchAll(/<iframe[^>]+data-vsrc=["']([^"']+)["']/gi)].map((m) => m[1]);
    let frames = grab(await req(url));
    if (!frames.length || frames.every((f) => /youtube|fragman|trailer/i.test(f))) {
      const alt = url.endsWith("/") ? `${url}2/` : `${url}/2/`;
      try {
        frames = grab(await req(alt, { referer: url }));
      } catch {}
    }
    const out = { streams: [], subtitles: [] };
    for (const raw of frames.slice(0, 3)) {
      const frame = absolute(this.mainUrl, raw.split("?img=")[0]);
      const r = await resolveFrame(frame, `${this.mainUrl}/`);
      out.streams.push(...r.streams);
      out.subtitles.push(...r.subtitles);
    }
    return out;
  },
};

const roketDizi = macellan({ name: "RoketDizi", mainUrl: "https://roketdizi.life", types: ["show"] });

/**
 * Etkin sağlayıcılar. Hub'daki providerPriority sırasının bu sürümde
 * karşılığı olan kısmı. Dışarıda kalanlar ve sebepleri (2026-09-09 ölçümü,
 * 54 kaynak tarandı: 19 açılıyor, 15 Cloudflare, 20 ölü):
 *   - Dizilla, SelcukFlix : cevabı AES ile şifreli, anahtar çözümü portlanmadı
 *   - HDFilmCehennemi, DiziPal, DiziYou, FilmMakinesi, WebteIzle, JetFilmizle,
 *     SezonlukDizi (arama sayfası) ve 8 tanesi daha : Cloudflare duvarı
 *   - 20 kaynak : alan adı ölü (domains.json'daki "disabled" listesi)
 */
const PROVIDERS = [roketDizi, hdFilmIzle, sinemaCx];

// ─────────────────────────── Toplayıcı ───────────────────────────

const SCAN_BUDGET_MS = 18000;
const CHUNK = 4;
const winners = new Map();

async function withTimeout(promise, ms) {
  let timer;
  try {
    return await Promise.race([
      promise,
      new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error("zaman aşımı")), ms);
      }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}

async function scanProvider(provider, query) {
  const { title, originalTitle, year, type, season, episode } = query;
  const isSeries = type === "tv" || type === "show";
  if (isSeries !== provider.types.includes("show")) return [];

  let picked = null;
  for (const q of [...new Set([title, originalTitle].filter(Boolean))]) {
    const results = await provider.search(q);
    picked =
      results.find((r) => titleMatches(r.title, title)) ||
      (originalTitle ? results.find((r) => titleMatches(r.title, originalTitle)) : null) ||
      (year ? results.find((r) => r.year && Math.abs(r.year - year) <= 1) : null);
    if (picked) break;
  }
  if (!picked) return [];

  let target = picked.url;
  if (isSeries && provider.episodeUrl) {
    target = await provider.episodeUrl(picked, season ?? 1, episode ?? 1);
    if (!target) return [];
  }

  const { streams, subtitles } = await provider.links(target);
  return streams.map((s) => ({
    provider: provider.name,
    name: `${provider.name}${s.language ? ` [${s.language}]` : ""}${s.quality ? ` ${s.quality}` : ""}`,
    quality: s.quality || "Bilinmiyor",
    language: s.language || "Türkçe",
    format: s.format,
    url: s.url,
    headers: s.headers || null,
    matchedTitle: picked.title,
    subtitles,
  }));
}

/**
 * Bütün kaynakları öbek öbek tarar, bütçe dolunca durur.
 * query: {title, originalTitle, year, type:"movie"|"tv", season, episode}
 */
async function resolveQuery(query) {
  const key = `${looseTitle(query.title)}_${query.season ?? 0}_${query.episode ?? 0}`;
  const winner = winners.get(key);
  const ordered = [...PROVIDERS].sort((a, b) =>
    a.name === winner ? -1 : b.name === winner ? 1 : 0,
  );

  const deadline = Date.now() + SCAN_BUDGET_MS;
  const sources = [];
  const report = [];

  for (let i = 0; i < ordered.length; i += CHUNK) {
    if (Date.now() > deadline) break;
    const settled = await Promise.allSettled(
      ordered.slice(i, i + CHUNK).map(async (p) => {
        const started = Date.now();
        const streams = await withTimeout(scanProvider(p, query), Math.max(1000, deadline - Date.now()));
        return { provider: p.name, streams, ms: Date.now() - started };
      }),
    );
    for (const r of settled) {
      if (r.status !== "fulfilled") {
        report.push({ provider: "?", ok: false, error: String(r.reason?.message || r.reason) });
        continue;
      }
      const { provider, streams, ms } = r.value;
      report.push({ provider, ok: true, count: streams.length, ms });
      if (streams.length) {
        if (!sources.length) winners.set(key, provider);
        sources.push(...streams.map((s) => ({ ...s, ping: ms })));
      }
    }
    if (sources.length >= 3) break;
  }
  return { sources, report };
}

// ─────────────────── Host sözleşmesi (JSON döner) ───────────────────

async function search(query) {
  const data = await getJson(
    `${TMDB}/search/multi?api_key=${TMDB_KEY}&language=tr-TR&include_adult=false&query=${encodeURIComponent(query)}`,
  );
  return JSON.stringify(
    (data.results || [])
      .filter((x) => x.media_type === "movie" || x.media_type === "tv")
      .slice(0, 30)
      .map((x) => ({
        id: `${x.media_type}:${x.id}`,
        title: x.title || x.name,
        original_title: x.original_title || x.original_name || null,
        poster_url: x.poster_path ? `${IMAGE}${x.poster_path}` : null,
        media_type: x.media_type === "movie" ? "movie" : "show",
        year: parseInt((x.release_date || x.first_air_date || "").slice(0, 4), 10) || null,
        rating: x.vote_average || null,
        description: x.overview || null,
      })),
  );
}

async function getEpisodes(mediaId) {
  const [kind, id] = String(mediaId).split(":");
  if (kind !== "tv" && kind !== "show") return "[]";
  const show = await getJson(`${TMDB}/tv/${id}?api_key=${TMDB_KEY}&language=tr-TR`);
  const episodes = [];
  for (const season of (show.seasons || []).filter((s) => s.season_number > 0)) {
    const data = await getJson(
      `${TMDB}/tv/${id}/season/${season.season_number}?api_key=${TMDB_KEY}&language=tr-TR`,
    );
    for (const ep of data.episodes || []) {
      episodes.push({
        id: `tv:${id}:${season.season_number}:${ep.episode_number}`,
        title: ep.name || `Bölüm ${ep.episode_number}`,
        season: season.season_number,
        episode_number: ep.episode_number,
        thumbnail_url: ep.still_path ? `${IMAGE}${ep.still_path}` : null,
        description: ep.overview || null,
      });
    }
  }
  return JSON.stringify(episodes);
}

/** mediaId biçimleri: "movie:<tmdbId>" veya "tv:<tmdbId>:<sezon>:<bölüm>" */
async function getStreams(mediaId) {
  const parts = String(mediaId).split(":");
  const kind = parts[0] === "movie" ? "movie" : "tv";
  const id = parts[1];
  const detail = await getJson(`${TMDB}/${kind}/${id}?api_key=${TMDB_KEY}&language=tr-TR`);
  const { sources } = await resolveQuery({
    title: detail.title || detail.name,
    originalTitle: detail.original_title || detail.original_name,
    year: parseInt((detail.release_date || detail.first_air_date || "").slice(0, 4), 10) || null,
    type: kind,
    season: parts[2] ? Number(parts[2]) : 1,
    episode: parts[3] ? Number(parts[3]) : 1,
  });
  return JSON.stringify(sources);
}

/** BerkStream PC bunu çağırıyor: TMDB kimliği olmadan da çalışır. */
async function resolve(queryJson) {
  const query = typeof queryJson === "string" ? JSON.parse(queryJson) : queryJson;
  return JSON.stringify(await resolveQuery(query));
}

const BerkStreamHub = { search, getEpisodes, getStreams, resolve, PROVIDERS, titleMatches, unpack };

if (typeof module !== "undefined" && module.exports) module.exports = BerkStreamHub;
if (typeof globalThis !== "undefined") globalThis.BerkStreamHub = BerkStreamHub;
