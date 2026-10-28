import {availability,resourceById} from './data.js';
// This browser-only store is a demo adapter; replace it with authenticated server calls for production.
const database = new Promise((resolve,reject)=>{
  const request=indexedDB.open('campusrent-ui-v1',1);
  request.onupgradeneeded=()=>request.result.createObjectStore('state');
  request.onsuccess=()=>resolve(request.result);
  request.onerror=()=>reject(new Error('시연 저장소를 열 수 없습니다. 브라우저 저장소 설정을 확인해주세요.'));
});
const empty=()=>({records:[],restrictions:{}});
export async function readState(){
  const db=await database;
  return new Promise((resolve,reject)=>{
    const tx=db.transaction('state','readonly');
    const request=tx.objectStore('state').get('data');
    request.onsuccess=()=>resolve(request.result||empty());
    request.onerror=()=>reject(request.error);
  });
}
async function mutate(change){
  const db=await database;
  return new Promise((resolve,reject)=>{
    const tx=db.transaction('state','readwrite');
    const store=tx.objectStore('state');
    let result, failure;
    const request=store.get('data');
    request.onsuccess=()=>{
      try{const state=request.result||empty();result=change(state);store.put(state,'data');}
      catch(error){failure=error;tx.abort();}
    };
    tx.oncomplete=()=>resolve(result);
    tx.onabort=()=>reject(failure||tx.error||new Error('저장하지 못했습니다. 다시 시도해주세요.'));
    tx.onerror=()=>{failure=tx.error;};
  });
}
const requireStudent=user=>{if(!user||user.role!=='student')throw new Error('학생 계정으로 로그인해주세요.');};
export function submitItems(items,user){
  requireStudent(user);
  return mutate(state=>{
    if(!items.length)throw new Error('신청할 항목을 선택해주세요.');
    for(const item of items){
      const resource=resourceById(item.resourceId);
      if(!resource)throw new Error('존재하지 않는 자원입니다.');
      const errors=availability(resource,item.condition,state.records,state.restrictions);
      if(errors.length)throw new Error(`${resource.name}: ${errors.join(' ')}`);
    }
    const ids=[];
    for(const item of items){
      const record={id:crypto.randomUUID(),resourceId:item.resourceId,condition:structuredClone(item.condition),owner:user.id,ownerName:user.name,status:'승인 대기',created:new Date().toISOString(),version:1,log:[],returned:{}};
      state.records.push(record);ids.push(record.id);
    }
    return ids;
  });
}
const transitions={
  '승인 대기':['승인','거절','취소'],
  '승인':['출고','이용 중'],
  '출고':['반납 요청'],
  '반납 요청':['반납 보류','반납 완료'],
  '반납 보류':['반납 보류','반납 완료'],
  '이용 중':['이용 완료'],
};
export function transition(id,target,user,expectedVersion,details={}){
  return mutate(state=>{
    const record=state.records.find(r=>r.id===id);
    if(!record)throw new Error('신청을 찾을 수 없습니다.');
    if(record.version!==expectedVersion)throw new Error('다른 탭에서 변경된 신청입니다. 최신 상태를 확인하고 다시 처리해주세요.');
    const studentAction=['취소','반납 요청'].includes(target);
    if(studentAction){requireStudent(user);if(record.owner!==user.id)throw new Error('본인의 신청만 처리할 수 있습니다.');}
    else if(user?.role!=='admin')throw new Error('관리자 로그인이 필요합니다.');
    if(!transitions[record.status]?.includes(target))throw new Error('현재 상태에서 처리할 수 없는 작업입니다.');
    const resource=resourceById(record.resourceId);
    if(resource.kind==='시설'&&['출고','반납 요청','반납 보류','반납 완료'].includes(target)||resource.kind==='장비'&&['이용 중','이용 완료'].includes(target))throw new Error('자원 종류에 맞지 않는 처리입니다.');
    const reason=(details.reason||'').trim();
    if(['거절','반납 보류'].includes(target)&&!reason)throw new Error('처리 사유를 입력해주세요.');
    if(target==='승인'){
      const errors=availability(resource,record.condition,state.records.filter(r=>r.id!==id),state.restrictions);
      if(errors.length)throw new Error(errors.join(' '));
    }
    if(target==='출고'){
      if(state.restrictions[resource.id])throw new Error('점검 중인 장비는 출고할 수 없습니다.');
      if(!details.confirmed)throw new Error('구성품을 확인해주세요.');
      record.checkout=structuredClone(resource.components);
    }
    if(['반납 보류','반납 완료'].includes(target)){
      const returned={...record.returned};
      for(const part of record.checkout||[]){
        const value=Number(details.quantities?.[part.name]??0);
        const previous=returned[part.name]||0;
        if(!Number.isSafeInteger(value)||value<0||previous+value>part.quantity)throw new Error(`${part.name}: 반환 수량을 확인해주세요.`);
        returned[part.name]=previous+value;
      }
      if(target==='반납 완료'&&(record.checkout||[]).some(p=>(returned[p.name]||0)<p.quantity))throw new Error('미반납 구성품이 있습니다. 보류 처리 후 추가 반환을 확인해주세요.');
      record.returned=returned;
      if(target==='반납 완료'&&details.needsInspection)state.restrictions[resource.id]='반납 후 점검 중입니다.';
    }
    record.log.push({from:record.status,to:target,reason,at:new Date().toISOString(),by:user.name});
    record.status=target;record.version++;
    return record;
  });
}
export function finishInspection(resourceId,user){
  if(user?.role!=='admin')throw new Error('관리자 로그인이 필요합니다.');
  return mutate(state=>{delete state.restrictions[resourceId];});
}
const accounts=new Map();
const hash=async value=>Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',new TextEncoder().encode(value))),b=>b.toString(16).padStart(2,'0')).join('');
const ready=(async()=>{
  for(const [email,password,id,name,role] of [
    ['demo@campusrent.example','Campus123!','demo-user','시연 사용자','student'],
    ['other@campusrent.example','Campus123!','other-user','다른 사용자','student'],
    ['admin@campusrent.example','CampusAdmin123!','demo-admin','시연 관리자','admin'],
  ])accounts.set(email,{verifier:await hash(password),user:{id,name,role,email}});
})();
export async function authenticate(fields,signup=false){
  await ready;
  const email=fields.email.trim().toLowerCase(),password=fields.password;
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))throw new Error('올바른 이메일을 입력해주세요.');
  if(signup){
    if(!fields.name?.trim())throw new Error('이름을 입력해주세요.');
    if(password.length<8||!/[A-Za-z]/.test(password)||!/[0-9]/.test(password))throw new Error('비밀번호는 영문·숫자를 포함해 8자 이상 입력해주세요.');
    if(accounts.has(email))throw new Error('이미 사용 중인 시연 이메일입니다.');
    accounts.set(email,{verifier:await hash(password),user:{id:crypto.randomUUID(),name:fields.name.trim(),role:'student',email}});
  }
  const account=accounts.get(email);
  if(!account||account.verifier!==await hash(password))throw new Error('이메일 또는 비밀번호를 확인해주세요.');
  return {...account.user};
}
