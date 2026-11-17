import test from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import {createWeather,WEATHER_LIMITS} from '../runtime/weather.js';

test('weather is two bounded draws shown only near the street',()=>{
  const scene=new THREE.Scene(),w=createWeather(scene),camera=new THREE.PerspectiveCamera(),target=new THREE.Vector3();
  assert.equal(scene.children.length,2);
  assert.equal(scene.children[0].geometry.getAttribute('position').count,WEATHER_LIMITS.rain*2);
  assert.equal(scene.children[1].geometry.getAttribute('position').count,WEATHER_LIMITS.snow);
  w.set('rain');camera.position.set(0,80,120);w.update(1,camera,target);
  assert.deepEqual(w.stats(),{weather:'rain',weatherDraws:1});
  camera.position.set(0,2000,2000);w.update(2,camera,target);
  assert.equal(w.stats().weatherDraws,1,'scaled with the camera, still visible over the whole city');
  camera.position.set(0,30000,30000);w.update(3,camera,target);
  assert.equal(w.stats().weatherDraws,0,'hidden beyond the city');
  w.set('cloudy');assert.equal(w.kind,'cloudy');w.update(4,camera,target);assert.equal(w.stats().weatherDraws,0);
  w.set('hail');assert.equal(w.kind,'clear');
  w.destroy();assert.equal(scene.children.length,0);
});
