// @plugin-info {"id":"berkstream-desktop","name":"BerkStream Desktop","version":"1.0.0","description":"Windows için Türkçe film ve dizi arama; içerikleri web oynatıcısında açar.","author":"berkdemir18","icon_url":"https://raw.githubusercontent.com/berkdemir18/BerkStream-Hub/main/assets/berkstream-icon.png","supported_types":["movie","show"],"is_builtin":false}

const TMDB_KEY = "e6333b32409e02a4a6eba6fb7ff866bb";
const TMDB = "https://api.themoviedb.org/3";
const IMAGE = "https://image.tmdb.org/t/p/w500";

async function search(query) {
  const response = await fetch(`${TMDB}/search/multi?api_key=${TMDB_KEY}&language=tr-TR&include_adult=false&query=${encodeURIComponent(query)}`);
  if (!response.ok) return "[]";
  const data = await response.json();
  return JSON.stringify((data.results || []).filter(x => x.media_type === "movie" || x.media_type === "tv").slice(0, 30).map(x => ({
    id: `${x.media_type}:${x.id}`,
    title: x.title || x.name,
    poster_url: x.poster_path ? `${IMAGE}${x.poster_path}` : null,
    media_type: x.media_type === "movie" ? "movie" : "show",
    year: parseInt((x.release_date || x.first_air_date || "").slice(0, 4)) || null,
    rating: x.vote_average || null,
    description: x.overview || null
  })));
}

async function getEpisodes(mediaId) {
  const parts = mediaId.split(":");
  if (parts[0] !== "tv") return "[]";
  const showResponse = await fetch(`${TMDB}/tv/${parts[1]}?api_key=${TMDB_KEY}&language=tr-TR`);
  if (!showResponse.ok) return "[]";
  const show = await showResponse.json();
  const episodes = [];
  for (const season of (show.seasons || []).filter(s => s.season_number > 0)) {
    const response = await fetch(`${TMDB}/tv/${parts[1]}/season/${season.season_number}?api_key=${TMDB_KEY}&language=tr-TR`);
    if (!response.ok) continue;
    const data = await response.json();
    for (const episode of (data.episodes || [])) episodes.push({
      id: `tv:${parts[1]}:${season.season_number}:${episode.episode_number}`,
      title: episode.name || `Bölüm ${episode.episode_number}`,
      season: season.season_number,
      episode_number: episode.episode_number,
      thumbnail_url: episode.still_path ? `${IMAGE}${episode.still_path}` : null,
      description: episode.overview || null
    });
  }
  return JSON.stringify(episodes);
}

async function getStreams(mediaId) {
  const p = mediaId.split(":");
  let path = "";
  if (p[0] === "movie") path = `movie/${p[1]}`;
  if (p[0] === "tv" && p.length >= 4) path = `tv/${p[1]}/${p[2]}/${p[3]}`;
  if (!path) return "[]";
  return JSON.stringify([
    { url: `https://vidsrc.to/embed/${path}`, quality: "Otomatik", format: "embed", subtitles: [], headers: {} },
    { url: `https://vidsrc.xyz/embed/${path}`, quality: "Yedek", format: "embed", subtitles: [], headers: {} }
  ]);
}
