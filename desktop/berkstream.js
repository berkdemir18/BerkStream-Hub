// @plugin-info {"id":"berkstream-desktop","name":"BerkStream Desktop","version":"2.3.0","description":"BerkStream Hub'ın Windows motoru: çeşitli raflar, Türkçe kaynaklar ve uygulama içi oynatıcı.","author":"berkdemir18","icon_url":"https://raw.githubusercontent.com/berkdemir18/BerkStream-Hub/main/assets/berkstream-icon.png","supported_types":["movie","show"],"is_builtin":false}
//
// BerkStream Hub - masaüstü (JavaScript) sürümü.
//
// Neden bu dosya var: Hub'ın kataloğu (repo.json / plugins.json) CloudStream'in
// Android eklenti formatı; .cs3 dosyaları PC'de açılmıyor. Bu dosya Hub'daki
// Kotlin sağlayıcıların çalışan JS karşılığı.
//
// ÇALIŞMA ORTAMI - buna dikkat (2.0.0 tam da bu yüzden sessizce çuvalladı):
// BerkStream masaüstü eklentileri **Boa** (Rust JS motoru) içinde çalıştırıyor
// ve host yalnızca iki şey enjekte ediyor: `fetch` ve `console`.
// YOK olanlar: setTimeout / clearTimeout, Buffer, atob, URL, URLSearchParams,
// AbortController, TextDecoder. Bunlardan birine dokunan ilk satır bütün
// eklentiyi düşürüyor ve uygulamada "hiçbir şey gelmiyor" olarak görünüyor.
// Host'un fetch'i: fetch(url, {method, headers, body}) -> {ok, status, text(), json()}
// (yanıt başlıkları okunamıyor, yönlendirmeyi host hallediyor.)
//
// Sözleşme (host bu üçünü çağırır, JSON string bekler):
//   getCatalog(arg)      -> [{title, items:[...]}]  (ana sayfa rafları)
//   search(query)        -> [{id,title,poster_url,media_type,year,...}]
//   getEpisodes(mediaId) -> [{id,title,season,episode_number,...}]
//   getStreams(mediaId)  -> [{url,quality,format,provider,language,headers}]

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

/** new URL yok; origin'i elle çıkarıyoruz. */
function originOf(url) {
  var m = /^(https?:\/\/[^/]+)/i.exec(url || "");
  return m ? m[1] : "";
}

/** new URL yok; göreli adresi elle mutlaklaştırıyoruz. */
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

/** URLSearchParams yok. */
function formEncode(obj) {
  var parts = [];
  for (var k in obj) {
    if (Object.prototype.hasOwnProperty.call(obj, k)) {
      parts.push(encodeURIComponent(k) + "=" + encodeURIComponent(obj[k]));
    }
  }
  return parts.join("&");
}

/** Buffer ve atob yok; base64 çözümü elle. Latin-1 bayt dizisi döner. */
var B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
function b64decode(input) {
  var data = String(input).replace(/[^A-Za-z0-9+/=]/g, "");
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

/** TextDecoder yok; latin-1 bayt dizisini UTF-8 metne çeviriyoruz. */
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

// ───────────────────────────────── HTTP ─────────────────────────────────

function isChallenge(body) {
  return /Just a moment|cf-browser-verification|challenge-platform|__cf_chl/i.test(body || "");
}

/** setTimeout yok; zaman aşımını host hallediyor. */
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
// BerkStreamProvider.kt'deki looseTitle/titleMatches portu. Gevşek "içeren"
// karşılaştırması "Dexter" aramasını "Dexter's Laboratory" ile eşleştiriyordu.

var NOISE =
  /\b(izle|seyret|full|hd|fullhd|4k|1080p|720p|480p|turkce|turkiye|dublaj|altyazili|altyazi|filmi|film|dizisi|dizi|online|tek|parca|part|sezon|bolum|yerli|yabanci|hdfilm|tr)\b/g;

var ACCENTS = { ı: "i", İ: "i", ş: "s", Ş: "s", ğ: "g", Ğ: "g", ü: "u", Ü: "u", ö: "o", Ö: "o", ç: "c", Ç: "c", â: "a", î: "i", û: "u", é: "e", è: "e", á: "a", ó: "o", ú: "u", ñ: "n" };

/** normalize("NFD") yok sayılıyor; harf eşlemesi elle. */
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

function titleMatches(candidate, target) {
  var a = looseTitle(candidate);
  var b = looseTitle(target);
  if (!a || !b) return false;
  if (a === b) return true;
  if (deaccent(candidate).replace(/[^a-z0-9]/g, "") === deaccent(target).replace(/[^a-z0-9]/g, ""))
    return true;

  // Adayda hedefte geçmeyen anlamlı kelime varsa bu başka bir yapım:
  // "dexter resurrection" ve "dexters laboratory", "dexter" değildir.
  var targetWords = {};
  var bw = b.split(" ");
  for (var i = 0; i < bw.length; i++) targetWords[bw[i]] = 1;
  var aw = a.split(" ");
  for (var j = 0; j < aw.length; j++) {
    if (aw[j].length > 2 && !targetWords[aw[j]]) return false;
  }
  return tokenSetRatio(a, b) >= 92;
}

// ────────────────────── packed-eval çözücü ──────────────────────
// Türk oynatıcı sayfalarının çoğu m3u8'i "p,a,c,k,e,d" sıkıştırmasında saklıyor.

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

/** matchAll yerine motor-güvenli global tarama. */
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

/** ContentX ailesi: pichive.online, contentx.me, hotlinger.com, playru.net */
async function contentX(url, referer) {
  var origin = originOf(url);
  var page = await req(url, { referer: referer });
  var streams = [];
  var subtitles = [];
  var seen = {};

  var subMatches = findAll(/"file":"((?:\\"|[^"])+)","label":"((?:\\"|[^"])+)"/g, page);
  for (var i = 0; i < subMatches.length; i++) {
    var subUrl = unescapeUrl(subMatches[i][1]);
    if (!subUrl || seen[subUrl]) continue;
    seen[subUrl] = 1;
    subtitles.push({ lang: subMatches[i][2], url: absolute(origin, subUrl) });
  }

  var targets = [];
  var main = /window\.openPlayer\('([^']+)'/.exec(page);
  if (main) targets.push({ id: main[1], label: "Türkçe Altyazı" });
  var dub = /,"([^"']+?)","Türkçe/.exec(page);
  if (dub) targets.push({ id: dub[1], label: "Türkçe Dublaj" });

  for (var t = 0; t < targets.length; t++) {
    try {
      var src = await req(origin + "/source2.php?v=" + targets[t].id, { referer: referer });
      var file = /file":"([^"]+)/.exec(src);
      if (!file) continue;
      var link = unescapeUrl(file[1]);
      if (/hotlinger|dplayer82\.site/.test(link)) link = link.replace("m.php", "master.m3u8");
      streams.push({
        url: link,
        format: "hls",
        language: targets[t].label,
        headers: { Referer: url, "User-Agent": UA },
      });
    } catch (e) {}
  }
  return { streams: streams, subtitles: subtitles };
}

/** VidRame: m3u8 ters çevrilmiş + ROT13 + base64 sarılı. */
function rot13(s) {
  return s.replace(/[a-zA-Z]/g, function (c) {
    var base = c <= "Z" ? 90 : 122;
    var next = c.charCodeAt(0) + 13;
    return String.fromCharCode(base >= next ? next : next - 26);
  });
}

async function vidRame(url, referer) {
  var page = await req(url, { referer: referer });
  var packed = /\.dd\("([^"]+)"\)/.exec(page);
  if (!packed) return { streams: [], subtitles: [] };
  var raw = packed[1].replace(/-/g, "+").replace(/_/g, "/");
  while (raw.length % 4) raw += "=";
  var link = rot13(b64decode(raw)).split("").reverse().join("");
  return {
    streams: [
      { url: link, format: "hls", language: "Türkçe", headers: { Referer: originOf(url) + "/" } },
    ],
    subtitles: [],
  };
}

/** PlayerJS / FirePlayer şablonu - Türk sitelerinin ortak oynatıcısı. */
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

/**
 * İframe'i akışa çevirir; çeviremezse "embed" olarak geri verir.
 *
 * Oynatıcı sunucularının çoğu Cloudflare arkasında (2026-09-09 ölçümü: 54
 * kaynağın 15'i). CloudStream Android'de bunu WebView ile aşıyor; masaüstünde
 * karşılığı uygulamanın kendi web görünümü - embed açıldığında Cloudflare
 * kendiliğinden geçiliyor.
 */
async function resolveFrame(frame, referer, language) {
  // Sayfa şablonlarında çözülmemiş `${...}` kalıntısı iframe olarak geliyor.
  if (!frame || frame.indexOf("${") !== -1 || frame.indexOf("%7B") !== -1) {
    return { streams: [], subtitles: [] };
  }
  try {
    if (/contentx\.me|pichive\.online|playru\.net|hotlinger\.com|dplayer82\.site/i.test(frame)) {
      var cx = await contentX(frame, referer);
      if (cx.streams.length) return cx;
    } else if (/vidrame\./i.test(frame)) {
      var vr = await vidRame(frame, referer);
      if (vr.streams.length) return vr;
    } else if (!/youtube|youtu\.be|fragman|trailer/i.test(frame)) {
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

// ──────────────────────────── sağlayıcılar ────────────────────────────

/** RoketDizi'nin altyapısı (Hub: RoketDizi.kt). Yanıtlar base64 sarılı. */
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
    },

    episodeUrl: async function (item, season, episode) {
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
      return null;
    },

    links: async function (url) {
      var root = await secureData(url);
      var out = { streams: [], subtitles: [] };
      if (!root) return out;
      var related = root.RelatedResults || root.relatedResults || {};

      // source_content bir URL değil, komple <iframe ...> HTML'i.
      var frames = [];
      function push(list) {
        for (var i = 0; i < (list || []).length; i++) {
          var s = list[i];
          var src = /src=["']([^"']+)["']/.exec(s.source_content || "");
          if (src) {
            frames.push({
              url: absolute(mainUrl, src[1]),
              quality: s.quality_name,
              language: s.language_name,
            });
          }
        }
      }
      push(related.getEpisodeSources && related.getEpisodeSources.result);
      var keys = Object.keys(related);
      for (var k = 0; k < keys.length; k++) {
        if (keys[k].indexOf("getMoviePartSourcesById") === 0) {
          push(related[keys[k]] && related[keys[k]].result);
        }
      }

      var seen = {};
      for (var f = 0; f < frames.length && f < 6; f++) {
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
      return out;
    },
  };
}

/** HDFilmİzle - arama JSON döndürüyor (Hub: HDFilmIzle.kt). */
var hdFilmIzle = {
  name: "HDFilmİzle",
  kind: "movie",
  mainUrl: "https://www.hdfilmizle.vip",

  search: async function (query) {
    var raw = await req(this.mainUrl + "/search/", {
      method: "POST",
      body: formEncode({ query: query }),
      referer: this.mainUrl + "/",
    });
    var list;
    try {
      list = JSON.parse(raw);
    } catch (e) {
      return [];
    }
    var out = [];
    for (var i = 0; i < (list || []).length; i++) {
      if (!list[i].name || !list[i].slug) continue;
      out.push({
        title: list[i].name,
        url: absolute(this.mainUrl, list[i].slug),
        year: parseInt(list[i].year, 10) || null,
      });
    }
    return out;
  },

  links: async function (url) {
    var html = await req(url);
    var out = { streams: [], subtitles: [] };
    var frames = findAll(/<iframe[^>]+data-src=["']([^"']+)["']/gi, html);
    for (var i = 0; i < frames.length && i < 3; i++) {
      var r = await resolveFrame(absolute(this.mainUrl, frames[i][1]), this.mainUrl + "/");
      out.streams = out.streams.concat(r.streams);
      out.subtitles = out.subtitles.concat(r.subtitles);
    }
    return out;
  },
};

/** SinemaCX (Hub: SinemaCX.kt). */
var sinemaCx = {
  name: "SinemaCX",
  kind: "movie",
  mainUrl: "https://www.sinema.gg",

  search: async function (query) {
    var html = await req(this.mainUrl + "/?s=" + encodeURIComponent(query));
    var out = [];
    var hits = findAll(/<a[^>]+href=["']([^"']+)["'][^>]*title=["']([^"']+)["']/gi, html);
    for (var i = 0; i < hits.length; i++) {
      var url = absolute(this.mainUrl, hits[i][1]);
      if (!url || !/\/[a-z0-9-]+-izle/i.test(url)) continue;
      var dup = false;
      for (var j = 0; j < out.length; j++) if (out[j].url === url) dup = true;
      if (dup) continue;
      out.push({ title: hits[i][2].replace(/^\s+|\s+$/g, ""), url: url, year: null });
    }
    return out;
  },

  links: async function (url) {
    var self = this;
    function grab(html) {
      var frames = findAll(/<iframe[^>]+data-vsrc=["']([^"']+)["']/gi, html);
      var list = [];
      for (var i = 0; i < frames.length; i++) list.push(frames[i][1]);
      return list;
    }
    var frames = grab(await req(url));
    var onlyTrailer = frames.length > 0;
    for (var t = 0; t < frames.length; t++) {
      if (!/youtube|fragman|trailer/i.test(frames[t])) onlyTrailer = false;
    }
    if (!frames.length || onlyTrailer) {
      try {
        var alt = url.charAt(url.length - 1) === "/" ? url + "2/" : url + "/2/";
        frames = grab(await req(alt, { referer: url }));
      } catch (e) {}
    }
    var out = { streams: [], subtitles: [] };
    for (var i2 = 0; i2 < frames.length && i2 < 3; i2++) {
      var frame = absolute(self.mainUrl, frames[i2].split("?img=")[0]);
      var r = await resolveFrame(frame, self.mainUrl + "/");
      out.streams = out.streams.concat(r.streams);
      out.subtitles = out.subtitles.concat(r.subtitles);
    }
    return out;
  },
};

var roketDizi = macellan("RoketDizi", "https://roketdizi.life", "show");

/**
 * Etkin sağlayıcılar - Hub'daki providerPriority sırasının bu sürümdeki karşılığı.
 * Dışarıda kalanlar (2026-09-09 ölçümü, 54 kaynak: 19 açılıyor, 15 Cloudflare, 20 ölü):
 *   - Dizilla, SelcukFlix : yanıtı AES ile şifreli, anahtar çözümü portlanmadı
 *   - HDFilmCehennemi, DiziPal, DiziYou, FilmMakinesi, WebteIzle, JetFilmizle,
 *     SezonlukDizi (arama sayfası) ve 8 tanesi daha : Cloudflare duvarı
 *   - 20 kaynak : alan adı ölü (Hub'daki domains.json "disabled" listesi)
 */
var PROVIDERS = [roketDizi, hdFilmIzle, sinemaCx];

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
    // Yıl doğruluyorsa bulanık eşleşmeyi kabul et; yoksa yanlış yapım riski var.
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
      subtitles: found.subtitles,
    });
  }
  return out;
}

/** Bütün kaynakları paralel tarar. Hepsi hata verse bile boş dizi döner. */
async function resolveQuery(query) {
  var jobs = [];
  for (var i = 0; i < PROVIDERS.length; i++) {
    jobs.push(
      (function (p) {
        return scanProvider(p, query).then(
          function (v) {
            return v;
          },
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


/** TMDB listesini host'un beklediği karta çevirir. */
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

/**
 * Ana sayfa rafları. Uygulama açılışta bunu çağırıyor; bu çağrı olmadan
 * ana ekran boş kalıyordu ve içerik yalnızca aramadan geliyordu.
 * Raflar TMDB'den, oynatma yine kaynak taramasıyla.
 */
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

/** mediaId biçimleri: "movie:<tmdbId>" veya "tv:<tmdbId>:<sezon>:<bölüm>" */
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

  // Dahili oynatıcı seçenekleri her zaman ilk sırada. Böylece içerik VLC'ye
  // veya tarayıcıya gönderilmeden doğrudan BerkStream penceresinde açılır.
  var path = kind === "movie" ? "movie/" + id : "tv/" + id + "/" + (parts[2] || 1) + "/" + (parts[3] || 1);
  sources = [
    { provider: "BerkStream", name: "BerkStream Dahili Oynatıcı", quality: "1080p", language: "Çoklu dil / altyazı", format: "embed", url: "https://vidlink.pro/" + path, headers: {}, subtitles: [] },
    { provider: "BerkStream", name: "BerkStream Dahili Oynatıcı (Yedek)", quality: "1080p", language: "Orijinal / Altyazı", format: "embed", url: "https://vidsrc.cc/v2/embed/" + path, headers: {}, subtitles: [] },
  ].concat(sources);
  return JSON.stringify(sources);
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { getCatalog: getCatalog, search: search, getEpisodes: getEpisodes, getStreams: getStreams, titleMatches: titleMatches, unpack: unpack, resolveQuery: resolveQuery };
}
