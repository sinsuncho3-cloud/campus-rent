import {purposes,resources,resourceById,initialConditions,localDate,conditionErrors,availability,analyzeInput,recommend,recommendationReasons,alternateResources,alternateSlots} from './data.js';
import {readState,submitItems,transition,finishInspection,authenticate} from './store.js';

const $=selector=>document.querySelector(selector);
const esc=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon=(file)=>`<img src="/assets/${file}" alt="">`;
const link=(path,label,cls='')=>`<a class="${cls}" href="${esc(path)}">${label}</a>`;
const button=(action,label,extra='',cls='secondary')=>`<button type="button" class="btn ${cls}" data-action="${action}" ${extra}>${label}</button>`;
const selectOptions=(values,current,empty)=>`${empty?`<option value="">${empty}</option>`:''}${values.map(value=>`<option ${value===current?'selected':''} value="${esc(value)}">${esc(value)}</option>`).join('')}`;
const field=(name,label,type,value,extra='')=>`<label class="field">${label}<input name="${name}" type="${type}" value="${esc(value)}" ${extra}></label>`;
let state={records:[],restrictions:{}},user=null,cart=[],search='',category='',purpose='',activity='',analysis=analyzeInput('');
let equipmentCondition=null,facilityCondition=null;
let drafts={equipment:{...initialConditions},facility:{...initialConditions}};
let noticeTimer;
try{const saved=JSON.parse(sessionStorage.getItem('campusrent-cart')||'[]');cart=Array.isArray(saved)?saved.filter(i=>resourceById(i.resourceId)&&i.condition):[];}catch{cart=[];}
const saveCart=()=>{try{sessionStorage.setItem('campusrent-cart',JSON.stringify(cart));}catch{notify('선택 목록을 저장할 수 없습니다. 브라우저 저장소를 확인해주세요.');}};
function notify(message){$('#notice').textContent=message;$('#notice').style.display='block';clearTimeout(noticeTimer);noticeTimer=setTimeout(()=>$('#notice').style.display='none',6000);}
function conditionFor(r){return r.kind==='장비'?equipmentCondition:facilityCondition;}
function summary(c,r){return `${c.start.replace('T',' ')} ~ ${c.end.replace('T',' ')}${r.kind==='시설'?` · ${c.people}명 · ${c.use}`:''}`;}
function heading(title,description){return `<div class="heading"><p class="breadcrumb">${link('/','홈')} / ${title}</p><h1>${title}</h1><p>${description}</p></div>`;}
function renderHeader(){
  $('#header').innerHTML=`<div class="container">${link('/',`<span class="logo">${icon('9dd8c.svg')}</span><span style="color:inherit">Campus<span>Rent</span></span>`,'brand')}<nav aria-label="주요 메뉴">${[['/equipment','장비 대여'],['/facilities','시설 대여'],['/purpose','목적별 대여']].map(([path,label])=>link(path,label,location.pathname===path?'active':'')).join('')}</nav><div class="auth">${user?`<span class="user-name">${esc(user.name)}님</span>${link(user.role==='admin'?'/admin':'/records',user.role==='admin'?'관리자':'내 신청','btn secondary small')}${button('logout','로그아웃','','small secondary')}`:`${button('login','로그인','','small secondary')}${button('signup','회원가입','','small')}`}</div></div>`;
}
function home(){
  const choices=['인터뷰 촬영','발표 연습','팀 회의','과제 영상 촬영','기타'];
  return `<section class="hero"><span class="badge">교내 시설·장비 이용을 한곳에서</span><h1>하고 싶은 활동에 맞춰,<br>공간과 장비를 찾아보세요.</h1><p>필요한 것이 정해져 있다면 직접 찾고, 처음이라면 목적에 맞는 추천으로 시작하세요.</p><form class="search-box" data-form="home"><label for="activity">어떤 활동을 준비하고 계신가요?</label><div class="search-row">${icon('8adc4.svg')}<input id="activity" name="activity" value="${esc(new URLSearchParams(location.search).get('activity')||'')}" placeholder="예: 팀 과제 인터뷰 영상을 촬영하고 싶어요"><button class="btn">목적에 맞게 추천받기</button></div><div class="keywords"><span class="muted">추천 키워드</span>${choices.map(c=>`<button type="button" class="chip" data-keyword="${c}" aria-pressed="false">${c}</button>`).join('')}</div></form></section>
  <section class="container section"><h2 class="section-title">필요한 것이 정해져 있나요?</h2><div class="grid two">${[['/equipment','ff97a.svg','장비 대여','카메라 · 빔프로젝터 · 무선 마이크 등'],['/facilities','8f64d.svg','시설 대여','스터디룸 · 회의실 · 촬영실 등']].map(([path,img,title,description])=>link(path,`<div class="row"><div class="iconbox">${icon(img)}</div><div><h3>${title}</h3><p>${description}</p></div></div>${icon('0272b.svg')}`,'quick')).join('')}</div></section>
  <section class="activity-section"><div class="container"><h2 class="section-title">이런 활동을 준비하고 있다면</h2><div class="grid three">${[
    ['인터뷰 촬영','054e2.svg','인터뷰 촬영 시작하기','카메라 세트 + 마이크 세트','영상과 음성을 함께 기록할 때 필요한 구성입니다.'],
    ['발표 연습','1eae3.svg','발표 연습 준비하기','회의실 + 필요 시 빔프로젝터','발표 공간을 확인하고, 비치 설비에 따라 필요한 장비를 선택하세요.'],
    ['팀 회의','b46a5.svg','팀 회의 시작하기','스터디룸·회의실','참여 인원과 이용 목적에 맞는 공간을 찾으세요.']
  ].map(([p,img,title,subtitle,description])=>`<article class="activity"><div><div class="iconbox gray">${icon(img)}</div><h3>${title}</h3><p class="description">${subtitle}<br>${description}</p></div><p class="footnote">추천 구성은 예시입니다. 실제 재고와 이용 가능 여부를 확인한 뒤 신청하세요.</p><div class="divider">${icon('f70fa.svg')}</div>${link('/purpose?activity='+encodeURIComponent(p),`구성 살펴보기 ${icon('8ad40.svg')}`,'link')}</article>`).join('')}</div></div></section>
  <section class="container section guide"><h2>간단한 3단계 예약 절차</h2><p>신청부터 최종 예약 확정까지의 과정입니다.</p><div class="grid three">${[
    ['시설 및 장비 탐색','목적별 추천 또는 개별 탐색을 통해 필요한 공간과 장비를 찾습니다.'],
    ['조건 확인 및 개별 신청','이용 규칙, 구비 품목을 확인하고 날짜와 시간에 맞춰 개별 신청을 작성합니다.'],
    ['관리자 승인 후 이용','내 신청 현황에서 승인 결과를 확인한 뒤, 안내된 절차에 따라 이용합니다.']
  ].map(([title,text],i)=>`<article class="step"><div class="row"><span class="number">0${i+1}</span>${i<2?icon('79009.svg'):''}</div><h3>${title}</h3><p>${text}</p></article>`).join('')}</div><div class="info">${icon('cb39c.svg')}<p>시설과 장비는 각각 신청합니다. 신청 시에는 승인 대기이며, 관리자 승인 후 예약이 확정됩니다.</p></div></section>`;
}
function conditionFields(kind,c){
  return `${field('start',kind==='equipment'?'수령 예정 날짜·시간':'이용 시작','datetime-local',c.start,'required')}${field('end',kind==='equipment'?'반납 예정 날짜·시간':'이용 종료','datetime-local',c.end,'required')}${kind==='facility'?`${field('people','참여 인원','number',c.people,'min="1" step="1" required')}<label class="field">실제 이용 목적<select name="use" required>${selectOptions(purposes,c.use,'목적 선택')}</select></label>`:''}`;
}
function conditionPanel(kind){
  const c=drafts[kind];
  return `<form class="panel filters" data-form="conditions" data-kind="${kind}">${conditionFields(kind,c)}<div class="wide"><button class="btn">조건 적용하고 조회</button></div></form>`;
}
function card(r,role=''){
  const c=conditionFor(r),errors=availability(r,c,state.records,state.restrictions),selected=cart.some(i=>i.resourceId===r.id);
  return `<article class="panel resource ${selected?'selected':''}"><div class="row"><div class="iconbox">${icon(r.icon)}</div><span class="status ${c?errors.length?'bad':'good':''}">${c?errors.length?'이용 불가':'이용 가능':'조건 확인 필요'}</span></div><div><p>${esc(r.category)} · ${esc(r.code)}</p><h3>${esc(r.name)}</h3></div>${role?`<span class="badge">${esc(role)}</span>`:''}<p>${esc(r.specs.join(' · '))}</p><p>${esc(r.location)}</p>${errors.length&&c?`<p>${esc(errors.join(' '))}</p>`:''}<div class="actions">${button('detail','상세 보기',`data-id="${r.id}"`)}${button(selected?'remove':'add',selected?'선택 해제':'선택 목록에 담기',`data-id="${r.id}" ${errors.length&&!selected?'disabled':''}`,selected?'secondary':'')}${errors.length&&c?button('alternatives','다른 일정·대체 조회',`data-id="${r.id}"`,'secondary small'):''}</div></article>`;
}
function cartView(){
  return `<aside class="panel cart" aria-label="선택 목록"><h2>선택 목록 <span class="badge">${cart.length}</span></h2>${cart.length?cart.map(item=>{const r=resourceById(item.resourceId);return `<div class="cart-item"><h3>${esc(r.name)}</h3><p>${esc(summary(item.condition,r))}</p><div class="actions">${button('edit-item','조건 수정',`data-id="${r.id}"`,'secondary small')}${button('remove','제거',`data-id="${r.id}"`,'secondary small')}</div></div>`;}).join(''):'<p class="empty">필요한 시설과 장비를<br>선택 목록에 담아주세요.</p>'}<p class="note">항목별 조건을 확인한 뒤 신청합니다. 관리자 승인 전에는 예약이 확정되지 않습니다.</p>${button('checkout','조건 확인 및 신청',cart.length?'':'disabled','')}</aside>`;
}
function rental(kind){
  const facility=kind==='facility',title=facility?'시설 대여':'장비 대여';
  const candidates=resources.filter(r=>r.kind===(facility?'시설':'장비'));
  const filtered=candidates.filter(r=>(!category||r.category===category)&&(!search||`${r.name} ${r.category} ${r.model} ${r.code} ${r.contents.join(' ')}`.toLowerCase().includes(search.toLowerCase())));
  return `<div class="container page">${heading(title,facility?'인원과 이용 목적에 맞는 공간을 찾고 날짜·시간을 확인하세요.':'필요한 장비의 사양과 구성품을 확인하고 대여 일정을 선택하세요.')}<p class="info">시연용 가상 자산·시설·예약입니다. 실제 학교 재고와 운영 기준은 확인이 필요합니다.</p><div class="rental-layout"><div><form class="panel filters" data-form="search">${field('search','검색어','search',search,'placeholder="이름, 모델, 관리번호 검색"')}<label class="field">분류<select name="category">${selectOptions([...new Set(candidates.map(r=>r.category))],category,'전체 분류')}</select></label><div class="wide"><button class="btn secondary">검색</button></div></form>${conditionPanel(kind)}<div class="results-head"><strong>전체 ${filtered.length}개</strong>${button('reset-search','검색 초기화','','secondary small')}</div><div class="cards">${filtered.length?filtered.map(r=>card(r)).join(''):'<div class="panel empty"><h3>검색 결과가 없습니다.</h3><p>검색어와 분류를 변경해보세요.</p></div>'}</div></div>${cartView()}</div></div>`;
}
function purposeView(){
  const items=purpose?recommend(purpose,analysis,facilityCondition?.people??drafts.facility.people,equipmentCondition,state.records,state.restrictions):[];
  return `<div class="container page">${heading('목적별 대여','준비하는 활동을 알려주시면 등록된 용도·키워드·인원에 맞는 시설과 장비를 추천합니다.')}<div class="rental-layout"><div><form class="panel" data-form="purpose"><label class="field">어떤 활동을 준비하고 계신가요?<textarea name="activity" required placeholder="예: 학생회 회의 6명">${esc(activity)}</textarea></label><button class="btn" style="margin-top:14px">추천받기</button></form>${activity?`<div class="recommendation"><strong>활동 분석</strong><p>${analysis.people?`입력에서 ${analysis.people}명을 확인했습니다. `:''}${analysis.peopleStatus==='invalid'||analysis.peopleStatus==='ambiguous'?'정확한 참여 인원을 아래 조건에 입력해주세요. ':''}${purpose?esc(recommendationReasons[purpose]):analysis.intent==='direct'?'자원 이름이 입력되었습니다. 직접 검색으로 이동할 수 있습니다.':'활동 목적을 한 가지 선택해 추천 기준을 확인해주세요.'}</p>${!purpose?`<div class="confirmations">${(analysis.candidates.length?analysis.candidates:purposes).map(p=>button('confirm-purpose',p,`data-purpose="${p}"`,'secondary small')).join('')}</div>`:''}${analysis.intent==='direct'?link((analysis.equipmentQuery?'/equipment':'/facilities')+'?q='+encodeURIComponent(analysis.equipmentQuery||analysis.facilityQuery),'직접 검색으로 이동 →','link'):''}</div>`:''}${purpose?`<h2 style="margin:24px 0 14px;font-size:18px">${esc(purpose)} · 이용 조건</h2><p class="note">시설과 장비의 일정을 각각 적용하세요. 시설 후보 중 공간 한 곳을 선택하세요.</p><h3 style="margin:16px 0;font-size:15px">시설 이용 일정·인원</h3>${conditionPanel('facility')}<h3 style="margin:16px 0;font-size:15px">장비 수령·반납 일정</h3>${conditionPanel('equipment')}<div class="results-head"><strong>추천 자원 ${items.length}개</strong></div><div class="cards">${items.map(({resource,role})=>card(resource,role)).join('')||'<div class="panel empty">조건에 맞는 추천 자원이 없습니다. 인원과 목적을 확인해주세요.</div>'}</div>`:''}</div>${cartView()}</div></div>`;
}
function recordsView(history=false){
  const title=history?'과거 신청 내역':'내 신청 현황';
  if(!user)return `<div class="container page">${heading(title,'본인의 신청과 처리 결과를 확인할 수 있습니다.')}<div class="panel empty"><h3>로그인이 필요합니다.</h3>${button('login','로그인','','')}</div></div>`;
  if(user.role!=='student')return `<div class="container page">${heading(title,'학생 계정에서 신청 내역을 확인할 수 있습니다.')}${link('/admin','관리자 화면으로 이동','btn')}</div>`;
  const finished=['거절','취소','반납 완료','이용 완료'];
  const records=state.records.filter(r=>r.owner===user.id&&finished.includes(r.status)===history).slice().reverse();
  return `<div class="container page">${heading(title,'신청별 일정과 승인·이용 상태, 처리 사유를 확인하세요.')}<div class="tabs">${link('/records','진행 중 신청','btn '+(history?'secondary':''))}${link('/history','과거 내역','btn '+(history?'':'secondary'))}</div>${records.map(r=>recordView(r,false)).join('')||'<div class="panel empty"><h3>신청 내역이 없습니다.</h3><p>필요한 장비와 시설을 찾아 신청해보세요.</p></div>'}</div>`;
}
function recordView(record,admin){
  const r=resourceById(record.resourceId);
  let actions='';
  const action=(target,label=target)=>button('transition',label,`data-id="${record.id}" data-target="${target}"`,'secondary small');
  if(admin){
    if(record.status==='승인 대기') actions=action('승인')+action('거절');
    if(record.status==='승인') actions=action(r.kind==='장비'?'출고':'이용 중',r.kind==='장비'?'출고 검수':'이용 시작');
    if(['반납 요청','반납 보류'].includes(record.status)) actions=button('inspection','반납·추가 반환 검수',`data-id="${record.id}"`,'secondary small');
    if(record.status==='이용 중') actions=action('이용 완료','이용 종료');
  }else{
    if(record.status==='승인 대기') actions=action('취소','신청 취소');
    if(record.status==='출고') actions=action('반납 요청');
  }
  return `<article class="panel record"><div class="row"><h3>${esc(r.name)}</h3><span class="status ${['거절','반납 보류'].includes(record.status)?'bad':'good'}">${record.status}</span></div><p>${esc(summary(record.condition,r))}</p><p>신청 번호 ${record.id.slice(0,8)} · ${esc(record.ownerName)}</p>${record.checkout?`<p>출고 기준: ${record.checkout.map(p=>`${esc(p.name)} ${p.quantity}개`).join(' · ')}</p>`:''}${record.checkout&&Object.keys(record.returned).length?`<p>누적 반환: ${record.checkout.map(p=>`${esc(p.name)} ${record.returned[p.name]||0}/${p.quantity}`).join(' · ')}</p>`:''}${record.log.length?`<details><summary>처리 이력 ${record.log.length}건</summary><ul>${record.log.map(l=>`<li>${esc(l.at.replace('T',' ').slice(0,16))} · ${l.to} · ${esc(l.by)}${l.reason?` — ${esc(l.reason)}`:''}</li>`).join('')}</ul></details>`:''}<div class="actions">${actions}</div></article>`;
}
function adminView(){
  if(user?.role!=='admin')return `<div class="container page">${heading('관리자','신청 승인과 실제 이용·반납을 관리합니다.')}<div class="panel empty"><h3>관리자 계정으로 로그인해주세요.</h3><p>시연 계정은 로그인 창에서 확인할 수 있습니다.</p><br>${button('login','관리자 로그인','','')}</div></div>`;
  return `<div class="container page">${heading('관리자','신청별 조건을 재확인하고 승인·출고·반납·시설 이용을 처리합니다.')}<p class="info">브라우저에서 동작하는 관리자 시연입니다. 실제 서버 권한·학교 계정·Oracle 연동은 아직 적용되지 않았습니다.</p><div class="tabs"><span class="badge">승인 대기 ${state.records.filter(r=>r.status==='승인 대기').length}</span><span class="badge">전체 신청 ${state.records.length}</span>${button('refresh','최신 상태 조회','','secondary small')}</div>${Object.keys(state.restrictions).length?`<section class="panel" style="margin-bottom:24px"><h2 style="font-size:18px">반납 후 장비 점검</h2>${Object.keys(state.restrictions).map(id=>`<div class="cart-item row"><span>${esc(resourceById(id).name)}</span>${button('finish-inspection','점검 완료',`data-id="${id}"`,'secondary small')}</div>`).join('')}</section>`:''}${state.records.slice().reverse().map(r=>recordView(r,true)).join('')||'<div class="panel empty">접수된 신청이 없습니다.</div>'}</div>`;
}
function render(){
  renderHeader();
  const path=location.pathname;
  const content=path==='/'?home():path==='/equipment'?rental('equipment'):path==='/facilities'?rental('facility'):path==='/purpose'?purposeView():path==='/records'?recordsView():path==='/history'?recordsView(true):path==='/admin'?adminView():`<div class="container page">${heading('페이지를 찾을 수 없습니다.','주소를 확인해주세요.')}${link('/','홈으로 돌아가기','btn')}</div>`;
  $('#main').innerHTML=content;
  document.title=`${{'/':'홈','/equipment':'장비 대여','/facilities':'시설 대여','/purpose':'목적별 대여','/records':'내 신청 현황','/history':'과거 신청 내역','/admin':'관리자'}[path]||'페이지 없음'} · CampusRent`;
}
function updatePurpose(value){
  activity=value;analysis=analyzeInput(value);purpose=analysis.candidates.length===1?analysis.candidates[0]:'';
  if(analysis.people)drafts.facility.people=analysis.people;
  drafts.facility.use=purpose;
  // Changing the activity requires explicit reapplication of the facility conditions.
  facilityCondition=null;
}
function route(){
  search=new URLSearchParams(location.search).get('q')||'';category='';
  if(location.pathname==='/purpose')updatePurpose(new URLSearchParams(location.search).get('activity')||'');
  render();
}
function navigate(path){history.pushState({},'',path);route();window.scrollTo(0,0);$('#main').focus({preventScroll:true});}
function dialog(title,content){
  const modal=$('#modal');
  modal.innerHTML=`<div class="dialog-head"><h2 id="modal-title">${title}</h2><button type="button" class="close" data-action="close" aria-label="닫기">×</button></div>${content}<p class="error" id="dialog-error" role="alert" hidden></p>`;
  if(!modal.open)modal.showModal();
}
function login(signup=false){
  dialog(signup?'시연 회원가입':'로그인',`<p class="muted small">브라우저 시연 계정입니다. 로그인·회원가입 정보는 새로고침하면 초기화됩니다.</p><form data-form="auth" data-signup="${signup}">${signup?field('name','이름','text','','required autocomplete="name" maxlength="40"'):''}${field('email','이메일','email','','required autocomplete="username"')}${field('password','비밀번호','password','','required autocomplete="'+(signup?'new-password':'current-password')+'"')}<div class="actions"><button class="btn">${signup?'가입하기':'로그인'}</button>${button(signup?'login':'signup',signup?'로그인으로 전환':'회원가입')}</div></form><div class="demo-accounts"><strong>시연 계정</strong><p>학생: demo@campusrent.example / Campus123!</p><p>다른 학생: other@campusrent.example / Campus123!</p><p>관리자: admin@campusrent.example / CampusAdmin123!</p></div>`);
}
function details(id){
  const r=resourceById(id),c=conditionFor(r),errors=availability(r,c,state.records,state.restrictions);
  dialog(esc(r.name),`<span class="badge">${r.kind} · ${esc(r.category)}</span><p style="margin-top:16px">${esc(r.model)} · ${esc(r.code)}</p><p class="muted">${esc(r.location)}</p><h3 style="margin-top:20px">주요 사양</h3><ul>${r.specs.map(s=>`<li>${esc(s)}</li>`).join('')}</ul><h3>구성품·비치 설비</h3><ul>${r.contents.map(s=>`<li>${esc(s)}</li>`).join('')}</ul>${r.kind==='시설'?`<p>운영 09:00–20:00 · 공간 전체 예약<br>허용 용도: ${esc(r.allowedUses.join(' · '))}</p>`:''}<p class="info" style="margin-top:18px">${esc(errors.length?errors.join(' '):'선택한 일정에 이용 가능합니다. 승인 시 최신 조건을 재확인합니다.')}</p><div class="actions">${button('add','선택 목록에 담기',`data-id="${id}" ${errors.length?'disabled':''}`,'')}${c?button('alternatives','다른 일정·대체 조회',`data-id="${id}"`):''}</div>`);
}
function alternatives(id){
  const r=resourceById(id),c=conditionFor(r);
  if(!c)throw new Error('이용 조건을 먼저 적용해주세요.');
  const candidates=alternateResources(r,c,state.records,state.restrictions);
  const until=localDate(new Date(Date.parse(c.start)+7*86400000));
  dialog('다른 일정·대체 자원 조회',`<p class="muted">${esc(r.name)}<br>${esc(summary(c,r))}</p><h3 style="margin-top:20px">같은 일정의 대체 자원</h3>${candidates.length?candidates.map(item=>`<div class="cart-item"><p>${esc(item.name)}</p>${button('add','이 자원 담기',`data-id="${item.id}"`,'secondary small')}</div>`).join(''):'<p class="note">같은 일정에 이용 가능한 대체 자원이 없습니다.</p>'}<h3 style="margin-top:24px">같은 자원의 다른 일정</h3><form data-form="slots" data-id="${id}" class="filters" style="margin-top:14px">${field('from','검색 시작일','date',c.start.slice(0,10),'required')}${field('until','검색 종료일 (최대 31일)','date',until,'required')}<button class="btn secondary wide">가능한 일정 조회</button></form><div id="slots-results"></div>`);
}
function editItem(id){
  const item=cart.find(i=>i.resourceId===id),r=resourceById(id);
  dialog('선택 항목 조건 수정',`<p>${esc(r.name)}</p><form data-form="edit-item" data-id="${id}" class="filters" style="margin-top:18px">${conditionFields(r.kind==='장비'?'equipment':'facility',item.condition)}<button class="btn wide">조건 수정</button></form>`);
}
function checkout(){
  if(!user){login();return;}
  if(user.role!=='student')throw new Error('학생 계정으로 로그인해 신청해주세요.');
  dialog('신청 전 최종 확인',`<p class="muted">각 항목은 개별 신청으로 접수됩니다.</p>${cart.map(i=>`<div class="cart-item"><h3>${esc(resourceById(i.resourceId).name)}</h3><p>${esc(summary(i.condition,resourceById(i.resourceId)))}</p></div>`).join('')}<form data-form="checkout"><label class="checkrow"><input name="agree" type="checkbox" required><span>항목별 이용 조건을 확인했으며, 관리자 승인 후 이용이 확정됨을 확인했습니다.</span></label><div class="actions"><button class="btn">최종 신청 제출</button></div></form>`);
}
function transitionDialog(id,target){
  const record=state.records.find(r=>r.id===id),r=resourceById(record.resourceId);
  dialog(target==='출고'?'출고 구성품 검수':`${target} 확인`,`<p>${esc(r.name)}</p><p class="muted small">${esc(summary(record.condition,r))}</p><form data-form="transition" data-id="${id}" data-target="${target}" data-version="${record.version}">${['거절','반납 보류'].includes(target)?'<label class="field">필수 처리 사유<textarea name="reason" required maxlength="500"></textarea></label>':''}${target==='출고'?`<ul>${r.components.map(p=>`<li>${esc(p.name)} ${p.quantity}개</li>`).join('')}</ul><label class="checkrow"><input type="checkbox" name="confirmed" required><span>실제 전달할 구성품과 수량을 확인했습니다.</span></label>`:''}<p class="note">${target==='승인'?'승인 시 최신 일정 중복과 장비 상태를 다시 확인합니다.':'처리 결과는 신청 내역에 기록됩니다.'}</p><div class="actions"><button class="btn">${target} 처리</button></div></form>`);
}
function inspectionDialog(id){
  const record=state.records.find(r=>r.id===id),r=resourceById(record.resourceId);
  dialog('반납·추가 반환 검수',`<p>${esc(r.name)}</p><p class="note">이번에 반환받은 수량을 입력하세요. 이전 반환 수량과 합산됩니다.</p><form data-form="inspection" data-id="${id}" data-version="${record.version}">${record.checkout.map((p,i)=>`<label class="inspection-row"><span>${esc(p.name)}<br><span class="note">기준 ${p.quantity}개 · 누적 ${record.returned[p.name]||0}개</span></span><input aria-label="${esc(p.name)} 이번 반환 수량" name="quantity-${i}" type="number" min="0" max="${p.quantity-(record.returned[p.name]||0)}" step="1" value="${p.quantity-(record.returned[p.name]||0)}" required></label>`).join('')}<label class="field">처리 사유 (보류 시 필수)<textarea name="reason" maxlength="500"></textarea></label><label class="checkrow"><input type="checkbox" name="needsInspection"><span>반납 완료 후 장비 점검 필요</span></label><div class="actions"><button name="target" value="반납 완료" class="btn">반납 완료</button><button name="target" value="반납 보류" class="btn secondary">반납 보류</button></div></form>`);
}
async function refresh(){state=await readState();render();}
async function action(element){
  const {action:which,id,target}=element.dataset;
  if(which==='close')$('#modal').close();
  if(which==='login'||which==='signup')login(which==='signup');
  if(which==='logout'){user=null;render();notify('로그아웃했습니다.');}
  if(which==='detail')details(id);
  if(which==='alternatives')alternatives(id);
  if(which==='add'){
    const r=resourceById(id),c=conditionFor(r),errors=availability(r,c,state.records,state.restrictions);
    if(errors.length)throw new Error(errors.join(' '));
    if(!cart.some(i=>i.resourceId===id))cart.push({resourceId:id,condition:structuredClone(c)});
    saveCart();if($('#modal').open)$('#modal').close();render();notify('선택 목록에 담았습니다.');
  }
  if(which==='remove'){cart=cart.filter(i=>i.resourceId!==id);saveCart();render();}
  if(which==='edit-item')editItem(id);
  if(which==='checkout')checkout();
  if(which==='confirm-purpose'){purpose=element.dataset.purpose;drafts.facility.use=purpose;facilityCondition=null;render();}
  if(which==='reset-search'){search='';category='';render();}
  if(which==='transition')transitionDialog(id,target);
  if(which==='inspection')inspectionDialog(id);
  if(which==='finish-inspection'){await finishInspection(id,user);await refresh();notify('점검을 완료했습니다.');}
  if(which==='refresh')await refresh();
}
function readCondition(form){const f=new FormData(form);return{start:String(f.get('start')||''),end:String(f.get('end')||''),people:Number(f.get('people')||1),use:String(f.get('use')||'')};}
async function submit(form,submitter){
  const values=Object.fromEntries(new FormData(form)),type=form.dataset.form;
  if(type==='home'){navigate('/purpose?activity='+encodeURIComponent(values.activity));return;}
  if(type==='search'){search=values.search.trim();category=values.category;render();}
  if(type==='purpose'){updatePurpose(values.activity.trim());history.replaceState({},'','/purpose?activity='+encodeURIComponent(activity));render();}
  if(type==='conditions'){
    const kind=form.dataset.kind,c=readCondition(form),errors=conditionErrors(c,kind==='facility');
    if(kind==='facility'&&!c.use)errors.push('실제 이용 목적을 선택해주세요.');
    if(errors.length)throw new Error(errors.join(' '));
    drafts[kind]=c;if(kind==='equipment')equipmentCondition={...c};else facilityCondition={...c};render();notify('조회 조건을 적용했습니다. 기존 선택 항목은 조건 수정으로 변경할 수 있습니다.');
  }
  if(type==='edit-item'){
    const c=readCondition(form),r=resourceById(form.dataset.id),errors=availability(r,c,state.records,state.restrictions);
    if(errors.length)throw new Error(errors.join(' '));
    cart.find(i=>i.resourceId===r.id).condition=c;saveCart();$('#modal').close();render();
  }
  if(type==='auth'){user=await authenticate(values,form.dataset.signup==='true');$('#modal').close();render();notify('로그인했습니다. 신청하려면 선택 목록의 신청 버튼을 눌러주세요.');}
  if(type==='checkout'){
    if(values.agree!=='on')throw new Error('이용 조건을 확인해주세요.');
    await submitItems(cart,user);cart=[];saveCart();$('#modal').close();state=await readState();navigate('/records');notify('신청이 접수되었습니다. 관리자 승인 대기 상태입니다.');
  }
  if(type==='slots'){
    if(values.from>values.until||Date.parse(values.until)-Date.parse(values.from)>31*86400000)throw new Error('31일 이내의 올바른 검색 기간을 선택해주세요.');
    const r=resourceById(form.dataset.id),c=conditionFor(r),slots=alternateSlots(r,c,values.from,values.until,state.records,state.restrictions);
    $('#slots-results').innerHTML=slots.length?slots.map((slot,i)=>`<div class="cart-item"><p>${esc(summary(slot,r))}</p>${button('use-slot','이 일정으로 조회',`data-slot="${i}" data-id="${r.id}"`,'secondary small')}</div>`).join(''):'<p class="note">검색 기간에 가능한 일정이 없습니다.</p>';
    $('#slots-results').querySelectorAll('[data-slot]').forEach(b=>b.addEventListener('click',()=>{
      const c=slots[Number(b.dataset.slot)],kind=r.kind==='장비'?'equipment':'facility';drafts[kind]=c;if(kind==='equipment')equipmentCondition=c;else facilityCondition=c;$('#modal').close();render();notify('대체 일정을 조회 조건에 적용했습니다. 목록에서 자원을 선택해주세요.');
    }));
  }
  if(type==='transition'){
    await transition(form.dataset.id,form.dataset.target,user,Number(form.dataset.version),{reason:values.reason,confirmed:values.confirmed==='on'});$('#modal').close();await refresh();notify('처리 결과를 저장했습니다.');
  }
  if(type==='inspection'){
    const record=state.records.find(r=>r.id===form.dataset.id),quantities=Object.fromEntries(record.checkout.map((p,i)=>[p.name,Number(values[`quantity-${i}`])]));
    await transition(record.id,submitter.value,user,Number(form.dataset.version),{quantities,reason:values.reason,needsInspection:values.needsInspection==='on'});$('#modal').close();await refresh();notify('검수 결과를 저장했습니다.');
  }
}
function report(error){if($('#modal').open){const box=$('#dialog-error');box.hidden=false;box.textContent=error.message;}else notify(error.message||'처리 중 오류가 발생했습니다.');}
document.addEventListener('click',event=>{
  const anchor=event.target.closest('a[href]');
  if(anchor&&!event.ctrlKey&&!event.metaKey&&!event.shiftKey&&event.button===0){const url=new URL(anchor.href);if(url.origin===location.origin&&!url.hash&&!anchor.target){event.preventDefault();navigate(url.pathname+url.search);return;}}
  const keyword=event.target.closest('[data-keyword]');
  if(keyword){$('#activity').value=keyword.dataset.keyword;document.querySelectorAll('[data-keyword]').forEach(b=>b.setAttribute('aria-pressed',String(b===keyword)));$('#activity').focus();}
  const control=event.target.closest('[data-action]');
  if(control&&!control.disabled)action(control).catch(report);
});
document.addEventListener('submit',event=>{
  if(!event.target.matches('[data-form]'))return;
  event.preventDefault();const form=event.target,submitter=event.submitter;
  if(form.dataset.busy)return;
  form.dataset.busy='true';if(submitter)submitter.disabled=true;
  Promise.resolve(submit(form,submitter)).catch(report).finally(()=>{delete form.dataset.busy;if(submitter)submitter.disabled=false;});
});
// Preserve unfinished condition drafts when another control causes a redraw.
document.addEventListener('input',event=>{
  const form=event.target.closest('[data-form="conditions"]');
  if(form)drafts[form.dataset.kind]=readCondition(form);
});
window.addEventListener('popstate',route);
window.addEventListener('focus',()=>refresh().catch(report));
try{state=await readState();route();}catch(error){render();report(error);}
