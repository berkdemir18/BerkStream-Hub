/**
 * BerkStream tek eklenti derleyicisi.
 *
 * Upstream CloudStream depolarindaki saglayici modullerinin Kotlin kaynagini
 * BerkStream modulunun icine kopyalar, her modulu kendine ozel bir pakete
 * tasiyarak sinif cakismalarini imkansiz kilar ve hepsini tek bir
 * @CloudstreamPlugin altinda kayit eden VendoredSources.kt dosyasini uretir.
 *
 * Kullanim: node scripts/vendor-sources.mjs [--offline]
 */
import { execFileSync } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const config = JSON.parse(await readFile(path.join(root, "sources.json"), "utf8"));
const offline = process.argv.includes("--offline");

const cacheDir = path.join(root, ".vendor-cache");
const vendorRoot = path.join(root, "BerkStream/src/main/kotlin/com/berkstream/vendor");
const registryFile = path.join(root, "BerkStream/src/main/kotlin/com/berkstream/VendoredSources.kt");
const reportFile = path.join(root, "vendor-report.json");
const basePackage = "com.berkstream.vendor";

const repositories = config.vendorRepositories ?? [];
const allowedStatuses = new Set(config.includeStatuses ?? [1]);
const blockedNamePatterns = (config.blockedNamePatterns ?? []).map(normalizedName);
const excluded = new Set((config.excludedPlugins ?? []).map(normalizedName));
const vendorExcluded = new Set((config.vendorExcludedPlugins ?? []).map(normalizedName));

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

function git(args, cwd) {
  try {
    return execFileSync("git", args, { cwd, stdio: ["ignore", "pipe", "pipe"] }).toString();
  } catch (error) {
    const stderr = error.stderr?.toString().trim();
    throw new Error(`git ${args.join(" ")} basarisiz: ${stderr || error.message}`);
  }
}

/**
 * Varsayilan dal kullanilir; upstream depolar dal adini degistirse bile calisir.
 */
async function syncRepository(repository) {
  const target = path.join(cacheDir, repository.id);
  const ref = repository.branch ?? "HEAD";
  if (existsSync(path.join(target, ".git"))) {
    if (!offline) {
      git(["fetch", "--depth", "1", "origin", ref], target);
      git(["reset", "--hard", "FETCH_HEAD"], target);
      git(["clean", "-fd"], target);
    }
    return target;
  }
  if (offline) throw new Error(`${repository.id}: onbellek yok, --offline calisamaz`);
  await mkdir(cacheDir, { recursive: true });
  const args = ["clone", "--depth", "1"];
  if (repository.branch) args.push("--branch", repository.branch);
  args.push(repository.url, target);
  git(args, cacheDir);
  return target;
}

function readGradleMeta(text) {
  const pick = (key) => (text.match(new RegExp(`${key}\\s*=\\s*"((?:[^"\\\\]|\\\\.)*)"`)) || [])[1];
  const pickInt = (key) => {
    const raw = (text.match(new RegExp(`(?:^|\\n)\\s*${key}\\s*=\\s*(\\d+)`)) || [])[1];
    return raw === undefined ? undefined : Number(raw);
  };
  const list = (key) => {
    const raw = (text.match(new RegExp(`${key}\\s*=\\s*listOf\\(([^)]*)\\)`)) || [])[1];
    return raw ? [...raw.matchAll(/"([^"]*)"/g)].map((m) => m[1]) : [];
  };
  return {
    version: pickInt("version") ?? 1,
    status: pickInt("status") ?? 3,
    description: pick("description") ?? "",
    language: pick("language") ?? "",
    iconUrl: pick("iconUrl") ?? "",
    tvTypes: list("tvTypes"),
    authors: list("authors"),
  };
}

async function listKotlinFiles(dir) {
  const out = [];
  async function walk(current) {
    for (const entry of await readdir(current, { withFileTypes: true })) {
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) await walk(full);
      else if (entry.name.endsWith(".kt")) out.push(full);
    }
  }
  if (existsSync(dir)) await walk(dir);
  return out.sort();
}

function packageSuffix(oldPackage) {
  return oldPackage.replace(/^com\./, "");
}

function slugify(name, taken) {
  let slug = normalizedName(name);
  if (!slug) slug = "kaynak";
  if (/^\d/.test(slug)) slug = `p${slug}`;
  let candidate = slug;
  let index = 2;
  while (taken.has(candidate)) candidate = `${slug}${index++}`;
  taken.add(candidate);
  return candidate;
}

const selected = new Map();
const skipped = [];
const repoOrder = [...repositories].sort((a, b) => (b.priority ?? 0) - (a.priority ?? 0));

for (const repository of repoOrder) {
  const repoPath = await syncRepository(repository);
  const entries = await readdir(repoPath, { withFileTypes: true });
  for (const entry of entries.sort((a, b) => a.name.localeCompare(b.name, "tr"))) {
    if (!entry.isDirectory() || entry.name.startsWith(".") || entry.name.startsWith("__")) continue;
    const modulePath = path.join(repoPath, entry.name);
    const gradleFile = path.join(modulePath, "build.gradle.kts");
    const sourceDir = path.join(modulePath, "src/main/kotlin");
    if (!existsSync(gradleFile) || !existsSync(sourceDir)) continue;

    const meta = readGradleMeta(await readFile(gradleFile, "utf8"));
    const key = normalizedName(entry.name);
    const reject = (reason) => skipped.push({ origin: repository.id, module: entry.name, reason });

    if (!allowedStatuses.has(meta.status)) { reject(`status=${meta.status}`); continue; }
    if (excluded.has(key) || vendorExcluded.has(key)) { reject("liste disi birakildi"); continue; }
    if (blockedNamePatterns.some((pattern) => key.includes(pattern))) { reject("yetiskin icerik filtresi"); continue; }
    if (meta.tvTypes.includes("NSFW")) { reject("yetiskin icerik filtresi"); continue; }
    if (selected.has(key)) { reject(`${selected.get(key).origin} surumu tercih edildi`); continue; }

    selected.set(key, {
      origin: repository.id,
      originName: repository.name ?? repository.id,
      module: entry.name,
      sourceDir,
      meta,
    });
  }
}

await rm(vendorRoot, { recursive: true, force: true });

const slugs = new Set();
const vendored = [];
const failures = [];

for (const item of [...selected.values()].sort((a, b) => a.module.localeCompare(b.module, "tr"))) {
  const files = await listKotlinFiles(item.sourceDir);
  if (files.length === 0) {
    skipped.push({ origin: item.origin, module: item.module, reason: "kotlin kaynagi yok" });
    continue;
  }

  const slug = slugify(item.module, slugs);
  const moduleBase = `${basePackage}.${item.origin}.${slug}`;

  const parsed = [];
  const packageCounts = new Map();
  for (const file of files) {
    const text = await readFile(file, "utf8");
    const oldPackage = (text.match(/^[ \t]*package\s+([\w.]+)/m) || [])[1] ?? null;
    if (oldPackage) packageCounts.set(oldPackage, (packageCounts.get(oldPackage) ?? 0) + 1);
    parsed.push({ file, text, oldPackage });
  }
  if (parsed.length === 0) continue;

  // Bazi modullerde yardimci dosyalar varsayilan pakette duruyor; onlari modulun
  // ana paketine aliyoruz ki kardes dosyalar import olmadan gorebilsin.
  const dominantPackage = [...packageCounts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0];
  if (!dominantPackage) {
    failures.push(`${item.module}: paket satiri olan dosya yok`);
    continue;
  }
  const packages = new Set(packageCounts.keys());

  const rename = new Map([...packages].map((pkg) => [pkg, `${moduleBase}.${packageSuffix(pkg)}`]));
  const renameOrder = [...rename].sort((a, b) => b[0].length - a[0].length);
  const pluginClasses = [];

  for (const entry of parsed) {
    let text = entry.text;
    // Paket adlarini yeniden yaz: en uzun paket once, alt paketleri bozmamak icin.
    for (const [oldPackage, newPackage] of renameOrder) {
      text = text.split(oldPackage).join(newPackage);
    }
    const newPackage = rename.get(entry.oldPackage ?? dominantPackage);
    if (!entry.oldPackage) {
      const fileAnnotations = text.match(/^(?:\s*@file:[^\n]*\n)+/);
      const offset = fileAnnotations ? fileAnnotations[0].length : 0;
      text = `${text.slice(0, offset)}package ${newPackage}\n${text.slice(offset)}`;
    }
    // Tek .cs3 icinde yalnizca bir giris noktasi olabilir; alt eklentilerin
    // annotation'i silinir, siniflari VendoredSources.kt uzerinden cagrilir.
    for (const match of text.matchAll(
      /@CloudstreamPlugin\s+(?:internal\s+|public\s+|open\s+)*class\s+(`[^`]+`|[A-Za-z0-9_]+)/g,
    )) {
      pluginClasses.push(`${newPackage}.${match[1]}`);
    }
    text = text.replace(/^[ \t]*import\s+com\.lagradost\.cloudstream3\.plugins\.CloudstreamPlugin[ \t]*\r?\n/gm, "");
    text = text.replace(/@CloudstreamPlugin[ \t]*\r?\n?/g, "");

    const relativeDirs = newPackage.split(".").slice(basePackage.split(".").length + 2);
    const target = path.join(vendorRoot, item.origin, slug, ...relativeDirs, path.basename(entry.file));
    await mkdir(path.dirname(target), { recursive: true });
    const header =
      `// BerkStream tarafindan ${item.originName} deposundan derlendi (${item.module}).\n` +
      `// Kaynak paket: ${entry.oldPackage ?? "(varsayilan paket)"} -> ${newPackage}\n` +
      `// Bu dosyayi elle duzenleme: 'npm run vendor' her calistiginda yeniden uretilir.\n`;
    await writeFile(target, header + text);
  }

  if (pluginClasses.length === 0) {
    failures.push(`${item.module}: @CloudstreamPlugin sinifi bulunamadi`);
    await rm(path.join(vendorRoot, item.origin, slug), { recursive: true, force: true });
    continue;
  }

  vendored.push({
    id: item.module,
    slug,
    origin: item.origin,
    originName: item.originName,
    version: item.meta.version,
    description: item.meta.description,
    language: item.meta.language,
    tvTypes: item.meta.tvTypes,
    authors: item.meta.authors,
    iconUrl: item.meta.iconUrl,
    pluginClasses,
    fileCount: parsed.length,
  });
}

vendored.sort((a, b) => a.id.localeCompare(b.id, "tr"));

const entries = vendored.flatMap((item) =>
  item.pluginClasses.map(
    (className) =>
      `    VendoredSource("${item.id}", "${item.originName.replace(/"/g, "'")}") { ${className}() },`,
  ),
);

const registry = `// BU DOSYA URETILMISTIR - elle duzenleme.
// Ureten: scripts/vendor-sources.mjs
// Gomulu kaynak sayisi: ${vendored.length}
package com.berkstream

import com.lagradost.cloudstream3.plugins.BasePlugin

internal class VendoredSource(
    val id: String,
    val origin: String,
    val create: () -> BasePlugin,
)

internal val VENDORED_SOURCES: List<VendoredSource> = listOf(
${entries.join("\n")}
)
`;

await mkdir(path.dirname(registryFile), { recursive: true });
await writeFile(registryFile, registry);

const report = {
  generatedAt: new Date().toISOString(),
  vendoredCount: vendored.length,
  pluginClassCount: entries.length,
  originCounts: Object.fromEntries(
    repoOrder.map((repository) => [
      repository.id,
      vendored.filter((item) => item.origin === repository.id).length,
    ]),
  ),
  vendored,
  skipped: skipped.sort((a, b) => a.module.localeCompare(b.module, "tr")),
  failures,
};
await writeFile(reportFile, `${JSON.stringify(report, null, 2)}\n`);

console.log(`BerkStream tek eklenti: ${vendored.length} kaynak gomuldu (${entries.length} plugin sinifi).`);
for (const [origin, count] of Object.entries(report.originCounts)) console.log(`- ${origin}: ${count}`);
console.log(`- atlanan modul: ${skipped.length}`);
if (failures.length) {
  console.log("- uyarilar:");
  for (const failure of failures) console.log(`  ! ${failure}`);
}
