// Resource names, specifications and recommendation rules transcribed from the supplied Figma Make archive.
import {purposes,analyzeInput,recommendationReasons,configureRecommendations,equipmentForPurpose} from './recommendation.js';
export {purposes,analyzeInput,recommendationReasons};
export function localDate(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
export const demoDay = localDate(new Date(Date.now() + 86400000));
export const initialConditions = {start:`${demoDay}T14:00`,end:`${demoDay}T16:00`,people:3,use:''};
export const resources = [];
export const bookings = [];
export async function loadCatalog(){const [catalog,config]=await Promise.all([fetch('/api/catalog',{credentials:'same-origin'}),fetch('/api/recommendation-rules',{credentials:'same-origin'})]);if(!catalog.ok||!config.ok)throw new Error('자원·추천 기준을 조회하지 못했습니다.');const [rows,rules]=await Promise.all([catalog.json(),config.json()]);resources.splice(0,resources.length,...rows);configureRecommendations(rules);}
export const resourceById = id => resources.find(r=>r.id===id);
export const overlaps = (a,b) => new Date(a.start)<new Date(b.end) && new Date(a.end)>new Date(b.start);
export function conditionErrors(c, facilities=false) {
  const errors=[];
  if (!c || !Number.isFinite(Date.parse(c.start)) || !Number.isFinite(Date.parse(c.end))) return ['시작·종료 날짜와 시간을 입력해주세요.'];
  if (Date.parse(c.start)<=Date.now()) errors.push('시작 시간은 현재보다 이후여야 합니다.');
  if (Date.parse(c.end)<=Date.parse(c.start)) errors.push('종료 시간은 시작 시간보다 늦어야 합니다.');
  if (facilities && (!Number.isSafeInteger(c.people)||c.people<1)) errors.push('이용 인원은 1명 이상의 정수로 입력해주세요.');
  return errors;
}
export function availability(r,c,records=[],restrictions={}) {
  if (!c) return ['이용 기간 선택 후 확인할 수 있습니다.'];
  const errors=conditionErrors(c,r.kind==='시설');
  if (!r.rentable) errors.push('대여가 허용되지 않은 자산입니다.');
  if (r.status!=='정상') errors.push(`${r.status} 중입니다. 이용 재개 시점은 확인이 필요합니다.`);
  if (restrictions[r.id]) errors.push(restrictions[r.id]);
  if (r.kind==='시설') {
    if (c.people>r.capacity) errors.push(`수용 ${r.capacity}명보다 이용 인원 ${c.people}명이 많습니다.`);
    if (!c.use) errors.push('시설의 실제 이용 목적을 선택해주세요.');
    else if (!r.allowedUses.includes(c.use)) errors.push(`허용 용도: ${r.allowedUses.join(' · ')}`);
    const day=new Date(c.start).getDay()||7,hours=r.hoursByDay?.[day];
    if(!hours)errors.push('선택한 날짜는 시설 휴무일입니다.');
    else if(c.start.slice(0,10)!==c.end.slice(0,10)||c.start.slice(11,16)<hours[0]||c.end.slice(11,16)>hours[1])errors.push(`운영 시간 ${hours.join('–')} 안에서 같은 날짜로 선택해주세요.`);
  }
  if (bookings.some(b=>b.resourceId===r.id && overlaps(c,b))) errors.push('시연 예약·점검 시간과 겹칩니다.');
  if (records.some(b=>b.resourceId===r.id && ['승인','출고','반납 요청','반납 보류','이용 중'].includes(b.status) && overlaps(c,b.condition))) errors.push('관리자가 승인한 신청과 이용 기간이 겹칩니다.');
  return errors;
}
export function recommend(purpose,analysis,people,condition,records=[],restrictions={}) {
  const result=resources.filter(r=>r.kind==='시설'&&r.rentable&&r.status==='정상'&&r.allowedUses.includes(purpose)&&r.capacity>=people&&(!analysis.facilityQuery||r.category===analysis.facilityQuery)).sort((a,b)=>a.capacity-b.capacity).map(resource=>({resource,role:`공간 후보 · 수용 ${resource.capacity}명`}));
  const needs=equipmentForPurpose(purpose,analysis);
  if(analysis.equipmentQuery&&!needs.some(r=>r.category===analysis.equipmentQuery)&&!(analysis.excludesRecording||analysis.excludesCamera&&analysis.equipmentQuery==='카메라'||analysis.excludesAudio&&analysis.equipmentQuery==='마이크'))needs.push({category:analysis.equipmentQuery,role:'선택 · 직접 요청한 장비'});
  for(const need of needs){const candidates=resources.filter(r=>r.kind==='장비'&&r.category===need.category&&r.rentable&&r.status==='정상'&&!restrictions[r.id]);const resource=candidates.find(r=>condition&&!availability(r,condition,records,restrictions).length)||candidates[0];if(resource)result.push({resource,role:need.role});}  return result;
}
export function alternateResources(resource,condition,records=[],restrictions={}) {
  return resources.filter(r=>r.id!==resource.id&&(resource.kind==='장비'?resource.alternatives.includes(r.id):r.kind==='시설')&&!availability(r,condition,records,restrictions).length);
}
export function alternateSlots(resource,condition,from,until,records=[],restrictions={}) {
  const duration=Date.parse(condition.end)-Date.parse(condition.start);
  if (!Number.isFinite(duration)||duration<=0||!from||!until||from>until) return [];
  const result=[],limit=Date.parse(`${until}T23:59`);
  for(let cursor=Date.parse(`${from}T00:00`),step=0;cursor<=limit&&step<31*48;cursor+=1800000,step++){
    const serialize=d=>`${localDate(d)}T${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
    const c={...condition,start:serialize(new Date(cursor)),end:serialize(new Date(cursor+duration))};
    if(cursor+duration<=limit&&!availability(resource,c,records,restrictions).length) result.push(c);
    if(result.length===8) break;
  }
  return result;
}
