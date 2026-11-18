// Purpose names, aliases and equipment needs come from Oracle; context guards stay explicit.
export const purposes=[];
export const recommendationReasons={};
let definitions=[],keywords=[],equipment=[];
export function configureRecommendations(data){definitions=data.purposes;keywords=data.keywords;equipment=data.equipment;purposes.splice(0,purposes.length,...definitions.map(p=>p.PURPOSE_NAME));for(const name of Object.keys(recommendationReasons))delete recommendationReasons[name];for(const p of definitions)recommendationReasons[p.PURPOSE_NAME]=p.DESCRIPTION_TEXT||'등록된 목적과 이용 조건을 확인해주세요.';}
export const normalize=value=>String(value||'').normalize('NFKC').toLowerCase().replace(/[\s.,!?~…·'"“”‘’()[\]{}:;_\-/]+/g,'');
function matches(input,rule){const word=normalize(rule.KEYWORD_TEXT),text=normalize(input);if(!word)return false;if(rule.MATCH_MODE==='EXACT')return word===text;
 if(/^[a-z0-9\s]+$/i.test(rule.KEYWORD_TEXT)){const escaped=rule.KEYWORD_TEXT.trim().replace(/[.*+?^${}()|[\]\\]/g,'\\$&').replace(/\s+/g,'\\s*');return new RegExp('(^|[^a-z0-9])'+escaped+'($|[^a-z0-9])','i').test(input.normalize('NFKC'));}
 return text.includes(word);
}
export function analyzeInput(input){
 const text=normalize(input),peopleValues=[...input.matchAll(/([+-]?\d+(?:\.\d+)?)\s*명/g)].map(m=>Number(m[1])),unique=[...new Set(peopleValues)];
 const invalid=peopleValues.some(n=>!Number.isSafeInteger(n)||n<1),ambiguous=unique.length>1||/\d+\s*명?\s*(~|–|—|-|에서|부터)\s*\d+\s*명/.test(input);
 const editOnly=/영상편집|동영상편집|편집작업/.test(text)&&!/촬영|녹화|찍/.test(text);
 const excludesRecording=editOnly||/(촬영|녹화|영상)(은|는|을|를)?(없이|안하고|안함|안해|하지않|은안|는안)|촬영안|녹화안|편집만/.test(text);
 const excludesCamera=/카메라(는|가|를)?(없이|필요없|안쓰)/.test(text),excludesAudio=/(마이크|녹음|음성)(는|은|이|을)?(없이|필요없|안쓰|안함)/.test(text);
 const production=/촬영|제작|녹화|찍|만들|라이브|스트리밍/.test(text);
 const consuming=/(유튜브|youtube|영상|동영상|브이로그|쇼츠|릴스|인터뷰)(을|를|만)?(시청|감상|보기|보는|보고싶)/.test(text)&&!production||/(유튜브|youtube|영상).*(보고서|레포트|리포트|자료조사|분석|참고)/.test(text)&&!production&&!/영상(보고서|레포트|리포트)/.test(text);
 const assignment=/과제|수업|조별|팀플|캡스톤|졸업작품|수행평가/.test(text),video=/영상|동영상|촬영|녹화|찍|유튜브|브이로그|쇼츠|릴스/.test(text)&&!excludesRecording&&!consuming;
 const interviewPractice=/면접|자기소개|모의인터뷰|mockinterview|jobinterview/.test(text);
 const scores=new Map(),matched=[];
 for(const p of definitions){const own=keywords.filter(k=>k.PURPOSE_ID===p.PURPOSE_ID),included=own.filter(k=>k.EFFECT_CODE==='INCLUDE'&&matches(input,k)),excluded=own.some(k=>k.EFFECT_CODE==='EXCLUDE'&&matches(input,k));if(included.length&&!excluded){scores.set(p.PURPOSE_CODE,Math.max(...included.map(k=>Number(k.PRIORITY_NO))));matched.push(...included.map(k=>k.KEYWORD_TEXT));}}
 if(assignment&&video)scores.set('ASSIGNMENT_VIDEO',100);
 if(excludesRecording||consuming){for(const code of ['INTERVIEW','ASSIGNMENT_VIDEO','CONTENT_VIDEO'])scores.delete(code);}
 if(interviewPractice){scores.set('JOB_INTERVIEW',110);scores.delete('INTERVIEW');if(!/과제|졸업|논문|학습|공부/.test(text))scores.delete('STUDY');if(!/발표|프레젠테이션|스피치|피칭/.test(text))scores.delete('PRESENTATION');if(!/회의|토론|토의/.test(text))scores.delete('MEETING');scores.delete('CONTENT_VIDEO');}
 if(scores.has('INTERVIEW')||scores.has('ASSIGNMENT_VIDEO'))scores.delete('CONTENT_VIDEO');
 if(scores.has('ASSIGNMENT_VIDEO')||scores.has('PRESENTATION'))scores.delete('STUDY');
 if(scores.has('ASSIGNMENT_VIDEO')&&!/회의|토론|토의|미팅/.test(text))scores.delete('MEETING');
 if(scores.has('MEETING')&&/과제|스터디|보고서/.test(text)&&!/공부|학습|시험/.test(text))scores.delete('STUDY');
 const genericAssignment=/^(과제|팀과제|조별과제|조별과제\d*명?)$/.test(text);if(genericAssignment){scores.set('STUDY',40);scores.set('MEETING',30);scores.set('ASSIGNMENT_VIDEO',20);}
 const equipmentQuery=['카메라','프로젝터','마이크'].find(w=>text.includes(w)),facilityQuery=['회의실','스터디룸','촬영실'].find(w=>text.includes(w));
 if(/^(카메라|프로젝터|마이크|회의실|스터디룸|촬영실)\d*명?$/.test(text))scores.clear();
 const candidates=definitions.filter(p=>scores.has(p.PURPOSE_CODE)).sort((a,b)=>scores.get(b.PURPOSE_CODE)-scores.get(a.PURPOSE_CODE)).map(p=>p.PURPOSE_NAME);
 return {candidates,matchedKeywords:[...new Set(matched)],people:!invalid&&!ambiguous?unique[0]:undefined,peopleStatus:ambiguous?'ambiguous':invalid?'invalid':unique.length?'valid':'none',intent:candidates.length>1?'multiple':candidates.length?'recognized':equipmentQuery||facilityQuery?'direct':video?'video':'unknown',equipmentQuery,facilityQuery,excludesRecording,excludesCamera,excludesAudio,recordingRequested:production&&!excludesRecording&&!consuming,presentation:scores.has('PRESENTATION')};
}
export function equipmentForPurpose(name,analysis){const purpose=definitions.find(p=>p.PURPOSE_NAME===name);if(!purpose)return [];return equipment.filter(r=>r.PURPOSE_ID===purpose.PURPOSE_ID).filter(r=>!(r.CATEGORY_LABEL==='카메라'&&(analysis.excludesRecording||analysis.excludesCamera))&&!(r.CATEGORY_LABEL==='마이크'&&(analysis.excludesRecording||analysis.excludesAudio))).filter(r=>purpose.PURPOSE_CODE!=='JOB_INTERVIEW'||analysis.recordingRequested).map(r=>({category:r.CATEGORY_LABEL,role:(r.NEED_LEVEL==='REQUIRED'?'기본':'선택')+' · '+r.REASON_TEXT}));}
