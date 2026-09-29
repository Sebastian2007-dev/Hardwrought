import fs from "node:fs";
import path from "node:path";

const sourcePath = process.argv[2];
if (!sourcePath) {
  throw new Error("Usage: node tools/import_water_wheel.mjs <wasserrad.bbmodel>");
}

const root = process.cwd();
const model = JSON.parse(fs.readFileSync(sourcePath, "utf8"));
if (model.meta?.model_format !== "free" || !Array.isArray(model.elements)) {
  throw new Error("Expected a generic Blockbench cube model");
}

// The kinetic implementation occupies the eight cells around its hub. Scale the
// authored wheel to the same three-block diameter while retaining its axle width.
const radialScale = 48 / 28.9;
const textureNames = [
  "spruce_planks",
  "stripped_spruce_log",
  "stripped_spruce_log_top",
  "iron_block",
];
const textureReferences = Object.fromEntries(
  textureNames.map((name, index) => [String(index), `hardwrought:block/water_wheel/${name}`]),
);
textureReferences.particle = "#0";

const round = (value) => {
  const result = Math.round(value * 100000) / 100000;
  return Object.is(result, -0) ? 0 : result;
};

// Generic model: X/Y is the wheel plane, Z is the axle. The renderer expects
// the wheel flat in X/Z, centred at [8, 8, 8], with Y as its axle.
const transformPoint = ([x, y, z]) => [
  round(8 + x * radialScale),
  round(8 + z),
  round(8 + (y - 10) * radialScale),
];

const sourceToTargetFace = {
  north: "down",
  south: "up",
  east: "east",
  west: "west",
  up: "south",
  down: "north",
};

const directionVectors = {
  east: [1, 0],
  west: [-1, 0],
  south: [0, 1],
  north: [0, -1],
};

const vectorDirections = new Map([
  ["1,0", "east"],
  ["-1,0", "west"],
  ["0,1", "south"],
  ["0,-1", "north"],
]);

function rotateDirection(direction, degrees) {
  if (direction === "up" || direction === "down") return direction;
  const [x, z] = directionVectors[direction];
  const turns = ((Math.round(degrees / 90) % 4) + 4) % 4;
  let rx = x;
  let rz = z;
  for (let turn = 0; turn < turns; turn++) [rx, rz] = [rz, -rx];
  return vectorDirections.get(`${rx},${rz}`);
}

function rotateBounds(from, to, origin, degrees) {
  const radians = degrees * Math.PI / 180;
  const cosine = Math.round(Math.cos(radians));
  const sine = Math.round(Math.sin(radians));
  const corners = [];
  for (const x of [from[0], to[0]]) {
    for (const z of [from[2], to[2]]) {
      const dx = x - origin[0];
      const dz = z - origin[2];
      corners.push([
        origin[0] + cosine * dx + sine * dz,
        origin[2] - sine * dx + cosine * dz,
      ]);
    }
  }
  return [
    [round(Math.min(...corners.map(([x]) => x))), from[1], round(Math.min(...corners.map(([, z]) => z)))],
    [round(Math.max(...corners.map(([x]) => x))), to[1], round(Math.max(...corners.map(([, z]) => z)))],
  ];
}

function transformFaces(sourceFaces, quarterTurn) {
  const faces = {};
  for (const [sourceDirection, face] of Object.entries(sourceFaces)) {
    if (!face || face.texture === null) continue;
    const targetDirection = rotateDirection(sourceToTargetFace[sourceDirection], quarterTurn);
    const transformed = { uv: face.uv.map(round), texture: `#${face.texture}` };
    if (face.rotation) transformed.rotation = face.rotation;
    faces[targetDirection] = transformed;
  }
  return faces;
}

const elements = model.elements.filter((element) => element.type === "cube" && element.export !== false).map((element) => {
  let from = transformPoint(element.from);
  let to = transformPoint(element.to);
  from = from.map((value, axis) => Math.min(value, to[axis]));
  to = to.map((value, axis) => Math.max(value, transformPoint(element.from)[axis]));
  const origin = transformPoint(element.origin ?? [0, 10, 0]);
  const sourceAngle = element.rotation?.[2] ?? 0;
  if ((element.rotation?.[0] ?? 0) !== 0 || (element.rotation?.[1] ?? 0) !== 0) {
    throw new Error(`Unsupported multi-axis rotation on ${element.name}`);
  }

  // Java block models accept rotations through 45 degrees. Fold complete
  // quarter turns into the cube bounds and leave only the small remainder.
  const targetAngle = -sourceAngle;
  const quarterTurn = Math.floor((targetAngle + 45) / 90) * 90;
  const angle = round(targetAngle - quarterTurn);
  if (quarterTurn !== 0) [from, to] = rotateBounds(from, to, origin, quarterTurn);

  const result = {
    name: element.name,
    from,
    to,
    faces: transformFaces(element.faces, quarterTurn),
  };
  if (angle !== 0) result.rotation = { origin, axis: "y", angle };
  return result;
});

if (elements.length !== model.elements.length) {
  throw new Error(`Only ${elements.length} of ${model.elements.length} elements were exportable cubes`);
}

const output = {
  parent: "minecraft:block/block",
  ambientocclusion: true,
  textures: textureReferences,
  elements,
  display: {
    gui: { rotation: [30, 45, 0], translation: [0, 0, 0], scale: [0.25, 0.25, 0.25] },
    ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.2, 0.2, 0.2] },
    fixed: { rotation: [90, 0, 0], translation: [0, 0, 0], scale: [0.25, 0.25, 0.25] },
    thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.15, 0.15, 0.15] },
    firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.175, 0.175, 0.175] },
  },
};

const outputPath = path.join(root, "src/main/resources/assets/hardwrought/models/block/water_wheel_rim.json");
fs.writeFileSync(outputPath, `${JSON.stringify(output, null, 2)}\n`);

const textureDirectory = path.join(root, "src/main/resources/assets/hardwrought/textures/block/water_wheel");
fs.mkdirSync(textureDirectory, { recursive: true });
for (let index = 0; index < textureNames.length; index++) {
  const source = model.textures?.[index]?.source;
  const match = /^data:image\/png;base64,(.+)$/.exec(source ?? "");
  if (!match) throw new Error(`Texture ${index} is not an embedded PNG`);
  fs.writeFileSync(path.join(textureDirectory, `${textureNames[index]}.png`), Buffer.from(match[1], "base64"));
}

const artDirectory = path.join(root, "art_source/machinery/water_wheel");
fs.mkdirSync(artDirectory, { recursive: true });
fs.copyFileSync(sourcePath, path.join(artDirectory, "water_wheel.bbmodel"));
const previewPath = path.join(path.dirname(sourcePath), "wasserrad_preview.png");
if (fs.existsSync(previewPath)) fs.copyFileSync(previewPath, path.join(artDirectory, "water_wheel_preview.png"));

console.log(`Imported ${elements.length} cubes from ${path.basename(sourcePath)}`);
console.log(`Radial scale: ${radialScale.toFixed(5)} (48 model units / 3 blocks)`);
console.log(outputPath);
