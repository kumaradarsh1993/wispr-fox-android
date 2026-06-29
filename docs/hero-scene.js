import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.170.0/build/three.module.js";

const canvases = document.querySelectorAll("[data-three-hero]");
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

for (const canvas of canvases) {
  mountScene(canvas);
}

function mountScene(canvas) {
  const sceneType = canvas.dataset.scene || "product";
  const accent = canvas.dataset.accent || "#1266d6";
  const accentTwo = canvas.dataset.accentTwo || "#12917b";
  const accentThree = canvas.dataset.accentThree || "#bf7a10";

  const renderer = new THREE.WebGLRenderer({
    canvas,
    alpha: true,
    antialias: true,
    preserveDrawingBuffer: true,
    powerPreference: "high-performance"
  });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 100);
  camera.position.set(0.2, 0.8, 6.5);

  const root = new THREE.Group();
  root.rotation.set(-0.15, -0.22, 0.03);
  scene.add(root);

  scene.add(new THREE.AmbientLight(0xffffff, 1.4));
  const key = new THREE.DirectionalLight(0xffffff, 2.6);
  key.position.set(3, 4, 5);
  scene.add(key);
  const rim = new THREE.PointLight(accent, 45, 9);
  rim.position.set(-3, -1, 4);
  scene.add(rim);

  const palette = { accent, accentTwo, accentThree };
  buildShowcase(root, sceneType, palette);
  addParticles(root, palette);

  const gsap = window.gsap;
  if (gsap && !reducedMotion) {
    gsap.to(root.rotation, { y: root.rotation.y + 0.18, x: root.rotation.x + 0.05, duration: 4.5, repeat: -1, yoyo: true, ease: "sine.inOut" });
    gsap.to(root.position, { y: 0.12, duration: 3.4, repeat: -1, yoyo: true, ease: "sine.inOut" });
  }

  let pointerX = 0;
  let pointerY = 0;
  window.addEventListener("pointermove", (event) => {
    const rect = canvas.getBoundingClientRect();
    pointerX = ((event.clientX - rect.left) / Math.max(rect.width, 1) - 0.5) * 0.16;
    pointerY = ((event.clientY - rect.top) / Math.max(rect.height, 1) - 0.5) * 0.12;
  }, { passive: true });

  function resize() {
    const width = Math.max(canvas.clientWidth, 1);
    const height = Math.max(canvas.clientHeight, 1);
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
  }

  function frame(time) {
    resize();
    const t = time * 0.001;
    if (!reducedMotion) {
      root.rotation.z = Math.sin(t * 0.55) * 0.025;
      root.rotation.y += (pointerX - root.rotation.y * 0.04) * 0.01;
      root.rotation.x += (-pointerY - root.rotation.x * 0.03) * 0.01;
      for (const child of root.children) {
        if (child.userData.float) {
          child.position.y += Math.sin(t * child.userData.float + child.userData.phase) * 0.0018;
        }
      }
    }
    renderer.render(scene, camera);
    requestAnimationFrame(frame);
  }

  resize();
  requestAnimationFrame(frame);
}

function buildShowcase(root, type, palette) {
  if (type === "markdown") return buildMarkdown(root, palette);
  if (type === "culling") return buildCulling(root, palette);
  if (type === "android") return buildAndroid(root, palette);
  return buildDictation(root, palette);
}

function buildMarkdown(root, palette) {
  const page = makeBox(2.35, 3.1, 0.08, "#ffffff", 0.96);
  page.position.set(0.55, 0, 0);
  page.rotation.z = -0.07;
  page.userData.float = 1.3;
  root.add(page);

  const side = makeBox(1.1, 2.7, 0.07, "#1c2333", 0.96);
  side.position.set(-1.2, -0.06, -0.08);
  side.rotation.z = 0.08;
  side.userData.float = 1.1;
  root.add(side);

  addLineStack(root, -1.42, 0.95, 5, "#dbe6ff", 0.11, 0.46);
  addLineStack(root, 0.05, 1.05, 6, palette.accent, 0.12, 0.82);
  addLineStack(root, 0.05, 0.2, 4, "#6b7280", 0.08, 0.72);
  addBadge(root, 1.2, -1.1, palette.accentTwo);
  addRing(root, -1.5, -1.05, 0.44, palette.accentThree);
}

function buildDictation(root, palette) {
  const mic = new THREE.Group();
  const body = new THREE.Mesh(new THREE.CapsuleGeometry(0.34, 1.1, 16, 32), material(palette.accent, 0.98, 0.58));
  body.rotation.z = 0.03;
  mic.add(body);
  const stand = makeBox(0.08, 0.74, 0.08, "#1f2937", 0.9);
  stand.position.y = -1.03;
  mic.add(stand);
  const base = makeBox(1.1, 0.1, 0.15, "#111827", 0.9);
  base.position.y = -1.42;
  mic.add(base);
  mic.userData.float = 1.4;
  root.add(mic);

  for (let i = 0; i < 3; i++) {
    const torus = new THREE.Mesh(new THREE.TorusGeometry(1 + i * 0.42, 0.014, 16, 96), material(i % 2 ? palette.accentTwo : palette.accent, 0.36, 0.15));
    torus.rotation.x = Math.PI / 2;
    torus.position.y = 0.02;
    torus.userData.float = 0.9 + i * 0.2;
    root.add(torus);
  }
  for (let i = 0; i < 12; i++) {
    const bar = makeBox(0.08, 0.25 + Math.random() * 0.9, 0.08, i % 2 ? palette.accentTwo : palette.accentThree, 0.84);
    bar.position.set(-1.85 + i * 0.34, -1.15 + bar.scale.y * 0.03, -0.12);
    bar.userData.float = 1.2 + i * 0.05;
    bar.userData.phase = i;
    root.add(bar);
  }
}

function buildCulling(root, palette) {
  const colors = [
    ["#174151", "#f0c56a", "#f7ead1"],
    ["#233143", "#81a99d", "#f2ca99"],
    ["#143b2a", "#f0efe2", "#c8913b"],
    ["#102333", "#3e7fa2", "#f3d083"],
    ["#2a2f23", "#86a360", "#efd1ad"]
  ];
  colors.forEach((set, index) => {
    const tile = new THREE.Mesh(new THREE.BoxGeometry(1.2, 0.86, 0.06), new THREE.MeshStandardMaterial({ map: photoTexture(set), roughness: 0.72, metalness: 0.02 }));
    tile.position.set(-1.7 + (index % 3) * 1.32, 0.65 - Math.floor(index / 3) * 1.05, index * 0.04);
    tile.rotation.z = (index - 2) * 0.035;
    tile.userData.float = 1 + index * 0.12;
    root.add(tile);
  });
  addBadge(root, 1.7, -1.05, palette.accentTwo);
  addBadge(root, -1.7, -1.15, palette.accentThree);
  addRing(root, 0.75, 1.25, 0.38, palette.accent);
}

function buildAndroid(root, palette) {
  const phone = makeBox(1.65, 3.25, 0.16, "#15161a", 0.96);
  phone.userData.float = 1.2;
  root.add(phone);
  const screen = makeBox(1.38, 2.8, 0.05, "#fffdf8", 0.98);
  screen.position.z = 0.11;
  root.add(screen);
  addLineStack(root, -0.5, 0.9, 4, "#6b7280", 0.08, 0.72, 0.16);
  const bubble = new THREE.Mesh(new THREE.SphereGeometry(0.38, 32, 32), material(palette.accent, 0.96, 0.45));
  bubble.position.set(0.78, -0.92, 0.35);
  bubble.userData.float = 1.6;
  root.add(bubble);
  for (let i = 0; i < 3; i++) {
    const chip = makeBox(0.36, 0.16, 0.04, i === 0 ? palette.accent : "#ffffff", 0.96);
    chip.position.set(-0.42 + i * 0.42, -0.32, 0.18);
    root.add(chip);
  }
  addRing(root, 0.78, -0.92, 0.66, palette.accentTwo);
}

function addParticles(root, palette) {
  const geometry = new THREE.BufferGeometry();
  const positions = [];
  for (let i = 0; i < 80; i++) {
    positions.push((Math.random() - 0.5) * 7, (Math.random() - 0.5) * 4.5, (Math.random() - 0.5) * 2.4 - 0.5);
  }
  geometry.setAttribute("position", new THREE.Float32BufferAttribute(positions, 3));
  const points = new THREE.Points(geometry, new THREE.PointsMaterial({ color: palette.accent, size: 0.025, transparent: true, opacity: 0.34 }));
  root.add(points);
}

function addLineStack(root, x, y, count, color, height, width, z = 0.12) {
  for (let i = 0; i < count; i++) {
    const line = makeBox(width * (0.55 + Math.random() * 0.45), height, 0.035, color, i === 0 ? 0.88 : 0.48);
    line.position.set(x + line.scale.x * 0.04, y - i * 0.24, z);
    root.add(line);
  }
}

function addBadge(root, x, y, color) {
  const badge = new THREE.Mesh(new THREE.SphereGeometry(0.26, 32, 32), material(color, 0.94, 0.36));
  badge.position.set(x, y, 0.28);
  badge.userData.float = 1.5;
  root.add(badge);
}

function addRing(root, x, y, radius, color) {
  const ring = new THREE.Mesh(new THREE.TorusGeometry(radius, 0.018, 12, 80), material(color, 0.45, 0.18));
  ring.position.set(x, y, 0.22);
  ring.userData.float = 1.1;
  root.add(ring);
}

function makeBox(width, height, depth, color, opacity) {
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(width, height, depth), material(color, opacity, 0.42));
  return mesh;
}

function material(color, opacity, roughness) {
  return new THREE.MeshStandardMaterial({
    color,
    transparent: opacity < 1,
    opacity,
    roughness,
    metalness: 0.08
  });
}

function photoTexture(colors) {
  const canvas = document.createElement("canvas");
  canvas.width = 256;
  canvas.height = 180;
  const ctx = canvas.getContext("2d");
  const gradient = ctx.createLinearGradient(0, 0, 256, 180);
  gradient.addColorStop(0, colors[0]);
  gradient.addColorStop(0.48, colors[1]);
  gradient.addColorStop(1, colors[2]);
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, 256, 180);
  ctx.fillStyle = "rgba(255,255,255,0.18)";
  ctx.fillRect(18, 18, 108, 24);
  ctx.fillStyle = "rgba(0,0,0,0.22)";
  ctx.fillRect(0, 124, 256, 56);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}
