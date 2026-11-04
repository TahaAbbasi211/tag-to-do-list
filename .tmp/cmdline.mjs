const r = await fetch("https://dl.google.com/android/repository/repository2-3.xml");
const t = await r.text();
const i = t.indexOf('path="cmdline-tools;latest"');
console.log("idx", i);
if (i > -1) {
  const seg = t.slice(i, i + 3000);
  console.log(seg.replace(/<[^>]+>/g, (m) => m.length > 60 ? m.slice(0, 60) + '...>' : m).slice(0, 2500));
}
