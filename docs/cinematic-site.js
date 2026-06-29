import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.170.0/build/three.module.js";

const data = window.PRODUCT_SITE;
const root = document.querySelector("#site-root");
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

document.documentElement.style.setProperty("--accent", data.theme.accent);
document.documentElement.style.setProperty("--accent-2", data.theme.accent2);
document.documentElement.style.setProperty("--accent-3", data.theme.accent3);
document.documentElement.style.setProperty("--bg", data.theme.bg);
document.documentElement.style.setProperty("--ink", data.theme.ink);

root.innerHTML = `
  <div class="world"><canvas id="world-canvas" aria-hidden="true"></canvas></div>
  <header class="nav">
    <div class="nav-inner">
      <a class="brand" href="#top"><span class="mark">${data.mark}</span><span>${data.name}</span></a>
      <nav class="nav-links" aria-label="Primary">
        <a href="#story">What it does</a>
        <a href="#download">Download</a>
        <a href="#setup">Setup</a>
        <a class="nav-pill" href="${data.repoUrl}">GitHub</a>
      </nav>
    </div>
    <div class="progress"><span id="page-progress"></span></div>
  </header>

  <main id="top">
    <section class="section hero">
      <div class="hero-grid">
        <div class="hero-copy reveal">
          <span class="eyebrow">${data.kicker}</span>
          <h1>${data.headline}</h1>
          <p class="lede">${data.subhead}</p>
          <div class="download-grid">
            ${data.downloads.map((item, index) => `
              <a class="download-card ${index === 0 ? "primary" : ""}" href="${item.href}">
                ${item.label}<small>${item.note}</small>
              </a>
            `).join("")}
          </div>
          <div class="secondary-actions">
            ${data.secondary.map((item) => `<a class="button" href="${item.href}">${item.label}</a>`).join("")}
          </div>
        </div>
        <div class="product-stage reveal">
          <div class="stage-top"><strong>${data.stage.title}</strong><span>${data.stage.status}</span></div>
          <div class="stage-body">
            <aside class="stage-rail">
              ${data.stage.rail.map((item, index) => `<div class="rail-item ${index === 0 ? "active" : ""}"><span>${item[0]}</span><strong>${item[1]}</strong></div>`).join("")}
            </aside>
            <div class="mock-surface">
              <p class="surface-title">${data.stage.surfaceTitle}</p>
              <div class="surface-grid">
                ${data.stage.tiles.map(() => `<div class="surface-tile"></div>`).join("")}
              </div>
              <p class="surface-note">${data.stage.note}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="section story" id="story">
      <div class="story-head reveal">
        <h2>${data.storyTitle}</h2>
        <p>${data.storyIntro}</p>
      </div>
      <div class="chapters">
        ${data.chapters.map((chapter, index) => `
          <article class="chapter" data-scene-step="${index}">
            <div class="chapter-card reveal">
              <span class="chapter-index">${String(index + 1).padStart(2, "0")}</span>
              <h3>${chapter.title}</h3>
              <p>${chapter.body}</p>
            </div>
          </article>
        `).join("")}
      </div>
    </section>

    <section class="section download-section" id="download">
      <div class="reveal">
        <h2>${data.downloadTitle}</h2>
        <p>${data.downloadIntro}</p>
      </div>
      <div class="panel-grid">
        ${data.panels.map((panel) => `
          <article class="info-panel reveal">
            <h3>${panel.title}</h3>
            <p>${panel.body}</p>
          </article>
        `).join("")}
      </div>
    </section>

    <section class="section setup-section" id="setup">
      <div class="reveal">
        <h2>${data.setupTitle}</h2>
        <p>${data.setupIntro}</p>
      </div>
      <div class="setup-steps">
        ${data.setup.map((step) => `
          <div class="setup-step reveal">
            <div><strong>${step.title}</strong><span>${step.body}</span></div>
          </div>
        `).join("")}
      </div>
    </section>
  </main>

  <footer class="footer">
    <span>${data.name} by Kumar Adarsh</span>
    <span>${data.footer}</span>
  </footer>
`;

setupAnimation();
setupWorld();

function setupAnimation() {
  const progress = document.querySelector("#page-progress");
  const gsap = window.gsap;
  if (!gsap || !window.ScrollTrigger || reduceMotion) {
    progress.style.width = "100%";
    return;
  }
  gsap.registerPlugin(window.ScrollTrigger);
  gsap.to(progress, {
    width: "100%",
    ease: "none",
    scrollTrigger: { trigger: document.body, start: "top top", end: "bottom bottom", scrub: 0.35 }
  });
  gsap.utils.toArray(".reveal").forEach((el) => {
    gsap.fromTo(el, { y: 64, opacity: 0 }, {
      y: 0,
      opacity: 1,
      duration: 0.9,
      ease: "power3.out",
      scrollTrigger: { trigger: el, start: "top 84%", once: true }
    });
  });
  gsap.utils.toArray(".chapter").forEach((chapter, index) => {
    window.ScrollTrigger.create({
      trigger: chapter,
      start: "top 55%",
      end: "bottom 45%",
      onEnter: () => setSceneTarget(index + 1),
      onEnterBack: () => setSceneTarget(index + 1)
    });
  });
}

let sceneTarget = 0;
function setSceneTarget(value) {
  sceneTarget = value;
}

const pointer = { x: 0, y: 0 };
window.addEventListener("pointermove", (event) => {
  pointer.x = (event.clientX / Math.max(1, innerWidth) - 0.5) * 2;
  pointer.y = (event.clientY / Math.max(1, innerHeight) - 0.5) * 2;
}, { passive: true });

function setupWorld() {
  const canvas = document.querySelector("#world-canvas");
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(Math.min(devicePixelRatio || 1, 2));

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(38, 1, 0.1, 100);
  camera.position.set(0, 0.25, 7.2);

  scene.add(new THREE.AmbientLight(0xffffff, 1.15));
  const key = new THREE.DirectionalLight(0xffffff, 2.6);
  key.position.set(3, 4, 5);
  scene.add(key);
  const wash = new THREE.PointLight(data.theme.accent, 70, 10);
  wash.position.set(-3.2, -1.4, 4.5);
  scene.add(wash);

  const group = new THREE.Group();
  scene.add(group);
  buildScene(group, data.scene);
  addField(group);

  function resize() {
    const width = Math.max(canvas.clientWidth, 1);
    const height = Math.max(canvas.clientHeight, 1);
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
  }

  function tick(now) {
    resize();
    const t = now * 0.001;
    const scroll = document.documentElement.scrollTop / Math.max(1, document.documentElement.scrollHeight - innerHeight);
    const target = sceneTarget + scroll * 1.8;
    group.rotation.y += ((target * 0.32 + pointer.x * 0.18) - group.rotation.y) * 0.035;
    group.rotation.x += ((Math.sin(t * 0.4) * 0.08 - pointer.y * 0.12) - group.rotation.x) * 0.04;
    group.position.y = Math.sin(t * 0.35) * 0.08;
    group.position.x = innerWidth < 620 ? 2.2 : innerWidth < 900 ? 0.7 : 1.45;
    group.scale.setScalar(innerWidth < 620 ? 0.42 : innerWidth < 900 ? 0.72 : 1);
    if (!reduceMotion) {
      group.children.forEach((child, index) => {
        if (child.userData.float) child.position.y += Math.sin(t * child.userData.float + index) * 0.0015;
      });
    }
    renderer.render(scene, camera);
    requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
}

function buildScene(group, type) {
  if (type === "culling" || type === "edit") return buildTiles(group, type);
  if (type === "dictation") return buildMic(group);
  if (type === "android") return buildPhone(group);
  if (type === "jarvis") return buildJarvis(group);
  return buildMarkdown(group);
}

function buildMarkdown(group) {
  addPanel(group, 0, 0, 0, 2.1, 3.1, 0.08, "#ffffff");
  addPanel(group, -1.25, -0.08, -0.16, 0.95, 2.7, 0.08, "#20283a");
  for (let i = 0; i < 9; i++) addPanel(group, -0.2 + (i % 3) * 0.55, 1.0 - Math.floor(i / 3) * 0.38, 0.1, 0.36 + (i % 2) * 0.24, 0.07, 0.05, i % 2 ? data.theme.accent : data.theme.accent2);
}

function buildTiles(group, type) {
  for (let i = 0; i < 9; i++) {
    const color = i % 3 === 0 ? data.theme.accent : i % 3 === 1 ? data.theme.accent2 : data.theme.accent3;
    const panel = addPanel(group, -1.55 + (i % 3) * 0.92, 1.05 - Math.floor(i / 3) * 0.72, i * 0.04, 0.78, 0.52, 0.08, color);
    panel.rotation.z = (i - 4) * 0.02;
  }
  if (type === "edit") {
    for (let i = 0; i < 5; i++) addPanel(group, -1.3 + i * 0.62, -1.45, 0.3, 0.42, 0.22, 0.07, i % 2 ? data.theme.accent2 : data.theme.accent3);
  }
}

function buildMic(group) {
  const mic = new THREE.Mesh(new THREE.CapsuleGeometry(0.35, 1.25, 18, 36), mat(data.theme.accent));
  mic.userData.float = 1.2;
  group.add(mic);
  addPanel(group, 0, -1.25, 0, 1.05, 0.1, 0.1, "#111318");
  for (let i = 0; i < 16; i++) addPanel(group, -2.1 + i * 0.28, -1.55, -0.2, 0.08, 0.25 + (i % 5) * 0.15, 0.08, i % 2 ? data.theme.accent2 : data.theme.accent3);
  for (let i = 0; i < 3; i++) {
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.95 + i * 0.45, 0.014, 12, 96), mat(i % 2 ? data.theme.accent2 : data.theme.accent));
    ring.rotation.x = Math.PI / 2;
    group.add(ring);
  }
}

function buildPhone(group) {
  addPanel(group, 0, 0, 0, 1.55, 3.05, 0.18, "#15161a");
  addPanel(group, 0, 0, 0.13, 1.28, 2.62, 0.06, "#fffdf8");
  addPanel(group, 0.42, -0.95, 0.25, 0.52, 0.52, 0.12, data.theme.accent);
  for (let i = 0; i < 4; i++) addPanel(group, -0.35, 0.82 - i * 0.34, 0.22, 0.62 + i * 0.08, 0.08, 0.04, i % 2 ? data.theme.accent2 : "#808899");
}

function buildJarvis(group) {
  addPanel(group, 0, 0, 0, 2.35, 2.85, 0.08, "#ffffff");
  for (let col = 0; col < 3; col++) {
    addPanel(group, -0.78 + col * 0.78, 0.86, 0.14, 0.54, 0.16, 0.04, [data.theme.accent, data.theme.accent2, data.theme.accent3][col]);
    for (let row = 0; row < 4; row++) addPanel(group, -0.78 + col * 0.78, 0.48 - row * 0.42, 0.16, 0.58, 0.24, 0.04, row % 2 ? "#dfe4ea" : "#ffffff");
  }
  const orb = new THREE.Mesh(new THREE.IcosahedronGeometry(0.42, 2), mat(data.theme.accent2));
  orb.position.set(1.35, -1.05, 0.35);
  orb.userData.float = 1.4;
  group.add(orb);
}

function addField(group) {
  const geometry = new THREE.BufferGeometry();
  const positions = [];
  for (let i = 0; i < 130; i++) {
    positions.push((Math.random() - 0.5) * 8, (Math.random() - 0.5) * 5, (Math.random() - 0.5) * 3 - 0.5);
  }
  geometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
  group.add(new THREE.Points(geometry, new THREE.PointsMaterial({ color: data.theme.accent, size: 0.018, transparent: true, opacity: 0.42 })));
}

function addPanel(group, x, y, z, w, h, d, color) {
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat(color));
  mesh.position.set(x, y, z);
  mesh.userData.float = 0.7 + Math.random();
  group.add(mesh);
  return mesh;
}

function mat(color) {
  return new THREE.MeshStandardMaterial({
    color,
    roughness: 0.52,
    metalness: 0.08,
    transparent: true,
    opacity: 0.88
  });
}
