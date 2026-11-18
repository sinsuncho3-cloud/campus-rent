// Oracle-backed API adapter. Identity and permissions are determined by the server session.
let csrf='';
let identity='';
export let demoAccounts=false;
export async function loadClientConfig(){demoAccounts=(await request('/client-config')).demoAccounts;}
export async function request(path,body){
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),25000);let response,data;
  try{response=await fetch('/api'+path,{signal:controller.signal,credentials:'same-origin',method:body===undefined?'GET':'POST',headers:body===undefined?{}:{'Content-Type':'application/json','X-CSRF-Token':csrf},body:body===undefined?undefined:JSON.stringify(body)});data=await response.json();}catch{throw new Error(body===undefined?'서버 응답을 확인할 수 없습니다. 잠시 후 다시 조회해주세요.':'처리 결과를 확인할 수 없습니다. 내역을 조회한 뒤 재시도해주세요. 같은 선택의 신청 재시도는 중복 저장을 방지합니다.');}finally{clearTimeout(timer);}
  if(!response.ok)throw new Error(data.message||'서버 처리에 실패했습니다.');
  return data;
}
export async function getSession(){const data=await request('/session');csrf=data.csrf;identity=data.user?.id||'';return data.user;}
export async function readState(page=0){
  const view=location.pathname==='/history'?'history':location.pathname==='/records'?'current':'all';
  const data=await request('/state?page='+page+'&size=25&view='+view);
  // Reservation intervals contain no applicant names/emails. Full records are owner/admin scoped.
  return data;
}
export function submitItems(items){const fingerprint=JSON.stringify([identity,items]);let saved;try{saved=JSON.parse(sessionStorage.getItem('campusrent-pending-submit')||'null');}catch{}if(saved?.fingerprint!==fingerprint){saved={fingerprint,key:crypto.randomUUID()};sessionStorage.setItem('campusrent-pending-submit',JSON.stringify(saved));}return request('/requests',{items,requestKey:saved.key});}
export function clearSubmission(){sessionStorage.removeItem('campusrent-pending-submit');}
export function transition(id,target,user,version,details={}){return request('/requests/'+encodeURIComponent(id)+'/transition',{target,version,details});}
export function finishInspection(resourceId){return request('/equipment/'+encodeURIComponent(resourceId)+'/inspection-complete',{});}
export async function authenticate(fields,signup=false){const data=await request(signup?'/signup':'/login',fields);csrf=data.csrf;return data.user;}
export async function logout(){await request('/logout',{});csrf='';await getSession();}
