(function(root,factory){
    const api=factory(root);if(typeof module==='object'&&module.exports)module.exports=api;else root.FhemniGovernmentAPI=api;
})(typeof window==='object'?window:globalThis,function(root){
    async function request(path,{signal}={}){
        const response=await root.fetch(path,{signal,headers:{Accept:'application/json'},credentials:'same-origin'});
        if(!response.ok)throw new Error(`Request failed (${response.status})`);
        return response.json();
    }
    function fetchMandate({signal}={}){return request('/api/catalog/government/2026-2031',{signal});}
    function createLatestRequest(load,apply){
        let version=0;
        return async()=>{const current=++version;try{const value=await load();if(current===version)apply(value,null);}catch(error){if(current===version)apply(null,error);}};
    }
    return {request,fetchMandate,createLatestRequest};
});
