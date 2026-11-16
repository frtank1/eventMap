// Hand-authored vector UI symbols. No emoji/fonts or user-provided markup.
const paths={
  sport:'<circle cx="12" cy="12" r="9"/><path d="m12 7 5 4-2 6H9l-2-6zM12 3v4m9 5-4-1m1 8-3-2M6 19l3-2m-6-5 4-1"/>',
  music:'<path d="M9 17V6l11-3v12M9 9l11-3"/><ellipse cx="6" cy="18" rx="3" ry="2"/><ellipse cx="17" cy="16" rx="3" ry="2"/>',
  market:'<path d="m4 4-2 5h20l-2-5zM4 10v10h16V10M9 20v-7h6v7M2 9c0 4 5 4 5 0 0 4 5 4 5 0 0 4 5 4 5 0 0 4 5 4 5 0"/>',
  discovery:'<path d="m4 8 12-5 3 7-12 5zM16 3l3-1 4 9-3 1M11 14v7m0-5-6 5m6-5 6 5M2 9l2-1 3 7-2 1z"/>',
  route:'<path d="M4 21V4h16v17M8 4v4h4V4m0 4h4V4M9 21l3-7 3 7M12 14v-3"/>',
  quest:'<path d="M5 22V3m0 1c5-4 9 4 14 0v10c-5 4-9-4-14 0"/>',
  community:'<circle cx="9" cy="8" r="3"/><path d="M2 21v-3a7 7 0 0 1 14 0v3M16 5a3 3 0 0 1 0 6m3 10v-3a8 8 0 0 0-2-5"/>',
  cluster:'<circle cx="7" cy="7" r="3"/><circle cx="17" cy="7" r="3"/><circle cx="7" cy="17" r="3"/><circle cx="17" cy="17" r="3"/>',
};
export function eventIcon(key){return `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[key]||paths.community}</svg>`;}
