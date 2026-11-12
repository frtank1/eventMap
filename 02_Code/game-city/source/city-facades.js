/**
 * Procedural city masonry for THREE r170 MeshStandardMaterial (WebGLRenderer).
 * No imports, textures, extra geometry, lights, time uniform, or frame callback.
 *
 * Apply only to cream/cyan/sand/mint, once per shared material. Call setNight
 * alongside the owner's palette update and setTheme on each season change.
 * Each controller owns its uniforms; equivalent materials share a program.
 * Input: static merged meshes, Y-up world metres, ground at y = 0.55 (city-stream).
 * Floor heights are world-aligned; merged meshes have no per-building metadata
 * for true roof-edge cornices, so the detailing uses floor/ground-floor bands.
 * InstancedMesh/BatchedMesh and sheared transforms are outside this contract.
 */

const decorated = new WeakMap();
const PROGRAM_KEY = 'eventmap-city-facades-r170-v1';

const VARYINGS = /* glsl */ `
varying vec3 vCityFacadeWorldPosition;
varying vec3 vCityFacadeWorldNormal;
`;

const WORLD_VERTEX = /* glsl */ `
// Do not use normalMatrix: it transforms normals into CAMERA space.
// Remove scale before applying the model basis (rotation + nonuniform scale).
mat3 cityFacadeModelBasis = mat3( modelMatrix );
vec3 cityFacadeScaleSquared = vec3(
  dot( cityFacadeModelBasis[0], cityFacadeModelBasis[0] ),
  dot( cityFacadeModelBasis[1], cityFacadeModelBasis[1] ),
  dot( cityFacadeModelBasis[2], cityFacadeModelBasis[2] )
);
vCityFacadeWorldNormal = normalize( cityFacadeModelBasis *
  ( objectNormal / max( cityFacadeScaleSquared, vec3( 0.000001 ) ) ) );
// worldPosition in Three's chunk is conditional on shadows/environment maps;
// this independent value also works when all such renderer features are off.
vCityFacadeWorldPosition = ( modelMatrix * vec4( transformed, 1.0 ) ).xyz;
`;

const FRAGMENT_HELPERS = /* glsl */ `
uniform float uCityFacadeNight;
uniform float uCityFacadeSnow;

// Stateless, sine-free hash; inputs identify a window, never a frame or tile.
float cityFacadeHash( vec3 cell ) {
  vec3 p = fract( cell * vec3( 0.1031, 0.1030, 0.0973 ) );
  p += dot( p, p.yxz + 33.33 );
  return fract( ( p.x + p.y ) * p.z );
}

float cityFacadeBand( float p, float center, float halfWidth, float footprint ) {
  float aa = max( footprint * 0.5, 0.0001 );
  return 1.0 - smoothstep( halfWidth - aa, halfWidth + aa, abs( p - center ) );
}

float cityFacadeRect( vec2 p, vec2 center, vec2 halfSize, vec2 footprint ) {
  vec2 aa = max( footprint * 0.5, vec2( 0.0001 ) );
  vec2 coverage = vec2( 1.0 ) - smoothstep( halfSize - aa, halfSize + aa, abs( p - center ) );
  return coverage.x * coverage.y;
}
`;

// Insert after emissivemap_fragment: diffuseColor, roughnessFactor and
// totalEmissiveRadiance exist, but physical lighting has not consumed them yet.
const FRAGMENT_SURFACE = /* glsl */ `
vec3 cityFacadeN = normalize( vCityFacadeWorldNormal );
float cityFacadeWall = 1.0 - smoothstep( 0.15, 0.55, abs( cityFacadeN.y ) );
float cityFacadeRoof = smoothstep( 0.65, 0.92, cityFacadeN.y );

// A face-aligned world projection keeps window widths consistent on oblique
// walls. The safe denominator also gives finite values on horizontal roofs.
vec2 cityFacadeOut = cityFacadeN.xz / max( length( cityFacadeN.xz ), 0.0001 );
vec2 cityFacadeTangent = vec2( cityFacadeOut.y, -cityFacadeOut.x );
float cityFacadeU = dot( vCityFacadeWorldPosition.xz, cityFacadeTangent );
float cityFacadeY = vCityFacadeWorldPosition.y - 0.55;
vec2 cityFacadeGrid = vec2( cityFacadeU / 3.8, ( cityFacadeY - 4.4 ) / 3.25 );
// Derivatives MUST precede fract/floor and must run outside divergent branches.
vec2 cityFacadeFootprint = fwidth( cityFacadeGrid );
float cityFacadeHeightFootprint = max( fwidth( cityFacadeY ), 0.0001 );
float cityFacadeDistanceFade = 1.0 - smoothstep( 850.0, 1800.0,
  distance( cameraPosition, vCityFacadeWorldPosition ) );
float cityFacadePixelFade = 1.0 - smoothstep( 0.18, 0.5,
  max( cityFacadeFootprint.x, cityFacadeFootprint.y ) );
float cityFacadeDetail = cityFacadeWall * cityFacadeDistanceFade * cityFacadePixelFade;
vec2 cityFacadePhase = fract( cityFacadeGrid );

// Plane and orientation distinguish parallel/opposite walls. The plane is
// constant across a planar face; floor(grid) is constant across each pane.
float cityFacadePlane = floor( dot( vCityFacadeWorldPosition.xz, cityFacadeOut ) * 0.25 + 0.5 );
vec2 cityFacadeDirection = floor( ( cityFacadeOut + 1.0 ) * 16.0 + 0.5 );
float cityFacadeSeed = cityFacadePlane + dot( cityFacadeDirection, vec2( 113.0, 4177.0 ) );
vec3 cityFacadeCell = vec3( floor( cityFacadeGrid ), cityFacadeSeed );
float cityFacadeRandom = cityFacadeHash( cityFacadeCell );
float cityFacadeUpper = smoothstep( 4.35, 4.65, cityFacadeY );
float cityFacadeOuter = cityFacadeRect( cityFacadePhase, vec2( 0.5 ),
  vec2( 0.31, 0.32 ), cityFacadeFootprint ) * cityFacadeUpper;
float cityFacadePane = cityFacadeRect( cityFacadePhase, vec2( 0.5 ),
  vec2( 0.255, 0.265 ), cityFacadeFootprint ) * cityFacadeUpper;
float cityFacadeFrame = max( cityFacadeOuter - cityFacadePane, 0.0 );
float cityFacadeMullion = cityFacadeBand( cityFacadePhase.x, 0.5, 0.012,
  cityFacadeFootprint.x ) * cityFacadePane;
float cityFacadeGlass = max( cityFacadePane - cityFacadeMullion * 0.65, 0.0 );

float cityFacadeFloorShadow = cityFacadeBand( cityFacadePhase.y, 0.035, 0.025,
  cityFacadeFootprint.y ) * cityFacadeUpper;
float cityFacadeFloorLip = cityFacadeBand( cityFacadePhase.y, 0.10, 0.025,
  cityFacadeFootprint.y ) * cityFacadeUpper;

// A darker plinth, broad shop glazing and a two-tone ground-floor cornice.
vec2 cityFacadeShopUV = vec2( cityFacadeU / 5.6, cityFacadeY );
vec2 cityFacadeShopFootprint = fwidth( cityFacadeShopUV );
vec2 cityFacadeShopPhase = vec2( fract( cityFacadeShopUV.x ), cityFacadeShopUV.y );
float cityFacadeShopOuter = cityFacadeRect( cityFacadeShopPhase, vec2( 0.5, 2.15 ),
  vec2( 0.405, 1.24 ), cityFacadeShopFootprint );
float cityFacadeShopPane = cityFacadeRect( cityFacadeShopPhase, vec2( 0.5, 2.15 ),
  vec2( 0.36, 1.08 ), cityFacadeShopFootprint );
float cityFacadeShopMullion = cityFacadeBand( cityFacadeShopPhase.x, 0.5, 0.018,
  cityFacadeShopFootprint.x ) * cityFacadeShopPane;
float cityFacadeShopGlass = max( cityFacadeShopPane - cityFacadeShopMullion * 0.8, 0.0 );
float cityFacadeGround = 1.0 - smoothstep( 3.75, 4.15, cityFacadeY );
float cityFacadeCorniceShade = cityFacadeBand( cityFacadeY, 4.02, 0.12, cityFacadeHeightFootprint );
float cityFacadeCorniceLip = cityFacadeBand( cityFacadeY, 4.27, 0.08, cityFacadeHeightFootprint );

// All masonry modulation is relative to the externally controlled base color.
vec3 cityFacadeBase = diffuseColor.rgb;
diffuseColor.rgb *= 1.0 - cityFacadeDetail * ( 0.10 * cityFacadeGround +
  0.17 * cityFacadeFloorShadow + 0.20 * cityFacadeCorniceShade );
diffuseColor.rgb += cityFacadeBase * cityFacadeDetail * ( 0.09 * cityFacadeFloorLip +
  0.13 * cityFacadeCorniceLip + 0.09 * cityFacadeFrame +
  0.08 * max( cityFacadeShopOuter - cityFacadeShopPane, 0.0 ) );
float cityFacadeGlazing = clamp( cityFacadeGlass + cityFacadeShopGlass, 0.0, 1.0 ) * cityFacadeDetail;
vec3 cityFacadeGlassTint = mix( vec3( 0.52, 0.69, 0.75 ), vec3( 0.27, 0.40, 0.48 ), uCityFacadeNight );
diffuseColor.rgb = mix( diffuseColor.rgb, cityFacadeBase * cityFacadeGlassTint,
  cityFacadeGlazing * mix( 0.75, 0.94, cityFacadeRandom ) );
roughnessFactor = mix( roughnessFactor, 0.46, cityFacadeGlazing * 0.8 );

float cityFacadeLit = step( 0.38, cityFacadeRandom );
float cityFacadeCool = step( 0.80, cityFacadeHash( cityFacadeCell + vec3( 17.0, 53.0, 11.0 ) ) );
vec3 cityFacadeLamp = mix( vec3( 1.0, 0.61, 0.27 ), vec3( 0.46, 0.72, 1.0 ), cityFacadeCool );
float cityFacadeBrightness = mix( 0.48, 1.15,
  cityFacadeHash( cityFacadeCell + vec3( 31.0, 7.0, 59.0 ) ) );
float cityFacadeShopRandom = cityFacadeHash( vec3( floor( cityFacadeShopUV.x ), -3.0, cityFacadeSeed ) );
vec3 cityFacadeShopLamp = mix( vec3( 1.0, 0.72, 0.40 ), vec3( 0.61, 0.80, 1.0 ),
  step( 0.85, cityFacadeShopRandom ) );
totalEmissiveRadiance += uCityFacadeNight * cityFacadeDetail * (
  cityFacadeGlass * cityFacadeLit * cityFacadeLamp * cityFacadeBrightness +
  cityFacadeShopGlass * step( 0.28, cityFacadeShopRandom ) * cityFacadeShopLamp * 0.58 );

// Only UP-facing roofs receive snow; undersides and walls remain untouched.
// Snow=0 is an exact no-op, including roof roughness. Winter/newyear are equal.
float cityFacadeSnowMask = uCityFacadeSnow * cityFacadeRoof;
vec3 cityFacadeSnowColor = mix( vec3( 0.83, 0.90, 0.95 ), vec3( 0.96, 0.98, 1.0 ), cityFacadeRoof );
diffuseColor.rgb = mix( diffuseColor.rgb, cityFacadeSnowColor, cityFacadeSnowMask );
roughnessFactor = mix( roughnessFactor, 0.96, cityFacadeSnowMask );
`;

function insertAfter(source, marker, addition) {
  const at = source.indexOf(marker);
  if (at < 0 || source.indexOf(marker, at + marker.length) >= 0) {
    throw new Error(`city-facades: expected one ${marker}; requires THREE r170 standard shader chunks`);
  }
  return source.replace(marker, `${marker}\n${addition}`);
}

/**
 * @param {import('three').MeshStandardMaterial} material Caller-owned material.
 * @returns {{setNight: (night: boolean) => void,
 *   setTheme: (theme: 'default'|'winter'|'newyear') => void, dispose: () => void}}
 * dispose() detaches only this decoration; it does NOT dispose the material.
 * A prior hook is called first. Its cache signature is captured on decoration;
 * if that hook's shader topology later changes, dispose and decorate again.
 */
export function decorateCityMaterial(material) {
  if (!material?.isMeshStandardMaterial) {
    throw new TypeError('decorateCityMaterial expects a THREE.MeshStandardMaterial');
  }
  if (decorated.has(material)) return decorated.get(material);

  const previousHook = material.onBeforeCompile;
  const previousKey = String(material.customProgramCacheKey());
  const hookDescriptor = Object.getOwnPropertyDescriptor(material, 'onBeforeCompile');
  const keyDescriptor = Object.getOwnPropertyDescriptor(material, 'customProgramCacheKey');
  const uniforms = {uCityFacadeNight: {value: 0}, uCityFacadeSnow: {value: 0}};
  let disposed = false;

  function onBeforeCompile(shader, renderer) {
    previousHook.call(this, shader, renderer);
    if (disposed) return;
    let vertex = insertAfter(shader.vertexShader, '#include <common>', VARYINGS);
    vertex = insertAfter(vertex, '#include <worldpos_vertex>', WORLD_VERTEX);
    let fragment = insertAfter(shader.fragmentShader, '#include <common>', VARYINGS + FRAGMENT_HELPERS);
    fragment = insertAfter(fragment, '#include <emissivemap_fragment>', FRAGMENT_SURFACE);
    shader.vertexShader = vertex;
    shader.fragmentShader = fragment;
    Object.assign(shader.uniforms, uniforms);
  }

  function customProgramCacheKey() {
    return disposed ? previousKey : `${previousKey}|${PROGRAM_KEY}`;
  }

  const controller = {
    setNight(night) {
      if (!disposed) uniforms.uCityFacadeNight.value = night ? 1 : 0;
    },
    setTheme(theme) {
      if (disposed) return;
      if (theme !== 'default' && theme !== 'winter' && theme !== 'newyear') {
        throw new RangeError('city-facades theme must be default, winter, or newyear');
      }
      uniforms.uCityFacadeSnow.value = theme === 'default' ? 0 : 1;
    },
    dispose() {
      if (disposed) return;
      disposed = true;
      for (const [name, hook, descriptor] of [
        ['onBeforeCompile', onBeforeCompile, hookDescriptor],
        ['customProgramCacheKey', customProgramCacheKey, keyDescriptor],
      ]) {
        // Do not overwrite a hook subsequently installed by another owner.
        if (material[name] !== hook) continue;
        if (descriptor) Object.defineProperty(material, name, descriptor);
        else delete material[name];
      }
      decorated.delete(material);
      material.needsUpdate = true;
    },
  };

  material.onBeforeCompile = onBeforeCompile;
  material.customProgramCacheKey = customProgramCacheKey;
  material.needsUpdate = true;
  decorated.set(material, controller);
  return controller;
}
