// Fictional locations and activities for visual stress testing, NOT a listing
// of real advertised events. This module is served only by the loopback preview.
const programs=[
  ['Музыка','music',['OPEN AIR · город звучит','Джаз под звёздами','Электронная ночь','Акустика во дворе','Танцы до заката']],
  ['Спорт','sport',['Футбол · собираем команду','Стритбол 3 × 3','Волейбол после работы','Открытая тренировка','Матч под прожекторами']],
  ['Маркет','market',['Ночной маркет','Еда со всего города','Кофе и винил','Цветочная ярмарка','Вкусный двор']],
  ['Маршруты','route',['Бежим вдоль Есиля','Велозаезд на закате','Марафон огней','Пешком по городу','Роллер-прогулка']],
  ['Находки','discovery',['Смотрим на звёзды','Телескоп на площади','Наблюдаем закат','Уличный художник','Город в объективе']],
  ['Квесты','quest',['Найди светящийся символ','Городской квест','Охота за деталями','Тайна набережной','Ночной маршрут загадок']],
  ['Встречи','community',['Настолки на свежем воздухе','Книжный обмен','Пикник соседей','Кино под открытым небом','Рисуем город вместе']],
];
// Local metres from the map origin. Synthetic demonstration clusters, not
// verified venue coordinates, entrances or permissions to hold an event.
const hubs=[[0,0,170],[-420,180,240],[500,40,320],[-1750,0,700],[-1960,2245,400],[-955,4400,750],
  [1700,-1500,800],[3400,-300,900],[-2100,-3200,1000],[0,-4100,1000],[3300,-4000,1000],
  [-3900,-2000,1100],[6300,-3500,1200],[-1800,-6700,1100],[2700,-7600,1400]];
export function festivalEvents({now=Date.now(),count=150}={}){
  const size=Math.min(150,Math.max(0,Number.isFinite(count)?Math.floor(count):150));
  return Array.from({length:size},(_,i)=>{
    const h=Math.floor(i/10),j=i%10,[cx,cz,radius]=hubs[h],angle=j*2.399963+h*.73;
    const r=j===0?0:radius*Math.sqrt(j/9),x=cx+Math.cos(angle)*r,z=cz+Math.sin(angle)*r;
    const [category,cover,titles]=programs[(i+h)%programs.length],live=i%5!==4;
    return {id:`festival-demo-${String(i+1).padStart(3,'0')}`,title:titles[(h+j)%titles.length],category,
      lat:51.12822-z/111320,lng:71.43056+x/(111320*Math.cos(51.12822*Math.PI/180)),
      kind:category==='Находки'?'live_signal':i%5===4?'gathering':'planned',sourceType:category==='Находки'?'witness':'initiator',
      status:live?'live':'soon',visibility:'public',startAt:new Date(now+(live?-1800000:3600000)).toISOString(),endAt:new Date(now+86400000).toISOString(),
      goingCount:8+(i*37)%280,confirmationCount:3+i%6,capacity:i%5===4?350:null,
      coverImage:`event-covers/${cover}.svg`,demo:true};
  });
}
