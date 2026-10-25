(function(root,factory){const api=factory(typeof module==='object'?require('./government-model.js'):root.FhemniGovernmentModel);if(typeof module==='object'&&module.exports)module.exports=api;else root.FhemniGovernmentCharts=api;})(typeof window==='object'?window:globalThis,function(model){
    function chartModel(target,observations){const result=model.comparableObservations(target,observations);if(!result.available)return {...result,bars:[]};const latest=result.rows.at(-1);const bars=[{kind:'observed',value:Number(latest.value),period:latest.periodEnd},{kind:'target',value:Number(target.targetValue),period:target.targetDate}];const max=Math.max(...bars.map(row=>row.value));return {...result,bars:bars.some(row=>row.value<0)?[]:bars.map(row=>({...row,width:max>0?row.value/max*100:0})),unit:target.unit};}
    function timeSeries(rows){
        if(rows.length<2||rows.some(row=>!model.validDate(row.periodEnd)||row.value==null||!Number.isFinite(Number(row.value))))return [];
        const ordered=[...rows].sort((a,b)=>a.periodEnd.localeCompare(b.periodEnd)),start=Date.parse(ordered[0].periodEnd),end=Date.parse(ordered.at(-1).periodEnd);
        if(start===end)return [];
        const min=Math.min(0,...ordered.map(row=>Number(row.value))),max=Math.max(0,...ordered.map(row=>Number(row.value)));
        return ordered.map(row=>({...row,x:20+(Date.parse(row.periodEnd)-start)/(end-start)*560,y:160-(max===min?0:(Number(row.value)-min)/(max-min)*140)}));
    }
    function seriesLabels(rows,locale){const min=Math.min(0,...rows.map(row=>Number(row.value))),max=Math.max(0,...rows.map(row=>Number(row.value)));return {values:[min,(min+max)/2,max].map(value=>model.number(value,locale)),dates:[rows[0],rows.at(-1)].map(row=>model.date(row.periodEnd,locale))};}
    function renderChart(container,target,observations,locale='ar',doc=globalThis.document){
        const view=chartModel(target,observations),copy=model.COPY[locale];container.replaceChildren();
        const el=(tag,text)=>{const node=doc.createElement(tag);if(text!=null)node.textContent=text;return node;};
        container.append(el('p',`${copy.target}: ${model.number(target.targetValue,locale)} ${target.unit}${target.targetDate?' · '+model.date(target.targetDate,locale):''}`));
        container.append(el('p',target.baseline==null?copy.noBaseline:`${copy.baseline}: ${model.number(target.baseline,locale)} ${target.unit}${target.baselineDate?' · '+model.date(target.baselineDate,locale):''}`));
        if(!view.available){container.append(el('p',copy.notComparable));return view;}
        container.append(el('p',copy.quantityNote));
        for(const row of view.bars){const group=el('div');group.className='government-chart-row';const caption=el('p',`${copy[row.kind]} · ${model.number(row.value,locale)} ${target.unit}${row.period?' · '+model.date(row.period,locale):''}`);const track=el('div');track.className='government-chart-track';track.setAttribute('aria-hidden','true');const bar=el('span');bar.className='government-chart-bar '+row.kind;bar.style.width=row.width+'%';track.append(bar);group.append(caption,track);container.append(group);}
        const points=timeSeries(view.rows);
        if(points.length&&doc.createElementNS){
            const figure=el('figure'),caption=el('figcaption',`${copy.observations} · ${target.unit} · ${model.date(points[0].periodEnd,locale)} — ${model.date(points.at(-1).periodEnd,locale)}`);
            const svg=doc.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('viewBox','0 0 600 180');svg.setAttribute('role','img');svg.setAttribute('aria-label',caption.textContent);svg.classList.add('government-timeseries');
            const line=doc.createElementNS('http://www.w3.org/2000/svg','polyline');line.setAttribute('points',points.map(point=>`${point.x},${point.y}`).join(' '));line.setAttribute('fill','none');line.setAttribute('stroke','currentColor');line.setAttribute('stroke-width','3');svg.append(line);
            for(const point of points){const circle=doc.createElementNS('http://www.w3.org/2000/svg','circle');circle.setAttribute('cx',point.x);circle.setAttribute('cy',point.y);circle.setAttribute('r','4');circle.setAttribute('fill','currentColor');const title=doc.createElementNS('http://www.w3.org/2000/svg','title');title.textContent=`${model.date(point.periodEnd,locale)}: ${model.number(point.value,locale)} ${target.unit}`;circle.append(title);svg.append(circle);}
            const labels=seriesLabels(points,locale);
            const scale=el('div');scale.className='government-chart-scale';for(const value of labels.values)scale.append(el('span',value));
            const dates=el('div');dates.className='government-chart-dates';for(const date of labels.dates)dates.append(el('span',date));
            const tableLink=el('a',copy.observations);tableLink.href='#commitmentObservations';
            const plot=el('div');plot.className='government-series-layout';svg.setAttribute('preserveAspectRatio','none');plot.append(scale,svg);
            figure.append(caption,plot,dates,tableLink);container.append(figure);
        }
        const latest=view.rows.at(-1);
        container.append(el('p',`${copy.period}: ${model.date(latest.periodStart,locale)} — ${model.date(latest.periodEnd,locale)}`));
        for(const [index,id]of (latest.evidenceIds||[]).entries()){const link=el('a',`${copy.evidenceLink} ${index+1}`);link.href='#evidence-'+id;link.className='government-evidence-link';link.onclick=()=>{const sources=doc.getElementById('commitmentSources');if(sources)sources.open=true;};container.append(link);}
        return view;
    }
    return {chartModel,timeSeries,seriesLabels,renderChart};
});
