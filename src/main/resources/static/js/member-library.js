(function(root){'use strict';
 function init(){const doc=root.document;if(!doc.getElementById('memberLibraryPage'))return;
  const api=root.FhemniMember,get=id=>doc.getElementById(id),locale=()=>root.FhemniI18n?.locale()||'ar';
  let state='loading',owner=null,policy=null,data=null,page=api.readState(root.location.search).page,epoch=0,busy=false,error=null,pager=null,draft=null,timer=null;
  const form=get('memberSubmitForm'),url=get('memberUrl'),language=get('memberLanguage');
  let storage;try{storage=root.sessionStorage;}catch{const entries=new Map();storage={getItem:k=>entries.get(k),setItem:(k,v)=>entries.set(k,v),removeItem:k=>entries.delete(k)};}
  const text=(id,value)=>{get(id).textContent=value;};
  function render(){const copy=api.COPY[locale()];doc.title=copy.title+' — Fhemni';
   for(const node of doc.querySelectorAll('[data-member-copy]'))node.textContent=copy[node.getAttribute('data-member-copy')]||'';
   get('memberPitch').hidden=state!=='anonymous';get('memberWorkspace').hidden=!owner||state==='loading';
   get('memberPitchLogin').href=root.FhemniAuth.loginPage('/analyse');get('memberPageRetry').hidden=state!=='error';
   text('memberPageStatus',state==='loading'?copy.loading:state==='error'?copy.error:'');
   text('memberSubmitButton',copy[busy?'working':'submit']);get('memberSubmitButton').disabled=busy||!api.canSubmit(policy);url.disabled=language.disabled=busy;
   text('memberLimits',api.limitText(policy,locale()));text('memberChatLimits',api.chatLimitText(policy,locale()));
   text('memberSubmitStatus',error?api.errorMessage(error,locale()):!policy?'':!policy.enabled?copy.unavailable:policy.active?copy.active:policy.remaining<1?copy.exhausted:copy.private);
   get('memberLibraryEmpty').hidden=!data||data.items.length>0;
   api.renderLibrary(get('memberLibraryRows'),data?.items||[],locale(),doc,page);
   get('memberLibraryPager').hidden=!data||data.totalItems<=10;
   if(data){const options={page,pages:Math.max(1,Math.ceil(data.totalItems/10)),total:data.totalItems,locale:locale()};if(!pager){pager=root.FhemniUI.createPager({...options,onChange:next=>{page=next;root.history.pushState(null,'','?page='+page);load();}});get('memberLibraryPager').append(pager);}else pager.update(options);}
  }
  function clear(){owner=null;policy=null;data=null;draft?.clear();draft=null;url.value='';error=null;busy=false;get('memberLibraryRows').replaceChildren();}
  async function load(){root.clearTimeout(timer);const version=++epoch;state='loading';data=null;policy=null;busy=false;render();try{
   const session=await root.FhemniAuth.refreshSession();if(version!==epoch)return;
   if(!session.authenticated){clear();state='anonymous';render();return;}
   if(owner&&owner!==session.user.id)clear();
   if(owner!==session.user.id){owner=session.user.id;draft=api.submissionDraft(storage,undefined,owner);const saved=draft.read();url.value=saved?.url||'';language.value=saved?.language||'ary';}
   const values=await Promise.allSettled([api.request('/api/account/analyses/policy'),api.loadMemberHistory(page)]);if(version!==epoch)return;
   const denied=values.find(value=>value.status==='rejected'&&value.reason.status===401);if(denied)throw denied.reason;
   policy=values[0].status==='fulfilled'?values[0].value:null;data=values[1].status==='fulfilled'?values[1].value:null;
   const last=data?Math.max(1,Math.ceil(data.totalItems/10)):page;if(page>last){page=last;root.history.replaceState(null,'','?page='+page);return load();}
   state=values.every(value=>value.status==='fulfilled')?'ready':'error';render();
   if(data?.items.some(row=>['QUEUED','PROCESSING'].includes(row.state))&&doc.visibilityState!=='hidden')timer=root.setTimeout(load,10000);
  }catch(failure){if(version!==epoch)return;if(failure.status===401){clear();state='anonymous';}else state='error';render();}}
  form.onsubmit=async event=>{event.preventDefault();if(busy||!api.canSubmit(policy))return;const version=epoch;busy=true;error=null;render();try{
   const job=await draft.send(api,{url:url.value.trim(),language:language.value});if(version!==epoch)return;draft.confirmed();root.location.assign('/account/analyses/'+encodeURIComponent(job.id));
  }catch(failure){if(version!==epoch)return;if(failure.status===401){clear();state='anonymous';}else{error=failure;try{const refreshed=await api.request('/api/account/analyses/policy');if(version===epoch)policy=refreshed;}catch{if(version===epoch)policy=null;}}}finally{if(version===epoch){busy=false;render();}}};
  get('memberPageRetry').onclick=load;doc.addEventListener('fhemni:localechange',render);root.addEventListener('popstate',()=>{page=api.readState(root.location.search).page;load();});root.addEventListener('pageshow',event=>{if(event.persisted)load();});
  doc.addEventListener('visibilitychange',()=>{if(doc.visibilityState==='visible')load();else{root.clearTimeout(timer);++epoch;data=null;policy=null;state='loading';render();}});load();
 }
 root.document.addEventListener('DOMContentLoaded',init);
})(window);
