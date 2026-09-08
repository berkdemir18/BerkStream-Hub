import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const config = JSON.parse(await readFile(path.join(root, "sources.json"), "utf8"));
const verifyFiles = process.argv.includes("--verify");
const repositorySlug = process.env.BERKSTREAM_REPOSITORY || "berkdemir18/BerkStream-Hub";
const branch = process.env.BERKSTREAM_BRANCH || "builds";
const outputDir = process.env.BERKSTREAM_OUTPUT_DIR
  ? path.resolve(process.env.BERKSTREAM_OUTPUT_DIR)
  : root;
const localManifestPath = process.env.BERKSTREAM_LOCAL_PLUGIN_MANIFEST;
const localBuildDir = process.env.BERKSTREAM_LOCAL_BUILD_DIR;

function normalizedName(value) {
  return String(value)
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/ı/g, "i")
    .replace(/[^a-zA-Z0-9]/g, "")
    .toLowerCase();
}

async function fetchJson(url) {
  const response = await fetch(url, {
    headers: { "user-agent": "BerkStream-Hub/1.0" },
  });
  if (!response.ok) throw new Error(`${url} -> HTTP ${response.status}`);
  return response.json();
}

async function fetchBytes(url) {
  const response = await fetch(url, {
    headers: { "user-agent": "BerkStream-Hub/1.0" },
  });
  if (!response.ok) throw new Error(`${url} -> HTTP ${response.status}`);
  return Buffer.from(await response.arrayBuffer());
}

function sha256(bytes) {
  return `sha256-${createHash("sha256").update(bytes).digest("hex")}`;
}

async function mapLimit(items, limit, worker) {
  const results = new Array(items.length);
  let cursor = 0;
  async function consume() {
    while (cursor < items.length) {
      const index = cursor++;
      results[index] = await worker(items[index], index);
    }
  }
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, consume));
  return results;
}

const allowedStatuses = new Set(config.includeStatuses ?? [1]);
const excluded = new Set((config.excludedPlugins ?? []).map(normalizedName));
const blockedNamePatterns = (config.blockedNamePatterns ?? []).map(normalizedName);
const preferred = new Map(
  Object.entries(config.preferredSourceByPlugin ?? {}).map(([name, source]) => [
    normalizedName(name),
    source,
  ]),
);

const loadedSources = await Promise.all(
  config.sources.map(async (source) => {
    const plugins = await fetchJson(source.manifest);
    if (!Array.isArray(plugins)) throw new Error(`${source.id}: manifest bir dizi değil`);
    return { ...source, plugins };
  }),
);

if (localManifestPath) {
  const plugins = JSON.parse(await readFile(path.resolve(localManifestPath), "utf8"));
  if (!Array.isArray(plugins)) throw new Error("Yerel plugin manifesti bir dizi değil");
  loadedSources.push({
    id: "berkstream",
    name: "BerkStream Custom Provider",
    repository: `https://github.com/${repositorySlug}`,
    priority: 1000,
    plugins,
  });
}

const allCandidates = loadedSources.flatMap((source) =>
    source.plugins.map((plugin) => ({
      plugin,
      source,
      key: normalizedName(plugin.internalName || plugin.name),
    })),
  );
const candidates = allCandidates
  .filter(({ plugin, key }) =>
    allowedStatuses.has(plugin.status) &&
    !excluded.has(key) &&
    plugin.isAdult !== true &&
    !blockedNamePatterns.some((pattern) => key.includes(pattern)),
  )
  .sort((a, b) => {
    const wantedA = preferred.get(a.key) === a.source.id ? 1 : 0;
    const wantedB = preferred.get(b.key) === b.source.id ? 1 : 0;
    return wantedB - wantedA || b.source.priority - a.source.priority;
  });

const selected = new Map();
const duplicates = [];
for (const item of candidates) {
  if (selected.has(item.key)) {
    duplicates.push({
      plugin: item.plugin.name,
      kept: selected.get(item.key).source.id,
      skipped: item.source.id,
    });
    continue;
  }
  selected.set(item.key, item);
}

const chosen = [...selected.values()].sort((a, b) =>
  String(a.plugin.name).localeCompare(String(b.plugin.name), "tr"),
);
const verification = [];

if (verifyFiles) {
  await mapLimit(chosen, 8, async ({ plugin, source }) => {
    const localCs3 =
      source.id === "berkstream" && localBuildDir
        ? path.resolve(localBuildDir, `${plugin.internalName}.cs3`)
        : null;
    const bytes = localCs3 ? await readFile(localCs3) : await fetchBytes(plugin.url);
    const actualHash = sha256(bytes);
    if (plugin.fileHash && plugin.fileHash !== actualHash) {
      throw new Error(`${plugin.name}: CS3 hash uyuşmuyor (${source.id})`);
    }
    plugin.fileHash = actualHash;
    plugin.fileSize = bytes.length;

    if (plugin.jarUrl) {
      const localJar =
        source.id === "berkstream" && localBuildDir
          ? path.resolve(localBuildDir, `${plugin.internalName}.jar`)
          : null;
      const jar = localJar ? await readFile(localJar) : await fetchBytes(plugin.jarUrl);
      const actualJarHash = sha256(jar);
      if (plugin.jarHash && plugin.jarHash !== actualJarHash) {
        throw new Error(`${plugin.name}: JAR hash uyuşmuyor (${source.id})`);
      }
      plugin.jarHash = actualJarHash;
      plugin.jarFileSize = jar.length;
    }

    verification.push({
      name: plugin.name,
      source: source.id,
      cs3Bytes: bytes.length,
      result: "ok",
    });
  });
}

const plugins = chosen.map(({ plugin }) => plugin);
// Ana katalog artik tek eklenti: BerkStream butun kaynaklari kendi icinde tasiyor.
// Tekil paketler yedek katalogda (plugins-all.json) yayinlanmaya devam ediyor.
const allInOne = chosen
  .filter(({ source }) => source.id === "berkstream")
  .map(({ plugin }) => plugin);
const sourceCounts = Object.fromEntries(
  loadedSources.map((source) => [
    source.id,
    chosen.filter((item) => item.source.id === source.id).length,
  ]),
);

const repo = {
  name: "BerkStream",
  description: "Tüm Türkçe kaynakları tek eklentide toplayan BerkStream",
  manifestVersion: 1,
  pluginLists: [
    `https://raw.githubusercontent.com/${repositorySlug}/${branch}/plugins.json`,
  ],
};

const fullRepo = {
  name: "BerkStream Hub (tekil paketler)",
  description: "Güncel ve tekilleştirilmiş CloudStream eklenti kataloğu — tek tek kurmak isteyenler için",
  manifestVersion: 1,
  pluginLists: [
    `https://raw.githubusercontent.com/${repositorySlug}/${branch}/plugins-all.json`,
  ],
};

const report = {
  generatedAt: new Date().toISOString(),
  repositorySlug,
  allInOnePlugins: allInOne.length,
  totalPlugins: plugins.length,
  sourceCounts,
  duplicates,
  verified: verifyFiles,
  verifiedFiles: verification.sort((a, b) => a.name.localeCompare(b.name, "tr")),
};

await mkdir(outputDir, { recursive: true });
await Promise.all([
  writeFile(path.join(outputDir, "plugins.json"), `${JSON.stringify(allInOne, null, 2)}\n`),
  writeFile(path.join(outputDir, "plugins-all.json"), `${JSON.stringify(plugins, null, 2)}\n`),
  writeFile(path.join(outputDir, "repo.json"), `${JSON.stringify(repo, null, 2)}\n`),
  writeFile(path.join(outputDir, "repo-full.json"), `${JSON.stringify(fullRepo, null, 2)}\n`),
  writeFile(path.join(outputDir, "catalog-report.json"), `${JSON.stringify(report, null, 2)}\n`),
]);

console.log(`BerkStream ana katalog: ${allInOne.length} eklenti (tek paket).`);
console.log(`BerkStream Hub yedek katalog: ${plugins.length} eklenti seçildi.`);
for (const [source, count] of Object.entries(sourceCounts)) {
  console.log(`- ${source}: ${count}`);
}
console.log(`- tekilleştirilen tekrar: ${duplicates.length}`);
console.log(`- dosya doğrulaması: ${verifyFiles ? "tamamlandı" : "atlanıldı"}`);
