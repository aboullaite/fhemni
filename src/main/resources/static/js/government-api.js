(function(root,factory){
    const api=factory(root);if(typeof module==='object'&&module.exports)module.exports=api;else root.FhemniGovernmentAPI=api;
})(typeof window==='object'?window:globalThis,function(root){
    async function request(path,{signal}={}){
        const response=await root.fetch(path,{signal,headers:{Accept:'application/json'},credentials:'same-origin'});
        if(!response.ok){const error=new Error(`Request failed (${response.status})`);error.status=response.status;throw error;}
        return response.json();
    }
    function fetchMandate({signal}={}){return request('/api/catalog/government/2026-2031',{signal});}
    function fetchCommitments(state,{signal}={}){const query=new URLSearchParams();for(const key of ['origin','topic','party','q','assessment','page'])if(state[key])query.set(key,state[key]);return request('/api/catalog/government/2026-2031/commitments?'+query,{signal});}
    function fetchInsights(state,{signal}={}){const query=new URLSearchParams();for(const key of ['origin','topic','party','q','assessment'])if(state[key])query.set(key,state[key]);return request('/api/catalog/government/2026-2031/programme-insights?'+query,{signal});}
    async function fetchPreview(options={}){try{return await request('/api/catalog/government/2026-2031/preview',options);}catch(error){if(error.status===404)return null;throw error;}}
    function fetchCommitment(slug,{signal}={}){return request('/api/catalog/government/commitments/'+encodeURIComponent(slug),{signal});}
    function createLatestRequest(load,apply){
        let version=0;
        return async()=>{const current=++version;try{const value=await load();if(current===version)apply(value,null);}catch(error){if(current===version)apply(null,error);}};
    }
    return {request,fetchMandate,fetchCommitments,fetchInsights,fetchCommitment,fetchPreview,createLatestRequest};
});
