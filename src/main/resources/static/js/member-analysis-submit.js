(function(root){'use strict';
 function init(){const doc=root.document,api=root.FhemniMember,locale=()=>root.FhemniI18n?.locale()||'ar',byId=id=>doc.getElementById(id),text=(id,value)=>{byId(id).textContent=value;};
  if(byId('memberSubmit')){
   let state='loading',policy=null,busy=false,owner=null,error=false,epoch=0;
   const form=byId('memberSubmitForm'),url=byId('memberUrl'),language=byId('memberLanguage');
   let storage;try{storage=root.sessionStorage;}catch{const memory=new Map();storage={getItem:k=>memory.get(k),setItem:(k,v)=>memory.set(k,v)};}
   let draft=null;
   function render(){const copy=api.COPY[locale()];for(const[id,key]of Object.entries({memberUrlLabel:'url',memberLanguageLabel:'language',memberHistoryLink:'history',memberSubmitRetry:'retry',memberSignIn:'signin'}))text(id,copy[key]);
    text('memberSubmitButton',copy[busy?'working':'submit']);text('memberLimits',api.limitText(policy,locale()));
    form.hidden=state!=='ready';url.disabled=language.disabled=busy;byId('memberSubmitButton').disabled=busy||!api.canSubmit(policy);
    byId('memberSignIn').hidden=state!=='anonymous';byId('memberSignIn').href=root.FhemniAuth.loginPage('/analyse');byId('memberSubmitRetry').hidden=state!=='error';
    text('memberSubmitStatus',error?api.errorMessage(error,locale()):copy[state==='loading'?'loading':state==='error'?'error':state==='anonymous'?'signin':!policy?.enabled?'unavailable':policy.active?'active':policy.remaining<1?'exhausted':'private']);
   }
   async function load(){const version=++epoch;state='loading';render();try{const session=await root.FhemniAuth.refreshSession();if(version!==epoch)return;if(!session.authenticated){draft?.clear();draft=null;url.value='';owner=null;policy=null;state='anonymous';render();return;}if(owner&&owner!==session.user.id){draft?.clear();url.value='';policy=null;}if(owner!==session.user.id){owner=session.user.id;draft=api.submissionDraft(storage,undefined,owner);const saved=draft.read();url.value=saved?.url||'';language.value=saved?.language||'ary';}policy=await api.request('/api/account/analyses/policy');if(version!==epoch)return;state='ready';render();}catch(failure){if(version!==epoch)return;policy=null;if(failure.status===401){draft?.clear();draft=null;owner=null;url.value='';}state=failure.status===401?'anonymous':'error';render();}}
   form.onsubmit=async event=>{event.preventDefault();if(busy||!api.canSubmit(policy))return;busy=true;error=false;render();try{const job=await draft.send(api,{url:url.value.trim(),language:language.value});draft.confirmed();root.location.assign('/account/analyses/'+encodeURIComponent(job.id));}catch(failure){error=failure;if(failure.status===401){draft?.clear();policy=null;state='anonymous';}}finally{busy=false;render();}};
   byId('memberSubmitRetry').onclick=load;doc.addEventListener('fhemni:localechange',render);root.addEventListener('pageshow',event=>{if(event.persisted)load();});load();
  }
  if(byId('memberHistory')){
   let state='loading',data=null,page=api.readState(root.location.search).page,pager=null,epoch=0,owner=null;
   function render(){const copy=api.COPY[locale()];for(const[id,key]of Object.entries({memberHistoryTitle:'history',memberHistoryCaption:'history',memberNewAnalysis:'title',memberPrivateNote:'private',memberVideoHead:'video',memberStateHead:'state',memberDateHead:'date',memberLanguageHead:'language',memberHistoryRetry:'retry',memberSignIn:'signin'}))text(id,copy[key]);doc.title=copy.history+' — Fhemni';
    byId('memberHistoryContent').hidden=!data;byId('memberHistoryContent').setAttribute('aria-busy',String(state==='loading'));byId('memberSignIn').hidden=state!=='anonymous';byId('memberSignIn').href=root.FhemniAuth.loginPage(root.location.pathname+root.location.search);byId('memberHistoryRetry').hidden=state!=='error';text('memberHistoryStatus',state==='ready'?(data?.items?.length?'':copy.empty):copy[state==='anonymous'?'signin':state==='error'?'error':'loading']);
    const shownPage=api.historyPage(data,page);api.renderHistory(byId('memberHistoryRows'),data?.items||[],locale(),doc,shownPage);if(data){const options={page:shownPage,pages:Math.max(1,Math.ceil(data.totalItems/10)),total:data.totalItems,locale:locale()};if(!pager){pager=root.FhemniUI.createPager({...options,onChange:next=>{page=next;root.history.pushState(null,'','?page='+page);load();}});byId('memberHistoryPager').append(pager);}else pager.update(options);for(const button of pager.querySelectorAll('button'))if(state==='loading')button.disabled=true;}
   }
   async function load(){const version=++epoch;state='loading';render();try{const session=await root.FhemniAuth.refreshSession();if(version!==epoch)return;if(!session.authenticated){data=null;owner=null;state='anonymous';render();return;}if(owner&&owner!==session.user.id){data=null;render();}owner=session.user.id;const result=await api.loadMemberHistory(page);if(version!==epoch)return;data=result;const last=Math.max(1,Math.ceil(data.totalItems/10));if(page>last){page=last;root.history.replaceState(null,'','?page='+page);return load();}state='ready';render();}catch(failure){if(version!==epoch)return;if(failure.status===401){data=null;owner=null;}state=failure.status===401?'anonymous':'error';render();}}
   byId('memberHistoryRetry').onclick=load;doc.addEventListener('fhemni:localechange',render);root.addEventListener('popstate',()=>{page=api.readState(root.location.search).page;load();});root.addEventListener('pageshow',event=>{if(event.persisted)load();});doc.addEventListener('visibilitychange',()=>{if(doc.visibilityState==='visible')load();else{data=null;render();}});load();
  }
 }
 root.document.addEventListener('DOMContentLoaded',init);
})(window);
