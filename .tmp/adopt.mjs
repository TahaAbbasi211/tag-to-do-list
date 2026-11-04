const u = "https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse";
const r = await fetch(u); const j = await r.json();
for (const a of j) console.log(a.binary.package.name, "|", a.binary.package.size, "|", a.binary.package.link);
