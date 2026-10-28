// @plugin-info {"id":"berkstream-desktop","name":"BerkStream Desktop","version":"2.5.0","description":"BerkStream Hub'ın Windows motoru: FilmModu, SinemaCX, SezonlukDizi ve çalışan Türkçe kaynaklar.","author":"berkdemir18","icon_url":"https://raw.githubusercontent.com/berkdemir18/BerkStream-Hub/main/assets/berkstream-icon.png","supported_types":["movie","show"],"is_builtin":false}

var TMDB_KEY = "e6333b32409e02a4a6eba6fb7ff866bb";
var TMDB = "https://api.themoviedb.org/3";
var IMAGE = "https://image.tmdb.org/t/p/w342";
var BACKDROP = "https://image.tmdb.org/t/p/w1280";

var UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

var BROWSER_HEADERS = {
  "User-Agent": UA,
  Accept:
    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
  "Accept-Language": "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7",
  "Upgrade-Insecure-Requests": "1",
};

// ───────────────────────── yardımcılar (motor-güvenli) ─────────────────────────

function originOf(url) {
  var m = /^(https?:\/\/[^/]+)/i.exec(url || "");
  return m ? m[1] : "";
}

function absolute(base, href) {
  if (!href) return null;
  href = String(href).trim();
  if (/^https?:\/\//i.test(href)) return href;
  if (href.indexOf("//") === 0) return "https:" + href;
  var origin = originOf(base);
  if (!origin) return null;
  if (href.charAt(0) === "/") return origin + href;
  return base.replace(/\/[^/]*$/, "/") + href;
}

function formEncode(obj) {
  var parts = [];
  for (var k in obj) {
    if (Object.prototype.hasOwnProperty.call(obj, k)) {
      parts.push(encodeURIComponent(k) + "=" + encodeURIComponent(obj[k]));
    }
  }
  return parts.join("&");
}

var B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
function b64decode(input) {
  var data = String(input || "").replace(/[^A-Za-z0-9+/=]/g, "");
  while (data.length % 4) data += "=";
  var out = "";
  for (var i = 0; i < data.length; i += 4) {
    var e1 = B64.indexOf(data.charAt(i));
    var e2 = B64.indexOf(data.charAt(i + 1));
    var e3 = B64.indexOf(data.charAt(i + 2));
    var e4 = B64.indexOf(data.charAt(i + 3));
    var c1 = (e1 << 2) | (e2 >> 4);
    var c2 = ((e2 & 15) << 4) | (e3 >> 2);
    var c3 = ((e3 & 3) << 6) | e4;
    out += String.fromCharCode(c1);
    if (e3 !== -1 && data.charAt(i + 2) !== "=") out += String.fromCharCode(c2);
    if (e4 !== -1 && data.charAt(i + 3) !== "=") out += String.fromCharCode(c3);
  }
  return out;
}

function utf8FromBytes(bytes) {
  var out = "";
  var i = 0;
  while (i < bytes.length) {
    var c = bytes.charCodeAt(i) & 0xff;
    if (c < 0x80) {
      out += String.fromCharCode(c);
      i += 1;
    } else if (c > 0xbf && c < 0xe0) {
      out += String.fromCharCode(((c & 0x1f) << 6) | (bytes.charCodeAt(i + 1) & 0x3f));
      i += 2;
    } else if (c > 0xdf && c < 0xf0) {
      out += String.fromCharCode(
        ((c & 0x0f) << 12) | ((bytes.charCodeAt(i + 1) & 0x3f) << 6) | (bytes.charCodeAt(i + 2) & 0x3f),
      );
      i += 3;
    } else {
      var cp =
        (((c & 0x07) << 18) |
          ((bytes.charCodeAt(i + 1) & 0x3f) << 12) |
          ((bytes.charCodeAt(i + 2) & 0x3f) << 6) |
          (bytes.charCodeAt(i + 3) & 0x3f)) - 0x10000;
      out += String.fromCharCode(0xd800 + (cp >> 10), 0xdc00 + (cp & 0x3ff));
      i += 4;
    }
  }
  return out;
}

function decodeB64Json(b64) {
  return JSON.parse(utf8FromBytes(b64decode(b64)));
}

function decodeB64Text(b64) {
  try {
    var raw = String(b64 || "").replace(/-/g, "+").replace(/_/g, "/");
    return utf8FromBytes(b64decode(raw));
  } catch (e) {
    return "";
  }
}

// ───────────────────────────────── HTTP ─────────────────────────────────

function isChallenge(body) {
  return /Just a moment|cf-browser-verification|challenge-platform|__cf_chl/i.test(body || "");
}

async function req(url, opts) {
  opts = opts || {};
  var origin = originOf(url);
  var headers = {};
  for (var k in BROWSER_HEADERS) headers[k] = BROWSER_HEADERS[k];
  headers.Referer = opts.referer || origin + "/";
  if (opts.method === "POST") {
    headers.Origin = origin;
    headers["Content-Type"] = "application/x-www-form-urlencoded; charset=UTF-8";
    headers["X-Requested-With"] = "XMLHttpRequest";
    headers.Accept = "application/json, text/plain, */*";
  }
  for (var h in opts.headers || {}) headers[h] = opts.headers[h];

  var res = await fetch(url, { method: opts.method || "GET", headers: headers, body: opts.body });
  var text = await res.text();
  if (isChallenge(text)) throw new Error("cloudflare: " + url);
  if (!res.ok) throw new Error("http" + res.status + ": " + url);
  return text;
}

async function getJson(url) {
  return JSON.parse(await req(url));
}

// ────────────────────────── başlık eşleştirme ──────────────────────────

var NOISE =
  /\b(izle|seyret|full|hd|fullhd|4k|1080p|720p|480p|turkce|turkiye|dublaj|altyazili|altyazi|filmi|film|dizisi|dizi|online|tek|parca|part|sezon|bolum|yerli|yabanci|hdfilm|tr)\b/g;

var ACCENTS = { ı: "i", İ: "i", ş: "s", Ş: "s", ğ: "g", Ğ: "g", ü: "u", Ü: "u", ö: "o", Ö: "o", ç: "c", Ç: "c", â: "a", î: "i", û: "u", é: "e", è: "e", á: "a", ó: "o", ú: "u", ñ: "n" };

function deaccent(value) {
  var s = String(value == null ? "" : value);
  var out = "";
  for (var i = 0; i < s.length; i++) {
    var ch = s.charAt(i);
    out += ACCENTS[ch] || ch;
  }
  return out.toLowerCase();
}

function looseTitle(value) {
  return deaccent(value)
    .replace(/[^a-z0-9]+/g, " ")
    .replace(NOISE, " ")
    .replace(/\s+/g, " ")
    .replace(/^ | $/g, "");
}

function tokenSetRatio(a, b) {
  var A = a.split(" ").filter(Boolean);
  var B = b.split(" ").filter(Boolean);
  if (!A.length || !B.length) return 0;
  var setA = {};
  var setB = {};
  var i;
  for (i = 0; i < A.length; i++) setA[A[i]] = 1;
  for (i = 0; i < B.length; i++) setB[B[i]] = 1;
  var keysA = Object.keys(setA);
  var keysB = Object.keys(setB);
  var shared = 0;
  for (i = 0; i < keysA.length; i++) if (setB[keysA[i]]) shared++;
  return Math.round((2 * shared * 100) / (keysA.length + keysB.length));
}

function extractAliases(raw) {
  var s = String(raw == null ? "" : raw).trim();
  if (!s) return [];
  var list = [s];
  var parts = s.split(/[\(\)\[\]\/\-–—|:]+/);
  for (var i = 0; i < parts.length; i++) {
    var p = parts[i].trim();
    if (p && p.length >= 2 && p !== s) list.push(p);
  }
  return list;
}

function singleMatch(cand, targ) {
  var a = looseTitle(cand);
  var b = looseTitle(targ);
  if (!a || !b) return false;
  if (a === b) return true;
  var da = deaccent(cand).replace(/[^a-z0-9]/g, "");
  var db = deaccent(targ).replace(/[^a-z0-9]/g, "");
  if (da && db && da === db) return true;

  var targetWords = {};
  var bw = b.split(" ");
  for (var i = 0; i < bw.length; i++) targetWords[bw[i]] = 1;
  var aw = a.split(" ");
  var missing = 0;
  for (var j = 0; j < aw.length; j++) {
    if (aw[j].length > 2 && !targetWords[aw[j]]) missing++;
  }
  if (missing === 0 && tokenSetRatio(a, b) >= 75) return true;

  var candWords = {};
  for (var k = 0; k < aw.length; k++) candWords[aw[k]] = 1;
  var missingTarg = 0;
  for (var l = 0; l < bw.length; l++) {
    if (bw[l].length > 2 && !candWords[bw[l]]) missingTarg++;
  }
  if (missingTarg === 0 && tokenSetRatio(a, b) >= 75) return true;

  return tokenSetRatio(a, b) >= 85;
}

function titleMatches(candidate, target) {
  if (!candidate || !target) return false;
  var cands = extractAliases(candidate);
  var targs = extractAliases(target);
  for (var i = 0; i < cands.length; i++) {
    for (var j = 0; j < targs.length; j++) {
      if (singleMatch(cands[i], targs[j])) return true;
    }
  }
  return false;
}

// ────────────────────── packed-eval çözücü ──────────────────────

var PACKED = /eval\(function\(p,a,c,k,e,(?:r|d)\)\{[\s\S]*?\}\((.*?)\)\)/;

function baseN(value, base) {
  var digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  var out = "";
  var n = value;
  do {
    out = digits.charAt(n % base) + out;
    n = Math.floor(n / base);
  } while (n > 0);
  return out;
}

function unpack(source) {
  if (!source || source.indexOf("p,a,c,k,e,") === -1) return source;
  var out = source;
  var seen = {};
  for (var guard = 0; guard < 8; guard++) {
    var match = PACKED.exec(out);
    if (!match || seen[match[0]]) break;
    seen[match[0]] = 1;
    var m =
      /^\s*'([\s\S]*?)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'([\s\S]*?)'\.split\('\|'\)/.exec(match[1]) ||
      /^\s*"([\s\S]*?)"\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*"([\s\S]*?)"\.split\('\|'\)/.exec(match[1]);
    if (!m) break;
    var payload = m[1].replace(/\\'/g, "'").replace(/\\"/g, '"').replace(/\\\\/g, "\\");
    var base = parseInt(m[2], 10);
    var count = parseInt(m[3], 10);
    var words = m[4].split("|");
    if (words.length !== count) break;
    var dict = {};
    for (var i = 0; i < count; i++) if (words[i]) dict[baseN(i, base)] = words[i];
    out = out.replace(
      match[0],
      payload.replace(/\b\w+\b/g, function (t) {
        return dict[t] || t;
      }),
    );
  }
  return out;
}

// ──────────────────────────── çözücüler ────────────────────────────

function unescapeUrl(v) {
  return String(v == null ? "" : v)
    .replace(/\\u0026/g, "&")
    .replace(/\\\//g, "/")
    .replace(/\\/g, "");
}

function findAll(re, text) {
  var out = [];
  var rx = new RegExp(re.source, re.flags.indexOf("g") === -1 ? re.flags + "g" : re.flags);
  var m;
  while ((m = rx.exec(text)) !== null) {
    out.push(m);
    if (m.index === rx.lastIndex) rx.lastIndex++;
  }
  return out;
}

async function playerJs(url, referer) {
  var page = unpack(await req(url, { referer: referer }));
  var streams = [];
  var subtitles = [];
  var seen = {};

  var subs = /playerjsSubtitle\s*=\s*"(.+?)"/.exec(page);
  if (subs) {
    var sm = findAll(/\[(.*?)\](https?:\/\/[^\s",]+)/g, subs[1]);
    for (var i = 0; i < sm.length; i++) subtitles.push({ lang: sm[i][1], url: sm[i][2] });
  }

  function push(raw) {
    var link = unescapeUrl(raw);
    if (!link || seen[link]) return;
    seen[link] = 1;
    streams.push({ url: link, format: "hls", language: "Türkçe", headers: { Referer: url } });
  }

  var direct = findAll(/["']?file["']?\s*:\s*["']([^"']+\.m3u8[^"']*)["']/g, page);
  for (var j = 0; j < direct.length; j++) push(direct[j][1]);
  if (!streams.length) {
    var loose = findAll(/(https?:[^\s"'<>]*?\.m3u8[^\s"'<>]*)/g, page);
    for (var k = 0; k < loose.length; k++) push(loose[k][1]);
  }
  return { streams: streams, subtitles: subtitles };
}

async function resolveFrame(frame, referer, language) {
  if (!frame || frame.indexOf("${") !== -1 || frame.indexOf("%7B") !== -1) {
    return { streams: [], subtitles: [] };
  }
  try {
    if (!/youtube|youtu\.be|fragman|trailer/i.test(frame)) {
      var pj = await playerJs(frame, referer);
      if (pj.streams.length) return pj;
    }
  } catch (e) {}
  return {
    streams: [
      { url: frame, format: "embed", language: language || "Türkçe", headers: { Referer: referer } },
    ],
    subtitles: [],
  };
}

// ──────────────────────────── SAĞLAYICILAR ────────────────────────────

/**
 * 1. FilmModu (Filmler için çalışan en hızlı doğrudan HLS kaynağı)
 */
var filmModu = {
  name: "FilmModu",
  kind: "movie",
  mainUrl: "https://www.filmmodu.one",

  search: async function (query) {
    var url = this.mainUrl + "/film-ara?term=" + encodeURIComponent(query);
    var res;
    var html;
    try {
      res = await fetch(url, {
        headers: {
          "User-Agent": UA,
          Referer: this.mainUrl + "/",
          Accept: "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        }
      });
      html = await res.text();
    } catch (e) {
      return [];
    }
    if (isChallenge(html)) return [];

    var out = [];

    // Case A: Tekli net eşleşmede FilmModu doğrudan film sayfasına yönlendirir.
    if (/<div class="[^"]*alternates[^"]*"/.test(html) && /var videoId =/.test(html)) {
      var canonical =
        (/<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)["']/i.exec(html) ||
          /<meta[^>]+property=["']og:url["'][^>]+content=["']([^"']+)["']/i.exec(html) ||
          [])[1] || res.url;
      var titleM = /<title>([^<]+)<\/title>/i.exec(html);
      var pageTitle = titleM ? titleM[1].replace(/\s*(?:izle|film izle|hd film izle).*$/i, "").trim() : query;
      if (canonical) {
        out.push({ title: pageTitle, url: canonical, year: null });
        return out;
      }
    }

    // Case B: Arama sonuç kartları
    var re = /<div class="[^"]*movie[^"]*"[^>]*>[\s\S]*?<div class="poster">[\s\S]*?<a href="([^"]+)">([^<]+)<\/a>/g;
    var m;
    var seen = {};
    while ((m = re.exec(html)) !== null) {
      var href = absolute(this.mainUrl, m[1]);
      var title = m[2].replace(/&amp;/g, "&").replace(/&#039;/g, "'").trim();
      if (!href || seen[href]) continue;
      seen[href] = 1;
      out.push({ title: title, url: href, year: null });
    }
    return out;
  },

  links: async function (url) {
    var self = this;
    var html;
    try {
      html = await req(url);
    } catch (e) {
      return { streams: [], subtitles: [] };
    }
    var alternates = [];
    var altBlock = /<div class="[^"]*alternates[^"]*">([\s\S]*?)<\/div>/.exec(html);
    if (altBlock) {
      var altRe = /<a[^>]+href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/g;
      var am;
      while ((am = altRe.exec(altBlock[1])) !== null) {
        var altText = am[2].replace(/<[^>]+>/g, "").trim();
        if (altText.indexOf("Fragman") === -1) {
          alternates.push({ href: absolute(self.mainUrl, am[1]), label: altText });
        }
      }
    }
    if (!alternates.length) {
      alternates.push({ href: url, label: "Film" });
    }

    var out = { streams: [], subtitles: [] };
    var seenStream = {};

    for (var i = 0; i < alternates.length && i < 3; i++) {
      var alt = alternates[i];
      try {
        var pageHtml = (alt.href === url) ? html : await req(alt.href, { referer: url });
        var vidId = (/var videoId = '([^']+)'/.exec(pageHtml) || [])[1];
        var vidType = (/var videoType = '([^']*)'/.exec(pageHtml) || [])[1];
        if (vidId) {
          var srcUrl = self.mainUrl + "/get-source?movie_id=" + vidId + "&type=" + (vidType || "");
          var srcText = await req(srcUrl, {
            referer: alt.href,
            headers: { "X-Requested-With": "XMLHttpRequest" }
          });
          var srcData = JSON.parse(srcText);
          var sources = srcData.sources || [];
          var isDub = /dublaj/i.test(alt.label);
          var langLabel = isDub ? "Türkçe Dublaj" : "Türkçe Altyazılı";
          for (var s = 0; s < sources.length; s++) {
            var item = sources[s];
            var streamUrl = item.src ? unescapeUrl(item.src) : null;
            if (!streamUrl || seenStream[streamUrl]) continue;
            seenStream[streamUrl] = 1;
            var qual = item.label || item.res || "1080p";
            out.streams.push({
              provider: "FilmModu",
              name: "FilmModu [" + langLabel + " - " + qual + "]",
              quality: qual,
              language: langLabel,
              format: "hls",
              url: streamUrl,
              headers: { Referer: self.mainUrl + "/" }
            });
          }
          if (srcData.subtitle) {
            out.subtitles.push({
              lang: "Türkçe",
              url: absolute(self.mainUrl, unescapeUrl(srcData.subtitle))
            });
          }
        }
      } catch (err) {}
    }
    return out;
  }
};

/**
 * 2. SinemaCX (Onarılmış sinemacc.com portu)
 */
var sinemaCx = {
  name: "SinemaCX",
  kind: "movie",
  mainUrl: "https://sinemacc.com",

  search: async function (query) {
    var raw;
    try {
      raw = await req(this.mainUrl + "/arama/", {
        method: "POST",
        body: "action=ajax_search&arama_kelime=" + encodeURIComponent(query),
        referer: this.mainUrl + "/",
        headers: { "X-Requested-With": "XMLHttpRequest" }
      });
    } catch (e) {
      return [];
    }
    var out = [];
    var re = /<a class="arama_s\s*" href="([^"]+)"[\s\S]*?<span class="year">([^<]+)<\/span>[\s\S]*?<span class="title">([^<]+)<\/span>/g;
    var m;
    var seen = {};
    while ((m = re.exec(raw)) !== null) {
      var href = absolute(this.mainUrl, m[1]);
      var year = parseInt(m[2].trim(), 10) || null;
      var title = m[3].replace(/&amp;/g, "&").replace(/&#039;/g, "'").trim();
      if (!href || seen[href]) continue;
      seen[href] = 1;
      out.push({ title: title, url: href, year: year });
    }
    return out;
  },

  links: async function (url) {
    var self = this;
    var html;
    try {
      html = await req(url, { referer: self.mainUrl + "/" });
    } catch (e) {
      return { streams: [], subtitles: [] };
    }
    var frames = [];
    var re = /<iframe[^>]+(?:data-vsrc|src)="([^"]+)"/gi;
    var m;
    while ((m = re.exec(html)) !== null) {
      var raw = m[1];
      if (/^[A-Za-z0-9+/=]{20,}$/.test(raw)) {
        var dec = decodeB64Text(raw);
        if (/^https?:\/\//i.test(dec)) raw = dec;
      }
      if (/vr_set=|vidon=|youtube|youtu\.be|fragman|trailer|abuse-telif|telif|dmca/i.test(raw)) continue;
      frames.push(raw);
    }

    var out = { streams: [], subtitles: [] };
    for (var i = 0; i < frames.length && i < 3; i++) {
      var frame = absolute(self.mainUrl, frames[i].split("?img=")[0]);
      var isDub = /lng=tur|dublaj/i.test(frame);
      var langLabel = isDub ? "Türkçe Dublaj" : "Türkçe";
      var r = await resolveFrame(frame, self.mainUrl + "/", langLabel);
      out.streams = out.streams.concat(r.streams);
      out.subtitles = out.subtitles.concat(r.subtitles);
    }
    return out;
  }
};

/**
 * 3. SezonlukDizi (Diziler için çalışan kaynak)
 */
var sezonlukDizi = {
  name: "SezonlukDizi",
  kind: "show",
  mainUrl: "https://sezonlukdizi.cc",

  search: async function (query) {
    var raw;
    try {
      raw = await req(this.mainUrl + "/ajax/arama.asp", {
        method: "POST",
        body: "q=" + encodeURIComponent(query),
        headers: {
          "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
          "X-Requested-With": "XMLHttpRequest"
        }
      });
    } catch (e) {
      return [];
    }
    var data;
    try {
      data = JSON.parse(raw);
    } catch (e) {
      return [];
    }
    var list = (data.results && data.results.diziler && data.results.diziler.results) || [];
    var out = [];
    for (var i = 0; i < list.length; i++) {
      var item = list[i];
      var rawTitle = item.title || "";
      var ym = /\((\d{4})\)/.exec(rawTitle);
      var year = ym ? parseInt(ym[1], 10) : null;
      var clean = rawTitle.replace(/\(\d{4}\)/, "").trim();
      var slug = (item.url || "")
        .replace("/diziler/", "")
        .replace(".html", "")
        .replace("-izle", "");
      if (slug) {
        out.push({ title: clean || rawTitle, url: slug, year: year });
      }
    }
    return out;
  },

  episodeUrl: async function (item, season, episode) {
    var s = season || 1;
    var e = episode || 1;
    return this.mainUrl + "/" + item.url + "/" + s + "-sezon-" + e + "-bolum.html";
  },

  links: async function (url) {
    var self = this;
    var html;
    try {
      html = await req(url);
    } catch (e) {
      return { streams: [], subtitles: [] };
    }
    var bidMatch = /data-id=["']([^"']+)["']/.exec(html);
    if (!bidMatch) return { streams: [], subtitles: [] };
    var bid = bidMatch[1];

    var out = { streams: [], subtitles: [] };
    var modes = [
      { dil: 0, label: "Türkçe Dublaj" },
      { dil: 1, label: "Türkçe Altyazılı" }
    ];

    for (var m = 0; m < modes.length; m++) {
      var mode = modes[m];
      try {
        var altRaw = await req(self.mainUrl + "/ajax/dataAlternatif22.asp", {
          method: "POST",
          body: "bid=" + bid + "&dil=" + mode.dil,
          headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Referer": url,
            "X-Requested-With": "XMLHttpRequest"
          }
        });
        var altData = JSON.parse(altRaw);
        var alts = altData.data || [];
        // Hizli ve rate-limit yememek icin en guvenilir 1 alternatif sec (Filemoon / Sibnet / VidMoly)
        var chosen = null;
        for (var a = 0; a < alts.length; a++) {
          if (/filemoon/i.test(alts[a].baslik)) { chosen = alts[a]; break; }
        }
        if (!chosen) {
          for (var a2 = 0; a2 < alts.length; a2++) {
            if (/sibnet|vidmoly/i.test(alts[a2].baslik)) { chosen = alts[a2]; break; }
          }
        }
        if (!chosen && alts.length) chosen = alts[0];

        if (chosen) {
          try {
            var embRaw = await req(self.mainUrl + "/ajax/dataEmbed22.asp", {
              method: "POST",
              body: "id=" + chosen.id,
              headers: {
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                "Referer": url,
                "X-Requested-With": "XMLHttpRequest"
              }
            });
            var srcMatch = /<iframe[^>]+src=["']([^"']+)["']/i.exec(embRaw);
            if (srcMatch && srcMatch[1].indexOf("/") !== 0) {
              var frameUrl = srcMatch[1].indexOf("//") === 0 ? "https:" + srcMatch[1] : srcMatch[1];
              out.streams.push({
                provider: "SezonlukDizi",
                name: "SezonlukDizi [" + mode.label + " - " + (chosen.baslik || "Player") + "]",
                quality: "1080p",
                language: mode.label,
                format: "embed",
                url: frameUrl,
                headers: { Referer: self.mainUrl + "/" }
              });
            }
          } catch (e) {}
        }
      } catch (e) {}
    }
    return out;
  }
};

/**
 * 4. RoketDizi (Dizi yedeği)
 */
function macellan(name, mainUrl, kind) {
  async function secureData(url) {
    var html = await req(url);
    var script = /<script id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/.exec(html);
    if (!script) return null;
    var parsed = JSON.parse(script[1]);
    var node = parsed && parsed.props && parsed.props.pageProps && parsed.props.pageProps.secureData;
    return node ? decodeB64Json(node) : null;
  }

  return {
    name: name,
    kind: kind,
    mainUrl: mainUrl,

    search: async function (query) {
      try {
        var raw = await req(mainUrl + "/api/bg/searchcontent?searchterm=" + encodeURIComponent(query), {
          method: "POST",
          body: "",
          referer: mainUrl + "/",
        });
        var data = decodeB64Json(JSON.parse(raw).response);
        if (data.state !== true) return [];
        var out = [];
        var list = data.result || [];
        for (var i = 0; i < list.length; i++) {
          var r = list[i];
          var slug = String(r.used_slug || "");
          if (!r.object_name || !slug || slug.indexOf("/seri-filmler/") !== -1) continue;
          out.push({
            title: r.object_name,
            url: absolute(mainUrl, "/" + slug),
            year: r.object_release_year || null,
          });
        }
        return out;
      } catch (e) {
        return [];
      }
    },

    episodeUrl: async function (item, season, episode) {
      try {
        var root = await secureData(item.url);
        var related = (root && (root.RelatedResults || root.relatedResults)) || {};
        var seasons =
          (related.getSerieSeasonAndEpisodes && related.getSerieSeasonAndEpisodes.result) || [];
        for (var i = 0; i < seasons.length; i++) {
          if (Number(seasons[i].season_no) !== Number(season)) continue;
          var eps = seasons[i].episodes || [];
          for (var j = 0; j < eps.length; j++) {
            if (Number(eps[j].episode_no) === Number(episode)) {
              return absolute(mainUrl, "/" + eps[j].used_slug);
            }
          }
        }
      } catch (e) {}
      return null;
    },

    links: async function (url) {
      var out = { streams: [], subtitles: [] };
      try {
        var root = await secureData(url);
        if (!root) return out;
        var related = root.RelatedResults || root.relatedResults || {};
        var frames = [];
        var sources = related.getEpisodeSources && related.getEpisodeSources.result;
        for (var i = 0; i < (sources || []).length; i++) {
          var s = sources[i];
          var src = /src=["']([^"']+)["']/.exec(s.source_content || "");
          if (src) {
            frames.push({
              url: absolute(mainUrl, src[1]),
              quality: s.quality_name,
              language: s.language_name,
            });
          }
        }

        var seen = {};
        for (var f = 0; f < frames.length && f < 3; f++) {
          var frame = frames[f];
          if (!frame.url || seen[frame.url]) continue;
          seen[frame.url] = 1;
          var r = await resolveFrame(frame.url, mainUrl + "/", frame.language);
          for (var s2 = 0; s2 < r.streams.length; s2++) {
            var st = r.streams[s2];
            st.quality = frame.quality || st.quality;
            out.streams.push(st);
          }
          out.subtitles = out.subtitles.concat(r.subtitles);
        }
      } catch (e) {}
      return out;
    },
  };
}

var roketDizi = macellan("RoketDizi", "https://www.roketdizi.live", "show");

var PROVIDERS = [filmModu, sinemaCx, sezonlukDizi, roketDizi];

// ──────────────────────────── toplayıcı ────────────────────────────

async function scanProvider(provider, query) {
  var isSeries = query.type === "tv" || query.type === "show";
  if (isSeries !== (provider.kind === "show")) return [];

  var names = [];
  if (query.title) names.push(query.title);
  if (query.originalTitle && query.originalTitle !== query.title) names.push(query.originalTitle);

  var picked = null;
  for (var n = 0; n < names.length && !picked; n++) {
    var results = await provider.search(names[n]);
    var i;
    for (i = 0; i < results.length && !picked; i++) {
      if (titleMatches(results[i].title, query.title)) picked = results[i];
    }
    if (!picked && query.originalTitle) {
      for (i = 0; i < results.length && !picked; i++) {
        if (titleMatches(results[i].title, query.originalTitle)) picked = results[i];
      }
    }
    if (!picked && query.year) {
      for (i = 0; i < results.length && !picked; i++) {
        if (results[i].year && Math.abs(results[i].year - query.year) <= 1) picked = results[i];
      }
    }
  }
  if (!picked) return [];

  var target = picked.url;
  if (isSeries && provider.episodeUrl) {
    target = await provider.episodeUrl(picked, query.season || 1, query.episode || 1);
    if (!target) return [];
  }

  var found = await provider.links(target);
  var out = [];
  for (var s = 0; s < found.streams.length; s++) {
    var st = found.streams[s];
    out.push({
      provider: provider.name,
      name:
        provider.name +
        (st.language ? " [" + st.language + "]" : "") +
        (st.quality ? " " + st.quality : ""),
      quality: st.quality || "Bilinmiyor",
      language: st.language || "Türkçe",
      format: st.format,
      url: st.url,
      headers: st.headers || {},
      subtitles: found.subtitles || [],
    });
  }
  return out;
}

async function resolveQuery(query) {
  var jobs = [];
  for (var i = 0; i < PROVIDERS.length; i++) {
    jobs.push(
      (function (p) {
        return scanProvider(p, query).then(
          function (v) { return v; },
          function (e) {
            console.log("[BerkStream] " + p.name + ": " + e);
            return [];
          },
        );
      })(PROVIDERS[i]),
    );
  }
  var results = await Promise.all(jobs);
  var sources = [];
  for (var j = 0; j < results.length; j++) sources = sources.concat(results[j]);
  return sources;
}

// ─────────────────── host sözleşmesi (JSON string döner) ───────────────────

function toCards(list, forcedType) {
  var out = [];
  for (var i = 0; i < (list || []).length; i++) {
    var x = list[i];
    var type = forcedType || (x.media_type === "tv" ? "tv" : x.media_type === "movie" ? "movie" : null);
    if (!type || !x.id) continue;
    if (!x.poster_path) continue;
    var date = x.release_date || x.first_air_date || "";
    out.push({
      id: (type === "tv" ? "tv" : "movie") + ":" + x.id,
      title: x.title || x.name,
      original_title: x.original_title || x.original_name || null,
      poster_url: IMAGE + x.poster_path,
      backdrop_url: x.backdrop_path ? BACKDROP + x.backdrop_path : null,
      media_type: type === "tv" ? "show" : "movie",
      year: parseInt(date.slice(0, 4), 10) || null,
      rating: x.vote_average || null,
      description: x.overview || null
    });
  }
  return out;
}

var SHELVES = [
  { title: "Bugün trend", path: "/trending/all/day", params: "", type: null },
  { title: "Vizyondakiler", path: "/movie/now_playing", params: "", type: "movie" },
  { title: "Popüler diziler", path: "/tv/popular", params: "", type: "tv" },
  { title: "Popüler filmler", path: "/movie/popular", params: "", type: "movie" },
  { title: "Türk dizileri", path: "/discover/tv", params: "&with_original_language=tr&sort_by=popularity.desc", type: "tv" },
  { title: "Türk filmleri", path: "/discover/movie", params: "&with_original_language=tr&sort_by=popularity.desc", type: "movie" },
  { title: "Anime", path: "/discover/tv", params: "&with_genres=16&with_original_language=ja&sort_by=popularity.desc", type: "tv" },
  { title: "En yüksek puanlı filmler", path: "/movie/top_rated", params: "", type: "movie" },
  { title: "En yüksek puanlı diziler", path: "/tv/top_rated", params: "", type: "tv" }
];

async function getCatalog() {
  var jobs = [];
  for (var i = 0; i < SHELVES.length; i++) {
    jobs.push(
      (function (shelf) {
        var url = TMDB + shelf.path + "?api_key=" + TMDB_KEY + "&language=tr-TR" + shelf.params;
        return getJson(url).then(
          function (data) {
            return { title: shelf.title, items: toCards(data.results, shelf.type) };
          },
          function (err) {
            console.log("[BerkStream] raf '" + shelf.title + "': " + err);
            return { title: shelf.title, items: [] };
          }
        );
      })(SHELVES[i])
    );
  }
  var rows = await Promise.all(jobs);
  var out = [];
  for (var r = 0; r < rows.length; r++) if (rows[r].items.length) out.push(rows[r]);
  return JSON.stringify(out);
}

async function search(query) {
  var data = await getJson(
    TMDB +
      "/search/multi?api_key=" +
      TMDB_KEY +
      "&language=tr-TR&include_adult=false&query=" +
      encodeURIComponent(query),
  );
  var out = [];
  var list = data.results || [];
  for (var i = 0; i < list.length && out.length < 30; i++) {
    var x = list[i];
    if (x.media_type !== "movie" && x.media_type !== "tv") continue;
    var date = x.release_date || x.first_air_date || "";
    out.push({
      id: x.media_type + ":" + x.id,
      title: x.title || x.name,
      original_title: x.original_title || x.original_name || null,
      poster_url: x.poster_path ? IMAGE + x.poster_path : null,
      backdrop_url: x.backdrop_path ? BACKDROP + x.backdrop_path : null,
      media_type: x.media_type === "movie" ? "movie" : "show",
      year: parseInt(date.slice(0, 4), 10) || null,
      rating: x.vote_average || null,
      description: x.overview || null,
    });
  }
  return JSON.stringify(out);
}

async function getEpisodes(mediaId) {
  var parts = String(mediaId).split(":");
  if (parts[0] !== "tv" && parts[0] !== "show") return "[]";
  var id = parts[1];
  var show = await getJson(TMDB + "/tv/" + id + "?api_key=" + TMDB_KEY + "&language=tr-TR");
  var episodes = [];
  var seasons = show.seasons || [];
  for (var i = 0; i < seasons.length; i++) {
    if (!(seasons[i].season_number > 0)) continue;
    var data = await getJson(
      TMDB + "/tv/" + id + "/season/" + seasons[i].season_number + "?api_key=" + TMDB_KEY + "&language=tr-TR",
    );
    var eps = data.episodes || [];
    for (var j = 0; j < eps.length; j++) {
      episodes.push({
        id: "tv:" + id + ":" + seasons[i].season_number + ":" + eps[j].episode_number,
        title: eps[j].name || "Bölüm " + eps[j].episode_number,
        season: seasons[i].season_number,
        episode_number: eps[j].episode_number,
        thumbnail_url: eps[j].still_path ? IMAGE + eps[j].still_path : null,
        description: eps[j].overview || null,
      });
    }
  }
  return JSON.stringify(episodes);
}

async function getStreams(mediaId) {
  var parts = String(mediaId).split(":");
  var kind = parts[0] === "movie" ? "movie" : "tv";
  var id = parts[1];
  var detail = await getJson(TMDB + "/" + kind + "/" + id + "?api_key=" + TMDB_KEY + "&language=tr-TR");
  var date = detail.release_date || detail.first_air_date || "";
  var sources = await resolveQuery({
    title: detail.title || detail.name,
    originalTitle: detail.original_title || detail.original_name,
    year: parseInt(date.slice(0, 4), 10) || null,
    type: kind,
    season: parts[2] ? Number(parts[2]) : 1,
    episode: parts[3] ? Number(parts[3]) : 1,
  });

  var path = kind === "movie" ? "movie/" + id : "tv/" + id + "/" + (parts[2] || 1) + "/" + (parts[3] || 1);
  var fallback = [
    { provider: "Evrensel", name: "Evrensel — VidSrc", quality: "1080p", language: "Orijinal / Çoklu Altyazı", format: "embed", url: "https://vidsrc.to/embed/" + path, headers: {}, subtitles: [] },
    { provider: "Evrensel", name: "Evrensel — SmashyStream", quality: "1080p", language: "Orijinal / Altyazı", format: "embed", url: "https://player.smashy.stream/" + path, headers: {}, subtitles: [] },
    { provider: "Evrensel", name: "Evrensel — VidLink", quality: "1080p", language: "Çoklu Dil / Altyazı", format: "embed", url: "https://vidlink.pro/" + path, headers: {}, subtitles: [] },
    { provider: "Evrensel", name: "Evrensel — 2Embed", quality: "720p", language: "Orijinal / Altyazı", format: "embed", url: "https://www.2embed.cc/embed/" + id + (kind === "tv" ? "&s=" + (parts[2] || 1) + "&e=" + (parts[3] || 1) : ""), headers: {}, subtitles: [] },
  ];
  return JSON.stringify(sources.concat(fallback));
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { getCatalog: getCatalog, search: search, getEpisodes: getEpisodes, getStreams: getStreams, titleMatches: titleMatches, unpack: unpack, resolveQuery: resolveQuery };
}
