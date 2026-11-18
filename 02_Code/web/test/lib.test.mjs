import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizeEvents,filterEvents,formatWhen,rangeFor,toCityEvent,isNight,themeFor,shareUrl,mapUrl,nearby} from '../lib.js';

const ok={id:'a1',title:'Концерт',category:'music',lat:51.128,lng:71.43,startAt:'2026-12-05T19:00:00+05:00',endAt:'2026-12-05T22:00:00+05:00'};
test('events file is validated strictly and never guessed',()=>{
  const out=normalizeEvents([ok,{...ok},{...ok,id:'b',lat:'51.1'},{...ok,id:'c',lat:43.2},{...ok,id:'d',startAt:'soon'},{...ok,id:'e',endAt:'2026-12-05T18:00:00+05:00'},
    {...ok,id:'f f'},{...ok,id:'g',title:''},{...ok,id:'h',category:'unknown',url:'javascript:alert(1)',cover:'../secret.png',endAt:undefined},null,'x']);
  assert.deepEqual(out.map(e=>e.id),['a1','h']);
  assert.equal(out[1].category,'community');assert.equal(out[1].url,'');assert.equal(out[1].cover,'');
  assert.equal(out[1].hasEnd,false);assert.equal(out[1].endMs-out[1].startMs,3*3600*1000);
  assert.deepEqual(normalizeEvents({}),[]);
});
test('filters use Astana days, not the visitor time zone',()=>{
  const events=normalizeEvents([ok,{...ok,id:'late',startAt:'2026-12-05T23:30:00+05:00',endAt:'2026-12-06T01:00:00+05:00'},
    {...ok,id:'sun',startAt:'2026-12-06T12:00:00+05:00',endAt:'2026-12-06T14:00:00+05:00'},{...ok,id:'mon',startAt:'2026-12-07T12:00:00+05:00',endAt:'2026-12-07T13:00:00+05:00'}]);
  const friEvening=Date.parse('2026-12-04T21:00:00+05:00'),satNoon=Date.parse('2026-12-05T12:00:00+05:00');
  assert.deepEqual(filterEvents(events,'today',friEvening).map(e=>e.id),[]);
  assert.deepEqual(filterEvents(events,'tomorrow',friEvening).map(e=>e.id),['a1','late']);
  assert.deepEqual(filterEvents(events,'weekend',friEvening).map(e=>e.id),['a1','late','sun']);
  assert.deepEqual(filterEvents(events,'today',satNoon).map(e=>e.id),['a1','late']);
  // 20:30 UTC on Friday is already 01:30 Saturday in Astana.
  assert.deepEqual(rangeFor('today',Date.parse('2026-12-04T20:30:00Z')),[Date.parse('2026-12-05T00:00:00+05:00'),Date.parse('2026-12-06T00:00:00+05:00')]);
  assert.deepEqual(filterEvents(events,'all',Date.parse('2026-12-06T13:00:00+05:00')).map(e=>e.id),['sun','mon']);
  assert.deepEqual(rangeFor('weekend',Date.parse('2026-12-06T10:00:00+05:00')),[Date.parse('2026-12-06T00:00:00+05:00'),Date.parse('2026-12-07T00:00:00+05:00')]);
});
test('time labels, engine payload and links',()=>{
  const [e]=normalizeEvents([ok]);
  assert.equal(formatWhen(e,Date.parse('2026-12-05T10:00:00+05:00')),'Сегодня · 19:00');
  assert.equal(formatWhen(e,Date.parse('2026-12-04T23:00:00+05:00')),'Завтра · 19:00');
  assert.equal(formatWhen(e,Date.parse('2026-11-20T10:00:00+05:00')),'5 декабря · 19:00');
  assert.equal(formatWhen(e,Date.parse('2026-12-05T20:00:00+05:00')),'Идёт сейчас · до 22:00');
  assert.equal(formatWhen(e,Date.parse('2026-12-05T22:00:00+05:00')),'Завершено');
  const [show]=normalizeEvents([{...ok,id:'show',startAt:'2026-10-30T00:00:00+05:00',endAt:'2027-01-24T23:59:00+05:00'}]);
  assert.equal(formatWhen(show,Date.parse('2026-12-05T12:00:00+05:00')),'Идёт до 24 января');
  assert.equal(formatWhen(show,Date.parse('2027-01-24T12:00:00+05:00')),'Идёт сейчас · до 23:59');
  const city=toCityEvent(e,Date.parse('2026-12-05T20:00:00+05:00'),{music:'covers/music.jpg'});
  assert.equal(city.status,'live');assert.equal(city.visibility,'public');assert.equal(city.coverRef,'covers/music.jpg');assert.equal(city.category,'музыка');
  assert.equal(toCityEvent(e,0).coverRef,undefined);
  assert.equal(shareUrl('https://x.kz/map/?event=old&utm=1#a','a1'),'https://x.kz/map/?event=a1');
  assert.match(mapUrl(e),/^https:\/\/2gis\.kz\/astana\?m=71\.430000%2C51\.128000%2F17$/);
  assert.equal(nearby([e],51.1281,71.4301).length,1);assert.equal(nearby([e],51.14,71.43).length,0);
});
test('day/night and season follow Astana time',()=>{
  assert.equal(isNight(Date.parse('2026-12-05T12:00:00+05:00')),false);assert.equal(isNight(Date.parse('2026-12-05T19:00:00+05:00')),true);
  assert.equal(themeFor(Date.parse('2026-11-30T12:00:00+05:00')),'default');assert.equal(themeFor(Date.parse('2026-12-01T12:00:00+05:00')),'winter');
  assert.equal(themeFor(Date.parse('2026-12-31T23:00:00+05:00')),'newyear');assert.equal(themeFor(Date.parse('2027-03-01T12:00:00+05:00')),'default');
});

import {weatherKind,parseWeather,formatTemp} from '../lib.js';
test('live weather codes map to the scene weather',()=>{
  assert.equal(weatherKind(0),'clear');assert.equal(weatherKind(3),'cloudy');assert.equal(weatherKind(45),'cloudy');
  assert.equal(weatherKind(61),'rain');assert.equal(weatherKind(95),'rain');assert.equal(weatherKind(73),'snow');assert.equal(weatherKind(86),'snow');
  assert.equal(weatherKind(undefined),null);
  assert.deepEqual(parseWeather({current:{temperature_2m:-7.6,weather_code:71}}),{kind:'snow',temp:-8});
  assert.equal(parseWeather({current:{}}),null);assert.equal(parseWeather(null),null);
  assert.equal(formatTemp(-8),'−8°');assert.equal(formatTemp(3),'+3°');assert.equal(formatTemp(0),'0°');assert.equal(formatTemp(null),'');
});

import {buildSuggestion,astanaInstant,suggestEndpoint} from '../lib.js';
test('a guest suggestion is checked like the server checks it',()=>{
  const now=Date.parse('2026-11-20T10:00:00+05:00'),place={lat:51.128,lng:71.43,title:'Хан Шатыр',buildingId:'landmark:khan'};
  const form={title:'  Ярмарка  ',category:'market',date:'2026-11-22',start:'12:00',end:'18:00',description:'Еда',url:'https://example.org/a',price:'',contact:'@me',website:''};
  const {body}=buildSuggestion(form,place,now);
  assert.equal(body.title,'Ярмарка');assert.equal(body.startAt,'2026-11-22T07:00:00.000Z');assert.equal(body.endAt,'2026-11-22T13:00:00.000Z');
  assert.deepEqual(body.location,{lat:51.128,lng:71.43,name:'Хан Шатыр'});assert.equal(body.buildingId,'landmark:khan');
  assert.deepEqual(body.price,{type:'free',amount:0,currency:'KZT'});assert.equal(body.sourceUrl,'https://example.org/a');assert.equal(body.contact,'@me');assert.equal(body.website,'');
  // Past midnight, paid, no optional fields, a plain map point.
  const late=buildSuggestion({...form,start:'22:00',end:'02:00',price:'3000',url:'',contact:''},{lat:51.1,lng:71.4,title:'Точка на карте',address:'ул. Достык, 5'},now).body;
  assert.equal(late.endAt,'2026-11-22T21:00:00.000Z') // 02:00 on the 23rd in Astana;assert.deepEqual(late.price,{type:'paid',amount:3000,currency:'KZT'});
  assert.equal(late.location.name,'ул. Достык, 5');assert.equal('sourceUrl' in late,false);assert.equal('contact' in late,false);assert.equal('buildingId' in late,false);
  for(const [patch,where] of [[{title:''},place],[{category:'casino'},place],[{date:'2026-02-31'},place],[{start:'25:00'},place],[{date:'2026-11-19'},place],
    [{date:'2028-01-01'},place],[{url:'javascript:alert(1)'},place],[{price:'12.5'},place],[{price:'-1'},place],[{title:'x'.repeat(121)},place],[{},{lat:43.2,lng:76.9}],[{},null]])
    assert.ok(buildSuggestion({...form,...patch},where,now).error,JSON.stringify(patch));
  assert.equal(astanaInstant('2026-11-22','00:00'),Date.parse('2026-11-21T19:00:00Z'));
  assert.equal(suggestEndpoint('https://api.eventmap.kz'),'https://api.eventmap.kz/events/suggestions');
  assert.equal(suggestEndpoint('https://x.kz/api/'),'https://x.kz/api/events/suggestions');
  assert.equal(suggestEndpoint('http://localhost:8080'),'http://localhost:8080/events/suggestions');
  assert.equal(suggestEndpoint('http://evil.kz'),'');assert.equal(suggestEndpoint(''),'');
});
