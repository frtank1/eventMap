// City people from the Quaternius "Ultimate Modular Men/Women" packs (CC0), built by
// build-people.mjs into characters/people-{men,women}.glb. Each outfit is split into
// Head (face + hair/hat), Body (top), Legs (bottom) and Feet (shoes) on one shared
// skeleton, so any head, top, bottom and shoes combine, and each gets its own colour.
import * as THREE from 'three';
import {GLTFLoader} from '../source/vendor/GLTFLoader.js';
import {clone as cloneSkinned} from '../source/vendor/SkeletonUtils.js';
import {palettes} from './social-state.js';

const KIT_MAX_BYTES=4*1024*1024;
export function readBundledBinary(url,limit=KIT_MAX_BYTES,timeoutMs=120000){
  // XHR also works for WKWebView file:// bundles, where fetch() is refused.
  return new Promise((resolve,reject)=>{const x=new XMLHttpRequest();x.open('GET',url);x.responseType='arraybuffer';x.timeout=timeoutMs;
    x.onload=()=>{const b=x.response;if((x.status===0||x.status===200)&&b instanceof ArrayBuffer&&b.byteLength>12&&b.byteLength<=limit)resolve(b);else reject(new Error(`Character file unavailable: ${url}`));};
    x.onerror=x.ontimeout=()=>reject(new Error(`Character file unavailable: ${url}`));x.send();});
}

export const PEOPLE_FILES=Object.freeze(['people-men.glb','people-women.glb']);
export const OUTFITS=Object.freeze({
  men:Object.freeze([['Casual_2','Повседневный'],['Casual_Hoodie','Худи'],['Suit','Костюм'],['Worker','Рабочий'],['Beach','Пляжный'],['Farmer','Фермер'],['Punk','Панк'],['Adventurer','Турист']]),
  women:Object.freeze([['Casual','Повседневный'],['Formal','Нарядный'],['Suit','Костюм'],['Worker','Рабочий'],['Punk','Панк'],['Adventurer','Турист']])});
export const SLOTS=Object.freeze([['head','Head','Голова'],['top','Body','Верх'],['bottom','Legs','Низ'],['feet','Feet','Обувь']]);
export const PEOPLE_CLIPS=Object.freeze({idle:'Idle_Neutral',walk:'Walk',run:'Run',wave:'Wave'});
export const PEOPLE_HEIGHT=Object.freeze([1.76,1.66]);
const GENDERS=['men','women'];
export function outfitIndex(look,key,gender){const n=OUTFITS[GENDERS[gender]].length;const v=Number.isInteger(look[key])?look[key]:0;return ((v%n)+n)%n;}

let kitPromise=null;
export function loadPeopleKit(base='characters/'){
  kitPromise||=(async()=>{
    const loader=new GLTFLoader();
    const packs=await Promise.all(PEOPLE_FILES.map(async f=>loader.parseAsync(await readBundledBinary(base+f),'')));
    const genders=packs.map((g,i)=>{
      const scene=g.scene,parts=new Map();scene.updateMatrixWorld(true);
      scene.traverse(o=>{if(o.name.includes('|')&&!o.parent?.name.includes('|'))parts.set(o.name,o);});
      // The garment colour is the part's largest non-skin, non-hair material.
      const main=new Map();
      for(const [name,obj]of parts){const area=new Map();obj.traverse(m=>{if(!m.isMesh)return;const n=m.material.name,c=m.geometry.index?.count||0;if(!/^Skin|^Hair/.test(n))area.set(n,(area.get(n)||0)+c);});
        main.set(name,[...area].sort((a,b)=>b[1]-a[1])[0]?.[0]||null);}
      const box=new THREE.Box3(),first=OUTFITS[GENDERS[i]][0][0];
      for(const [name,obj]of parts)if(name.startsWith(first+'|')&&!/Backpack/.test(name))box.expandByObject(obj,true);
      const height=box.max.y-box.min.y;
      if(!(height>0))throw new Error('People pack has no body');
      return {scene,parts,main,clips:new Map(g.animations.map(c=>[c.name,c])),scale:PEOPLE_HEIGHT[i]/height,floor:box.min.y};
    });
    for(const g of genders)for(const c of Object.values(PEOPLE_CLIPS))if(!g.clips.has(c))throw new Error(`People clip missing: ${c}`);
    const canvas=document.createElement('canvas');canvas.width=canvas.height=64;const ctx=canvas.getContext('2d'),grad=ctx.createRadialGradient(32,32,2,32,32,31);
    grad.addColorStop(0,'rgba(20,24,28,.42)');grad.addColorStop(1,'rgba(20,24,28,0)');ctx.fillStyle=grad;ctx.fillRect(0,0,64,64);
    const shadow={geometry:new THREE.PlaneGeometry(.9,.9).rotateX(-Math.PI/2),material:new THREE.MeshBasicMaterial({map:new THREE.CanvasTexture(canvas),transparent:true,depthWrite:false})};
    return {genders,shadow};
  })();
  kitPromise.catch(()=>{kitPromise=null;});
  return kitPromise;
}

// One person. Same surface as createAvatar(): root, apply, update, setDetail, stats, destroy.
export function createPerson(kit,look){
  const root=new THREE.Group();root.name='City person · Quaternius CC0';
  const blob=new THREE.Mesh(kit.shadow.geometry,kit.shadow.material);blob.position.y=.02;blob.renderOrder=-1;root.add(blob);
  let model=null,mixer=null,actions={},state='idle',gender=-1,lastTime=null,disposed=false,parts=new Map(),materials=new Set();
  function mount(index){
    if(model){model.removeFromParent();mixer.stopAllAction();for(const m of materials)m.dispose();materials.clear();}
    const g=kit.genders[index];gender=index;model=cloneSkinned(g.scene);model.scale.setScalar(g.scale);model.position.y=-g.floor*g.scale;root.add(model);
    // Own materials per character; garment materials per part so a top and trousers
    // that share "Black" in the pack can still get different colours.
    const shared=new Map();parts=new Map();
    model.traverse(o=>{if(o.name.includes('|')&&!o.parent?.name.includes('|'))parts.set(o.name,o);});
    for(const [name,obj]of parts){const own=new Map();
      obj.traverse(m=>{if(!m.isMesh)return;m.frustumCulled=false;m.castShadow=false;m.receiveShadow=true;
        const n=m.material.name,key=/^Skin|^Hair/.test(n)?n:`${name}/${n}`,cache=/^Skin|^Hair/.test(n)?shared:own;
        if(!cache.has(key)){const c=m.material.clone();c.userData.base=c.color.clone();cache.set(key,c);materials.add(c);}m.material=cache.get(key);});
      obj.userData.main=g.main.get(name);}
    mixer=new THREE.AnimationMixer(model);actions={};
    for(const [k,clip]of Object.entries(PEOPLE_CLIPS))actions[k]=mixer.clipAction(g.clips.get(clip));
    actions.walk.timeScale=1.05;actions[state].play();mixer.update(0);
  }
  function paint(obj,hex){obj.traverse(m=>{if(m.isMesh&&m.material.name===obj.userData.main&&hex!==undefined)m.material.color.setHex(hex);});}
  function apply(v){
    if(disposed)return;const want=v.body===1?1:0;if(want!==gender)mount(want);
    const list=OUTFITS[GENDERS[gender]],colours={top:palettes.coat[v.coat],bottom:palettes.pants[v.pants],feet:palettes.shoes[v.shoes]};
    for(const o of parts.values())o.visible=false;
    for(const [key,slot]of SLOTS){const part=parts.get(`${list[outfitIndex(v,key,gender)][0]}|${slot}`);if(!part)continue;part.visible=true;if(colours[key]!==undefined)paint(part,colours[key]);}
    const pack=parts.get(`${list.find(([o])=>o==='Adventurer')[0]}|Backpack`);if(pack)pack.visible=!!v.bag;
    for(const m of materials){if(/^Skin/.test(m.name))m.color.setHex(palettes.skin[v.skin]??palettes.skin[1]);if(/^Hair/.test(m.name))m.color.setHex(palettes.hair[v.hair]??palettes.hair[0]);}
    const height=[.95,1,1.05][v.height]??1,build=[.92,1,1.1][v.build]??1;root.scale.set(height*build,height,height*build);
  }
  function play(next){if(next===state||!actions[next])return;const from=actions[state],to=actions[next];to.reset().play();from.crossFadeTo(to,.25,false);state=next;}
  function update(time,moving=false,reduced=false){
    if(disposed||!mixer)return;if(state==='idle'||state==='walk')play(moving?'walk':'idle');
    const dt=lastTime===null?0:Math.min(.1,Math.max(0,time-lastTime));lastTime=time;mixer.update(reduced?0:dt);
  }
  function stats(){let draws=0,triangles=0;root.traverseVisible(o=>{if(o.isMesh){draws++;triangles+=(o.geometry.index?.count||o.geometry.attributes.position.count)/3;}});return {draws,triangles,detail:'people'};}
  function destroy(){if(disposed)return;disposed=true;mixer?.stopAllAction();root.removeFromParent();for(const m of materials)m.dispose();}
  mount(look.body===1?1:0);apply(look);
  return {root,apply,update,setState:play,setDetail(){},stats,destroy,get state(){return state;}};
}
