// Debug-only Android fixtures, from exactly the same source as browser QA.
// Release assets and the backend never receive these files.
import {mkdir,writeFile,copyFile} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {festivalEvents} from './test/festival-fixtures.js';
const here=path.dirname(fileURLToPath(import.meta.url));
export async function stageNativeFestival(root=path.resolve(here,'../androidApp/src/debug/assets')){
  const events=festivalEvents({now:0});
  // Native preview owns dates/lifetime, avoiding a stale build expiring fixtures.
  for(const event of events){delete event.startAt;delete event.endAt;}
  await mkdir(path.join(root,'eventmap-festival'),{recursive:true});
  await writeFile(path.join(root,'eventmap-festival/events.json'),JSON.stringify(events));
  const coverRoot=path.join(root,'composeResources/eventmap.common.feature.map.ui.generated.resources/files/game-city/event-covers');
  await mkdir(coverRoot,{recursive:true});
  for(const name of new Set(events.map(e=>path.basename(e.coverImage))))await copyFile(path.join(here,'test/event-covers',name),path.join(coverRoot,name));
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url))await stageNativeFestival();
