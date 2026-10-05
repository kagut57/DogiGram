import * as THREE from "https://cdn.jsdelivr.net/npm/three@0.169.0/build/three.module.js";

const canvas = document.getElementById("space");
const root = document.documentElement;
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const isSmall = () => window.innerWidth < 860;

function supportsWebGL() {
  try {
    const test = document.createElement("canvas");
    return !!(window.WebGLRenderingContext && (test.getContext("webgl2") || test.getContext("webgl")));
  } catch (e) {
    return false;
  }
}


function radialTexture(stops, size = 128) {
  const c = document.createElement("canvas");
  c.width = c.height = size;
  const ctx = c.getContext("2d");
  const g = ctx.createRadialGradient(size / 2, size / 2, 0, size / 2, size / 2, size / 2);
  stops.forEach(([at, color]) => g.addColorStop(at, color));
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, size, size);
  const texture = new THREE.CanvasTexture(c);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

const planetVertex = `
varying vec3 vNormal;
varying vec3 vPos;
varying vec3 vView;
void main() {
  vNormal = normalize(normalMatrix * normal);
  vPos = position;
  vec4 mv = modelViewMatrix * vec4(position, 1.0);
  vView = normalize(-mv.xyz);
  gl_Position = projectionMatrix * mv;
}`;

const planetFragment = `
uniform vec3 uA;
uniform vec3 uB;
uniform vec3 uRim;
uniform float uTime;
uniform float uRadius;
varying vec3 vNormal;
varying vec3 vPos;
varying vec3 vView;
void main() {
  float lat = vPos.y / uRadius;
  float swirl = sin(vPos.x / uRadius * 3.0 + uTime * 0.18) * 1.4 + sin(vPos.z / uRadius * 5.0 - uTime * 0.11) * 0.6;
  float bands = sin(lat * 13.0 + swirl) * 0.5 + 0.5;
  vec3 base = mix(uB, uA, clamp(0.35 + lat * 0.45 + bands * 0.35, 0.0, 1.0));
  float light = clamp(dot(vNormal, normalize(vec3(-0.35, 0.55, 0.75))), 0.0, 1.0);
  vec3 color = base * (0.28 + 0.9 * light);
  float fres = pow(1.0 - max(dot(vNormal, vView), 0.0), 2.4);
  color += uRim * fres * 1.35;
  gl_FragColor = vec4(color, 1.0);
}`;

const glowVertex = `
varying vec3 vNormal;
void main() {
  vNormal = normalize(normalMatrix * normal);
  gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
}`;

const glowFragment = `
uniform vec3 uColor;
uniform float uPower;
varying vec3 vNormal;
void main() {
  float intensity = pow(max(0.0, 0.72 - dot(vNormal, vec3(0.0, 0.0, 1.0))), uPower);
  gl_FragColor = vec4(uColor, 1.0) * intensity;
}`;

function makePlanet(radius, colorA, colorB, rim) {
  const group = new THREE.Group();
  const uniforms = {
    uA: { value: new THREE.Color(colorA) },
    uB: { value: new THREE.Color(colorB) },
    uRim: { value: new THREE.Color(rim) },
    uTime: { value: 0 },
    uRadius: { value: radius },
  };
  const body = new THREE.Mesh(
    new THREE.SphereGeometry(radius, 64, 48),
    new THREE.ShaderMaterial({ uniforms, vertexShader: planetVertex, fragmentShader: planetFragment })
  );
  const glow = new THREE.Mesh(
    new THREE.SphereGeometry(radius * 1.22, 48, 32),
    new THREE.ShaderMaterial({
      uniforms: { uColor: { value: new THREE.Color(rim) }, uPower: { value: 3.2 } },
      vertexShader: glowVertex,
      fragmentShader: glowFragment,
      side: THREE.BackSide,
      blending: THREE.AdditiveBlending,
      transparent: true,
      depthWrite: false,
    })
  );
  group.add(body, glow);
  group.userData.uniforms = uniforms;
  group.userData.body = body;
  return group;
}

function makeRing(inner, outer, count, color, size) {
  const positions = new Float32Array(count * 3);
  const colors = new Float32Array(count * 3);
  const base = new THREE.Color(color);
  const white = new THREE.Color("#ffffff");
  for (let i = 0; i < count; i++) {
    const angle = Math.random() * Math.PI * 2;
    const r = inner + Math.pow(Math.random(), 0.7) * (outer - inner);
    positions[i * 3] = Math.cos(angle) * r;
    positions[i * 3 + 1] = (Math.random() - 0.5) * 0.35;
    positions[i * 3 + 2] = Math.sin(angle) * r;
    const c = base.clone().lerp(white, Math.random() * 0.5);
    colors.set([c.r, c.g, c.b], i * 3);
  }
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
  return new THREE.Points(
    geometry,
    new THREE.PointsMaterial({
      size,
      map: radialTexture([[0, "rgba(255,255,255,1)"], [0.4, "rgba(255,255,255,0.6)"], [1, "rgba(255,255,255,0)"]], 32),
      vertexColors: true,
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    })
  );
}

function paperPlaneGeometry() {
  const nose = [0, 0, 1.25];
  const left = [-0.8, 0.06, -0.8];
  const right = [0.8, 0.06, -0.8];
  const tail = [0, 0, -0.8];
  const keel = [0, -0.38, -0.8];
  const tris = [nose, tail, left, nose, right, tail, nose, keel, tail];
  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.BufferAttribute(new Float32Array(tris.flat()), 3));
  geometry.computeVertexNormals();
  return geometry;
}

function iconGeometry() {
  const shape = new THREE.Shape();
  const s = 1;
  const r = 0.44;
  shape.moveTo(-s + r, -s);
  shape.lineTo(s - r, -s);
  shape.quadraticCurveTo(s, -s, s, -s + r);
  shape.lineTo(s, s - r);
  shape.quadraticCurveTo(s, s, s - r, s);
  shape.lineTo(-s + r, s);
  shape.quadraticCurveTo(-s, s, -s, s - r);
  shape.lineTo(-s, -s + r);
  shape.quadraticCurveTo(-s, -s, -s + r, -s);
  const geometry = new THREE.ExtrudeGeometry(shape, {
    depth: 0.3,
    bevelEnabled: true,
    bevelThickness: 0.09,
    bevelSize: 0.07,
    bevelSegments: 6,
    curveSegments: 28,
  });
  geometry.center();
  return geometry;
}

function start() {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true, powerPreference: "high-performance" });
  renderer.setClearColor(0x000000, 0);
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  root.classList.add("has-webgl");

  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x07040f, 0.0058);
  const camera = new THREE.PerspectiveCamera(55, window.innerWidth / window.innerHeight, 0.1, 900);
  camera.position.set(0, 0, 30);

  scene.add(new THREE.AmbientLight(0x8a78d8, 1.1));
  const key = new THREE.DirectionalLight(0xffffff, 2.2);
  key.position.set(6, 9, 12);
  scene.add(key);
  const rim = new THREE.DirectionalLight(0xc06bff, 3.0);
  rim.position.set(-10, -4, -8);
  scene.add(rim);

  const stops = Array.from(document.querySelectorAll("[data-stop]"));
  const worldSections = stops.filter((el) => el.dataset.stop === "world");

  const worldSpacing = 72;
  const worldStart = -78;
  const portalZ = worldStart - worldSpacing * worldSections.length - 30;
  const deepest = portalZ - 120;

  const starCount = isSmall() ? 1300 : 2800;
  const starPositions = new Float32Array(starCount * 3);
  const starColors = new Float32Array(starCount * 3);
  const palette = ["#ffffff", "#d9ccff", "#a9c8ff", "#ffc2e2", "#fff2c9"].map((c) => new THREE.Color(c));
  for (let i = 0; i < starCount; i++) {
    starPositions[i * 3] = (Math.random() - 0.5) * 360;
    starPositions[i * 3 + 1] = (Math.random() - 0.5) * 240;
    starPositions[i * 3 + 2] = 80 - Math.random() * (80 - deepest);
    const c = palette[(Math.random() * palette.length) | 0];
    starColors.set([c.r, c.g, c.b], i * 3);
  }
  const starGeometry = new THREE.BufferGeometry();
  starGeometry.setAttribute("position", new THREE.BufferAttribute(starPositions, 3));
  starGeometry.setAttribute("color", new THREE.BufferAttribute(starColors, 3));
  const stars = new THREE.Points(
    starGeometry,
    new THREE.PointsMaterial({
      size: 1.15,
      map: radialTexture([[0, "rgba(255,255,255,1)"], [0.25, "rgba(255,255,255,0.85)"], [1, "rgba(255,255,255,0)"]], 32),
      vertexColors: true,
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    })
  );
  scene.add(stars);

  const nebulaColors = ["rgba(139,92,246,", "rgba(236,72,153,", "rgba(59,130,246,", "rgba(168,85,247,"];
  const nebulae = [];
  for (let i = 0; i < 9; i++) {
    const tone = nebulaColors[i % nebulaColors.length];
    const sprite = new THREE.Sprite(
      new THREE.SpriteMaterial({
        map: radialTexture([[0, tone + "0.55)"], [0.45, tone + "0.18)"], [1, tone + "0)"]], 256),
        transparent: true,
        depthWrite: false,
        blending: THREE.AdditiveBlending,
        opacity: 0.85,
      })
    );
    const scale = 140 + Math.random() * 120;
    sprite.scale.set(scale, scale * 0.7, 1);
    sprite.position.set((i % 2 ? 1 : -1) * (40 + Math.random() * 60), (Math.random() - 0.5) * 60, -40 - i * ((Math.abs(deepest) - 40) / 9) - 60);
    scene.add(sprite);
    nebulae.push(sprite);
  }

  const hero = new THREE.Group();
  scene.add(hero);

  const heroPlanet = makePlanet(18, "#9b6bff", "#1b0f4a", "#a876ff");
  hero.add(heroPlanet);
  const heroRing = makeRing(24, 34, isSmall() ? 1400 : 2600, "#b393ff", 0.32);
  heroRing.rotation.x = 0.32;
  heroRing.rotation.z = -0.18;
  heroPlanet.add(heroRing);

  const iconTexture = new THREE.TextureLoader().load("assets/icon-512.png");
  iconTexture.colorSpace = THREE.SRGBColorSpace;
  iconTexture.anisotropy = 8;
  iconTexture.repeat.set(0.5, 0.5);
  iconTexture.offset.set(0.5, 0.5);
  const iconMaterials = [
    new THREE.MeshStandardMaterial({ map: iconTexture, roughness: 0.32, metalness: 0.05 }),
    new THREE.MeshStandardMaterial({ color: 0x7d4dff, roughness: 0.22, metalness: 0.75, emissive: 0x2c0f6e, emissiveIntensity: 0.6 }),
  ];
  const sharedIconGeometry = iconGeometry();
  const icon = new THREE.Mesh(sharedIconGeometry, iconMaterials);
  hero.add(icon);
  const iconHalo = new THREE.Sprite(
    new THREE.SpriteMaterial({
      map: radialTexture([[0, "rgba(160,110,255,0.75)"], [0.5, "rgba(120,70,255,0.18)"], [1, "rgba(120,70,255,0)"]], 128),
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    })
  );
  iconHalo.scale.set(16, 16, 1);
  hero.add(iconHalo);

  const planeGeometry = paperPlaneGeometry();
  const planeMaterial = new THREE.MeshStandardMaterial({
    color: 0xf4efff,
    emissive: 0x5b3bd6,
    emissiveIntensity: 0.35,
    roughness: 0.45,
    metalness: 0.1,
    side: THREE.DoubleSide,
    flatShading: true,
  });
  const planes = [];
  const planeCount = isSmall() ? 14 : 26;
  for (let i = 0; i < planeCount; i++) {
    const mesh = new THREE.Mesh(planeGeometry, planeMaterial);
    const scale = 0.35 + Math.random() * 0.55;
    mesh.scale.setScalar(scale);
    mesh.userData = {
      radius: 7 + Math.random() * 16,
      speed: (0.12 + Math.random() * 0.22) * (Math.random() < 0.5 ? 1 : -1),
      phase: Math.random() * Math.PI * 2,
      tilt: (Math.random() - 0.5) * 1.1,
      squash: 0.45 + Math.random() * 0.4,
      lift: (Math.random() - 0.5) * 6,
      next: new THREE.Vector3(),
    };
    hero.add(mesh);
    planes.push(mesh);
  }

  const worlds = worldSections.map((section, i) => {
    const planet = makePlanet(6.2, section.dataset.c1, section.dataset.c2, section.dataset.c1);
    if (i % 2 === 0) {
      const ring = makeRing(8.5, 12.5, isSmall() ? 500 : 900, section.dataset.c1, 0.22);
      ring.rotation.x = 0.5;
      ring.rotation.z = i % 4 === 0 ? 0.25 : -0.3;
      planet.add(ring);
    }
    const moon = new THREE.Mesh(
      new THREE.SphereGeometry(0.9, 24, 16),
      new THREE.MeshStandardMaterial({ color: 0xe9e2ff, roughness: 0.8, emissive: new THREE.Color(section.dataset.c1), emissiveIntensity: 0.15 })
    );
    planet.add(moon);
    planet.userData.moon = moon;
    planet.userData.side = section.dataset.side;
    planet.userData.z = worldStart - i * worldSpacing;
    scene.add(planet);
    return planet;
  });

  const portal = new THREE.Group();
  scene.add(portal);
  const torus = new THREE.Mesh(
    new THREE.TorusGeometry(10, 0.55, 32, 180),
    new THREE.MeshStandardMaterial({ color: 0xb08cff, emissive: 0x8b5cf6, emissiveIntensity: 1.6, roughness: 0.3, metalness: 0.6 })
  );
  portal.add(torus);
  const portalGlow = new THREE.Sprite(
    new THREE.SpriteMaterial({
      map: radialTexture([[0, "rgba(190,150,255,0.9)"], [0.35, "rgba(139,92,246,0.35)"], [0.7, "rgba(139,92,246,0.08)"], [1, "rgba(139,92,246,0)"]], 256),
      transparent: true,
      depthWrite: false,
      blending: THREE.AdditiveBlending,
    })
  );
  portalGlow.scale.set(46, 46, 1);
  portal.add(portalGlow);
  const swirl = makeRing(2.5, 9.6, isSmall() ? 900 : 1800, "#c7a8ff", 0.26);
  swirl.rotation.x = Math.PI / 2;
  portal.add(swirl);
  const portalIcon = new THREE.Mesh(sharedIconGeometry, iconMaterials);
  portalIcon.scale.setScalar(2.6);
  portal.add(portalIcon);
  portal.position.z = portalZ;

  let keyframes = [];

  function layout() {
    const small = isSmall();
    const width = window.innerWidth;
    const height = window.innerHeight;
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, small ? 1.5 : 1.75));
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.fov = small ? 62 : 55;
    camera.updateProjectionMatrix();

    if (small) {
      icon.position.set(0, 7.2, 0);
      icon.scale.setScalar(2.6);
      iconHalo.position.set(0, 7.2, -1);
      heroPlanet.position.set(0, -52, -44);
    } else {
      icon.position.set(10.5, 1.6, 0);
      icon.scale.setScalar(3.6);
      iconHalo.position.set(10.5, 1.6, -1);
      heroPlanet.position.set(17, -33, -34);
    }

    worlds.forEach((planet) => {
      const x = small ? 0 : planet.userData.side === "left" ? 13 : -13;
      planet.position.set(x, small ? 13 : 0.5, planet.userData.z);
    });

    const scrollMax = Math.max(1, document.documentElement.scrollHeight - height);
    keyframes = [{ s: 0, x: 0, y: 0, z: 30 }];
    worldSections.forEach((section, i) => {
      const rect = section.getBoundingClientRect();
      const center = rect.top + window.scrollY + rect.height / 2 - height / 2;
      const planet = worlds[i];
      keyframes.push({
        s: Math.min(scrollMax, Math.max(0, center)),
        x: small ? 0 : planet.position.x * 0.32,
        y: small ? 6 : 0,
        z: planet.userData.z + (small ? 30 : 25),
      });
    });
    const portalSection = stops.find((el) => el.dataset.stop === "portal");
    if (portalSection) {
      const rect = portalSection.getBoundingClientRect();
      keyframes.push({ s: Math.min(scrollMax, Math.max(0, rect.top + window.scrollY + rect.height / 2 - height / 2)), x: 0, y: 2, z: portalZ + (small ? 34 : 28) });
    }
    keyframes.push({ s: scrollMax + 1, x: 0, y: 2, z: portalZ + (small ? 30 : 24) });
    keyframes.sort((a, b) => a.s - b.s);
  }

  function sample(y) {
    if (y <= keyframes[0].s) return keyframes[0];
    for (let i = 0; i < keyframes.length - 1; i++) {
      const a = keyframes[i];
      const b = keyframes[i + 1];
      if (y <= b.s) {
        let f = (y - a.s) / Math.max(1, b.s - a.s);
        f = f * f * (3 - 2 * f);
        return { x: a.x + (b.x - a.x) * f, y: a.y + (b.y - a.y) * f, z: a.z + (b.z - a.z) * f };
      }
    }
    return keyframes[keyframes.length - 1];
  }

  const pointer = { x: 0, y: 0, tx: 0, ty: 0 };
  window.addEventListener("pointermove", (e) => {
    pointer.tx = e.clientX / window.innerWidth - 0.5;
    pointer.ty = e.clientY / window.innerHeight - 0.5;
  }, { passive: true });

  const clock = new THREE.Clock();
  let running = true;
  let firstFrame = true;
  const look = new THREE.Vector3();

  function frame() {
    const t = reduceMotion ? 0 : clock.getElapsedTime();
    const target = sample(window.scrollY);
    const ease = firstFrame || reduceMotion ? 1 : 0.075;
    firstFrame = false;
    camera.position.x += (target.x - camera.position.x) * ease;
    camera.position.y += (target.y - camera.position.y) * ease;
    camera.position.z += (target.z - camera.position.z) * ease;
    pointer.x += (pointer.tx - pointer.x) * 0.05;
    pointer.y += (pointer.ty - pointer.y) * 0.05;
    look.set(camera.position.x + pointer.x * 4, camera.position.y - pointer.y * 2.5, camera.position.z - 30);
    camera.lookAt(look);

    stars.rotation.z = t * 0.004;

    heroPlanet.rotation.y = t * 0.05;
    heroPlanet.userData.uniforms.uTime.value = t;
    heroRing.rotation.y = t * 0.03;

    const bob = Math.sin(t * 1.1) * 0.35;
    icon.rotation.y = Math.sin(t * 0.55) * 0.42 + pointer.x * 0.7;
    icon.rotation.x = Math.cos(t * 0.45) * 0.14 + pointer.y * 0.45;
    icon.position.y = (isSmall() ? 7.2 : 1.6) + bob;
    iconHalo.position.y = icon.position.y;

    planes.forEach((mesh) => {
      const d = mesh.userData;
      const angle = d.phase + t * d.speed;
      const ahead = angle + 0.05 * Math.sign(d.speed);
      const cx = icon.position.x;
      const cy = icon.position.y;
      const px = cx + Math.cos(angle) * d.radius;
      const pz = Math.sin(angle) * d.radius * d.squash;
      const py = cy + d.lift + Math.sin(angle) * d.radius * d.tilt * 0.35;
      mesh.position.set(px, py, pz);
      d.next.set(cx + Math.cos(ahead) * d.radius, cy + d.lift + Math.sin(ahead) * d.radius * d.tilt * 0.35, Math.sin(ahead) * d.radius * d.squash);
      mesh.lookAt(d.next);
    });

    worlds.forEach((planet, i) => {
      const distance = camera.position.z - planet.userData.z;
      const reveal = Math.min(1, Math.max(0, (96 - distance) / 30));
      planet.visible = reveal > 0.001 && distance > -20;
      planet.scale.setScalar(0.25 + 0.75 * reveal * reveal * (3 - 2 * reveal));
      planet.userData.body.rotation.y = t * (0.08 + i * 0.01);
      planet.userData.uniforms.uTime.value = t + i * 10;
      const moonAngle = t * (0.35 + i * 0.04) + i;
      planet.userData.moon.position.set(Math.cos(moonAngle) * 10, Math.sin(moonAngle * 0.7) * 2.5, Math.sin(moonAngle) * 10);
    });

    const portalDistance = camera.position.z - portalZ;
    const portalReveal = Math.min(1, Math.max(0, (120 - portalDistance) / 40));
    portal.visible = portalReveal > 0.001;
    portal.scale.setScalar(0.3 + 0.7 * portalReveal);
    torus.rotation.z = t * 0.25;
    swirl.rotation.z = -t * 0.4;
    portalIcon.rotation.y = t * 0.8;
    portalIcon.position.y = Math.sin(t * 1.3) * 0.4;
    nebulae.forEach((sprite, i) => { sprite.material.rotation = t * 0.01 * (i % 2 ? 1 : -1); });

    renderer.render(scene, camera);
    if (running && !reduceMotion) {
      requestAnimationFrame(frame);
    }
  }

  layout();
  window.addEventListener("resize", () => {
    layout();
    if (reduceMotion) frame();
  });
  window.addEventListener("load", () => {
    layout();
    if (reduceMotion) frame();
  });
  if (reduceMotion) {
    window.addEventListener("scroll", () => requestAnimationFrame(frame), { passive: true });
  }
  document.addEventListener("visibilitychange", () => {
    const wasRunning = running;
    running = !document.hidden;
    if (running && !wasRunning && !reduceMotion) {
      clock.getDelta();
      requestAnimationFrame(frame);
    }
  });
  if ("ResizeObserver" in window) {
    new ResizeObserver(() => layout()).observe(document.body);
  }
  requestAnimationFrame(frame);
  root.classList.add("space-ready");
}

if (!canvas || !supportsWebGL()) {
  root.classList.add("no-webgl");
} else {
  start();
}
