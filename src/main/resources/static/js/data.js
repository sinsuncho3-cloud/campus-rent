// Resource names, specifications and recommendation rules transcribed from the supplied Figma Make archive.
export const purposes = ['인터뷰 촬영', '발표 연습', '팀 회의', '과제 영상 촬영'];
export function localDate(date) {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}
export const demoDay = localDate(new Date(Date.now() + 86400000));
export const initialConditions = {start:`${demoDay}T14:00`,end:`${demoDay}T16:00`,people:3,use:''};
const components = (names) => names.map(([name, quantity = 1]) => ({name, quantity}));
const camera = components([['카메라 본체'],['기본 렌즈'],['배터리'],['충전기']]);
const projector = components([['프로젝터 본체'],['HDMI 케이블'],['전원 케이블'],['리모컨']]);
const microphone = (quantity, adapter=true) => components([['마이크',quantity],['수신기'],...(adapter?[['전원 어댑터']]:[])]);
const equipment = (id,name,category,model,specs,alternatives,parts) => ({id,name,category,model,specs,alternatives,components:parts,contents:parts.map(p=>`${p.name} ${p.quantity}개`),kind:'장비',code:`DEMO-${id.toUpperCase()}`,location:'시연 자산 보관실',icon:category==='카메라'?'ff97a.svg':category==='마이크'?'054e2.svg':'1eae3.svg',rentable:true,status:'정상'});
const facility = (id,name,category,capacity,allowedUses,contents,location) => ({id,name,category,capacity,allowedUses,contents,location,kind:'시설',code:`DEMO-${id.toUpperCase()}`,model:'가상 시설',specs:[`수용 ${capacity}명`,'공간 전체 예약'],operatingHours:['09:00','20:00'],alternatives:[],icon:'b46a5.svg',rentable:true,status:'정상'});
export const resources = [
  equipment('cam01','이동형 디지털카메라 고정 세트 A','카메라','DEMO-CAM100',['영상 4K / 30fps','기본 렌즈 24–70mm'],['cam02'],camera),
  equipment('cam02','이동형 디지털카메라 고정 세트 B','카메라','DEMO-CAM200',['영상 1080p / 60fps','기본 렌즈 18–55mm'],[],camera),
  equipment('proj01','이동형 빔프로젝터 고정 세트 A','프로젝터','DEMO-PJ100',['해상도 1920×1080','입력 HDMI'],['proj02'],projector),
  equipment('proj02','이동형 빔프로젝터 고정 세트 B','프로젝터','DEMO-PJ200',['해상도 1280×800','입력 HDMI'],[],projector),
  equipment('mic01','무선 마이크 고정 세트 A','마이크','DEMO-MIC100',['녹음 1채널','출력 3.5mm'],['mic02','mic03'],microphone(1)),
  equipment('mic02','무선 마이크 고정 세트 B','마이크','DEMO-MIC200',['녹음 2채널','출력 3.5mm'],[],microphone(2)),
  {...equipment('mic03','무선 마이크 고정 세트 C','마이크','DEMO-MIC300',['사양 확인 필요'],[],microphone(1,false)),status:'점검'},
  facility('room01','시연 회의실 A','회의실',4,['팀 회의','발표 연습'],['테이블','의자 4개','화이트보드'],'가상 캠퍼스 · A동 201호'),
  facility('room02','시연 회의실 B','회의실',8,['팀 회의','발표 연습'],['테이블','의자 8개','고정 전자칠판'],'가상 캠퍼스 · A동 202호'),
  facility('study01','시연 스터디룸 B','스터디룸',6,['팀 회의','발표 연습'],['테이블','의자 6개','고정 PC'],'가상 캠퍼스 · B동 101호'),
  facility('studio01','시연 촬영실 A','촬영실',4,['인터뷰 촬영','과제 영상 촬영'],['테이블','의자 4개','고정 조명'],'가상 캠퍼스 · C동 101호'),
  facility('studio02','시연 촬영실 B','촬영실',6,['인터뷰 촬영','과제 영상 촬영'],['테이블','의자 6개','고정 조명','방음 설비'],'가상 캠퍼스 · C동 102호'),
];
export const bookings = [
  {resourceId:'mic01',start:`${demoDay}T13:00`,end:`${demoDay}T17:00`},
  {resourceId:'studio01',start:`${demoDay}T14:00`,end:`${demoDay}T16:00`},
  {resourceId:'cam01',start:`${demoDay}T10:00`,end:`${demoDay}T12:00`},
  {resourceId:'room01',start:`${demoDay}T10:00`,end:`${demoDay}T12:00`},
];
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
    if (c.start.slice(0,10)!==c.end.slice(0,10)||c.start.slice(11,16)<'09:00'||c.end.slice(11,16)>'20:00') errors.push('운영 시간 09:00–20:00 안에서 같은 날짜로 선택해주세요.');
  }
  if (bookings.some(b=>b.resourceId===r.id && overlaps(c,b))) errors.push('시연 예약·점검 시간과 겹칩니다.');
  if (records.some(b=>b.resourceId===r.id && ['승인','출고','반납 요청','반납 보류','이용 중'].includes(b.status) && overlaps(c,b.condition))) errors.push('관리자가 승인한 신청과 이용 기간이 겹칩니다.');
  return errors;
}
export function analyzeInput(input) {
  const text=input.toLowerCase().replace(/[\s.,!?~…·'"“”‘’()[\]{}:;_-]+/g,'');
  const peopleValues=[...input.matchAll(/([+-]?\d+(?:\.\d+)?)\s*명/g)].map(m=>Number(m[1]));
  const invalid=peopleValues.some(n=>!Number.isSafeInteger(n)||n<1);
  const unique=[...new Set(peopleValues)];
  const ambiguous=unique.length>1 || /\d+\s*명?\s*(~|–|—|-|에서|부터)\s*\d+\s*명/.test(input);
  const candidates=[];
  const excludesRecording=/촬영없이|영상없이|카메라없이|촬영은안|촬영안하고/.test(text);
  if (/인터뷰/.test(text)) candidates.push('인터뷰 촬영');
  if (/과제/.test(text)&&/영상|촬영|찍/.test(text)&&!excludesRecording) candidates.push('과제 영상 촬영');
  if (/회의/.test(text)&&!/^회의실\d*명?$/.test(text)) candidates.push('팀 회의');
  else if (/발표|프레젠테이션|ppt/.test(text)) candidates.push('발표 연습');
  const equipmentQuery=['카메라','프로젝터','마이크'].find(w=>text.includes(w));
  const facilityQuery=['회의실','스터디룸','촬영실'].find(w=>text.includes(w));
  const intent=candidates.length>1?'multiple':candidates.length?'recognized':/영상|촬영|찍/.test(text)&&!excludesRecording?'video':equipmentQuery||facilityQuery?'direct':'unknown';
  return {candidates,people:!invalid&&!ambiguous?unique[0]:undefined,peopleStatus:ambiguous?'ambiguous':invalid?'invalid':unique.length?'valid':'none',intent,equipmentQuery,facilityQuery,excludesRecording,presentation:/발표|ppt|프레젠테이션/.test(text)};
}
export const recommendationReasons = {
  '인터뷰 촬영':'영상·음성 기록과 조용한 공간을 함께 준비하는 구성입니다.',
  '과제 영상 촬영':'카메라 중심 구성입니다. 음성과 실내 촬영 공간은 과제에 따라 선택하세요.',
  '발표 연습':'발표할 공간을 먼저 선택하고, 비치 화면이 없다면 프로젝터를 추가하세요.',
  '팀 회의':'참여 인원에 맞는 공간 후보입니다. 공간 한 곳을 선택하고 비치 설비를 확인하세요.',
};
export function recommend(purpose,analysis,people,condition,records=[],restrictions={}) {
  const result=resources.filter(r=>r.kind==='시설'&&r.allowedUses.includes(purpose)&&r.capacity>=people&&(!analysis.facilityQuery||r.category===analysis.facilityQuery)).sort((a,b)=>a.capacity-b.capacity).map(resource=>({resource,role:`공간 후보 · 수용 ${resource.capacity}명`}));
  const categories=[];
  if (['인터뷰 촬영','과제 영상 촬영'].includes(purpose)&&!analysis.excludesRecording) categories.push('카메라','마이크');
  if (purpose==='발표 연습'||analysis.presentation) categories.push('프로젝터');
  if (analysis.equipmentQuery&&!categories.includes(analysis.equipmentQuery)&&!(analysis.excludesRecording&&analysis.equipmentQuery==='카메라')) categories.push(analysis.equipmentQuery);
  categories.forEach(category=>{
    const candidates=resources.filter(r=>r.kind==='장비'&&r.category===category);
    const resource=candidates.find(r=>!availability(r,condition,records,restrictions).length)||candidates.find(r=>r.status==='정상'&&!restrictions[r.id])||candidates[0];
    result.push({resource,role:category==='카메라'?'기본 · 활동 영상 기록':category==='마이크'&&purpose==='인터뷰 촬영'?'기본 · 참여자 음성 녹음':'선택 · 활동에 따라 추가'});
  });
  return result;
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
