/**
 * Kaynak adresi sagligi + merkezi adres listesi uretici.
 *
 * Turk streaming siteleri surekli adres degistiriyor; gomulu kaynaklarin
 * `mainUrl` degerleri koda sabitlenmis oldugu icin upstream depo guncellemedigi
 * surece kaynak olu kaliyor. 2026-09-09 taramasinda 78 adresten 19'u hic cevap
 * vermiyordu.
 *
 * Bu script:
 *   1. vendor-report.json'daki her saglayicinin adresini yokluyor,
 *   2. PLT Stream'in kamuya acik domains.json'undaki guncel adreslerle
 *      karsilastirip olu olanlari duzeltiyor,
 *   3. sonucu `domains.json` olarak yaziyor: eklenti acilista bunu cekip
 *      saglayicilarin mainUrl'ini eziyor ve olu kaynaklari taramaya sokmuyor.
 *
 * Kullanim: node scripts/check-domains.mjs [--skip-network]
 */
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const skipNetwork = process.argv.includes("--skip-network");
const outputDir = process.env.BERKSTREAM_OUTPUT_DIR
  ? path.resolve(process.env.BERKSTREAM_OUTPUT_DIR)
  : root;

const pltDomainsUrl = "https://raw.githubusercontent.com/pltmustafa/plt-stream/builds/domains.json";
const userAgent =
  "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36";
const timeoutMs = 12_000;

function normalizedName(value) {
  return String(value)
    .normalize("NFKD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[ıİ]/g, "i")
    .replace(/[şŞ]/g, "s")
    .replace(/[ğĞ]/g, "g")
    .replace(/[çÇ]/g, "c")
    .replace(/[öÖ]/g, "o")
    .replace(/[üÜ]/g, "u")
    .replace(/[^a-zA-Z0-9]/g, "")
    .toLowerCase();
}

async function probe(url) {
  if (skipNetwork) return { ok: true, status: 0, skipped: true };
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      redirect: "follow",
      signal: controller.signal,
      headers: { "user-agent": userAgent, accept: "text/html,*/*" },
    });
    // 403 genelde bot korumasi; site ayakta sayilir, istemci tarafi cozer.
    return { ok: response.status < 400 || response.status === 403, status: response.status };
  } catch (error) {
    return { ok: false, status: 0, error: String(error.name || error) };
  } finally {
    clearTimeout(timer);
  }
}

async function mapLimit(items, limit, worker) {
  const results = new Array(items.length);
  let cursor = 0;
  async function consume() {
    while (cursor < items.length) {
      const index = cursor++;
      results[index] = await worker(items[index]);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, consume));
  return results;
}

const config = JSON.parse(await readFile(path.join(root, "sources.json"), "utf8"));
const report = JSON.parse(await readFile(path.join(root, "vendor-report.json"), "utf8"));
const providers = [];
for (const item of report.vendored ?? []) {
  for (const provider of item.providers ?? []) {
    if (!/^https?:\/\//i.test(provider.mainUrl)) continue;
    providers.push({ ...provider, source: item.id, key: normalizedName(provider.name) });
  }
}

let upstream = {};
if (!skipNetwork) {
  try {
    const response = await fetch(pltDomainsUrl, { headers: { "user-agent": userAgent } });
    if (response.ok) upstream = await response.json();
  } catch (error) {
    console.log(`! PLT adres listesi alinamadi: ${error}`);
  }
}
const manualByKey = new Map(
  Object.entries(config.manualDomains ?? {}).map(([name, url]) => [normalizedName(name), url]),
);
const upstreamByKey = new Map(
  Object.entries(upstream).map(([name, url]) => [normalizedName(name), url]),
);

const checked = await mapLimit(providers, 16, async (provider) => {
  const first = await probe(provider.mainUrl);
  if (first.ok) return { ...provider, status: first.status, alive: true };

  // Olu: once elle bakilan liste, sonra PLT'nin guncel adresi denenir.
  const candidate = manualByKey.get(provider.key) ?? upstreamByKey.get(provider.key);
  if (candidate && candidate !== provider.mainUrl) {
    const second = await probe(candidate);
    if (second.ok) {
      return {
        ...provider,
        status: second.status,
        alive: true,
        replacement: candidate,
        replacedFrom: provider.mainUrl,
      };
    }
  }
  return { ...provider, status: first.status, alive: false, error: first.error };
});

const overrides = {};
const disabled = [];
for (const provider of checked) {
  if (provider.replacement) overrides[provider.name] = provider.replacement;
  if (!provider.alive) disabled.push(provider.name);
}

const domains = {
  generatedAt: new Date().toISOString(),
  // Saglayici adi -> guncel adres. Eklenti acilista mainUrl'i bununla eziyor.
  overrides,
  // Cevap vermeyen kaynaklar: taramaya sokulmuyor, bosuna beklenmiyor.
  disabled,
};

await writeFile(path.join(outputDir, "domains.json"), `${JSON.stringify(domains, null, 2)}\n`);

const alive = checked.filter((item) => item.alive).length;
console.log(`Adres taramasi: ${alive}/${checked.length} kaynak ayakta.`);
if (Object.keys(overrides).length) {
  console.log("- adresi duzeltilenler:");
  for (const [name, url] of Object.entries(overrides)) console.log(`  ${name} -> ${url}`);
}
if (disabled.length) {
  console.log(`- cevap vermeyen (${disabled.length}): ${disabled.join(", ")}`);
}
